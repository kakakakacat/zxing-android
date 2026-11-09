/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
#include "native_decoder.h"
#include <new>
#include <utility>
#include "BinaryBitmap.h"
#include "GlobalHistogramBinarizer.h"
#include "HybridBinarizer.h"
#include "ReadBarcode.h"
#include "Version.h"

namespace scanner_native {
NativeDecoder::NativeDecoder() {
  // Configured to attempt the same work the Java backend attempts, which is the only way
  // a comparison between them measures the decoders rather than their defaults.
  //
  // zxing-cpp defaults tryHarder, tryRotate, tryInvert and tryDownscale all to true: it
  // will look for codes at 90/180/270 degrees, for inverted codes, and in downscaled
  // copies of the image. This fork's Java reader does none of that -- L2Config.DEFAULT is
  // explicitly "no TRY_HARDER", and the preview path rotates frames itself rather than
  // asking the decoder to search rotations. Leaving these on is not neutrality; it hands
  // the native side several times the work and then reports it as slower.
  //
  // This is the effort level, not an optimisation. A caller that wants rotation or
  // inversion search should get it through the hint set on both backends alike.
  options.setTryHarder(false);
  options.setTryRotate(false);
  options.setTryInvert(false);
  options.setTryDownscale(false);
  // How bytes become a String. zxing-cpp defaults to TextMode::HRI, which replaces every
  // non-graphical character with a symbolic stand-in -- ASCII 29 comes back as "<GS>" or
  // "\u241d". This library has always returned the character itself, and a host that
  // parses a GS1 separator or a MaxiCode structured message gets a string it cannot
  // parse. Measured before and after on the corpus: 11 of the 61 samples that the native
  // backend rendered differently are this and nothing else (BENCHMARKS.md section 9.1).
  //
  // Plain is zxing-cpp's own pre-2.0 default and transcodes by ECI or guessed charset,
  // which is what MultiFormatReader does.
  options.setTextMode(ZXing::TextMode::Plain);
  options.setMaxNumberOfSymbols(1);
}

namespace {
/** The binarizer `ReadBarcode` would build, for the two this decoder can be set to. */
std::unique_ptr<ZXing::BinaryBitmap> createBitmap(ZXing::Binarizer binarizer,
                                                  const ZXing::ImageView& image) {
  if (binarizer == ZXing::Binarizer::GlobalHistogram) {
    return std::make_unique<ZXing::GlobalHistogramBinarizer>(image);
  }
  return std::make_unique<ZXing::HybridBinarizer>(image);
}

/**
 * Whether `read` may take the cached-reader path for this frame and these options.
 *
 * Everything `ReadBarcodes` does between the image and `MultiFormatReader::read` has to
 * be a no-op for that to be the same decode: no luminance extraction (a dense greyscale
 * plane is already what the binarizers want), no downscale pyramid, no inversion pass,
 * and not the single-line `isPure` shortcut.
 */
bool canReuseReader(const ZXing::ReaderOptions& options, const ZXing::ImageView& image) {
  return image.format() == ZXing::ImageFormat::Lum && image.pixStride() == 1 &&
         !options.isPure() && !options.tryInvert() && !options.tryDownscale() &&
         // setReaderOptions is private; the text mode it carries is passed explicitly at
         // the JNI boundary instead, and a character set has no such second route.
         options.characterSet() == ZXing::CharacterSet::Unknown &&
         (options.binarizer() == ZXing::Binarizer::LocalAverage ||
          options.binarizer() == ZXing::Binarizer::GlobalHistogram);
}
}  // namespace

ZXing::Barcode NativeDecoder::read(const ZXing::ImageView& image) {
  if (!canReuseReader(options, image)) {
    // maxNumberOfSymbols is already 1, so this is ReadBarcode without its options copy.
    auto barcodes = ZXing::ReadBarcodes(image, options);
    return barcodes.empty() ? ZXing::Barcode() : std::move(barcodes.front());
  }
  if (!reader) {
    reader = std::make_unique<ZXing::MultiFormatReader>(options);
  }
  auto bitmap = createBitmap(options.binarizer(), image);
  auto barcodes = reader->read(*bitmap, 1);
  return barcodes.empty() ? ZXing::Barcode() : std::move(barcodes.front());
}

std::unique_ptr<NativeDecoder> createNativeDecoder() noexcept {
  try {
    return std::unique_ptr<NativeDecoder>(new (std::nothrow) NativeDecoder());
  } catch (...) {
    return nullptr;
  }
}

std::string nativeBuildInfo() {
  std::string info;
#if defined(__aarch64__)
  info += "abi=arm64-v8a";
#elif defined(__arm__)
  info += "abi=armeabi-v7a";
#elif defined(__x86_64__)
  info += "abi=x86_64";
#else
  info += "abi=other";
#endif
#if defined(__ARM_NEON)
  info += ";neon=1";
#else
  info += ";neon=0";
#endif
#if defined(__ARM_FEATURE_CRC32)
  info += ";crc32=1";
#endif
  info += ";cxx=20;zxingcpp=";
  info += ZXING_VERSION_STR;
  return info;
}

std::string NativeDecoder::diagnostics() const {
  std::string out;
  out += "hits=" + std::to_string(hits);
  out += ";misses=" + std::to_string(misses);
  out += ";formatErrors=" + std::to_string(formatErrors);
  out += ";checksumErrors=" + std::to_string(checksumErrors);
  out += ";unsupportedErrors=" + std::to_string(unsupportedErrors);
  out += ";internalErrors=" + std::to_string(internalErrors);
  if (!lastError.empty()) {
    out += ";lastError=" + lastError;
  }
  return out;
}
}  // namespace scanner_native

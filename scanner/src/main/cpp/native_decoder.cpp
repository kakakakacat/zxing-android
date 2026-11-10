/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
#include "native_decoder.h"
#include <cstddef>
#include <limits>
#include <new>
#include <utility>
#include "BinaryBitmap.h"
#include "BitMatrix.h"
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

ZXing::ImageView NativeDecoder::stageImage() const noexcept {
  // Only called with a frame held; `stageStorage.data()` is the crop's top-left pixel and
  // the strides are the caller's, so this is the same view `prepareStages` was given.
  return ZXing::ImageView(stageStorage.data(), stageWidth, stageHeight,
                          ZXing::ImageFormat::Lum, stageRowStride, stagePixelStride);
}

bool NativeDecoder::prepareStages(const ZXing::ImageView& image, std::string& error) {
  releaseStages();
  if (image.width() <= 0 || image.height() <= 0 || image.rowStride() <= 0 ||
      image.pixStride() <= 0 || image.data() == nullptr) {
    error = "a stage split needs a frame with positive dimensions and strides";
    return false;
  }
  if (!canReuseReader(options, image)) {
    // Refused rather than measured: see prepareStages' contract. Without this, a caller
    // that had left tryDownscale or tryInvert on would get two stage timings for a decode
    // whose real path is ZXing::ReadBarcodes, which does neither of these stages once.
    error = "these options decode through ZXing::ReadBarcodes, which this split does not "
            "reproduce; a stage timing from it would describe work the frame never does";
    return false;
  }
  const int64_t rowBytes = static_cast<int64_t>(image.width() - 1) * image.pixStride() + 1;
  const int64_t required =
      static_cast<int64_t>(image.height() - 1) * image.rowStride() + rowBytes;
  if (required <= 0 || static_cast<uint64_t>(required) >
                           static_cast<uint64_t>(std::numeric_limits<ptrdiff_t>::max())) {
    error = "the frame's dimensions and strides do not describe a copyable span";
    return false;
  }
  const uint8_t* const begin = image.data();
  stageStorage.assign(begin, begin + required);
  stageWidth = image.width();
  stageHeight = image.height();
  stageRowStride = image.rowStride();
  stagePixelStride = image.pixStride();
  auto bitmap = createBitmap(options.binarizer(), stageImage());
  if (bitmap->getBitMatrix() == nullptr) {
    releaseStages();
    error = "the binarizer produced no matrix for this frame";
    return false;
  }
  stageBitmap = std::move(bitmap);
  if (!reader) {
    reader = std::make_unique<ZXing::MultiFormatReader>(options);
  }
  return true;
}

int64_t NativeDecoder::binarizeOnly() {
  if (stageStorage.empty()) {
    return -1;
  }
  auto bitmap = createBitmap(options.binarizer(), stageImage());
  const ZXing::BitMatrix* matrix = bitmap->getBitMatrix();
  if (matrix == nullptr) {
    return -1;
  }
  return static_cast<int64_t>(matrix->width()) * matrix->height();
}

bool NativeDecoder::detectOnly() {
  if (!stageBitmap) {
    return false;
  }
  if (!reader) {
    // An option changed between prepare and here, so the readers were dropped. Rebuilt
    // rather than refused -- a benchmark's warmup absorbs one construction -- but the
    // matrix in stageBitmap was thresholded under the binarizer the options had then.
    reader = std::make_unique<ZXing::MultiFormatReader>(options);
  }
  return !reader->read(*stageBitmap, 1).empty();
}

void NativeDecoder::releaseStages() noexcept {
  stageBitmap.reset();
  stageStorage.clear();
  stageStorage.shrink_to_fit();
  stageWidth = 0;
  stageHeight = 0;
  stageRowStride = 0;
  stagePixelStride = 0;
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

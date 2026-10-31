/*
 * Copyright (C) 2026 ZXing Android maintenance fork contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// The JNI bridge to zxing-cpp. Deliberately thin: it owns a ReaderOptions, converts one
// greyscale frame into an ImageView, and converts one result back. Anything that can be
// decided in Java is decided in Java, because a mistake here is a native crash rather
// than an exception.

#include <jni.h>

// zxing-cpp carries no NEON intrinsics. It relies on the auto-vectorizer, and it tests
// __ARM_NEON to decide whether to copy a strided column into a packed buffer first --
// worth it only because the vectorizer can then do the threshold pass several lanes at a
// time (GlobalHistogramBinarizer.cpp). If the macro is not defined that path is compiled
// out silently and nothing else in the build notices.
//
// So the macro is asserted rather than assumed. On arm64 NEON is part of the ARMv8-A
// baseline. On armeabi-v7a NDK 29 refuses to turn it off -- ANDROID_ARM_NEON=FALSE is
// rejected outright with "Disabling Neon is no longer supported" -- so today this cannot
// fire through that route. What it does catch is an -mfpu flag that quietly drops NEON,
// which is a one-line change away in any build file and which nothing else would report.
// Verified: adding -mfpu=vfpv3-d16 to the armeabi-v7a build makes this fire.
//
// These translation units share the toolchain flags with zxing-cpp's, since it is added
// to this same CMake project.
#if defined(__arm__) || defined(__aarch64__)
#if !defined(__ARM_NEON)
#error "__ARM_NEON is not defined for this ARM ABI: zxing-cpp's packed-copy path would \
be compiled out. Check for an -mfpu flag that drops NEON."
#endif
#endif

#include <cstdint>
#include <exception>
#include <memory>
#include <string>

#include "BarcodeFormat.h"
#include "Version.h"
#include "ReadBarcode.h"
#include "ReaderOptions.h"

namespace {

/** One decoder's state. Owned by the Java object through a long handle. */
struct NativeDecoder {
  ZXing::ReaderOptions options;
};

NativeDecoder* fromHandle(jlong handle) {
  return reinterpret_cast<NativeDecoder*>(static_cast<uintptr_t>(handle));
}

std::string toStdString(JNIEnv* env, jstring value) {
  if (value == nullptr) {
    return {};
  }
  const char* chars = env->GetStringUTFChars(value, nullptr);
  if (chars == nullptr) {
    return {};
  }
  std::string copy(chars);
  env->ReleaseStringUTFChars(value, chars);
  return copy;
}

}  // namespace

extern "C" {

/**
 * What this library was built with, for the record and for a device test.
 *
 * <p>Reports the compile-time facts, not a runtime probe: the question is what the
 * shipped artifact was compiled with, which no amount of runtime feature detection
 * answers.
 */
JNIEXPORT jstring JNICALL
Java_io_github_kakakakacat_zxing_android_scanner_NativeFrameDecoder_nativeBuildInfo(
    JNIEnv* env, jclass) {
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
  info += ";zxingcpp=";
  info += ZXING_VERSION_STR;
  return env->NewStringUTF(info.c_str());
}

JNIEXPORT jlong JNICALL
Java_io_github_kakakakacat_zxing_android_scanner_NativeFrameDecoder_nativeCreate(
    JNIEnv*, jclass) {
  auto* decoder = new (std::nothrow) NativeDecoder();
  if (decoder == nullptr) {
    return 0;
  }
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
  decoder->options.setTryHarder(false);
  decoder->options.setTryRotate(false);
  decoder->options.setTryInvert(false);
  decoder->options.setTryDownscale(false);
  // How bytes become a String. zxing-cpp defaults to TextMode::HRI, which replaces every
  // non-graphical character with a symbolic stand-in -- ASCII 29 comes back as "<GS>" or
  // "\u241d". This library has always returned the character itself, and a host that
  // parses a GS1 separator or a MaxiCode structured message gets a string it cannot
  // parse. Measured before and after on the corpus: 11 of the 61 samples that the native
  // backend rendered differently are this and nothing else (BENCHMARKS.md section 9.1).
  //
  // Plain is zxing-cpp's own pre-2.0 default and transcodes by ECI or guessed charset,
  // which is what MultiFormatReader does.
  decoder->options.setTextMode(ZXing::TextMode::Plain);
  decoder->options.setMaxNumberOfSymbols(1);
  return static_cast<jlong>(reinterpret_cast<uintptr_t>(decoder));
}

/**
 * The effort level, as this fork's single TRY_HARDER hint maps onto zxing-cpp's four
 * flags.
 *
 * <p>tryHarder and tryRotate, and deliberately not tryInvert or tryDownscale. TRY_HARDER
 * in this decoder means two things: the 1D readers scan many more rows
 * (OneDReader.doDecode), and a failed 1D pass retries the image rotated 90 degrees
 * (OneDReader.decode). It has never meant "look for an inverted code" or "look in a
 * downscaled copy", and mapping it onto those would hand the native backend work the
 * Java one does not do and then compare the two.
 */
JNIEXPORT void JNICALL
Java_io_github_kakakakacat_zxing_android_scanner_NativeFrameDecoder_nativeSetTryHarder(
    JNIEnv*, jclass, jlong handle, jboolean tryHarder) {
  NativeDecoder* decoder = fromHandle(handle);
  if (decoder == nullptr) {
    return;
  }
  decoder->options.setTryHarder(tryHarder == JNI_TRUE);
  decoder->options.setTryRotate(tryHarder == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_io_github_kakakakacat_zxing_android_scanner_NativeFrameDecoder_nativeDestroy(
    JNIEnv*, jclass, jlong handle) {
  delete fromHandle(handle);
}

/**
 * @param formats a comma-separated list of zxing-cpp format names, or empty for all.
 *                Built in Java, where an unknown name is a caught exception rather than
 *                undefined behaviour.
 */
JNIEXPORT void JNICALL
Java_io_github_kakakakacat_zxing_android_scanner_NativeFrameDecoder_nativeSetFormats(
    JNIEnv* env, jclass, jlong handle, jstring formats) {
  NativeDecoder* decoder = fromHandle(handle);
  if (decoder == nullptr) {
    return;
  }
  const std::string names = toStdString(env, formats);
  try {
    decoder->options.setFormats(names.empty() ? ZXing::BarcodeFormats{}
                                              : ZXing::BarcodeFormatsFromString(names));
  } catch (const std::exception& e) {
    // An unparsed format name throws in zxing-cpp. Letting a C++ exception cross the JNI
    // boundary is undefined behaviour, so it becomes a Java exception here. Java maps
    // every format explicitly and rejects unmapped ones, so reaching this means the two
    // name tables have drifted apart -- which must be loud, not silent, because the
    // failure it would otherwise cause is "the format restriction stopped applying".
    jclass illegalArgument = env->FindClass("java/lang/IllegalArgumentException");
    if (illegalArgument != nullptr) {
      env->ThrowNew(illegalArgument, (std::string("zxing-cpp rejected the format list \"")
          + names + "\": " + e.what()).c_str());
    }
  }
}

/**
 * Decodes one greyscale frame.
 *
 * @return {text, formatName, x0,y0, x1,y1, x2,y2, x3,y3} as strings, or null when nothing
 *         was found. A miss is the ordinary case on live preview, so it must not cost an
 *         exception.
 *
 * The four corner points are carried back so a result still has somewhere to draw. The
 * Java reader reports candidate points *while* it searches, through a hint callback;
 * zxing-cpp has no such streaming callback, so only the points of a finished symbol
 * survive the switch.
 */
JNIEXPORT jobjectArray JNICALL
Java_io_github_kakakakacat_zxing_android_scanner_NativeFrameDecoder_nativeDecode(
    JNIEnv* env, jclass, jlong handle, jbyteArray luminance, jint width, jint height) {
  NativeDecoder* decoder = fromHandle(handle);
  if (decoder == nullptr || luminance == nullptr || width <= 0 || height <= 0) {
    return nullptr;
  }
  const jsize length = env->GetArrayLength(luminance);
  if (static_cast<int64_t>(length) < static_cast<int64_t>(width) * height) {
    // Short buffer. Reading it would be a native out-of-bounds read, so refuse instead.
    return nullptr;
  }

  // Critical section: no JNI calls and no allocation while the array is pinned, so the
  // decode result is copied out only after it is released.
  auto* pixels = static_cast<uint8_t*>(env->GetPrimitiveArrayCritical(luminance, nullptr));
  if (pixels == nullptr) {
    return nullptr;
  }
  std::string text;
  std::string format;
  int corners[8] = {0, 0, 0, 0, 0, 0, 0, 0};
  try {
    const ZXing::ImageView image(pixels, width, height, ZXing::ImageFormat::Lum);
    const ZXing::Barcode barcode = ZXing::ReadBarcode(image, decoder->options);
    if (barcode.isValid()) {
      // RSS-Expanded is the one format where this library's Java reader prints the GS1
      // application identifiers in parentheses -- "(11)100224(17)110224" -- because
      // RSSExpandedReader parses the AI stream itself. zxing-cpp does that only in
      // TextMode::HRI, and HRI escapes every non-graphical character everywhere else,
      // which is what Plain is set for. So the mode is chosen per symbol rather than per
      // decoder. Measured both ways on the corpus: Plain alone loses 174 RSS-Expanded
      // samples, HRI alone loses 11 MaxiCode and PDF417 ones (BENCHMARKS.md section 9.1).
      //
      // Both expanded variants, not just the one: a stacked symbol comes back as
      // DataBarExpStk and testing only DataBarExpanded left 56 of them rendered without
      // their parentheses. The non-expanded DataBar family is deliberately not here --
      // RSS14Reader returns the bare 14 digits, and the AI prefix is removed in Java.
      const bool gs1Parentheses = barcode.format() == ZXing::BarcodeFormat::DataBarExp
          || barcode.format() == ZXing::BarcodeFormat::DataBarExpStk;
      text = gs1Parentheses ? barcode.text(ZXing::TextMode::HRI) : barcode.text();
      format = ZXing::ToString(barcode.format());
      const auto& position = barcode.position();
      const ZXing::PointI points[4] = {position.topLeft(), position.topRight(),
                                       position.bottomRight(), position.bottomLeft()};
      for (int i = 0; i < 4; i++) {
        corners[i * 2] = points[i].x;
        corners[i * 2 + 1] = points[i].y;
      }
    }
  } catch (...) {
    // A throwing decoder must not take the process with it, and must not leave the array
    // pinned either. A frame that cannot be read is a miss.
    format.clear();
  }
  env->ReleasePrimitiveArrayCritical(luminance, pixels, JNI_ABORT);

  if (format.empty()) {
    return nullptr;
  }

  jclass stringClass = env->FindClass("java/lang/String");
  if (stringClass == nullptr) {
    return nullptr;
  }
  jobjectArray result = env->NewObjectArray(10, stringClass, nullptr);
  if (result == nullptr) {
    return nullptr;
  }
  env->SetObjectArrayElement(result, 0, env->NewStringUTF(text.c_str()));
  env->SetObjectArrayElement(result, 1, env->NewStringUTF(format.c_str()));
  for (int i = 0; i < 8; i++) {
    env->SetObjectArrayElement(result, 2 + i,
                               env->NewStringUTF(std::to_string(corners[i]).c_str()));
  }
  return result;
}

}  // extern "C"

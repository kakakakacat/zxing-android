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
  // Match what the Java backend does on a preview frame: look for one symbol and return.
  // tryHarder/tryRotate are deliberately left at their defaults rather than tuned here --
  // tuning before the A/B of phase 5 item 10 would make the comparison meaningless.
  decoder->options.setMaxNumberOfSymbols(1);
  return static_cast<jlong>(reinterpret_cast<uintptr_t>(decoder));
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
 * @return {text, formatName} or null when nothing was found. A miss is the ordinary case
 *         on live preview, so it must not cost an exception.
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
  try {
    const ZXing::ImageView image(pixels, width, height, ZXing::ImageFormat::Lum);
    const ZXing::Barcode barcode = ZXing::ReadBarcode(image, decoder->options);
    if (barcode.isValid()) {
      text = barcode.text();
      format = ZXing::ToString(barcode.format());
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
  jobjectArray result = env->NewObjectArray(2, stringClass, nullptr);
  if (result == nullptr) {
    return nullptr;
  }
  jstring jText = env->NewStringUTF(text.c_str());
  jstring jFormat = env->NewStringUTF(format.c_str());
  env->SetObjectArrayElement(result, 0, jText);
  env->SetObjectArrayElement(result, 1, jFormat);
  return result;
}

}  // extern "C"

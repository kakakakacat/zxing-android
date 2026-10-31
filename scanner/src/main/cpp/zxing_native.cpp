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

#include <cstdint>
#include <exception>
#include <memory>
#include <string>

#include "BarcodeFormat.h"
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

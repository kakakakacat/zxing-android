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
#include <limits>
#include <string>
#include <vector>

#include "BarcodeFormat.h"
#include "ReadBarcode.h"
#include "native_decoder.h"
#include "jni_support.h"
#include "luma_frame.h"

namespace {

/** The exception a decode that failed for an internal reason is reported with. */
constexpr const char* kDecodeExceptionClass =
    "io/github/kakakakacat/zxing/android/scanner/NativeDecodeException";

using scanner_native::NativeDecoder;
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

/**
 * A Java String from standard UTF-8, by way of UTF-16.
 *
 * <p>Not `NewStringUTF`, which this file used to call. That function takes *modified*
 * UTF-8, which differs from the real thing in exactly the two ways a barcode payload is
 * most likely to hit: U+0000 is encoded as two bytes rather than one, and a character
 * outside the basic multilingual plane is encoded as a surrogate pair of three-byte
 * sequences rather than as one four-byte sequence. zxing-cpp produces standard UTF-8. So
 * a payload with an embedded NUL -- a GS1 separator's neighbour, a binary QR -- and any
 * emoji or supplementary-plane character were handed to `NewStringUTF` in an encoding it
 * does not accept. What that produces is up to the runtime and not something to rely on.
 *
 * <p>Converting explicitly and calling `NewString` with a length also means the string is
 * no longer NUL-terminated by construction, so an embedded NUL stops truncating it.
 *
 * <p>Malformed input becomes U+FFFD rather than being dropped or passed through: one
 * replacement character per byte that cannot start or continue a well-formed sequence.
 * Truncating, substituting silently or refusing the whole result would each lose more
 * than the byte that was actually wrong.
 */
jstring newJavaString(JNIEnv* env, const std::string& utf8) {
  std::u16string out;
  out.reserve(utf8.size());
  const size_t n = utf8.size();
  size_t i = 0;
  while (i < n) {
    const auto lead = static_cast<unsigned char>(utf8[i]);
    uint32_t codePoint;
    size_t continuations;
    uint32_t smallest;
    if (lead < 0x80) {
      codePoint = lead;
      continuations = 0;
      smallest = 0;
    } else if ((lead & 0xE0) == 0xC0) {
      codePoint = lead & 0x1Fu;
      continuations = 1;
      smallest = 0x80;
    } else if ((lead & 0xF0) == 0xE0) {
      codePoint = lead & 0x0Fu;
      continuations = 2;
      smallest = 0x800;
    } else if ((lead & 0xF8) == 0xF0) {
      codePoint = lead & 0x07u;
      continuations = 3;
      smallest = 0x10000;
    } else {
      // A continuation byte with no lead, or one of the five- and six-byte forms that
      // UTF-8 has not had since 2003.
      out.push_back(0xFFFD);
      i++;
      continue;
    }
    if (i + continuations >= n) {
      out.push_back(0xFFFD);
      i++;
      continue;
    }
    bool wellFormed = true;
    for (size_t k = 1; k <= continuations; k++) {
      const auto next = static_cast<unsigned char>(utf8[i + k]);
      if ((next & 0xC0) != 0x80) {
        wellFormed = false;
        break;
      }
      codePoint = (codePoint << 6) | (next & 0x3Fu);
    }
    // Overlong forms, the surrogate range -- which is not a character in UTF-8 and is how
    // modified UTF-8 would have spelled a supplementary character -- and anything past the
    // last code point are all rejected rather than re-encoded.
    if (!wellFormed || codePoint < smallest || codePoint > 0x10FFFF ||
        (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
      out.push_back(0xFFFD);
      i++;
      continue;
    }
    if (codePoint >= 0x10000) {
      const uint32_t shifted = codePoint - 0x10000;
      out.push_back(static_cast<char16_t>(0xD800 + (shifted >> 10)));
      out.push_back(static_cast<char16_t>(0xDC00 + (shifted & 0x3FFu)));
    } else {
      out.push_back(static_cast<char16_t>(codePoint));
    }
    i += continuations + 1;
  }
  if (out.size() > static_cast<size_t>(INT32_MAX)) {
    return nullptr;
  }
  return env->NewString(reinterpret_cast<const jchar*>(out.data()),
                        static_cast<jsize>(out.size()));
}

/** Throws, and says so, so a caller can tell a refusal from a frame that held nothing. */
void throwJava(JNIEnv* env, const char* className, const std::string& message) {
  if (env->ExceptionCheck()) {
    // Something already failed; that exception is the first cause and stays.
    return;
  }
  scanner_native::LocalRef<jclass> cls(env, env->FindClass(className));
  if (cls.get() == nullptr) {
    // FindClass left a NoClassDefFoundError pending, which is itself a fine answer.
    return;
  }
  env->ThrowNew(cls.get(), message.c_str());
}

/** A Java int[] from a span of ints, or null with a pending exception. */
jintArray newIntArray(JNIEnv* env, const jint* values, jsize count) {
  jintArray array = env->NewIntArray(count);
  if (array == nullptr) {
    return nullptr;
  }
  env->SetIntArrayRegion(array, 0, count, values);
  return array;
}

/**
 * Puts one element into the result array and drops the local reference to it.
 *
 * <p>A JNI frame guarantees only 16 local references. The result carries nine elements
 * built from several more, and a native method that quietly overruns that limit is
 * exactly the kind of thing CheckJNI exists to find.
 *
 * @return false if the value could not be built, in which case an exception is pending.
 */
bool setElement(JNIEnv* env, jobjectArray array, jsize index, jobject value) {
  if (value == nullptr) {
    return !env->ExceptionCheck();
  }
  env->SetObjectArrayElement(array, index, value);
  env->DeleteLocalRef(value);
  return !env->ExceptionCheck();
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
  const std::string info = scanner_native::nativeBuildInfo();
  return env->NewStringUTF(info.c_str());
}

JNIEXPORT jlong JNICALL
Java_io_github_kakakakacat_zxing_android_scanner_NativeFrameDecoder_nativeCreate(
    JNIEnv*, jclass) {
  auto decoder = scanner_native::createNativeDecoder();
  return static_cast<jlong>(reinterpret_cast<uintptr_t>(decoder.release()));
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
 * @return null when the frame held no barcode, or `Object[9]` laid out as
 *         `NativeFrameDecoder.RESULT_*` describes it: the text, the zxing-cpp format
 *         name, `int[8]` corners, the content bytes, the error-correction level, the
 *         symbology identifier, an EAN/UPC add-on, `int[5]` of flags, and the structured
 *         append sequence id.
 * @throws IllegalArgumentException if the frame is not one this function can read.
 * @throws NativeDecodeException if the decoder itself threw.
 *
 * <p>A miss is the ordinary case on live preview, so it stays a null return and costs no
 * allocation. The three ways this can go wrong that are *not* a miss -- a frame whose
 * dimensions do not describe its buffer, an exception out of zxing-cpp, and a failed
 * allocation -- are each an exception, because the shape they used to share with a miss
 * is what made them invisible.
 *
 * <p>The corner points are carried back so a result still has somewhere to draw. The Java
 * reader reports candidate points *while* it searches, through a hint callback; zxing-cpp
 * has no such streaming callback, so only the points of a finished symbol survive the
 * switch.
 */
static jobjectArray decodeImage(JNIEnv* env, NativeDecoder* decoder, const ZXing::ImageView& image) {
  ZXing::Barcode barcode;
  std::string internalError;
  try {
    barcode = ZXing::ReadBarcode(image, decoder->options);
  } catch (const std::exception& e) {
    internalError = e.what();
  } catch (...) {
    internalError = "a non-std::exception escaped zxing-cpp";
  }

  if (!internalError.empty()) {
    // A throwing decoder must not take the process with it. It must not be a miss
    // either: a frame that could not be attempted is not a frame that held nothing.
    decoder->internalErrors++;
    decoder->lastError = internalError;
    throwJava(env, kDecodeExceptionClass, internalError);
    return nullptr;
  }

  if (!barcode.isValid()) {
    // zxing-cpp reports *why* a symbol could not be read only when returnErrors is on,
    // and it is off by default here on purpose -- see nativeSetReturnErrors.
    switch (barcode.error().type()) {
      case ZXing::Error::Type::Format:
        decoder->formatErrors++;
        decoder->lastError = barcode.error().msg();
        break;
      case ZXing::Error::Type::Checksum:
        decoder->checksumErrors++;
        decoder->lastError = barcode.error().msg();
        break;
      case ZXing::Error::Type::Unsupported:
        decoder->unsupportedErrors++;
        decoder->lastError = barcode.error().msg();
        break;
      case ZXing::Error::Type::None:
      default:
        decoder->misses++;
        break;
    }
    return nullptr;
  }

  // RSS-Expanded is the one format where this library's Java reader prints the GS1
  // application identifiers in parentheses -- "(11)100224(17)110224" -- because
  // RSSExpandedReader parses the AI stream itself. zxing-cpp does that only in
  // TextMode::HRI, and HRI escapes every non-graphical character everywhere else, which
  // is what Plain is set for. So the mode is chosen per symbol rather than per decoder.
  // Measured both ways on the corpus: Plain alone loses 174 RSS-Expanded samples, HRI
  // alone loses 11 MaxiCode and PDF417 ones (BENCHMARKS.md section 9.1).
  //
  // Both expanded variants, not just the one: a stacked symbol comes back as
  // DataBarExpStk and testing only DataBarExpanded left 56 of them rendered without
  // their parentheses. The non-expanded DataBar family is deliberately not here --
  // RSS14Reader returns the bare 14 digits, and the AI prefix is removed in Java.
  const bool gs1Parentheses = barcode.format() == ZXing::BarcodeFormat::DataBarExp
      || barcode.format() == ZXing::BarcodeFormat::DataBarExpStk;
  const std::string text = gs1Parentheses ? barcode.text(ZXing::TextMode::HRI)
                                          : barcode.text();
  const std::string format = ZXing::ToString(barcode.format());
  decoder->hits++;

  if (env->EnsureLocalCapacity(16) != JNI_OK) {
    return nullptr;  // OutOfMemoryError is pending.
  }
  jclass objectClass = env->FindClass("java/lang/Object");
  if (objectClass == nullptr) {
    return nullptr;
  }
  jobjectArray result = env->NewObjectArray(9, objectClass, nullptr);
  env->DeleteLocalRef(objectClass);
  if (result == nullptr) {
    return nullptr;
  }

  jstring textString = newJavaString(env, text);
  if (textString == nullptr) {
    throwJava(env, "java/lang/OutOfMemoryError", "the decoded text could not be built");
    return nullptr;
  }
  if (!setElement(env, result, 0, textString)) {
    return nullptr;
  }
  if (!setElement(env, result, 1, newJavaString(env, format))) {
    return nullptr;
  }

  const auto& position = barcode.position();
  const ZXing::PointI points[4] = {position.topLeft(), position.topRight(),
                                   position.bottomRight(), position.bottomLeft()};
  jint corners[8];
  for (int i = 0; i < 4; i++) {
    corners[i * 2] = points[i].x;
    corners[i * 2 + 1] = points[i].y;
  }
  if (!setElement(env, result, 2, newIntArray(env, corners, 8))) {
    return nullptr;
  }

  // The content as the symbology defines it, before any character-set conversion. Not
  // the symbol's codewords, which is what the Java backend's 2D readers put in
  // Result.getRawBytes(); zxing-cpp does not expose those. NativeFrameDecoder says so
  // where a caller will read it.
  const std::vector<uint8_t>& bytes = barcode.bytes();
  jbyteArray byteArray = env->NewByteArray(static_cast<jsize>(bytes.size()));
  if (byteArray == nullptr) {
    return nullptr;
  }
  env->SetByteArrayRegion(byteArray, 0, static_cast<jsize>(bytes.size()),
                          reinterpret_cast<const jbyte*>(bytes.data()));
  if (!setElement(env, result, 3, byteArray)) {
    return nullptr;
  }

  if (!setElement(env, result, 4, newJavaString(env, barcode.ecLevel()))) {
    return nullptr;
  }
  if (!setElement(env, result, 5, newJavaString(env, barcode.symbologyIdentifier()))) {
    return nullptr;
  }
  if (!setElement(env, result, 6,
                  newJavaString(env, barcode.extra(ZXing::BarcodeExtra::EanAddOn)))) {
    return nullptr;
  }

  const jint flags[5] = {
      barcode.sequenceIndex(),
      barcode.sequenceSize(),
      barcode.readerInit() ? 1 : 0,
      static_cast<jint>(barcode.contentType()),
      barcode.hasECI() ? 1 : 0,
  };
  if (!setElement(env, result, 7, newIntArray(env, flags, 5))) {
    return nullptr;
  }
  if (!setElement(env, result, 8, newJavaString(env, barcode.sequenceId()))) {
    return nullptr;
  }
  return result;
}

JNIEXPORT jobjectArray JNICALL
Java_io_github_kakakakacat_zxing_android_scanner_NativeFrameDecoder_nativeDecode(
    JNIEnv* env, jclass, jlong handle, jbyteArray luminance, jint width, jint height) {
  NativeDecoder* decoder = fromHandle(handle);
  if (decoder == nullptr) {
    throwJava(env, "java/lang/IllegalStateException", "the native decoder is closed");
    return nullptr;
  }
  if (luminance == nullptr || width <= 0 || height <= 0) {
    throwJava(env, "java/lang/IllegalArgumentException",
              "a frame needs a buffer and positive dimensions, got " +
                  std::to_string(width) + "x" + std::to_string(height));
    return nullptr;
  }
  const jsize length = env->GetArrayLength(luminance);
  // int64_t throughout: width * height overflows a jint at 46341 square, and a buffer
  // check that overflows is a check that passes.
  if (static_cast<int64_t>(length) <
      static_cast<int64_t>(width) * static_cast<int64_t>(height)) {
    // Reading it would be a native out-of-bounds read, so refuse rather than read.
    throwJava(env, "java/lang/IllegalArgumentException",
              "a " + std::to_string(width) + "x" + std::to_string(height) +
                  " frame needs " + std::to_string(static_cast<int64_t>(width) * height) +
                  " bytes, got " + std::to_string(length));
    return nullptr;
  }

  scanner_native::PinnedBytes pixels(env, luminance);
  if (!pixels.ok()) return nullptr; // JVM allocation exception remains pending.
  std::string error;
  auto frame = LumaFrameView::create(
      std::span<const uint8_t>(pixels.get(), static_cast<size_t>(length)),
      width, height, width, 1, error);
  if (!frame) {
    throwJava(env, "java/lang/IllegalArgumentException", error);
    return nullptr;
  }
  return decodeImage(env, decoder, frame->image());
}

JNIEXPORT jobjectArray JNICALL
Java_io_github_kakakakacat_zxing_android_scanner_NativeFrameDecoder_nativeDecodeDirect(
    JNIEnv* env, jclass, jlong handle, jobject buffer, jint width, jint height,
    jint rowStride, jint pixelStride) {
  NativeDecoder* decoder = fromHandle(handle);
  if (decoder == nullptr) {
    throwJava(env, "java/lang/IllegalStateException", "the native decoder is closed");
    return nullptr;
  }
  auto* pixels = buffer == nullptr ? nullptr :
      static_cast<const uint8_t*>(env->GetDirectBufferAddress(buffer));
  const jlong capacity = buffer == nullptr ? -1 : env->GetDirectBufferCapacity(buffer);
  if (pixels == nullptr || capacity <= 0 ||
      static_cast<uint64_t>(capacity) > std::numeric_limits<size_t>::max()) {
    throwJava(env, "java/lang/IllegalArgumentException", "invalid direct luma plane bounds or strides");
    return nullptr;
  }
  std::string error;
  auto frame = LumaFrameView::create(
      std::span<const uint8_t>(pixels, static_cast<size_t>(capacity)),
      width, height, rowStride, pixelStride, error);
  if (!frame) {
    throwJava(env, "java/lang/IllegalArgumentException", error);
    return nullptr;
  }
  return decodeImage(env, decoder, frame->image());
}

/**
 * Whether zxing-cpp should report a symbol it detected but could not finish reading.
 *
 * <p>Off by default and deliberately so. With it on, `ReadBarcode` stops at the first
 * *result* rather than the first valid one, so a checksum failure on one symbol can take
 * the slot a readable symbol would have had -- on live preview, where a partially visible
 * symbol is in most frames, that trades reads for diagnosis. What it buys is the error
 * breakdown in `nativeDiagnostics`: with it off, a symbol that failed its checksum and a
 * frame that held nothing are both counted as a miss, because zxing-cpp has erased the
 * difference before this layer sees it.
 *
 * <p>So this exists for tests and measurement, and the scanning path leaves it alone.
 */
JNIEXPORT void JNICALL
Java_io_github_kakakakacat_zxing_android_scanner_NativeFrameDecoder_nativeSetReturnErrors(
    JNIEnv*, jclass, jlong handle, jboolean returnErrors) {
  NativeDecoder* decoder = fromHandle(handle);
  if (decoder == nullptr) {
    return;
  }
  decoder->options.setReturnErrors(returnErrors == JNI_TRUE);
}

/**
 * What this decoder has done since it was created, as `key=value;` pairs.
 *
 * <p>For a log line or a trace, not for the scanning path: it allocates and it is not on
 * a frame's critical path. Carries no scan content -- counts, and the last error message
 * zxing-cpp produced, which describes a symbol's structure rather than its payload.
 */
JNIEXPORT jstring JNICALL
Java_io_github_kakakakacat_zxing_android_scanner_NativeFrameDecoder_nativeDiagnostics(
    JNIEnv* env, jclass, jlong handle) {
  NativeDecoder* decoder = fromHandle(handle);
  if (decoder == nullptr) {
    return newJavaString(env, "closed=1");
  }
  return newJavaString(env, decoder->diagnostics());
}

}  // extern "C"

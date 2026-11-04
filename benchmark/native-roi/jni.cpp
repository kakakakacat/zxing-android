// SPDX-License-Identifier: Apache-2.0
#include "roi.h"
#include <jni.h>
#include <dlfcn.h>
static void* library = nullptr;
static RoiPack pack = nullptr;
extern "C" JNIEXPORT jboolean JNICALL Java_RoiPackExperiment_open(JNIEnv* env, jclass, jstring path) {
  const char* text = env->GetStringUTFChars(path, nullptr);
  if (!text) return false;
  library = dlopen(text, RTLD_NOW | RTLD_LOCAL);
  env->ReleaseStringUTFChars(path, text);
  pack = library ? reinterpret_cast<RoiPack>(dlsym(library, "roi_pack")) : nullptr;
  return pack != nullptr;
}
extern "C" JNIEXPORT jint JNICALL Java_RoiPackExperiment_nativePack(JNIEnv* env, jclass,
    jobject source, jbyteArray output, jint width, jint height, jint row, jint pixel, jint left, jint top) {
  if (!pack || !source || !output || width <= 0 || height <= 0 || row <= 0 || pixel <= 0 || left < 0 || top < 0)
    return -1;
  const auto* src = static_cast<const uint8_t*>(env->GetDirectBufferAddress(source));
  const jlong capacity = env->GetDirectBufferCapacity(source);
  if (!src || capacity < 0) return -1;
  const jsize size = env->GetArrayLength(output);
  jbyte* dst = env->GetByteArrayElements(output, nullptr);
  if (!dst) return -1;
  int result = pack(src, static_cast<size_t>(capacity), reinterpret_cast<uint8_t*>(dst), size,
                    width, height, row, pixel, left, top);
  env->ReleaseByteArrayElements(output, dst, result == 0 ? 0 : JNI_ABORT);
  return result;
}
extern "C" JNIEXPORT void JNICALL Java_RoiPackExperiment_shutdown(JNIEnv*, jclass) {
  pack = nullptr;
  if (library) dlclose(library);
  library = nullptr;
}
extern "C" JNIEXPORT jboolean JNICALL Java_RoiPackExperiment_arrayCopyProbe(JNIEnv* env, jclass, jbyteArray output) {
  jboolean copied = JNI_FALSE;
  jbyte* bytes = env->GetByteArrayElements(output, &copied);
  if (!bytes) return JNI_FALSE;
  env->ReleaseByteArrayElements(output, bytes, JNI_ABORT);
  return copied;
}

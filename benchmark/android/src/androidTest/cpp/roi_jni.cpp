#include <jni.h>
#include <cstdint>

extern "C" int roiPack(const uint8_t*, size_t, uint8_t*, size_t,
    size_t, size_t, size_t, size_t, size_t, size_t);

namespace {
bool valid(jlong capacity, jint src_limit, jint dst_len,
    jint dst_offset, jint frame_width, jint frame_height, jint left, jint top,
    jint width, jint height, jint row, jint pixel) {
  if (capacity < 0 || src_limit < 0 || src_limit > capacity
      || dst_len < 0 || dst_offset < 0 || frame_width <= 0 || frame_height <= 0
      || left < 0 || top < 0 || width <= 0 || height <= 0 || row <= 0 || pixel <= 0
      || left > frame_width - width || top > frame_height - height) return false;
  const uint64_t row_end = uint64_t(frame_width - 1) * uint64_t(pixel) + 1;
  const uint64_t output_bytes = uint64_t(width) * uint64_t(height);
  const uint64_t last = uint64_t(top + height - 1) * uint64_t(row)
      + uint64_t(left + width - 1) * uint64_t(pixel) + 1;
  return row_end <= uint64_t(row) && last <= uint64_t(src_limit)
      && output_bytes <= uint64_t(dst_len - dst_offset);
}
}

extern "C" JNIEXPORT jint JNICALL
Java_io_github_kakakakacat_zxing_benchmark_RoiPacking_nativePack(
    JNIEnv* env, jclass, jobject source, jbyteArray destination,
    jint src_limit, jint dst_offset, jint frame_width, jint frame_height,
    jint left, jint top, jint width, jint height, jint row, jint pixel) {
  if (!source || !destination) return -1;
  const jlong capacity = env->GetDirectBufferCapacity(source);
  const jsize dst_len = env->GetArrayLength(destination);
  if (!valid(capacity, src_limit, dst_len, dst_offset, frame_width,
      frame_height, left, top, width, height, row, pixel)) return -1;
  const auto* base = static_cast<const uint8_t*>(env->GetDirectBufferAddress(source));
  if (!base) return -1;
  jbyte* array = env->GetByteArrayElements(destination, nullptr);
  if (!array) return -1;
  const size_t output_size = size_t(width) * size_t(height);
  const int result = roiPack(base, size_t(src_limit),
      reinterpret_cast<uint8_t*>(array + dst_offset), output_size,
      size_t(width), size_t(height), size_t(row), size_t(pixel), size_t(left), size_t(top));
  env->ReleaseByteArrayElements(destination, array, result == 0 ? 0 : JNI_ABORT);
  return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_kakakakacat_zxing_benchmark_RoiPacking_nativeArrayCopyProbe(
    JNIEnv* env, jclass, jbyteArray destination) {
  if (!destination) return JNI_FALSE;
  jboolean copied = JNI_FALSE;
  jbyte* bytes = env->GetByteArrayElements(destination, &copied);
  if (!bytes) return JNI_FALSE;
  env->ReleaseByteArrayElements(destination, bytes, JNI_ABORT);
  return copied;
}

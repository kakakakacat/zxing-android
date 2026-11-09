/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
#include "jni_support.h"

namespace scanner_native {
PinnedBytes::PinnedBytes(JNIEnv* env, jbyteArray array) noexcept : env_(env), array_(array) {
  pixels_ = reinterpret_cast<uint8_t*>(env->GetByteArrayElements(array, nullptr));
}
PinnedBytes::~PinnedBytes() {
  if (pixels_ != nullptr) {
    env_->ReleaseByteArrayElements(array_, reinterpret_cast<jbyte*>(pixels_), JNI_ABORT);
  }
}
}  // namespace scanner_native

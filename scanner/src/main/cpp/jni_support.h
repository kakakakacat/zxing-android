/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
#pragma once
#include <jni.h>
#include <cstdint>

namespace scanner_native {
template <typename T>
class LocalRef final {
 public:
  LocalRef(JNIEnv* env, T value) noexcept : env_(env), value_(value) {}
  ~LocalRef() { if (value_ != nullptr) env_->DeleteLocalRef(value_); }
  LocalRef(const LocalRef&) = delete;
  LocalRef& operator=(const LocalRef&) = delete;
  T get() const noexcept { return value_; }
 private:
  JNIEnv* env_;
  T value_;
};

/**
 * Holds Java array elements for the call, releasing a pin or VM copy on every exit.
 *
 * Uses the ordinary array API: decoding and result allocation must not run inside a
 * GetPrimitiveArrayCritical section. The VM may copy; JNI_ABORT avoids copying back.
 */
class PinnedBytes {
 public:
  PinnedBytes(JNIEnv* env, jbyteArray array) noexcept;
  ~PinnedBytes();

  PinnedBytes(const PinnedBytes&) = delete;
  PinnedBytes& operator=(const PinnedBytes&) = delete;

  const uint8_t* get() const { return pixels_; }
  bool ok() const { return pixels_ != nullptr; }

 private:
  JNIEnv* env_;
  jbyteArray array_;
  uint8_t* pixels_ = nullptr;
};

}  // namespace scanner_native

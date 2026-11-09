/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
#pragma once
#include <cstdint>
#include <memory>
#include <string>
#include "ReaderOptions.h"

namespace scanner_native {
/**
 * One decoder's state. Owned by the Java object through a long handle.
 *
 * The counters exist so that the four things a frame can do -- decode, hold nothing,
 * hold something this decoder could not finish reading, and fail inside the decoder --
 * stay distinguishable after the fact. Before them every one of the last three left the
 * same trace: `nativeDecode` returned null and the frame was a miss.
 *
 * Only the decode thread touches them (see FrameDecoder's contract: one frame at a time,
 * never concurrently), so they are plain integers and not atomics.
 */
struct NativeDecoder {
  NativeDecoder();
  std::string diagnostics() const;
  ZXing::ReaderOptions options;
  uint64_t hits = 0;
  uint64_t misses = 0;
  uint64_t formatErrors = 0;
  uint64_t checksumErrors = 0;
  uint64_t unsupportedErrors = 0;
  uint64_t internalErrors = 0;
  /** The last error message, from zxing-cpp or from a C++ exception. Never scan content. */
  std::string lastError;
};

std::unique_ptr<NativeDecoder> createNativeDecoder() noexcept;
std::string nativeBuildInfo();
}  // namespace scanner_native

/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
#pragma once
#include <cstdint>
#include <memory>
#include <string>
#include "Barcode.h"
#include "ImageView.h"
#include "MultiFormatReader.h"
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

  /**
   * Reads one frame, reusing the readers built for the current options.
   *
   * `ZXing::ReadBarcode` constructs a `MultiFormatReader` -- and with it every format
   * reader the options ask for -- on every call. That is a fixed per-frame cost that
   * does not depend on the frame: on DN-ENG41 the all-format 1D hit measured 18 us more
   * than the UPC-A-only one at VGA, HD and FHD alike, and the only work that differs
   * between those two is the construction of the readers that never run. A live preview
   * decodes the same options thousands of times, so the readers are built once here and
   * rebuilt only when an option changes (see `optionsChanged`).
   *
   * Only the configuration this decoder actually fixes takes that path. Anything else --
   * a downscale pyramid, an inversion pass, `isPure`, or a frame that is not a dense
   * greyscale plane -- falls back to `ZXing::ReadBarcode`, because reproducing those
   * parts of its body here is how a reimplementation drifts from the library it copies.
   */
  ZXing::Barcode read(const ZXing::ImageView& image);

  /** Drops the cached readers. Every option setter must call this. */
  void optionsChanged() noexcept { reader.reset(); }

  ZXing::ReaderOptions options;
  uint64_t hits = 0;
  uint64_t misses = 0;
  uint64_t formatErrors = 0;
  uint64_t checksumErrors = 0;
  uint64_t unsupportedErrors = 0;
  uint64_t internalErrors = 0;
  /** The last error message, from zxing-cpp or from a C++ exception. Never scan content. */
  std::string lastError;

 private:
  /**
   * Built from `options`, which it holds by reference -- so it is declared after it and
   * destroyed before it, and is dropped rather than mutated whenever `options` changes.
   */
  std::unique_ptr<ZXing::MultiFormatReader> reader;
};

std::unique_ptr<NativeDecoder> createNativeDecoder() noexcept;
std::string nativeBuildInfo();
}  // namespace scanner_native

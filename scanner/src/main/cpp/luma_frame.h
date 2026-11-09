/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
#pragma once

#include <cstdint>
#include <optional>
#include <span>
#include <string>
#include "ImageView.h"

/** Validated, non-owning view of a cropped luminance plane. */
class LumaFrameView final {
 public:
  static std::optional<LumaFrameView> create(std::span<const uint8_t> storage,
      int width, int height, int rowStride, int pixelStride, std::string& error);
  ZXing::ImageView image() const noexcept;

 private:
  LumaFrameView(std::span<const uint8_t> storage, int width, int height,
      int rowStride, int pixelStride) noexcept
      : storage_(storage), width_(width), height_(height),
        rowStride_(rowStride), pixelStride_(pixelStride) {}
  std::span<const uint8_t> storage_;
  int width_;
  int height_;
  int rowStride_;
  int pixelStride_;
};

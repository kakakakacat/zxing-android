/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
#include "luma_frame.h"
#include <limits>

std::optional<LumaFrameView> LumaFrameView::create(
    std::span<const uint8_t> storage, int width, int height,
    int rowStride, int pixelStride, std::string& error) {
  if (storage.empty() || width <= 0 || height <= 0 || rowStride <= 0 || pixelStride <= 0) {
    error = "a luma frame needs storage, positive dimensions, and positive strides";
    return std::nullopt;
  }
  const int64_t rowBytes = static_cast<int64_t>(width - 1) * pixelStride + 1;
  const int64_t required = static_cast<int64_t>(height - 1) * rowStride + rowBytes;
  if (rowBytes > rowStride || required <= 0 ||
      static_cast<uint64_t>(required) > storage.size() ||
      required > std::numeric_limits<int32_t>::max()) {
    error = "luma dimensions or strides run past storage";
    return std::nullopt;
  }
  return LumaFrameView(storage.first(static_cast<size_t>(required)), width, height,
                       rowStride, pixelStride);
}

ZXing::ImageView LumaFrameView::image() const noexcept {
  return ZXing::ImageView(storage_.data(), width_, height_, ZXing::ImageFormat::Lum,
                          rowStride_, pixelStride_);
}

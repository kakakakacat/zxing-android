// SPDX-License-Identifier: Apache-2.0
#include "roi.h"
#include <cstring>
#include <limits>

extern "C" int roi_pack(const uint8_t* __restrict src, size_t src_len,
    uint8_t* __restrict dst, size_t dst_len, size_t width, size_t height,
    size_t row_stride, size_t pixel_stride, size_t left, size_t top) {
  const size_t max = std::numeric_limits<size_t>::max();
  if (!src || !dst || !width || !height || !row_stride || !pixel_stride ||
      width > dst_len / height || left > max / pixel_stride ||
      width - 1 > (max - 1) / pixel_stride || top > max / row_stride) return -1;
  const size_t offset = left * pixel_stride;
  const size_t span = (width - 1) * pixel_stride + 1;
  if (offset > row_stride || span > row_stride - offset) return -1;
  const size_t first_row = top * row_stride;
  if (first_row > max - offset) return -1;
  const size_t start = first_row + offset;
  if (start > src_len || span > src_len - start ||
      height - 1 > (src_len - start - span) / row_stride) return -1;
  for (size_t y = 0; y < height; ++y) {
    const auto* row = src + start + y * row_stride;
    auto* out = dst + y * width;
    if (pixel_stride == 1) std::memcpy(out, row, width);
    else for (size_t x = 0; x < width; ++x) out[x] = row[x * pixel_stride];
  }
  return 0;
}

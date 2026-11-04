#include <cstddef>
#include <cstdint>
#include <cstring>

extern "C" int roiPack(const uint8_t* src, size_t src_len, uint8_t* dst,
    size_t dst_len, size_t width, size_t height, size_t row_stride,
    size_t pixel_stride, size_t left, size_t top) {
  if (!src || !dst || !width || !height || !row_stride || !pixel_stride
      || width > dst_len / height || left > (SIZE_MAX - 1) / pixel_stride
      || width - 1 > (SIZE_MAX - 1) / pixel_stride
      || top > SIZE_MAX / row_stride) return -1;
  const size_t offset = left * pixel_stride;
  const size_t span = (width - 1) * pixel_stride + 1;
  if (offset > row_stride || span > row_stride - offset) return -1;
  const size_t first = top * row_stride;
  if (first > SIZE_MAX - offset) return -1;
  const size_t start = first + offset;
  if (start > src_len || span > src_len - start
      || height - 1 > (src_len - start - span) / row_stride) return -1;
  for (size_t y = 0; y < height; ++y) {
    const uint8_t* row = src + start + y * row_stride;
    uint8_t* out = dst + y * width;
    if (pixel_stride == 1) std::memcpy(out, row, width);
    else for (size_t x = 0; x < width; ++x) out[x] = row[x * pixel_stride];
  }
  return 0;
}

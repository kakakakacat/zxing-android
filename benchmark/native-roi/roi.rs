// SPDX-License-Identifier: Apache-2.0
use std::slice;

// Same C ABI and nonoverlap/lifetime contract as roi.h; checks precede all slice creation.
#[no_mangle]
pub unsafe extern "C" fn roi_pack(src: *const u8, src_len: usize, dst: *mut u8,
    dst_len: usize, width: usize, height: usize, row_stride: usize,
    pixel_stride: usize, left: usize, top: usize) -> i32 {
    if src.is_null() || dst.is_null() || width == 0 || height == 0 ||
        row_stride == 0 || pixel_stride == 0 || width > dst_len / height {
        return -1;
    }
    let offset = match left.checked_mul(pixel_stride) { Some(v) => v, None => return -1 };
    let span = match (width - 1).checked_mul(pixel_stride).and_then(|n| n.checked_add(1)) {
        Some(v) => v, None => return -1,
    };
    if offset > row_stride || span > row_stride - offset { return -1; }
    let start = match top.checked_mul(row_stride).and_then(|n| n.checked_add(offset)) {
        Some(v) => v, None => return -1,
    };
    if start > src_len || span > src_len - start ||
        height - 1 > (src_len - start - span) / row_stride { return -1; }
    // The harness supplies real allocations, not invented pointer/length pairs.
    let input = slice::from_raw_parts(src, src_len);
    let output = slice::from_raw_parts_mut(dst, dst_len);
    for y in 0..height {
        let row = &input[start + y * row_stride..start + y * row_stride + span];
        let out = &mut output[y * width..(y + 1) * width];
        if pixel_stride == 1 { out.copy_from_slice(&row[..width]); }
        else { for x in 0..width { out[x] = row[x * pixel_stride]; } }
    }
    0
}

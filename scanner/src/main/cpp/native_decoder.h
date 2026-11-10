/* Copyright (C) 2026 ZXing Android maintenance fork contributors
 * SPDX-License-Identifier: Apache-2.0 */
#pragma once
#include <cstdint>
#include <memory>
#include <string>
#include <vector>
#include "Barcode.h"
#include "BinaryBitmap.h"
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
  void optionsChanged() noexcept {
    reader.reset();
    // Built from a copy of `options` and holding it by reference, so they describe the
    // format set as it was. Dropped rather than rebuilt: nothing is timed yet.
    stageGroups.clear();
  }

  /**
   * Measurement only: holds one frame so thresholding and detection can be timed apart.
   *
   * `read` is one call from a greyscale plane to a `Barcode`, so the share of it that is
   * thresholding has never been measured -- `DecoderBackendBenchmark` has an `endToEnd`
   * case and nothing narrower, and its own comment says why: a native backend brings its
   * own thresholding, so there is no stage the two backends share. That is an argument
   * against a *comparable* stage, not against measuring this one, and without the split
   * there is no way to tell whether the 18.7 ms an FHD all-format miss costs is mostly
   * the binarizer or mostly the detectors.
   *
   * Copies the frame rather than viewing the caller's buffer: the bitmap built here
   * outlives the JNI call that prepared it, and a Java array is only pinned for the
   * duration of one call. Geometry -- both strides included -- is preserved exactly, so
   * the binarizer sees the same packed-or-strided shape it sees on the live path, and
   * `GlobalHistogramBinarizer`'s `__ARM_NEON` packed-copy branch makes the same decision.
   *
   * Refuses any configuration `read` would not take the cached-reader path for. With one
   * of those, the live path runs `ZXing::ReadBarcodes` -- a downscale pyramid, an
   * inversion pass, `isPure` -- and a two-stage split of a decode that never happens
   * would attribute time to stages the frame does not go through.
   *
   * @return false with `error` filled, which the JNI layer throws
   */
  bool prepareStages(const ZXing::ImageView& image, std::string& error);

  /**
   * Thresholding alone: a fresh binarizer over the held frame, forced to produce its
   * matrix. The allocation is inside, because the live path pays it per frame too.
   *
   * @return the matrix cell count, so the call has a result the optimiser cannot drop,
   *     or -1 if no frame is held
   */
  int64_t binarizeOnly();

  /**
   * Detection alone, against the matrix `prepareStages` already built and cached.
   *
   * `BinaryBitmap` caches its matrix on first use and `MultiFormatReader::read` takes it
   * by const reference, so repeating this re-runs the readers over a matrix that is
   * built once. **That matrix is also warm in cache, which it is not on a live frame**,
   * so `binarizeOnly + detectOnly` is a floor for `read` rather than a partition of it.
   * The two are reported next to the existing end-to-end case for exactly that reason.
   *
   * One-dimensional readers do not use the matrix at all -- `getPatternRow` thresholds a
   * row at a time -- and with `tryHarder` off, which is how this decoder is configured,
   * `MultiFormatReader` runs the 1D reader before every 2D one. So a 1D hit ends the read
   * before anything requests a matrix, and `binarizeOnly` for such a frame measures work
   * `read` never does. The two halves are not additive there.
   */
  bool detectOnly();

  /**
   * Detection by one reader group, over the same matrix `detectOnly` runs against.
   *
   * `MultiFormatReader::read` loops over readers that `_readers` keeps private, so there
   * is no seam inside it to time. This reaches the same decomposition from outside: a
   * second `MultiFormatReader` built from this decoder's own options with the format set
   * narrowed to one symbology, which makes its constructor build exactly one of the
   * readers the all-format run builds. `formats` is a zxing-cpp format list -- the six
   * groups that partition the reader set are "AllLinear", "QRCode", "DataMatrix",
   * "Aztec", "PDF417" and "MaxiCode".
   *
   * The groups are narrowed to a symbology, never below it, because the readers branch
   * on their own format set: `QRReader` skips Micro-QR and rMQR detection unless they are
   * in it, `ODReader` builds one sub-reader per linear symbology. A group narrower than a
   * symbology would time a reader doing less than it does in the all-format run.
   *
   * Intersected with this decoder's own formats and refused when that is empty, so a
   * group never times a reader the live decode would not construct.
   *
   * **On a miss the groups sum; on a hit they do not.** `read` returns as soon as a
   * reader produces a symbol, so a hit charges nothing to the readers behind it -- and
   * under this fork's shipping default the 1D reader is first (see `detectOnly`). A miss
   * runs every reader to the end, which is the frame a live preview decodes thousands of
   * and the one these groups are meant to attribute.
   *
   * @return 1 for a hit, 0 for a miss, -1 with `error` filled, which the JNI layer throws
   */
  int detectOnlyGroup(const std::string& formats, std::string& error);

  /** Drops the held frame and its bitmap. */
  void releaseStages() noexcept;

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

  /**
   * The frame `prepareStages` copied, and the bitmap over it.
   *
   * Declared in this order on purpose: the bitmap holds an `ImageView` into the storage,
   * so it has to be destroyed first, and members are destroyed in reverse declaration
   * order. Nothing may reallocate the storage while the bitmap is alive.
   */
  std::vector<uint8_t> stageStorage;
  int stageWidth = 0;
  int stageHeight = 0;
  int stageRowStride = 0;
  int stagePixelStride = 0;
  std::unique_ptr<ZXing::BinaryBitmap> stageBitmap;

  /**
   * One reader group's own reader, with the options it was built from.
   *
   * `MultiFormatReader` holds its `ReaderOptions` by reference, so the copy has to
   * outlive it and must not move -- hence a member here, declared before the reader that
   * references it, and held through a `unique_ptr` in `stageGroups` so that growing the
   * vector does not relocate it.
   */
  struct GroupReader {
    std::string id;
    ZXing::ReaderOptions options;
    std::unique_ptr<ZXing::MultiFormatReader> reader;
  };

  /** Built on first use, keyed by the format string the caller named the group with. */
  std::vector<std::unique_ptr<GroupReader>> stageGroups;

  /** The view over `stageStorage`, with the geometry the caller's frame had. */
  ZXing::ImageView stageImage() const noexcept;
};

std::unique_ptr<NativeDecoder> createNativeDecoder() noexcept;
std::string nativeBuildInfo();
}  // namespace scanner_native

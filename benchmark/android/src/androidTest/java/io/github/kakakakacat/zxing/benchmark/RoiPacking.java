package io.github.kakakakacat.zxing.benchmark;

import java.nio.ByteBuffer;

/** Test-APK-only Java/JNI ROI packers. Coordinates use buffer index zero, like YPlaneLuminanceSource. */
final class RoiPacking {
  static { System.loadLibrary("roiPack"); }

  private RoiPacking() {}

  static boolean packJava(ByteBuffer source, byte[] destination, int destinationOffset,
      int frameWidth, int frameHeight, int left, int top, int width, int height,
      int rowStride, int pixelStride) {
    if (!valid(source, destination, destinationOffset, frameWidth, frameHeight, left, top,
        width, height, rowStride, pixelStride)) return false;
    if (pixelStride == 1) {
      ByteBuffer cursor = source.duplicate();
      for (int y = 0; y < height; y++) {
        cursor.position((int) ((long) (top + y) * rowStride + left));
        cursor.get(destination, destinationOffset + y * width, width);
      }
    } else {
      for (int y = 0; y < height; y++) {
        int start = (int) ((long) (top + y) * rowStride + (long) left * pixelStride);
        for (int x = 0; x < width; x++) {
          destination[destinationOffset + y * width + x] = source.get(start + x * pixelStride);
        }
      }
    }
    return true;
  }

  static boolean packNative(ByteBuffer source, byte[] destination, int destinationOffset,
      int frameWidth, int frameHeight, int left, int top, int width, int height,
      int rowStride, int pixelStride) {
    if (source == null || destination == null) return false;
    return nativePack(source, destination, source.limit(), destinationOffset,
        frameWidth, frameHeight, left, top, width, height, rowStride, pixelStride) == 0;
  }

  private static boolean valid(ByteBuffer source, byte[] destination, int destinationOffset,
      int frameWidth, int frameHeight, int left, int top, int width, int height,
      int rowStride, int pixelStride) {
    if (source == null || destination == null || !source.isDirect() || frameWidth <= 0
        || frameHeight <= 0 || left < 0 || top < 0 || width <= 0 || height <= 0
        || rowStride <= 0 || pixelStride <= 0 || destinationOffset < 0
        || left > frameWidth - width || top > frameHeight - height) return false;
    long rowEnd = (long) (frameWidth - 1) * pixelStride + 1;
    long last = (long) (top + height - 1) * rowStride
        + (long) (left + width - 1) * pixelStride + 1;
    long bytes = (long) width * height;
    return rowEnd <= rowStride && last <= source.limit()
        && bytes <= destination.length - (long) destinationOffset;
  }

  private static native int nativePack(ByteBuffer source, byte[] destination,
      int sourceLimit, int destinationOffset, int frameWidth, int frameHeight,
      int left, int top, int width, int height, int rowStride, int pixelStride);

  static native boolean nativeArrayCopyProbe(byte[] destination);
}

package com.erhu.erhu.core.network.apple;

/** Deliberately narrow scope matching the accepted SDR reference, with bounded resource use. */
public final class ArtworkCompressionPolicy {
  private ArtworkCompressionPolicy() {}

  public static final long RESERVE_BYTES = 16L * 1024 * 1024;

  public static boolean eligible(
      String mime, int width, int height, int fps, long durationUs, long bytes) {
    return "video/avc".equals(mime)
        && width == 1080
        && height == 1080
        && fps == 30
        && durationUs >= 1_000_000
        && durationUs <= 30_000_000
        && bytes > 0
        && bytes <= 64L * 1024 * 1024
        && bytes * 8_000_000L / durationUs >= 8_000_000;
  }

  public static boolean enoughSpace(long original, long available) {
    return original > 0 && original <= 64L * 1024 * 1024 && available >= original + RESERVE_BYTES;
  }

  public static boolean worthwhile(long original, long candidate) {
    return candidate > 0 && original - candidate >= Math.max(1024 * 1024, original / 10);
  }
}

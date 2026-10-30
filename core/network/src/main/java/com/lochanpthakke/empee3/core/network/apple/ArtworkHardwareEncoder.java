package com.lochanpthakke.empee3.core.network.apple;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.*;
import android.os.SystemClock;
import java.io.*;
import java.nio.*;
import java.util.*;
import org.json.*;

/** Bounded Android hardware encode for the accepted 1080-square, 30 FPS SDR workflow. */
public final class ArtworkHardwareEncoder {
  private ArtworkHardwareEncoder() {}

  static final int W = 1080,
      H = 1080,
      FPS = 30,
      FLEX = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;

  static MediaFormat config(String mime, int bitrate) {
    MediaFormat f = MediaFormat.createVideoFormat(mime, W, H);
    f.setInteger(MediaFormat.KEY_COLOR_FORMAT, FLEX);
    f.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
    f.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
    f.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
    f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
    f.setInteger(
        MediaFormat.KEY_PROFILE,
        mime.equals("video/hevc")
            ? MediaCodecInfo.CodecProfileLevel.HEVCProfileMain
            : MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
    f.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709);
    f.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
    f.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED);
    f.setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0);
    f.setInteger("sar-width", 1);
    f.setInteger("sar-height", 1);
    return f;
  }

  public static final class Input {
    public final int frames;
    public final long durationUs, bytes;

    Input(int frames, long durationUs, long bytes) {
      this.frames = frames;
      this.durationUs = durationUs;
      this.bytes = bytes;
    }
  }

  public static byte[] bytes(ByteBuffer buffer) throws IOException {
    if (buffer == null || buffer.remaining() > 65536)
      throw new IOException("Missing/oversized codec configuration");
    ByteBuffer copy = buffer.duplicate();
    byte[] data = new byte[copy.remaining()];
    copy.get(data);
    return data;
  }

  static void close(MediaCodec codec, boolean started) {
    if (codec != null) {
      try {
        if (started) codec.stop();
      } catch (RuntimeException ignored) {
      } finally {
        try {
          codec.release();
        } catch (RuntimeException ignored) {
          // Continue releasing the other codecs and muxer after a platform failure.
        }
      }
    }
  }

  static String encoderName(MediaFormat format) throws IOException {
    if (android.os.Build.VERSION.SDK_INT < 29)
      throw new IOException("Hardware compression requires Android 10 or later");
    for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos())
      if (info.isEncoder()
          && info.isHardwareAccelerated()
          && !info.isAlias()
          && Arrays.asList(info.getSupportedTypes()).contains("video/hevc")) {
        MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType("video/hevc");
        if (caps.isFormatSupported(format)
            && caps.getVideoCapabilities().areSizeAndRateSupported(W, H, FPS)
            && caps.getEncoderCapabilities().isBitrateModeSupported(1)) return info.getName();
      }
    throw new IOException("No supported hardware HEVC encoder");
  }

  public static Input inspect(File source) throws Exception {
    if (android.os.Build.VERSION.SDK_INT < 29)
      throw new IOException("Hardware compression requires Android 10 or later");
    MediaExtractor extractor = new MediaExtractor();
    try {
      extractor.setDataSource(source.getAbsolutePath());
      if (extractor.getTrackCount() != 1) throw new IOException("Requires one silent video track");
      MediaFormat f = extractor.getTrackFormat(0);
      String mime = f.getString(MediaFormat.KEY_MIME);
      long duration = f.getLong(MediaFormat.KEY_DURATION);
      if (!ArtworkCompressionPolicy.eligible(
          mime,
          f.getInteger(MediaFormat.KEY_WIDTH),
          f.getInteger(MediaFormat.KEY_HEIGHT),
          FPS,
          duration,
          source.length()))
        throw new IOException(
            "Requires high-bitrate 1080 square, 30 FPS AVC SDR artwork up to 30 seconds; HEVC is"
                + " kept unchanged");
      checkColors(f);
      ArtworkSps.validateAvc(bytes(f.getByteBuffer("csd-0")));
      if (f.containsKey(MediaFormat.KEY_ROTATION) && f.getInteger(MediaFormat.KEY_ROTATION) != 0)
        throw new IOException("Rotated artwork unsupported");
      extractor.selectTrack(0);
      int frames = 0;
      java.util.TreeSet<Long> times = new java.util.TreeSet<>();
      while (extractor.getSampleTime() >= 0) {
        times.add(extractor.getSampleTime());
        if (++frames > 900) throw new IOException("Artwork exceeds frame limit");
        if (!extractor.advance()) break;
      }
      if (frames < 30
          || times.size() != frames
          || Math.abs(frames * 1000000L / 30 - duration) > 100)
        throw new IOException("Duration/cadence unsupported");
      int frame = 0;
      long first = times.first();
      for (long time : times)
        if (Math.abs(time - first - Math.round(frame++ * 1000000.0 / FPS)) > 100)
          throw new IOException("Requires constant 30 FPS artwork");
      encoderName(config("video/hevc", 6000000));
      return new Input(frames, frames * 1000000L / FPS, source.length());
    } finally {
      extractor.release();
    }
  }

  public static void validateEncoded(File file, Input expected) throws Exception {
    MediaExtractor e = new MediaExtractor();
    try {
      e.setDataSource(file.getAbsolutePath());
      if (e.getTrackCount() != 1) throw new IOException("Unexpected tracks");
      MediaFormat f = e.getTrackFormat(0);
      if (!"video/hevc".equals(f.getString(MediaFormat.KEY_MIME))
          || f.getInteger(MediaFormat.KEY_WIDTH) != W
          || f.getInteger(MediaFormat.KEY_HEIGHT) != H
          || Math.abs(f.getLong(MediaFormat.KEY_DURATION) - expected.durationUs) > 100)
        throw new IOException("Encoded geometry/timing changed");
      checkColors(f);
      ArtworkSps.validateHevc(bytes(f.getByteBuffer("csd-0")));
      e.selectTrack(0);
      int frames = 0;
      long first = -1;
      while (e.getSampleTime() >= 0) {
        long pts = e.getSampleTime();
        if (first < 0) first = pts;
        if (Math.abs(pts - first - Math.round(frames * 1000000.0 / FPS)) > 2)
          throw new IOException("Encoded cadence changed");
        if (++frames > expected.frames) throw new IOException("Encoded frames changed");
        if (!e.advance()) break;
      }
      if (frames != expected.frames) throw new IOException("Encoded frame count changed");
    } finally {
      e.release();
    }
  }

  static final class Drain {
    final MediaCodec encoder;
    final MediaMuxer muxer;
    long totalBytes = 0;
    boolean started = false, ended = false;
    int track = -1, frames = 0;
    String outputFormat = "";

    Drain(MediaCodec encoder, MediaMuxer muxer) {
      this.encoder = encoder;
      this.muxer = muxer;
    }

    void poll(long wait) throws Exception {
      MediaCodec.BufferInfo b = new MediaCodec.BufferInfo();
      int n = encoder.dequeueOutputBuffer(b, wait);
      if (n == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        if (started) throw new IOException("Second format change");
        MediaFormat f = encoder.getOutputFormat();
        checkColors(f);
        if (f.getInteger(MediaFormat.KEY_BIT_RATE) != 6_000_000
            || f.getInteger(MediaFormat.KEY_BITRATE_MODE)
                != MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
            || f.getInteger(MediaFormat.KEY_FRAME_RATE) != FPS) {
          throw new IOException("Encoder changed quality settings. Original kept.");
        }
        if (f.getInteger(MediaFormat.KEY_WIDTH) != W
            || f.getInteger(MediaFormat.KEY_HEIGHT) != H
            || f.getInteger(MediaFormat.KEY_PROFILE)
                != MediaCodecInfo.CodecProfileLevel.HEVCProfileMain)
          throw new IOException("Unexpected encoder output geometry/profile: " + f);
        f.setByteBuffer(
            "csd-0", ByteBuffer.wrap(ArtworkSps.correctHevc(bytes(f.getByteBuffer("csd-0")))));
        f.setInteger("sar-width", 1);
        f.setInteger("sar-height", 1);
        outputFormat = f.toString();
        track = muxer.addTrack(f);
        muxer.start();
        started = true;
      } else if (n >= 0) {
        try {
          if (b.size > 0 && (b.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            if (!started) throw new IOException("No muxer format");
            ByteBuffer data = encoder.getOutputBuffer(n);
            data.position(b.offset);
            data.limit(b.offset + b.size);
            byte[] sample = new byte[b.size];
            data.duplicate().get(sample);
            ArtworkSps.validatePicture(sample);
            totalBytes += b.size;
            if (totalBytes > 64L * 1024 * 1024)
              throw new IOException("Encoded cover exceeds bounded size");
            muxer.writeSampleData(track, data, b);
            frames++;
          }
          if ((b.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) ended = true;
        } finally {
          encoder.releaseOutputBuffer(n, false);
        }
      }
    }
  }

  static void checkColors(MediaFormat f) throws IOException {
    String[] keys = {
      MediaFormat.KEY_COLOR_STANDARD, MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.KEY_COLOR_RANGE
    };
    int[] values = {
      MediaFormat.COLOR_STANDARD_BT709,
      MediaFormat.COLOR_TRANSFER_SDR_VIDEO,
      MediaFormat.COLOR_RANGE_LIMITED
    };
    for (int i = 0; i < keys.length; i++)
      if (!f.containsKey(keys[i]) || f.getInteger(keys[i]) != values[i])
        throw new IOException("Missing/changed color: " + f);
  }

  static JSONObject layout(Image image) throws Exception {
    JSONArray planes = new JSONArray();
    for (Image.Plane plane : image.getPlanes())
      planes.put(
          new JSONObject()
              .put("row_stride", plane.getRowStride())
              .put("pixel_stride", plane.getPixelStride()));
    return new JSONObject()
        .put("format", image.getFormat())
        .put("width", image.getWidth())
        .put("height", image.getHeight())
        .put("crop", image.getCropRect().toString())
        .put("planes", planes);
  }

  static void copy(Image src, Image dst) throws Exception {
    Rect a = src.getCropRect(), b = dst.getCropRect();
    if (src.getFormat() != ImageFormat.YUV_420_888
        || dst.getFormat() != ImageFormat.YUV_420_888
        || a.width() != W
        || a.height() != H
        || b.width() != W
        || b.height() != H)
      throw new IOException(
          "Cannot preserve 8-bit YUV420 and crop: " + layout(src) + " -> " + layout(dst));
    for (int p = 0; p < 3; p++) {
      int div = p == 0 ? 1 : 2, w = W / div, h = H / div;
      Image.Plane x = src.getPlanes()[p], y = dst.getPlanes()[p];
      int xp = x.getPixelStride(), yp = y.getPixelStride();
      if ((xp != 1 && xp != 2) || (yp != 1 && yp != 2))
        throw new IOException("Unexpected pixel stride");
      ByteBuffer in = x.getBuffer().duplicate(), out = y.getBuffer().duplicate();
      int inBase = in.position() + (a.top / div) * x.getRowStride() + (a.left / div) * xp;
      int outBase = out.position() + (b.top / div) * y.getRowStride() + (b.left / div) * yp;
      byte[] row = new byte[(w - 1) * xp + 1];
      for (int r = 0; r < h; r++) {
        in.position(inBase + r * x.getRowStride());
        in.get(row);
        int offset = outBase + r * y.getRowStride();
        if (xp == 1 && yp == 1) {
          out.position(offset);
          out.put(row);
        } else for (int c = 0; c < w; c++) out.put(offset + c * yp, row[c * xp]);
      }
    }
  }

  public static JSONObject encode(
      File sourceFile,
      File targetFile,
      Input expected,
      Runnable cancellation,
      java.util.function.IntConsumer progress)
      throws Exception {
    String source = sourceFile.getAbsolutePath(), target = targetFile.getAbsolutePath();
    int bitrate = 6000000;
    String encoderName = encoderName(config("video/hevc", bitrate));
    File dest = new File(target).getCanonicalFile();
    if (!dest.getParentFile().equals(sourceFile.getCanonicalFile().getParentFile())
        || !dest.isFile()
        || dest.length() != 0
        || dest.equals(sourceFile.getCanonicalFile()))
      throw new IOException("Invalid operation-owned staging file");
    MediaExtractor extractor = new MediaExtractor();
    MediaCodec decoder = null, encoder = null;
    MediaMuxer muxer = null;
    Drain drain = null;
    boolean decStarted = false, encStarted = false;
    long start = SystemClock.elapsedRealtime(), cpu = android.os.Process.getElapsedCpuTime();
    JSONObject result = new JSONObject();
    try {
      extractor.setDataSource(source);
      int track = -1;
      for (int i = 0; i < extractor.getTrackCount(); i++)
        if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).startsWith("video/")) {
          if (track != -1) throw new IOException("Multiple video tracks");
          track = i;
        }
      if (track < 0) throw new IOException("No video");
      extractor.selectTrack(track);
      MediaFormat input = extractor.getTrackFormat(track);
      if (input.getInteger(MediaFormat.KEY_WIDTH) != W
          || input.getInteger(MediaFormat.KEY_HEIGHT) != H) throw new IOException("Wrong geometry");
      checkColors(input);
      ArtworkSps.validateAvc(bytes(input.getByteBuffer("csd-0")));
      if (input.containsKey(MediaFormat.KEY_ROTATION)
          && input.getInteger(MediaFormat.KEY_ROTATION) != 0)
        throw new IOException("Unexpected rotation");
      input.setInteger(MediaFormat.KEY_COLOR_FORMAT, FLEX);
      input.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
      decoder = MediaCodec.createDecoderByType(input.getString(MediaFormat.KEY_MIME));
      decoder.configure(input, null, null, 0);
      decoder.start();
      decStarted = true;
      MediaFormat settings = config("video/hevc", bitrate);
      encoder = MediaCodec.createByCodecName(encoderName);
      if (!encoder.getCodecInfo().isHardwareAccelerated()
          || !encoder
              .getCodecInfo()
              .getCapabilitiesForType("video/hevc")
              .isFormatSupported(settings)) throw new IOException("Unsupported hardware format");
      encoder.configure(settings, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
      encoder.start();
      encStarted = true;
      MediaFormat actualInput = encoder.getInputFormat();
      checkColors(actualInput);
      if (actualInput.getInteger(MediaFormat.KEY_WIDTH) != W
          || actualInput.getInteger(MediaFormat.KEY_HEIGHT) != H
          || actualInput.getInteger(MediaFormat.KEY_FRAME_RATE) != FPS)
        throw new IOException("Unexpected encoder input geometry/cadence: " + actualInput);
      result.put("encoder_input_format", actualInput.toString());
      muxer = new MediaMuxer(target, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
      muxer.setOrientationHint(0);
      drain = new Drain(encoder, muxer);
      boolean inputEnded = false, decodedEnded = false;
      int frames = 0;
      long first = -1, last = -1;
      MediaCodec.BufferInfo b = new MediaCodec.BufferInfo();
      while (!decodedEnded) {
        cancellation.run();
        if (SystemClock.elapsedRealtime() - start > 90000)
          throw new IOException("Artwork encoding exceeded the 90-second limit");
        drain.poll(0);
        if (!inputEnded) {
          int n = decoder.dequeueInputBuffer(1000);
          if (n >= 0) {
            ByteBuffer buffer = decoder.getInputBuffer(n);
            int size = extractor.readSampleData(buffer, 0);
            if (size < 0) {
              decoder.queueInputBuffer(n, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
              inputEnded = true;
            } else {
              decoder.queueInputBuffer(n, 0, size, extractor.getSampleTime(), 0);
              extractor.advance();
            }
          }
        }
        int n = decoder.dequeueOutputBuffer(b, 1000);
        if (n == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          checkColors(decoder.getOutputFormat());
          result.put("decoder_output_format", decoder.getOutputFormat().toString());
        } else if (n >= 0) {
          try {
            if (b.size > 0) {
              if (first < 0) first = b.presentationTimeUs;
              long pts = b.presentationTimeUs - first;
              if (Math.abs(pts - Math.round(frames * 1000000.0 / FPS)) > 2)
                throw new IOException("Unexpected cadence");
              int en;
              while ((en = encoder.dequeueInputBuffer(1000)) < 0) {
                drain.poll(1000);
                cancellation.run();
                if (SystemClock.elapsedRealtime() - start > 90000)
                  throw new IOException("Encoder input timeout");
              }
              Image src = decoder.getOutputImage(n), dst = encoder.getInputImage(en);
              if (src == null || dst == null)
                throw new IOException("No direct YUV Image access; no RGB fallback permitted");
              if (frames == 0) {
                result.put("decoder_image", layout(src)).put("encoder_image", layout(dst));
              }
              copy(src, dst);
              src.close();
              dst.close();
              int bytes = encoder.getInputBuffer(en).capacity();
              encoder.queueInputBuffer(en, 0, bytes, pts, 0);
              frames++;
              last = pts;
              progress.accept((int) (frames * 80L / expected.frames));
              if (frames > expected.frames) throw new IOException("Unexpected frame count");
            }
            decodedEnded = (b.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
          } finally {
            decoder.releaseOutputBuffer(n, false);
          }
        }
      }
      if (frames != expected.frames)
        throw new IOException("Decoded " + frames + " frames, expected " + expected.frames + "");
      int en;
      while ((en = encoder.dequeueInputBuffer(1000)) < 0) {
        drain.poll(1000);
        cancellation.run();
        if (SystemClock.elapsedRealtime() - start > 90000)
          throw new IOException("EOS input timeout");
      }
      encoder.queueInputBuffer(en, 0, 0, last + 33333, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
      while (!drain.ended) {
        drain.poll(1000);
        cancellation.run();
        if (SystemClock.elapsedRealtime() - start > 90000)
          throw new IOException("EOS output timeout");
      }
      if (drain.frames != expected.frames)
        throw new IOException(
            "Encoded " + drain.frames + " frames, expected " + expected.frames + "");
      muxer.stop();
      drain.started = false;
      return result
          .put("encoder", encoderName)
          .put("settings", settings.toString())
          .put("encoder_output_format", drain.outputFormat)
          .put("input_frames", frames)
          .put("output_frames", drain.frames)
          .put("first_source_pts_us", first)
          .put("last_output_pts_us", last)
          .put("elapsed_ms", SystemClock.elapsedRealtime() - start)
          .put("process_cpu_ms", android.os.Process.getElapsedCpuTime() - cpu)
          .put("bytes", dest.length())
          .put("output", target)
          .put("decoder", decoder.getName());
    } finally {
      close(decoder, decStarted);
      close(encoder, encStarted);
      if (muxer != null) {
        try {
          if (drain != null && drain.started) muxer.stop();
        } catch (RuntimeException ignored) {
        } finally {
          try {
            muxer.release();
          } catch (RuntimeException ignored) {
            // Staging belongs to the transaction; a release failure never promotes it.
          }
        }
      }
      extractor.release();
    }
  }
}

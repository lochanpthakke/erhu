package com.lochanpthakke.empee3.core.network.apple;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.SystemClock;
import android.view.Surface;
import java.io.File;
import java.io.IOException;

/** Drains a complete cover through the platform decoder before publication. Never renders or writes it. */
public final class ArtworkVideoValidator {
    private static final long MAX_DECODE_MS = 90_000;
    private static final long POLL_US = 10_000;

    private ArtworkVideoValidator() {}

    public static final class Result {
        public final String decoder;
        public final int frames;
        public final int width;
        public final int height;
        public final long elapsedMs;

        Result(String decoder, int frames, int width, int height, long elapsedMs) {
            this.decoder = decoder;
            this.frames = frames;
            this.width = width;
            this.height = height;
            this.elapsedMs = elapsedMs;
        }
    }

    public static Result validate(File file, Runnable checkCancellation) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        SurfaceTexture texture = null;
        Surface surface = null;
        boolean started = false;
        try {
            checkCancellation.run();
            extractor.setDataSource(file.getAbsolutePath());
            int track = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) {
                    if (track != -1) throw new IOException("Multiple video tracks are not supported");
                    track = i;
                }
            }
            if (track == -1) throw new IOException("No video track");
            MediaFormat format = extractor.getTrackFormat(track);
            int width = format.getInteger(MediaFormat.KEY_WIDTH);
            int height = format.getInteger(MediaFormat.KEY_HEIGHT);
            if (width <= 0 || height <= 0) throw new IOException("Invalid video dimensions");
            extractor.selectTrack(track);
            decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            texture = new SurfaceTexture(0);
            texture.setDefaultBufferSize(width, height);
            surface = new Surface(texture);
            decoder.configure(format, surface, null, 0);
            decoder.start();
            started = true;
            long start = SystemClock.elapsedRealtime();
            boolean inputEnded = false;
            boolean outputEnded = false;
            int frames = 0;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (!outputEnded) {
                checkCancellation.run();
                if (SystemClock.elapsedRealtime() - start > MAX_DECODE_MS) throw new IOException("Cover decode timed out");
                if (!inputEnded) {
                    int index = decoder.dequeueInputBuffer(POLL_US);
                    if (index >= 0) {
                        java.nio.ByteBuffer buffer = decoder.getInputBuffer(index);
                        if (buffer == null) throw new IOException("No decoder input buffer");
                        int size = extractor.readSampleData(buffer, 0);
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEnded = true;
                        } else {
                            decoder.queueInputBuffer(index, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }
                int index = decoder.dequeueOutputBuffer(info, POLL_US);
                if (index >= 0) {
                    outputEnded = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    if (!outputEnded || info.size > 0) frames++;
                    decoder.releaseOutputBuffer(index, false);
                }
            }
            if (frames == 0) throw new IOException("The cover did not decode any frames");
            return new Result(decoder.getName(), frames, width, height, SystemClock.elapsedRealtime() - start);
        } catch (java.util.concurrent.CancellationException e) {
            throw e;
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new IOException("The cover could not be decoded", e);
        } finally {
            if (decoder != null) {
                try {
                    if (started) decoder.stop();
                } catch (IllegalStateException ignored) {
                    // A failed codec still needs all of its resources released.
                } finally {
                    try { decoder.release(); } catch (IllegalStateException ignored) { }
                }
            }
            if (surface != null) surface.release();
            if (texture != null) texture.release();
            extractor.release();
        }
    }
}

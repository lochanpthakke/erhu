package com.erhu.erhu.core.network.apple;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Strict, bounded H.264/H.265 syntax reader for the opt-in SDR cover workflow. H.265 7.3.2.2 /
 * E.2.1: changes only SAR and chroma-location VUI syntax. Unsupported structures are rejected,
 * never guessed or edited by byte offset.
 */
public final class ArtworkSps {
  private ArtworkSps() {}

  public static final class Geometry {
    public final int width, height;

    Geometry(int width, int height) {
      this.width = width;
      this.height = height;
    }
  }

  public static Geometry validateAvc(byte[] csd) throws IOException {
    List<byte[]> nals = split(csd);
    byte[] sps = null;
    for (byte[] nal : nals) {
      if ((nal[0] & 31) == 7) {
        require(sps == null, "Multiple AVC SPS");
        sps = nal;
      }
    }
    require(sps != null, "Missing AVC SPS");
    Reader r = new Reader(sps, 1);
    int profile = r.u(8);
    r.u(8);
    r.u(8);
    r.ue();
    require(profile == 100 || profile == 77 || profile == 66, "Unsupported AVC profile");
    if (profile == 100) {
      require(r.ue() == 1 && r.ue() == 0 && r.ue() == 0, "Requires 8-bit YUV420");
      r.u(1);
      require(r.u(1) == 0, "AVC scaling matrices unsupported");
    }
    r.ue();
    int poc = r.ue();
    if (poc == 0) r.ue();
    else require(poc == 2, "Unsupported AVC POC structure");
    r.ue();
    r.u(1);
    int width = (r.ue() + 1) * 16, height = (r.ue() + 1) * 16;
    require(width <= 1088 && height <= 1088, "Unsupported AVC coded dimensions");
    require(r.u(1) == 1, "Interlaced artwork unsupported");
    r.u(1);
    if (r.u(1) != 0) {
      int left = r.ue(), right = r.ue(), top = r.ue(), bottom = r.ue();
      width -= 2 * (left + right);
      height -= 2 * (top + bottom);
    }
    require(r.u(1) == 1, "Missing AVC VUI");
    readVui(r, true);
    require(width == 1080 && height == 1080, "Requires 1080 square artwork");
    return new Geometry(width, height);
  }

  public static byte[] correctHevc(byte[] csd) throws IOException {
    require(csd != null && csd.length <= 65536, "Oversized codec configuration");
    List<byte[]> nals = split(csd);
    int vps = -1, sps = -1, pps = -1;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (byte[] nal : nals) {
      require(nal.length >= 3, "Truncated HEVC NAL");
      int type = (nal[0] >> 1) & 63;
      require((nal[0] & 129) == 0 && (nal[1] & 255) == 1, "Unsupported HEVC layer/temporal header");
      if (type == 32) {
        require(vps == -1, "Multiple VPS");
        vps = new Reader(nal, 2).u(4);
      } else if (type == 33) {
        require(sps == -1, "Multiple SPS");
        Hevc h = parseHevc(nal);
        sps = h.spsId;
        require(h.vpsId == vps && vps == 0 && sps == 0, "Unexpected VPS/SPS relationship");
        String bits =
            h.r.bits.substring(0, h.aspectStart)
                + "100000001"
                + h.r.bits.substring(h.aspectEnd, h.chromaStart)
                + "1011011"
                + h.r.bits.substring(h.chromaEnd, h.r.stop + 1);
        // 1 + aspect_ratio_idc=1; 1 + ue(2) + ue(2). Preserve all other syntax.
        nal = escape(nal, bits);
        Hevc check = parseHevc(nal);
        require(check.explicit, "Metadata correction failed");
      } else if (type == 34) {
        require(pps == -1, "Multiple PPS");
        Reader r = new Reader(nal, 2);
        pps = r.ue();
        require(pps == 0 && r.ue() == sps, "Unexpected PPS/SPS relationship");
      } else throw new IOException("Unexpected codec configuration NAL");
      out.write(new byte[] {0, 0, 0, 1});
      out.write(nal);
    }
    require(vps == 0 && sps == 0 && pps == 0, "Incomplete HEVC parameter sets");
    return out.toByteArray();
  }

  public static void validateHevc(byte[] csd) throws IOException {
    // Validation must not fix an invalid candidate on read.
    List<byte[]> nals = split(csd);
    boolean found = false;
    for (byte[] nal : nals)
      if (((nal[0] >> 1) & 63) == 33) {
        require(!found && parseHevc(nal).explicit, "Missing required explicit metadata");
        found = true;
      }
    require(found, "Missing HEVC SPS");
    correctHevc(csd); // Also checks parameter-set relationships and supported syntax.
  }

  /** Refuse unexpected in-band parameter sets; output uses the validated muxer configuration. */
  public static void validatePicture(byte[] sample) throws IOException {
    List<byte[]> nals = split(sample);
    for (byte[] nal : nals) {
      int type = (nal[0] >> 1) & 63;
      require(type <= 31, "Unexpected in-band metadata");
      Reader r = new Reader(Arrays.copyOf(nal, Math.min(nal.length, 64)), 2);
      r.u(1);
      if (type >= 16 && type <= 23) r.u(1);
      require(r.ue() == 0, "Unexpected slice PPS reference");
    }
  }

  private static final class Hevc {
    Reader r;
    int vpsId, spsId, aspectStart, aspectEnd, chromaStart, chromaEnd;
    boolean explicit;
  }

  private static Hevc parseHevc(byte[] nal) throws IOException {
    Hevc h = new Hevc();
    Reader r = new Reader(nal, 2);
    h.r = r;
    h.vpsId = r.u(4);
    require(r.u(3) == 0, "HEVC sublayers unsupported");
    r.u(1);
    r.u(2);
    r.u(1);
    require(r.u(5) == 1, "Requires HEVC Main");
    r.skip(88); // Remaining general_profile_tier_level (32+48+8 bits).
    h.spsId = r.ue();
    require(r.ue() == 1, "Requires HEVC YUV420");
    int width = r.ue(), height = r.ue();
    require(width <= 1088 && height <= 1088, "Unsupported HEVC coded dimensions");
    if (r.u(1) != 0) {
      int l = r.ue(), rr = r.ue(), t = r.ue(), b = r.ue();
      width -= 2 * (l + rr);
      height -= 2 * (t + b);
    }
    require(width == 1080 && height == 1080, "HEVC geometry changed");
    require(r.ue() == 0 && r.ue() == 0, "Requires 8-bit HEVC");
    int poc = r.ue() + 4;
    require(poc <= 16, "Unsupported POC size");
    r.u(1);
    for (int i = 0; i < 3; i++) r.ue();
    for (int i = 0; i < 6; i++) r.ue();
    require(r.u(1) == 0, "HEVC scaling lists unsupported");
    r.u(1);
    r.u(1);
    require(r.u(1) == 0, "HEVC PCM unsupported");
    int sets = r.ue();
    require(sets <= 1, "Predicted reference sets unsupported");
    for (int i = 0; i < sets; i++) {
      int neg = r.ue(), pos = r.ue();
      require(neg + pos <= 16, "Reference set too large");
      for (int j = 0; j < neg + pos; j++) {
        r.ue();
        r.u(1);
      }
    }
    if (r.u(1) != 0) {
      int n = r.ue();
      require(n <= 16, "Too many long-term references");
      for (int i = 0; i < n; i++) {
        r.skip(poc);
        r.u(1);
      }
    }
    r.u(1);
    r.u(1);
    require(r.u(1) == 1, "Missing HEVC VUI");
    h.aspectStart = r.pos;
    boolean sar = aspect(r);
    h.aspectEnd = r.pos;
    signal(r);
    h.chromaStart = r.pos;
    boolean chroma = chroma(r);
    h.chromaEnd = r.pos;
    require(r.pos <= r.stop, "Truncated VUI tail");
    h.explicit = sar && chroma;
    return h;
  }

  private static void readVui(Reader r, boolean required) throws IOException {
    boolean sar = aspect(r);
    signal(r);
    boolean chroma = chroma(r);
    require(!required || (sar && chroma), "Requires explicit square pixels and top-left chroma");
  }

  private static boolean aspect(Reader r) throws IOException {
    if (r.u(1) == 0) return false;
    int id = r.u(8);
    if (id == 255) require(r.u(16) == 1 && r.u(16) == 1, "Non-square pixels");
    else require(id == 1, "Unsupported sample aspect ratio");
    return true;
  }

  private static void signal(Reader r) throws IOException {
    if (r.u(1) != 0) r.u(1); // overscan
    require(r.u(1) == 1, "Missing color signalling");
    r.u(3);
    require(r.u(1) == 0 && r.u(1) == 1, "Requires explicit limited-range color");
    require(r.u(8) == 1 && r.u(8) == 1 && r.u(8) == 1, "Requires BT.709 SDR");
  }

  private static boolean chroma(Reader r) throws IOException {
    if (r.u(1) == 0) return false;
    require(r.ue() == 2 && r.ue() == 2, "Unsupported chroma location");
    return true;
  }

  private static byte[] escape(byte[] original, String bits) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(original, 0, 2);
    int zeros = 0;
    for (int i = 0; i < bits.length(); i += 8) {
      String group = bits.substring(i, Math.min(i + 8, bits.length()));
      int value = Integer.parseInt(group, 2) << (8 - group.length());
      if (zeros == 2 && value <= 3) {
        out.write(3);
        zeros = 0;
      }
      out.write(value);
      zeros = value == 0 ? zeros + 1 : 0;
    }
    return out.toByteArray();
  }

  private static List<byte[]> split(byte[] data) throws IOException {
    require(
        data != null && data.length > 4 && data.length <= 4 * 1024 * 1024,
        "Invalid NAL buffer size");
    List<byte[]> result = new ArrayList<>();
    int start = -1, i = 0;
    while (i + 2 < data.length) {
      int prefix =
          data[i] == 0 && data[i + 1] == 0
              ? (data[i + 2] == 1
                  ? 3
                  : (i + 3 < data.length && data[i + 2] == 0 && data[i + 3] == 1 ? 4 : 0))
              : 0;
      if (prefix > 0) {
        if (start >= 0) {
          require(i > start, "Empty NAL");
          result.add(Arrays.copyOfRange(data, start, i));
        } else require(i == 0, "Unexpected NAL prefix");
        start = i + prefix;
        i = start;
      } else i++;
    }
    require(start >= 0 && start < data.length, "Requires Annex B codec data");
    result.add(Arrays.copyOfRange(data, start, data.length));
    return result;
  }

  private static final class Reader {
    final String bits;
    final int stop;
    int pos;

    Reader(byte[] nal, int header) throws IOException {
      require(nal.length > header && nal.length <= 65536, "Truncated NAL");
      StringBuilder out = new StringBuilder();
      int zeros = 0;
      for (int i = header; i < nal.length; i++) {
        int b = nal[i] & 255;
        if (zeros == 2 && b == 3) {
          require(i + 1 < nal.length && (nal[i + 1] & 255) <= 3, "Invalid emulation prevention");
          zeros = 0;
          continue;
        }
        for (int n = 7; n >= 0; n--) out.append((b >> n) & 1);
        zeros = b == 0 ? zeros + 1 : 0;
      }
      bits = out.toString();
      stop = bits.lastIndexOf('1');
      require(stop >= 0, "Missing RBSP stop bit");
    }

    int u(int count) throws IOException {
      require(count >= 0 && count <= 31 && pos + count <= bits.length(), "Truncated syntax");
      int value = 0;
      for (int i = 0; i < count; i++) value = (value << 1) | (bits.charAt(pos++) - '0');
      return value;
    }

    void skip(int count) throws IOException {
      require(count >= 0 && pos + count <= bits.length(), "Truncated syntax");
      pos += count;
    }

    int ue() throws IOException {
      int zeros = 0;
      while (u(1) == 0) require(++zeros <= 20, "Exp-Golomb value too large");
      return ((1 << zeros) - 1) + u(zeros);
    }
  }

  private static void require(boolean ok, String message) throws IOException {
    if (!ok) throw new IOException(message);
  }
}

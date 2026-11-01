package com.erhu.erhu.core.network.apple;

import static org.junit.Assert.*;

import java.io.IOException;
import org.junit.Test;

public class ArtworkSpsTest {
  static final byte[] AVC = hex("0000000127640020ac52140440227965ffc00040005a808080db615af7be02");
  static final byte[] HEVC =
      hex(
          "0000000140010c01ffff016000000300b00000030000030078ac0900000001420101016000000300b00000030000030078a002208011072cb96bb9324ba9a808080817685094000000014401c0e30f03b084");
  static final byte[] CORRECTED =
      hex(
          "0000000140010c01ffff016000000300b00000030000030078ac0900000001420101016000000300b00000030000030078a002208011072cb96bb9324bac05a808080db05da14250000000014401c0e30f03b084");

  static byte[] hex(String s) {
    byte[] b = new byte[s.length() / 2];
    for (int i = 0; i < b.length; i++)
      b[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
    return b;
  }

  @Test
  public void acceptsVerifiedAvc() throws Exception {
    assertEquals(1080, ArtworkSps.validateAvc(AVC).height);
  }

  @Test
  public void matchesIndependentlyVerifiedFfmpegCorrectionExactly() throws Exception {
    assertArrayEquals(CORRECTED, ArtworkSps.correctHevc(HEVC));
  }

  @Test
  public void correctionIsIdempotent() throws Exception {
    assertArrayEquals(CORRECTED, ArtworkSps.correctHevc(CORRECTED));
  }

  @Test
  public void validatesCorrectedMetadata() throws Exception {
    ArtworkSps.validateHevc(CORRECTED);
  }

  @Test(expected = IOException.class)
  public void rejectsOmittedMetadataOnValidation() throws Exception {
    ArtworkSps.validateHevc(HEVC);
  }

  @Test
  public void rejectsTruncatedSyntax() throws Exception {
    for (int n = 0; n < 16; n++) {
      try {
        ArtworkSps.correctHevc(java.util.Arrays.copyOf(HEVC, n));
        fail();
      } catch (IOException expected) {
      }
    }
  }

  @Test(expected = IOException.class)
  public void rejectsMissingAvcSps() throws Exception {
    ArtworkSps.validateAvc(HEVC);
  }

  @Test(expected = IOException.class)
  public void rejectsOversizedConfiguration() throws Exception {
    ArtworkSps.correctHevc(new byte[65537]);
  }

  @Test
  public void neverMutatesInput() throws Exception {
    byte[] copy = HEVC.clone();
    ArtworkSps.correctHevc(HEVC);
    assertArrayEquals(copy, HEVC);
  }
}

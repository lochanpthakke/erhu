package com.lochanpthakke.empee3.core.network.apple;

import static org.junit.Assert.*;

import org.junit.Test;

public class ArtworkCompressionPolicyTest {
  @Test
  public void acceptedReferenceIsEligible() {
    assertTrue(ArtworkCompressionPolicy.eligible("video/avc", 1080, 1080, 30, 15000000, 18113173));
  }

  @Test
  public void hevcCannotBeRecompressed() {
    assertFalse(
        ArtworkCompressionPolicy.eligible("video/hevc", 1080, 1080, 30, 15000000, 18113173));
  }

  @Test
  public void portraitAndOtherRatesAreExcluded() {
    assertFalse(ArtworkCompressionPolicy.eligible("video/avc", 1080, 1440, 30, 15000000, 18113173));
    assertFalse(ArtworkCompressionPolicy.eligible("video/avc", 1080, 1080, 24, 15000000, 18113173));
  }

  @Test
  public void lowBitrateAndUnboundedVideosAreExcluded() {
    assertFalse(ArtworkCompressionPolicy.eligible("video/avc", 1080, 1080, 30, 15000000, 3000000));
    assertFalse(ArtworkCompressionPolicy.eligible("video/avc", 1080, 1080, 30, 60000000, 60000000));
  }

  @Test
  public void spaceMustAllowStagingPlusReserve() {
    assertFalse(
        ArtworkCompressionPolicy.enoughSpace(
            18113173, 18113173 + ArtworkCompressionPolicy.RESERVE_BYTES - 1));
    assertTrue(
        ArtworkCompressionPolicy.enoughSpace(
            18113173, 18113173 + ArtworkCompressionPolicy.RESERVE_BYTES));
  }

  @Test
  public void actualSavingsMustBeMeaningful() {
    assertTrue(ArtworkCompressionPolicy.worthwhile(18113173, 11188275));
    assertFalse(ArtworkCompressionPolicy.worthwhile(18113173, 18113173));
    assertFalse(ArtworkCompressionPolicy.worthwhile(18113173, 18000000));
    assertFalse(ArtworkCompressionPolicy.worthwhile(18113173, 0));
    assertFalse(ArtworkCompressionPolicy.worthwhile(18113173, 20000000));
  }
}

package com.signa.sign.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewSamplingTest {
  @Test
  void selectsApproximatelyOnePercentAndIsStableOnRetry() {
    var sampling = new ReviewSampling();
    int count = 0;
    for (int i = 0; i < 10000; i++) {
      String id =
          UUID.nameUUIDFromBytes(
                  ("observation-" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8))
              .toString();
      boolean selected = sampling.select(id);
      assertThat(sampling.select(id)).isEqualTo(selected);
      if (selected) count++;
    }
    assertThat(count).isBetween(65, 135);
  }
}

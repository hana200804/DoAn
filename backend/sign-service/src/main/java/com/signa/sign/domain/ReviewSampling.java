package com.signa.sign.domain;

import org.springframework.stereotype.Component;

/** Stable one-percent sampling of UUID observations, independent of confidence (AI design §9). */
@Component
public class ReviewSampling {
  public boolean select(String observationId) {
    return Math.floorMod(observationId.hashCode(), 100) == 0;
  }
}

package com.signa.sign.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.signa.sign.domain.ClusterRules.Candidate;
import com.signa.sign.domain.ClusterRules.Evidence;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Ca UT-B01 → UT-B06 trong docs/08-kiem-thu.md §2.2. */
class ClusterRulesTest {

  private static final double LAT = 21.0285;
  private static final double LNG = 105.8048;

  @Test
  void ut_b01_nearbySameHeadingJoinsCluster_andFourUsersVerify() {
    Candidate c = new Candidate("c1", "PROHIBITORY", LAT, LNG, 90, false);
    double eightMetersNorth = LAT + Geo.metersToLatDeg(8);

    assertThat(ClusterRules.pick(List.of(c), "PROHIBITORY", eightMetersNorth, LNG, 95)).contains(c);

    var twoUsers = assess(Map.of("P.123a", 1.8), 0, 2, 0, ClusterStatus.CANDIDATE);
    assertThat(twoUsers.status()).isEqualTo(ClusterStatus.CANDIDATE);

    var fourUsers = assess(Map.of("P.123a", 3.6), 0, 4, 0, ClusterStatus.CANDIDATE);
    assertThat(fourUsers.confidence()).isGreaterThanOrEqualTo(0.8);
    assertThat(fourUsers.status()).isEqualTo(ClusterStatus.VERIFIED);
  }

  @Test
  void ut_b02_singleUserNeverVerifies() {
    var a = assess(Map.of("P.123a", 10.0), 0, 1, 0, ClusterStatus.CANDIDATE);

    assertThat(a.confidence()).isGreaterThan(0.8);
    assertThat(a.status()).isEqualTo(ClusterStatus.CANDIDATE);
  }

  @Test
  void ut_b03_oppositeHeadingCreatesSeparateCluster() {
    Candidate c = new Candidate("c1", "PROHIBITORY", LAT, LNG, 90, false);

    assertThat(ClusterRules.pick(List.of(c), "PROHIBITORY", LAT, LNG + 0.00004, 270)).isEmpty();
  }

  @Test
  void longRejectedClusterIsNotPickedButIsReported() {
    Candidate c = new Candidate("c1", "PROHIBITORY", LAT, LNG, 90, true);

    assertThat(ClusterRules.pick(List.of(c), "PROHIBITORY", LAT, LNG, 90)).isEmpty();
    assertThat(ClusterRules.nearRejected(List.of(c), "PROHIBITORY", LAT, LNG, 90)).isTrue();
    assertThat(ClusterRules.nearRejected(List.of(c), "WARNING", LAT, LNG, 90)).isFalse();
  }

  @Test
  void otherGroupOrFarAwayDoesNotMatch() {
    Candidate c = new Candidate("c1", "PROHIBITORY", LAT, LNG, 90, false);
    double thirtyMetersNorth = LAT + Geo.metersToLatDeg(30);

    assertThat(ClusterRules.pick(List.of(c), "WARNING", LAT, LNG, 90)).isEmpty();
    assertThat(ClusterRules.pick(List.of(c), "PROHIBITORY", thirtyMetersNorth, LNG, 90)).isEmpty();
  }

  @Test
  void ut_b04_secondCodeAboveThirtyPercentIsConflict() {
    var a = assess(Map.of("P.123a", 6.5, "P.123b", 3.5), 0, 5, 0, ClusterStatus.VERIFIED);

    assertThat(a.status()).isEqualTo(ClusterStatus.CONFLICT);
    assertThat(a.topCode()).isEqualTo("P.123a");

    var moreA = assess(Map.of("P.123a", 9.0, "P.123b", 3.5), 0, 5, 0, ClusterStatus.CONFLICT);
    assertThat(moreA.status()).isEqualTo(ClusterStatus.CONFLICT);
  }

  @Test
  void ut_b05_threeNegativePassesMarkVerifiedAsMissing() {
    var stillThere = assess(Map.of("P.102", 5.0), 1.0, 5, 2, ClusterStatus.VERIFIED);
    var missing = assess(Map.of("P.102", 5.0), 1.5, 5, 3, ClusterStatus.VERIFIED);

    assertThat(stillThere.status()).isEqualTo(ClusterStatus.VERIFIED);
    assertThat(missing.status()).isEqualTo(ClusterStatus.MISSING_SUSPECTED);
  }

  @Test
  void adminLockedCodeWinsAndRejectedStaysRejected() {
    var locked =
        ClusterRules.assess(
            new Evidence(
                Map.of("P.123a", 5.0, "P.123b", 4.0), 0, 3, 0, "P.123b", ClusterStatus.CONFLICT));
    var rejected = assess(Map.of("P.102", 9.0), 0, 6, 0, ClusterStatus.REJECTED);

    assertThat(locked.topCode()).isEqualTo("P.123b");
    assertThat(locked.status()).isEqualTo(ClusterStatus.VERIFIED);
    assertThat(rejected.status()).isEqualTo(ClusterStatus.REJECTED);
  }

  @Test
  void ut_b06_reputationMovesAndIsClamped() {
    assertThat(ClusterRules.updatedReputation(1.0, false)).isEqualTo(0.9);
    assertThat(ClusterRules.updatedReputation(0.55, false)).isEqualTo(0.5);
    assertThat(ClusterRules.updatedReputation(1.48, true)).isEqualTo(1.5);
  }

  private static ClusterRules.Assessment assess(
      Map<String, Double> weights, double neg, int users, int negPasses, ClusterStatus current) {
    return ClusterRules.assess(new Evidence(weights, neg, users, negPasses, null, current));
  }
}

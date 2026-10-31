package com.signa.sign.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.signa.common.geo.LatLng;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeoTest {

  @Test
  void distanceOfOneThousandthDegreeLatitudeIsAbout111m() {
    assertThat(Geo.distanceM(21.0, 105.8, 21.001, 105.8)).isCloseTo(111.2, within(0.5));
  }

  @Test
  void headingDiffWrapsAround() {
    assertThat(Geo.headingDiff(350, 10)).isEqualTo(20);
    assertThat(Geo.headingDiff(90, 270)).isEqualTo(180);
  }

  @Test
  void distanceToPolylineUsesNearestSegment() {
    List<LatLng> road = List.of(new LatLng(21.0, 105.80), new LatLng(21.0, 105.81));
    double tenMetersNorth = 21.0 + Geo.metersToLatDeg(10);

    assertThat(Geo.distanceToPolylineM(tenMetersNorth, 105.805, road)).isCloseTo(10, within(0.1));
  }

  @Test
  void weightedMedianIgnoresOutlier() {
    double m =
        Geo.weightedMedian(
            List.of(new double[] {1.0, 1}, new double[] {1.1, 1}, new double[] {9.0, 1}));
    assertThat(m).isEqualTo(1.1);
  }
}

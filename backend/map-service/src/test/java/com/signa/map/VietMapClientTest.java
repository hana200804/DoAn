package com.signa.map;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class VietMapClientTest {

  @Test
  void parsesRouteGeoJsonAsLatLngAndSeconds() throws Exception {
    var body =
        new ObjectMapper()
            .readTree(
                """
                {"code":"OK","paths":[{"distance":1234.5,"time":185000,
                  "points":{"type":"LineString","coordinates":[[105.80,21.02],[105.81,21.03]]}}]}
                """);

    var route = VietMapClient.parseRoute(body);

    assertThat(route.distanceM()).isEqualTo(1234.5);
    assertThat(route.durationS()).isEqualTo(185);
    assertThat(route.polyline()).hasSize(2);
    assertThat(route.polyline().get(0).lat()).isEqualTo(21.02);
    assertThat(route.polyline().get(0).lng()).isEqualTo(105.80);
  }
}

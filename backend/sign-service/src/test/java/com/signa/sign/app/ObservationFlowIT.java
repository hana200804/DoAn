package com.signa.sign.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.signa.common.geo.LatLng;
import com.signa.sign.api.SignDtos.CodeProb;
import com.signa.sign.api.SignDtos.CreateBatchRequest;
import com.signa.sign.api.SignDtos.Kind;
import com.signa.sign.api.SignDtos.ObservationIn;
import com.signa.sign.domain.ClusterStatus;
import com.signa.sign.store.CropStorage;
import com.signa.sign.store.SignRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** IT-B01 → IT-B03: luồng gửi quan sát trên MySQL thật. Chạy bằng {@code -Pintegration}. */
@Tag("integration")
@SpringBootTest
@Testcontainers
class ObservationFlowIT {

  @Container @ServiceConnection static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

  @MockBean CropStorage storage;

  @Autowired ObservationService observations;
  @Autowired SignRepository repo;

  private final double lat = 21.0 + Math.random() * 0.1;
  private final double lng = 105.8;

  @BeforeEach
  void storage() {
    when(storage.uploadUrl(anyString())).thenReturn("http://minio/upload");
    when(storage.exists(anyString())).thenReturn(true);
  }

  @Test
  void it_b01_sameIdempotencyKeyReturnsSameBatch() {
    var req = batch(0.9);
    var first = observations.createBatch("user-x", "key-12345678", req);
    var second = observations.createBatch("user-x", "key-12345678", req);

    assertThat(second.batchId()).isEqualTo(first.batchId());
    assertThat(repo.observations(first.batchId())).hasSize(1);
  }

  @Test
  void it_b02_fourDriversVerifyOneCluster_andIt_b03_bboxReturnsIt() {
    for (int i = 0; i < 4; i++) {
      String user = "user-" + i;
      var res = observations.createBatch(user, UUID.randomUUID().toString(), batch(0.95));
      observations.complete(user, res.batchId());
    }

    var clusters =
        repo.clustersInBox(
            lat - 0.001, lng - 0.001, lat + 0.001, lng + 0.001, List.of("VERIFIED"), 10);
    assertThat(clusters).hasSize(1);
    assertThat(clusters.get(0).status()).isEqualTo(ClusterStatus.VERIFIED);
    assertThat(clusters.get(0).distinctUsers()).isEqualTo(4);
    assertThat(clusters.get(0).signCode()).isEqualTo("P.123a");
  }

  private CreateBatchRequest batch(double p) {
    var obs =
        new ObservationIn(
            "o-1",
            Kind.POSITIVE,
            "P.123a",
            null,
            "prohibitory",
            List.of(new CodeProb("P.123a", p)),
            0.9,
            new LatLng(lat, lng),
            90,
            15.0,
            8,
            false,
            Instant.now());
    return new CreateBatchRequest("model-v0.1", 1, "test", List.of(obs));
  }
}

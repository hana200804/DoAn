package com.signa.sign.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.signa.common.error.ApiException;
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
  @Autowired ReviewService reviews;

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

  @Test
  void rejectedClusterDoesNotAttractNewObservations() {
    String first = submit("user-r1");
    repo.setStatus(first, ClusterStatus.REJECTED, "P.123a", false);

    String second = submit("user-r2");
    assertThat(second).isNotEqualTo(first);
    assertThat(repo.cluster(second).orElseThrow().status()).isEqualTo(ClusterStatus.CONFLICT);
  }

  @Test
  void sameUserAddsWeightOnlyOnceAndUnknownCodeIsSkipped() {
    String cluster = submit("user-w");
    double once = repo.codeWeights(cluster).get("P.123a");
    submit("user-w");
    assertThat(repo.codeWeights(cluster).get("P.123a")).isEqualTo(once);

    var req = batch(0.9);
    var bad = req.observations().get(0);
    var unknown =
        new ObservationIn(
            "o-2",
            bad.kind(),
            "X.999",
            null,
            bad.detectorGroup(),
            List.of(),
            bad.voteShare(),
            bad.location(),
            bad.headingDeg(),
            bad.distanceM(),
            bad.frames(),
            false,
            bad.observedAt());
    var res =
        observations.createBatch(
            "user-u",
            UUID.randomUUID().toString(),
            new CreateBatchRequest("model-v0.1", 1, "test", List.of(bad, unknown)));
    assertThat(res.uploads()).hasSize(1);
    assertThat(repo.observations(res.batchId())).hasSize(1);
  }

  @Test
  void resolvingConflictTwiceCountsReputationOnce() {
    String cluster = submit("user-c1");
    repo.setStatus(cluster, ClusterStatus.CONFLICT, "P.123a", false);

    reviews.resolveConflict(cluster, "admin", "P.123a", false, "đúng");
    assertThatThrownBy(() -> reviews.resolveConflict(cluster, "admin", "P.123a", false, "đúng"))
        .isInstanceOf(ApiException.class);
    assertThat(repo.reputation("user-c1")).isEqualTo(1.05);
  }

  @Test
  void storageOutageFailsCompleteSoItCanBeRetried() {
    var res = observations.createBatch("user-s", UUID.randomUUID().toString(), batch(0.95));
    when(storage.exists(anyString())).thenThrow(ApiException.upstream("down"));
    assertThatThrownBy(() -> observations.complete("user-s", res.batchId()))
        .isInstanceOf(ApiException.class);

    doReturn(true).when(storage).exists(anyString());
    observations.complete("user-s", res.batchId());
    assertThat(repo.observations(res.batchId()).get(0).cropKey()).isNotNull();
  }

  private String submit(String user) {
    var res = observations.createBatch(user, UUID.randomUUID().toString(), batch(0.95));
    observations.complete(user, res.batchId());
    return repo.observations(res.batchId()).get(0).clusterId();
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

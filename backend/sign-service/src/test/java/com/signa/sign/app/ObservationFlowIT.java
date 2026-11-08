package com.signa.sign.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.signa.common.error.ApiException;
import com.signa.common.geo.LatLng;
import com.signa.sign.SignDatabaseIT;
import com.signa.sign.api.SignDtos.CodeProb;
import com.signa.sign.api.SignDtos.CreateBatchRequest;
import com.signa.sign.api.SignDtos.Kind;
import com.signa.sign.api.SignDtos.ObservationIn;
import com.signa.sign.domain.ClusterStatus;
import com.signa.sign.store.CropStorage;
import com.signa.sign.store.SignRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** IT-B01 → IT-B03: luồng gửi quan sát trên MySQL thật. Chạy bằng {@code -Pintegration}. */
@Tag("integration")
@SpringBootTest
class ObservationFlowIT extends SignDatabaseIT {

  @MockBean CropStorage storage;

  @Autowired ObservationService observations;
  @SpyBean SignRepository repo;
  @Autowired ReviewService reviews;
  @Autowired NamedParameterJdbcTemplate jdbc;

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
  void rejectionHoldsForThirtyDaysThenNeedsReview() {
    String first = submit("user-r1");
    repo.setStatus(first, ClusterStatus.REJECTED, "P.123a", false);

    assertThat(submit("user-r2")).isEqualTo(first);
    assertThat(repo.cluster(first).orElseThrow().status()).isEqualTo(ClusterStatus.REJECTED);

    jdbc.update(
        "UPDATE sign_clusters SET updated_at = NOW(3) - INTERVAL 31 DAY WHERE id = :id",
        Map.of("id", first));
    String second = submit("user-r3");
    assertThat(second).isNotEqualTo(first);
    assertThat(repo.cluster(second).orElseThrow().status()).isEqualTo(ClusterStatus.CONFLICT);
  }

  @Test
  void sameUserAddsWeightOnlyOnceAndUnknownCodeIsSkipped() {
    String cluster = submit("user-w");
    double once = repo.codeWeights(cluster).get("P.123a");
    submit("user-w");
    assertThat(repo.codeWeights(cluster).get("P.123a")).isEqualTo(once);

    // Lượt đi sau 12 giờ được tính thêm
    jdbc.update(
        "UPDATE observations SET created_at = NOW(3) - INTERVAL 13 HOUR WHERE cluster_id = :c",
        Map.of("c", cluster));
    submit("user-w");
    assertThat(repo.codeWeights(cluster).get("P.123a")).isGreaterThan(once);

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

  @Test
  void adminDecisionSurvivesConcurrentComplete() throws Exception {
    String cluster = submit("user-a1");
    repo.setStatus(cluster, ClusterStatus.CONFLICT, "P.123a", false);
    var pending = observations.createBatch("racer", UUID.randomUUID().toString(), batch(0.95));

    // complete dừng sau khi transaction đã đọc dữ liệu; quản trị viên chốt mã và commit trong lúc
    // đó
    var armed = new AtomicBoolean(true);
    var paused = new CountDownLatch(1);
    var resume = new CountDownLatch(1);
    doAnswer(
            inv -> {
              if (armed.compareAndSet(true, false)) {
                paused.countDown();
                resume.await(10, TimeUnit.SECONDS);
              }
              return inv.callRealMethod();
            })
        .when(repo)
        .candidatesNear(anyDouble(), anyDouble(), anyDouble());
    try (var pool = Executors.newSingleThreadExecutor()) {
      var done = pool.submit(() -> observations.complete("racer", pending.batchId()));
      assertThat(paused.await(10, TimeUnit.SECONDS)).isTrue();
      reviews.resolveConflict(cluster, "admin", "P.123b", false, "duyệt");
      resume.countDown();
      done.get(20, TimeUnit.SECONDS);
    }

    var c = repo.cluster(cluster).orElseThrow();
    assertThat(c.signCode()).isEqualTo("P.123b");
    assertThat(c.status()).isEqualTo(ClusterStatus.VERIFIED);
  }

  @Test
  void negativePassesCountOnlyProcessedPassesAndRepeatDriversCount() {
    String cluster = verifiedCluster();

    // Lô âm đã tạo nhưng chưa complete không phải bằng chứng
    for (int i = 0; i < 3; i++) {
      observations.createBatch(
          "pending-" + i, UUID.randomUUID().toString(), negativeBatch(cluster));
    }
    assertThat(repo.facts(cluster).negativePasses()).isZero();

    // Cùng một người đi qua ba lượt cách nhau hơn 12 giờ là ba lượt
    for (int i = 0; i < 3; i++) {
      var b =
          observations.createBatch(
              "commuter", UUID.randomUUID().toString(), negativeBatch(cluster));
      observations.complete("commuter", b.batchId());
      jdbc.update(
          "UPDATE observations SET created_at = created_at - INTERVAL 13 HOUR WHERE user_id = :u",
          Map.of("u", "commuter"));
    }
    assertThat(repo.facts(cluster).negativePasses()).isEqualTo(3);
    assertThat(repo.cluster(cluster).orElseThrow().status())
        .isEqualTo(ClusterStatus.MISSING_SUSPECTED);
  }

  @Test
  void blockedPassDoesNotExtendTheTwelveHourWindow() {
    String user = "d-" + UUID.randomUUID().toString().substring(0, 8);
    String cluster = submit(user);
    double one = repo.codeWeights(cluster).get("P.123a");
    submit(user); // ngay sau đó: bị bỏ, weight 0
    // Lần góp thật cách 16 giờ, lần bị bỏ cách 8 giờ
    jdbc.update(
        "UPDATE observations SET created_at = NOW(3) - INTERVAL"
            + " (CASE WHEN weight > 0 THEN 16 ELSE 8 END) HOUR WHERE user_id = :u",
        Map.of("u", user));
    submit(user);
    assertThat(repo.codeWeights(cluster).get("P.123a")).isEqualTo(2 * one);
  }

  @Test
  void duplicateReportsSentTogetherCreateOnlyOne() throws Exception {
    String cluster = submit("reporter-c");
    var first = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    doAnswer(
            inv -> {
              Object r = inv.callRealMethod();
              if (first.getCount() > 0) {
                first.countDown();
                release.await(10, TimeUnit.SECONDS);
              }
              return r;
            })
        .when(repo)
        .hasPendingReport(cluster, "reporter");
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a = pool.submit(() -> reviews.report(cluster, "reporter", "NOT_EXIST", null));
      assertThat(first.await(10, TimeUnit.SECONDS)).isTrue();
      var b = pool.submit(() -> reviews.report(cluster, "reporter", "NOT_EXIST", null));
      Thread.sleep(500); // request thứ hai phải chờ khoá cụm
      release.countDown();
      a.get(20, TimeUnit.SECONDS);
      assertThatThrownBy(() -> b.get(20, TimeUnit.SECONDS)).hasCauseInstanceOf(ApiException.class);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM sign_reports WHERE cluster_id = :c AND user_id = 'reporter'",
                Map.of("c", cluster),
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void simultaneousFirstSightingsCreateOneCluster() throws Exception {
    var a = observations.createBatch("first-a", UUID.randomUUID().toString(), batch(.95));
    var b = observations.createBatch("first-b", UUID.randomUUID().toString(), batch(.95));
    try (var pool = Executors.newFixedThreadPool(2)) {
      var x = pool.submit(() -> observations.complete("first-a", a.batchId()));
      var y = pool.submit(() -> observations.complete("first-b", b.batchId()));
      x.get(20, TimeUnit.SECONDS);
      y.get(20, TimeUnit.SECONDS);
    }
    assertThat(repo.observations(a.batchId()).get(0).clusterId())
        .isEqualTo(repo.observations(b.batchId()).get(0).clusterId());
  }

  @Test
  void negativePassDoesNotMoveLastSeenAt() {
    String cluster = verifiedCluster();
    Instant seenAt = repo.cluster(cluster).orElseThrow().lastSeenAt();
    var negative =
        observations.createBatch("negative", UUID.randomUUID().toString(), negativeBatch(cluster));
    observations.complete("negative", negative.batchId());
    assertThat(repo.cluster(cluster).orElseThrow().lastSeenAt()).isEqualTo(seenAt);
  }

  @Test
  void mixedBatchAcceptsTheFixCapturedNearTheSignBeforeDrivingSixtyMetersAway() {
    String cluster = verifiedCluster();
    var near = new LatLng(lat, lng + com.signa.sign.domain.Geo.metersToLngDeg(10, lat));
    var negative =
        new ObservationIn(
            "near-pass",
            Kind.NEGATIVE,
            null,
            cluster,
            null,
            null,
            null,
            near,
            90,
            null,
            null,
            false,
            Instant.now());
    var positive = batch(.95).observations().get(0);
    var res =
        observations.createBatch(
            "mixed-pass",
            UUID.randomUUID().toString(),
            new CreateBatchRequest("model-v1", 1, "test", List.of(positive, negative)));
    observations.complete("mixed-pass", res.batchId());
    assertThat(repo.observations(res.batchId())).hasSize(2);
    assertThat(repo.batch(res.batchId()).orElseThrow().status()).isEqualTo("PROCESSED");
  }

  private String verifiedCluster() {
    String cluster = null;
    for (int i = 0; i < 4; i++) {
      cluster = submit("v" + i + "-" + UUID.randomUUID().toString().substring(0, 8));
    }
    assertThat(repo.cluster(cluster).orElseThrow().status()).isEqualTo(ClusterStatus.VERIFIED);
    return cluster;
  }

  private CreateBatchRequest negativeBatch(String clusterId) {
    var obs =
        new ObservationIn(
            "n-1",
            Kind.NEGATIVE,
            null,
            clusterId,
            null,
            null,
            null,
            new LatLng(lat, lng),
            90,
            null,
            null,
            false,
            Instant.now().plusSeconds(5));
    return new CreateBatchRequest("model-v0.1", 1, "test", List.of(obs));
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

package com.signa.sign.app;

import com.signa.common.error.ApiException;
import com.signa.sign.api.SignDtos.CodeProb;
import com.signa.sign.api.SignDtos.CreateBatchRequest;
import com.signa.sign.api.SignDtos.CreateBatchResponse;
import com.signa.sign.api.SignDtos.Kind;
import com.signa.sign.api.SignDtos.ObservationIn;
import com.signa.sign.api.SignDtos.Upload;
import com.signa.sign.domain.ClusterRules;
import com.signa.sign.domain.ClusterRules.Assessment;
import com.signa.sign.domain.ClusterRules.Candidate;
import com.signa.sign.domain.ClusterRules.Evidence;
import com.signa.sign.domain.ClusterStatus;
import com.signa.sign.domain.Geo;
import com.signa.sign.domain.ReviewSampling;
import com.signa.sign.store.CropStorage;
import com.signa.sign.store.LearningRepository;
import com.signa.sign.store.SignRepository;
import com.signa.sign.store.SignRepository.ClusterFacts;
import com.signa.sign.store.SignRepository.Observation;
import com.signa.sign.store.SignRepository.SignType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Nhận lô quan sát (FR-14) và gom cụm, tính điểm tin cậy (FR-17). */
@Service
public class ObservationService {

  private static final Logger log = LoggerFactory.getLogger(ObservationService.class);

  private static final double DEFAULT_PROBABILITY = 0.5;

  private final SignRepository repo;
  private final CropStorage storage;
  private final LearningRepository learning;
  private final ReviewSampling sampling;

  public ObservationService(
      SignRepository repo,
      CropStorage storage,
      LearningRepository learning,
      ReviewSampling sampling) {
    this.repo = repo;
    this.storage = storage;
    this.learning = learning;
    this.sampling = sampling;
  }

  @Transactional
  public CreateBatchResponse createBatch(
      String userId, String idempotencyKey, CreateBatchRequest req) {
    Optional<String> existing = repo.batchId(userId, idempotencyKey);
    if (existing.isPresent()) {
      return new CreateBatchResponse(existing.get(), uploadsFor(repo.observations(existing.get())));
    }
    if (!repo.supportsLabelMap(req.labelMapVersion())) {
      throw ApiException.businessRule("Phiên bản label map chưa được server hỗ trợ");
    }
    String batchId = UUID.randomUUID().toString();
    try {
      repo.insertBatch(
          batchId,
          userId,
          idempotencyKey,
          req.modelVersion(),
          req.labelMapVersion(),
          req.deviceModel());
    } catch (DuplicateKeyException e) {
      // Request trùng đang chạy song song; gửi lại sẽ nhận lô đã tạo
      throw ApiException.conflict("Lô quan sát đang được tạo, hãy gửi lại");
    }
    List<Observation> saved = new ArrayList<>();
    var clients = new HashSet<String>();
    for (int i = 0; i < req.observations().size(); i++) {
      if (!clients.add(req.observations().get(i).clientId())) {
        throw ApiException.validation("observations[" + i + "].clientId", "trùng trong cùng lô");
      }
      Observation o =
          toObservation(batchId, userId, req.observations().get(i), "observations[" + i + "]");
      if (o == null) {
        continue;
      }
      repo.insertObservation(batchId, o);
      saved.add(o);
    }
    return new CreateBatchResponse(batchId, uploadsFor(saved));
  }

  /** Xác nhận ảnh đã tải lên rồi gom cụm toàn bộ lô. Gọi lại với lô đã xử lý thì không làm gì. */
  // READ COMMITTED: sau khi khoá dòng cụm phải đọc được quyết định quản trị viên vừa commit, không
  // đọc snapshot cũ từ đầu transaction
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void complete(String userId, String batchId) {
    var batch =
        repo.batch(batchId).orElseThrow(() -> ApiException.notFound("Không tìm thấy lô quan sát"));
    if (!batch.userId().equals(userId)) {
      throw ApiException.notFound("Không tìm thấy lô quan sát");
    }
    if (!repo.claimBatch(batchId)) {
      return; // đã hoặc đang được xử lý bởi request khác
    }
    var observations = repo.observations(batchId);
    var crops = new java.util.HashMap<String, String>();
    var subs = new java.util.HashMap<String, String>();
    for (Observation o : observations) {
      if (o.cropKey() != null && storage.exists(o.cropKey())) crops.put(o.id(), o.cropKey());
      if (o.subSignKey() != null && storage.exists(o.subSignKey()))
        subs.put(o.id(), o.subSignKey());
    }
    // The short database-only section is serialized; S3 and AI calls never hold this lock.
    repo.lockIngestion();
    for (Observation o : observations) {
      String crop = crops.get(o.id());
      String sub = subs.get(o.id());
      if (Kind.POSITIVE.name().equals(o.kind())) {
        boolean conflict = processPositive(o, crop, sub) == ClusterStatus.CONFLICT;
        boolean hard = o.flags() != null && !o.flags().equals("[]");
        if (crop != null && (hard || conflict)) {
          if (conflict) repo.addObservationFlag(o.id(), "CONFLICT");
          learning.labelTask(
              o.id(),
              !hard
                  ? "CONFLICT"
                  : o.flags().contains("LOW_CONF")
                      ? "LOW_CONF"
                      : o.flags().contains("SPLIT_VOTE") ? "SPLIT_VOTE" : "MISMATCH");
        } else if (crop != null && sampling.select(o.id())) {
          learning.labelTask(o.id(), "RANDOM");
        }
        if ((crop != null && (hard || conflict)) || sub != null) learning.enqueue(o.id());
      } else {
        processNegative(o);
      }
    }
  }

  private ClusterStatus processPositive(Observation o, String crop, String sub) {
    String group = repo.signType(o.signCode()).orElseThrow().group();
    List<Candidate> candidates =
        repo.candidatesNear(o.lat(), o.lng(), ClusterRules.MATCH_RADIUS_M + 5);
    String clusterId =
        ClusterRules.pick(candidates, group, o.lat(), o.lng(), o.headingDeg())
            .map(Candidate::id)
            .orElse(null);
    if (clusterId == null) {
      clusterId = UUID.randomUUID().toString();
      repo.insertCluster(clusterId, o.signCode(), o.lat(), o.lng(), o.headingDeg(), o.observedAt());
      repo.event(clusterId, null, "CREATED", null, o.signCode(), null);
      // Trùng chỗ đã loại từ lâu: để quản trị viên xét lại, không tự công nhận lại
      if (ClusterRules.nearRejected(candidates, group, o.lat(), o.lng(), o.headingDeg())) {
        repo.setStatus(clusterId, ClusterStatus.CONFLICT, o.signCode(), false);
        repo.event(clusterId, null, "STATUS_CHANGED", "CANDIDATE", "CONFLICT", "gần cụm đã loại");
      }
    }
    // Khoá cụm trước mọi thao tác đọc/ghi bằng chứng, để không chen ngang quyết định quản trị viên
    repo.lockCluster(clusterId);
    // Mỗi người góp tối đa một lần mỗi 12 giờ cho một cụm, để một tài khoản không tự đẩy cụm lên
    double weight =
        repo.contributedRecently(clusterId, o.userId(), o.id(), Kind.POSITIVE)
            ? 0
            : (o.probability() == null ? DEFAULT_PROBABILITY : o.probability())
                * repo.reputation(o.userId());
    repo.addCodeWeight(clusterId, o.signCode(), weight);
    repo.assignObservation(o.id(), clusterId, weight, crop, sub);
    return reassess(clusterId, o);
  }

  private void processNegative(Observation o) {
    if (o.clusterId() == null || repo.cluster(o.clusterId()).isEmpty()) {
      return;
    }
    repo.lockCluster(o.clusterId());
    // Một lượt đi qua của mỗi người trong 12 giờ, như quan sát dương
    double weight =
        repo.contributedRecently(o.clusterId(), o.userId(), o.id(), Kind.NEGATIVE)
            ? 0
            : repo.reputation(o.userId());
    repo.addNegativeWeight(o.clusterId(), weight);
    repo.assignObservation(o.id(), o.clusterId(), weight, null, null);
    reassess(o.clusterId(), o);
  }

  private ClusterStatus reassess(String clusterId, Observation trigger) {
    Map<String, Double> weights = repo.codeWeights(clusterId);
    ClusterFacts facts = repo.facts(clusterId);
    Assessment a =
        ClusterRules.assess(
            new Evidence(
                weights,
                facts.negativeWeight(),
                facts.distinctUsers(),
                facts.negativePasses(),
                facts.adminLocked() ? facts.signCode() : null,
                facts.status()));
    List<double[]> positions = repo.positivePositions(clusterId);
    double lat = trigger.lat();
    double lng = trigger.lng();
    if (!positions.isEmpty()) {
      lat = Geo.weightedMedian(positions.stream().map(p -> new double[] {p[0], p[2]}).toList());
      lng = Geo.weightedMedian(positions.stream().map(p -> new double[] {p[1], p[2]}).toList());
    } else {
      var current = repo.cluster(clusterId).orElseThrow();
      lat = current.lat();
      lng = current.lng();
    }
    repo.updateAssessment(
        clusterId,
        a.topCode(),
        a.alpha(),
        a.beta(),
        a.confidence(),
        a.status(),
        lat,
        lng,
        facts.distinctUsers(),
        trigger.observedAt(),
        Kind.POSITIVE.name().equals(trigger.kind()));
    if (a.status() != facts.status()) {
      repo.event(clusterId, null, "STATUS_CHANGED", facts.status().name(), a.status().name(), null);
    }
    return a.status();
  }

  /** Null nếu quan sát dương có mã biển không dùng được. */
  private Observation toObservation(String batchId, String userId, ObservationIn in, String field) {
    String id = UUID.randomUUID().toString();
    if (in.observedAt().isAfter(java.time.Instant.now().plusSeconds(300))) {
      throw ApiException.validation(field + ".observedAt", "không được ở tương lai quá 5 phút");
    }
    if (in.kind() == Kind.NEGATIVE) {
      if (in.clusterId() == null) {
        throw ApiException.validation(field + ".clusterId", "bắt buộc với quan sát âm");
      }
      var cluster =
          repo.cluster(in.clusterId())
              .orElseThrow(
                  () -> ApiException.validation(field + ".clusterId", "không có cụm biển này"));
      if (Geo.distanceM(in.location().lat(), in.location().lng(), cluster.lat(), cluster.lng()) > 50
          || Geo.headingDiff(in.headingDeg(), cluster.headingDeg()) > 30) {
        throw ApiException.validation(
            field + ".clusterId", "quan sát âm phải gần biển và cùng hướng");
      }
      return new Observation(
          id,
          in.clientId(),
          userId,
          in.kind().name(),
          null,
          null,
          null,
          null,
          in.location().lat(),
          in.location().lng(),
          in.headingDeg(),
          null,
          null,
          in.clusterId(),
          null,
          null,
          null,
          in.observedAt());
    }
    // Mã ngừng dùng hoặc chưa có trên server: bỏ riêng quan sát này, không làm hỏng cả lô
    SignType type =
        Optional.ofNullable(in.signCode())
            .flatMap(repo::signType)
            .filter(SignType::active)
            .orElse(null);
    if (type == null) {
      log.info("Bỏ {} vì mã biển {} không dùng được", field, in.signCode());
      return null;
    }
    if (in.classProbs() != null) {
      var codes = new HashSet<String>();
      double sum = 0;
      for (CodeProb prob : in.classProbs()) {
        if (!codes.add(prob.code()) || !Double.isFinite(prob.p())) {
          throw ApiException.validation(
              field + ".classProbs", "mã trùng hoặc xác suất không hợp lệ");
        }
        sum += prob.p();
      }
      if (sum > 1.0001)
        throw ApiException.validation(field + ".classProbs", "tổng xác suất vượt 1");
    }
    Double p =
        in.classProbs() == null
            ? null
            : in.classProbs().stream()
                .filter(c -> c.code().equals(in.signCode()))
                .map(CodeProb::p)
                .findFirst()
                .orElse(null);
    List<String> flags = new ArrayList<>();
    if (p == null || p < 0.6) {
      flags.add("\"LOW_CONF\"");
    }
    if (in.voteShare() != null && in.voteShare() < 0.6) {
      flags.add("\"SPLIT_VOTE\"");
    }
    if (in.detectorGroup() != null && !in.detectorGroup().equalsIgnoreCase(type.group())) {
      flags.add("\"MISMATCH\"");
    }
    String prefix = "crops/" + batchId + "/" + id;
    return new Observation(
        id,
        in.clientId(),
        userId,
        in.kind().name(),
        in.signCode(),
        in.detectorGroup(),
        p,
        in.voteShare(),
        in.location().lat(),
        in.location().lng(),
        in.headingDeg(),
        in.distanceM(),
        in.frames(),
        null,
        prefix + ".jpg",
        in.hasSubSign() ? prefix + "-sub.jpg" : null,
        flags.isEmpty() ? null : "[" + String.join(",", flags) + "]",
        in.observedAt());
  }

  private List<Upload> uploadsFor(List<Observation> observations) {
    return observations.stream()
        .filter(o -> o.cropKey() != null)
        .map(
            o ->
                new Upload(
                    o.clientId(),
                    storage.uploadUrl(o.cropKey()),
                    o.subSignKey() == null ? null : storage.uploadUrl(o.subSignKey())))
        .toList();
  }
}

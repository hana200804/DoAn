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
import com.signa.sign.store.CropStorage;
import com.signa.sign.store.SignRepository;
import com.signa.sign.store.SignRepository.ClusterFacts;
import com.signa.sign.store.SignRepository.Observation;
import com.signa.sign.store.SignRepository.SignType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nhận lô quan sát (FR-14) và gom cụm, tính điểm tin cậy (FR-17). */
@Service
public class ObservationService {

  private static final Logger log = LoggerFactory.getLogger(ObservationService.class);

  private static final double DEFAULT_PROBABILITY = 0.5;

  private final SignRepository repo;
  private final CropStorage storage;

  public ObservationService(SignRepository repo, CropStorage storage) {
    this.repo = repo;
    this.storage = storage;
  }

  @Transactional
  public CreateBatchResponse createBatch(
      String userId, String idempotencyKey, CreateBatchRequest req) {
    Optional<String> existing = repo.batchId(userId, idempotencyKey);
    if (existing.isPresent()) {
      return new CreateBatchResponse(existing.get(), uploadsFor(repo.observations(existing.get())));
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
    for (int i = 0; i < req.observations().size(); i++) {
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
  // shortcut: xử lý đồng bộ trong request, hai lô gần cùng một biển xử lý cùng lúc có thể tạo hai
  // cụm; chuyển sang một worker đọc hàng đợi (Redis Stream) khi tải vượt NFR-12
  @Transactional
  public void complete(String userId, String batchId) {
    var batch =
        repo.batch(batchId).orElseThrow(() -> ApiException.notFound("Không tìm thấy lô quan sát"));
    if (!batch.userId().equals(userId)) {
      throw ApiException.notFound("Không tìm thấy lô quan sát");
    }
    if (!repo.claimBatch(batchId)) {
      return; // đã hoặc đang được xử lý bởi request khác
    }
    for (Observation o : repo.observations(batchId)) {
      String crop = o.cropKey() != null && storage.exists(o.cropKey()) ? o.cropKey() : null;
      String sub = o.subSignKey() != null && storage.exists(o.subSignKey()) ? o.subSignKey() : null;
      if (Kind.POSITIVE.name().equals(o.kind())) {
        processPositive(o, crop, sub);
      } else {
        processNegative(o);
      }
    }
  }

  private void processPositive(Observation o, String crop, String sub) {
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
    // Mỗi người góp tối đa một lần mỗi 12 giờ cho một cụm, để một tài khoản không tự đẩy cụm lên
    double weight =
        repo.contributedRecently(clusterId, o.userId(), o.id())
            ? 0
            : (o.probability() == null ? DEFAULT_PROBABILITY : o.probability())
                * repo.reputation(o.userId());
    repo.addCodeWeight(clusterId, o.signCode(), weight);
    repo.assignObservation(o.id(), clusterId, weight, crop, sub);
    reassess(clusterId, o);
  }

  private void processNegative(Observation o) {
    if (o.clusterId() == null || repo.cluster(o.clusterId()).isEmpty()) {
      return;
    }
    double weight = repo.reputation(o.userId());
    repo.addNegativeWeight(o.clusterId(), weight);
    repo.assignObservation(o.id(), o.clusterId(), weight, null, null);
    reassess(o.clusterId(), o);
  }

  private void reassess(String clusterId, Observation trigger) {
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
        trigger.observedAt());
    if (a.status() != facts.status()) {
      repo.event(clusterId, null, "STATUS_CHANGED", facts.status().name(), a.status().name(), null);
    }
  }

  /** Null nếu quan sát dương có mã biển không dùng được. */
  private Observation toObservation(String batchId, String userId, ObservationIn in, String field) {
    String id = UUID.randomUUID().toString();
    if (in.kind() == Kind.NEGATIVE) {
      if (in.clusterId() == null) {
        throw ApiException.validation(field + ".clusterId", "bắt buộc với quan sát âm");
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
    Double p =
        in.classProbs() == null
            ? null
            : in.classProbs().stream()
                .filter(c -> c.code().equals(in.signCode()))
                .map(CodeProb::p)
                .findFirst()
                .orElse(null);
    List<String> flags = new ArrayList<>();
    if (p != null && p < 0.6) {
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

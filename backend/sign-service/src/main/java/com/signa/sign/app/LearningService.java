package com.signa.sign.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.signa.common.error.ApiException;
import com.signa.sign.api.LearningDtos.ArtifactView;
import com.signa.sign.api.LearningDtos.DatasetExport;
import com.signa.sign.api.LearningDtos.LabelRequest;
import com.signa.sign.api.LearningDtos.ModelMetrics;
import com.signa.sign.api.LearningDtos.ModelView;
import com.signa.sign.api.LearningDtos.OcrReviewRequest;
import com.signa.sign.api.LearningDtos.RegisterModelRequest;
import com.signa.sign.api.LearningDtos.ReviewedSample;
import com.signa.sign.api.LearningDtos.TaskView;
import com.signa.sign.api.SignDtos.Page;
import com.signa.sign.domain.AppVersion;
import com.signa.sign.store.CropStorage;
import com.signa.sign.store.LearningRepository;
import com.signa.sign.store.LearningRepository.Model;
import com.signa.sign.store.SignRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningService {
  private final LearningRepository repo;
  private final SignRepository signs;
  private final CropStorage storage;
  private final ObjectMapper mapper;

  @Value("${signa.learning.dataset-version:v1.0}")
  private String targetDataset = "v1.0";

  public LearningService(
      LearningRepository repo, SignRepository signs, CropStorage storage, ObjectMapper mapper) {
    this.repo = repo;
    this.signs = signs;
    this.storage = storage;
    this.mapper = mapper;
  }

  public Page<TaskView> tasks(String status, String reason, int page, int size) {
    if (!java.util.Set.of("OPEN", "LABELED", "SKIPPED").contains(status)) {
      throw ApiException.validation("status", "chỉ nhận OPEN, LABELED, SKIPPED");
    }
    if (reason != null
        && !java.util.Set.of(
                "LOW_CONF",
                "SPLIT_VOTE",
                "MISMATCH",
                "CONFLICT",
                "RANDOM",
                "OCR_UNPARSED",
                "AI_UNAVAILABLE")
            .contains(reason)) {
      throw ApiException.validation("reason", "lý do gán nhãn không hợp lệ");
    }
    if (page < 0 || page > 100000 || size < 1 || size > 100)
      throw ApiException.validation("page", "page từ 0, size từ 1 đến 100");
    var items =
        repo.tasks(status, reason, page * size, size).stream()
            .map(
                t ->
                    new TaskView(
                        t.id(),
                        t.observationId(),
                        t.reason(),
                        t.status(),
                        t.signCode(),
                        storage.viewUrl(t.cropKey()),
                        storage.viewUrl(t.subSignKey()),
                        t.labelCode(),
                        t.labelBox(),
                        t.analysis(),
                        t.ocrText(),
                        t.conditions(),
                        t.createdAt(),
                        t.datasetVersion(),
                        t.ocrStatus()))
            .toList();
    return new Page<>(items, page, size, repo.taskCount(status, reason));
  }

  @Transactional
  public void label(String id, String admin, LabelRequest request) {
    var task =
        repo.taskForUpdate(id).orElseThrow(() -> ApiException.notFound("Không có tác vụ này"));
    if (!task.status().equals("OPEN")) throw ApiException.conflict("Tác vụ đã xử lý");
    if (task.labelCode() != null)
      throw ApiException.conflict("Nhãn biển chính đã được duyệt; chỉ còn phần OCR");
    signs
        .signType(request.labelCode())
        .filter(SignRepository.SignType::active)
        .orElseThrow(() -> ApiException.validation("labelCode", "mã biển không dùng được"));
    var box = request.labelBox();
    if (box != null && (box.x() + box.width() > 1 || box.y() + box.height() > 1)) {
      throw ApiException.validation("labelBox", "box vượt quá ảnh cắt");
    }
    try {
      repo.resolveTask(
          id,
          admin,
          request.labelCode(),
          box == null ? null : mapper.writeValueAsString(box),
          false,
          dataset(request.datasetVersion()));
      repo.invalidateConditionsForLabel(task.observationId(), request.labelCode());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  @Transactional
  public void skip(String id, String admin) {
    var task =
        repo.taskForUpdate(id).orElseThrow(() -> ApiException.notFound("Không có tác vụ này"));
    if (!task.status().equals("OPEN")) throw ApiException.conflict("Tác vụ đã xử lý");
    repo.resolveTask(id, admin, null, null, true, null);
  }

  @Transactional
  public void reviewOcr(String id, String admin, OcrReviewRequest request) {
    var task =
        repo.taskForUpdate(id).orElseThrow(() -> ApiException.notFound("Không có tác vụ này"));
    if (!task.status().equals("OPEN")) throw ApiException.conflict("Tác vụ đã xử lý");
    if (task.subSignKey() == null)
      throw ApiException.validation("id", "tác vụ không có ảnh biển phụ");
    boolean needsSignLabel =
        java.util.Set.of("LOW_CONF", "SPLIT_VOTE", "MISMATCH", "CONFLICT", "RANDOM")
            .contains(task.reason());
    if (needsSignLabel && request.signLabel() == null && task.labelCode() == null) {
      throw ApiException.validation("signLabel", "cần chốt nhãn biển chính cho tác vụ ảnh khó");
    }
    var conditions = request.conditions();
    boolean hasWindows = conditions.timeWindows() != null && !conditions.timeWindows().isEmpty();
    var vehicles = conditions.vehicles();
    boolean hasVehicles =
        vehicles != null
            && ((vehicles.include() != null && !vehicles.include().isEmpty())
                || (vehicles.exclude() != null && !vehicles.exclude().isEmpty()));
    if (!hasWindows
        && !hasVehicles
        && conditions.distanceAheadM() == null
        && conditions.zoneLengthM() == null
        && conditions.weightLimitTon() == null) {
      throw ApiException.validation("conditions", "cần ít nhất một điều kiện áp dụng");
    }
    try {
      String json = mapper.writeValueAsString(conditions);
      if (request.signLabel() != null) {
        if (task.labelCode() == null) label(id, admin, request.signLabel());
        else if (!task.labelCode().equals(request.signLabel().labelCode()))
          throw ApiException.conflict("Nhãn biển chính đã được duyệt");
      }
      repo.resolveOcrTask(id, admin, request.rawText(), json, dataset(task.datasetVersion()));
      String label =
          request.signLabel() == null ? task.labelCode() : request.signLabel().labelCode();
      if ((label == null || label.equals(task.signCode()))
          && repo.applyReviewedConditions(task.observationId(), json)) {
        signs.event(
            task.clusterId(), admin, "CONDITIONS_CHANGED", null, "REVIEWED", "Duyệt OCR biển phụ");
      }
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private String dataset(String requested) {
    String value = requested == null ? targetDataset : requested;
    if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,9}"))
      throw ApiException.validation("datasetVersion", "phiên bản dài 1–10 ký tự chữ/số/._-");
    return value;
  }

  public DatasetExport exportDataset(String version, int page, int size) {
    dataset(version);
    if (page < 0 || page > 100000 || size < 1 || size > 100)
      throw ApiException.validation("page", "page từ 0, size từ 1 đến 100");
    var samples =
        repo.reviewedImages(version, page * size, size).stream()
            .map(
                image ->
                    new ReviewedSample(
                        image.observationId(),
                        image.labelCode(),
                        image.labelBox(),
                        storage.viewUrl(image.cropKey()),
                        storage.cropSha256(image.cropKey()),
                        image.sequence(),
                        image.labelMapVersion()))
            .toList();
    return new DatasetExport(version, samples, page, size, repo.reviewedImageCount(version));
  }

  @Transactional
  public void retryAnalysis(String id) {
    var task =
        repo.taskForUpdate(id).orElseThrow(() -> ApiException.notFound("Không có tác vụ này"));
    if (!task.status().equals("OPEN")) throw ApiException.conflict("Tác vụ đã xử lý");
    if (!repo.requeue(task.observationId()))
      throw ApiException.conflict("Phân tích đã xong hoặc đang xử lý");
  }

  @Transactional
  public void register(RegisterModelRequest r, String admin) {
    version(r.minAppVersion());
    storage.verifyArtifact(
        r.detector().objectKey(), r.detector().sha256(), r.detector().sizeBytes());
    storage.verifyArtifact(
        r.classifier().objectKey(), r.classifier().sha256(), r.classifier().sizeBytes());
    try {
      repo.insertModel(
          new Model(
              r.version(),
              r.labelMapVersion(),
              r.minAppVersion(),
              r.detector().objectKey(),
              r.detector().sha256(),
              r.detector().sizeBytes(),
              r.classifier().objectKey(),
              r.classifier().sha256(),
              r.classifier().sizeBytes(),
              false,
              Instant.now(),
              "DRAFT",
              json(r.metrics())),
          admin);
    } catch (DuplicateKeyException e) {
      throw ApiException.conflict("Phiên bản mô hình đã tồn tại");
    }
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void activate(String version, String admin) {
    var m = repo.model(version).orElseThrow(() -> ApiException.notFound("Không có phiên bản này"));
    storage.verifyArtifact(m.detectorKey(), m.detectorSha256(), m.detectorSize());
    storage.verifyArtifact(m.classifierKey(), m.classifierSha256(), m.classifierSize());
    repo.lockRelease(m.labelMapVersion());
    m = repo.model(version).orElseThrow();
    var metrics = eligible(m);
    // Retired versions are eligible for an explicit rollback without a regression comparison.
    if (!m.status().equals("RETIRED")) {
      repo.activeModel(m.labelMapVersion())
          .filter(current -> !current.version().equals(version))
          .ifPresent(
              current -> {
                var baseline = eligible(current);
                if (metrics.map50() < baseline.map50()
                    || metrics.falseWarningsPer10Km() > baseline.falseWarningsPer10Km())
                  throw ApiException.businessRule(
                      "Mô hình mới kém bản đang dùng trên mAP hoặc cảnh báo sai");
              });
    }
    repo.activate(version, admin, m.labelMapVersion());
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void updateModelCard(String version, ModelMetrics metrics) {
    var m = repo.model(version).orElseThrow(() -> ApiException.notFound("Không có phiên bản này"));
    repo.lockRelease(m.labelMapVersion());
    m = repo.model(version).orElseThrow();
    if (!m.status().equals("DRAFT")) throw ApiException.conflict("Chỉ sửa thẻ của mô hình DRAFT");
    repo.updateModelCard(version, json(metrics));
  }

  public Optional<ModelView> latest(String appVersion, int labelMap, String currentVersion) {
    var app = version(appVersion);
    if (labelMap < 1) throw ApiException.validation("labelMapVersion", "phải lớn hơn 0");
    return repo.models().stream()
        .filter(Model::active)
        .filter(m -> m.labelMapVersion() == labelMap)
        .filter(m -> app.compareTo(version(m.minAppVersion())) >= 0)
        .filter(m -> !m.version().equals(currentVersion))
        .filter(this::hasReleaseEvidence)
        .findFirst()
        .map(
            m ->
                new ModelView(
                    m.version(),
                    m.labelMapVersion(),
                    m.minAppVersion(),
                    new ArtifactView(
                        storage.artifactUrl(m.detectorKey()), m.detectorSha256(), m.detectorSize()),
                    new ArtifactView(
                        storage.artifactUrl(m.classifierKey()),
                        m.classifierSha256(),
                        m.classifierSize())));
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private boolean hasReleaseEvidence(Model m) {
    try {
      eligible(m);
      return true;
    } catch (ApiException e) {
      return false;
    }
  }

  private ModelMetrics eligible(Model m) {
    ModelMetrics card;
    try {
      card = mapper.readValue(m.metrics() == null ? "null" : m.metrics(), ModelMetrics.class);
    } catch (JsonProcessingException e) {
      throw ApiException.businessRule("Thẻ mô hình không hợp lệ");
    }
    if (card == null
        || java.util.stream.Stream.of(
                card.datasetVersion(),
                card.fieldDatasetVersion(),
                card.trainingConfig(),
                card.commit(),
                card.referenceDevice())
            .anyMatch(s -> s == null || s.isBlank())
        || java.util.stream.Stream.of(
                card.map50(),
                card.criticalRecall(),
                card.int8MapDrop(),
                card.fps(),
                card.falseWarningsPer10Km())
            .anyMatch(n -> n == null || !Double.isFinite(n))
        || card.map50() < .89
        || card.map50() > 1
        || card.criticalRecall() < .9
        || card.criticalRecall() > 1
        || card.int8MapDrop() < 0
        || card.int8MapDrop() >= .02
        || card.fps() < 15
        || card.falseWarningsPer10Km() < 0
        || card.falseWarningsPer10Km() > 1
        || m.detectorSize() + m.classifierSize() >= 15 * 1024 * 1024) {
      throw ApiException.businessRule(
          "Chưa đạt tiêu chí phát hành: thẻ mô hình, mAP≥0,89, recall≥0,90, giảm INT8<0,02, FPS≥15, cảnh báo sai≤1/10km, tổng file<15MiB");
    }
    return card;
  }

  private static AppVersion version(String value) {
    try {
      return AppVersion.parse(value);
    } catch (IllegalArgumentException e) {
      throw ApiException.validation("appVersion", "định dạng major[.minor[.patch]]");
    }
  }
}

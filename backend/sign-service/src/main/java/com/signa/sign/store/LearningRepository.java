package com.signa.sign.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Durable review tasks, analysis leases and model releases (FR-15, FR-19, FR-23, FR-25). */
@Repository
public class LearningRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public LearningRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void enqueue(String observationId) {
    jdbc.update(
        "INSERT IGNORE INTO observation_analysis (observation_id) VALUES (:id)",
        Map.of("id", observationId));
  }

  public void labelTask(String observationId, String reason) {
    jdbc.update(
        """
        INSERT IGNORE INTO label_tasks (id, observation_id, reason, ocr_status)
        SELECT :id, id, :reason, CASE WHEN sub_sign_key IS NOT NULL THEN 'PENDING' ELSE 'NOT_REQUIRED' END
        FROM observations WHERE id = :obs
        """,
        Map.of("id", UUID.randomUUID().toString(), "obs", observationId, "reason", reason));
  }

  public record Job(
      String id,
      String crop,
      String sub,
      String group,
      int labelMap,
      boolean hard,
      int attempts,
      int generation) {}

  public List<Job> dueJobs() {
    return jdbc.query(
        """
        SELECT a.observation_id, o.crop_key, o.sub_sign_key, o.detector_group, b.label_map_version,
          o.flags IS NOT NULL AND JSON_LENGTH(o.flags) > 0 AS hard, a.attempts, a.generation
        FROM observation_analysis a JOIN observations o ON o.id = a.observation_id
        JOIN observation_batches b ON b.id = o.batch_id
        WHERE a.status IN ('PENDING', 'RETRY', 'PROCESSING') AND a.available_at <= CURRENT_TIMESTAMP(3)
        ORDER BY a.available_at LIMIT 10
        """,
        Map.of(),
        (rs, i) ->
            new Job(
                rs.getString("observation_id"),
                rs.getString("crop_key"),
                rs.getString("sub_sign_key"),
                rs.getString("detector_group"),
                rs.getInt("label_map_version"),
                rs.getBoolean("hard"),
                rs.getInt("attempts"),
                rs.getInt("generation")));
  }

  public boolean claim(Job job) {
    return jdbc.update(
            """
        UPDATE observation_analysis SET status = 'PROCESSING', attempts = attempts + 1,
          available_at = CURRENT_TIMESTAMP(3) + INTERVAL 60 SECOND
        WHERE observation_id = :id AND attempts = :attempts AND generation = :generation
          AND status IN ('PENDING', 'RETRY', 'PROCESSING') AND available_at <= CURRENT_TIMESTAMP(3)
        """,
            Map.of("id", job.id(), "attempts", job.attempts(), "generation", job.generation()))
        == 1;
  }

  public void expire(Job job) {
    int updated =
        jdbc.update(
            """
        UPDATE observation_analysis SET status = 'FAILED' WHERE observation_id = :id
          AND status = 'PROCESSING' AND attempts = :attempts AND generation = :generation
          AND available_at <= CURRENT_TIMESTAMP(3)
        """,
            Map.of("id", job.id(), "attempts", job.attempts(), "generation", job.generation()));
    if (updated == 1) labelTask(job.id(), "AI_UNAVAILABLE");
  }

  public boolean requeue(String observationId) {
    return jdbc.update(
            """
        UPDATE observation_analysis SET status = 'PENDING', attempts = 0, generation = generation + 1,
          available_at = CURRENT_TIMESTAMP(3)
        WHERE observation_id = :id AND status IN ('FAILED', 'RETRY')
        """,
            Map.of("id", observationId))
        == 1;
  }

  public void finish(
      Job job,
      String result,
      String conditions,
      double confidence,
      boolean failed,
      boolean permanent) {
    int updated =
        jdbc.update(
            """
        UPDATE observation_analysis SET status = :status, result = :result,
          available_at = CURRENT_TIMESTAMP(3) + INTERVAL :seconds SECOND
        WHERE observation_id = :id AND status = 'PROCESSING' AND attempts = :attempts AND generation = :generation
        """,
            Map.of(
                "id",
                job.id(),
                "attempts",
                job.attempts() + 1,
                "generation",
                job.generation(),
                "result",
                result,
                "status",
                !failed ? "DONE" : permanent || job.attempts() >= 4 ? "FAILED" : "RETRY",
                "seconds",
                Math.min(3600, 30 * (1 << Math.min(job.attempts(), 7)))));
    if (updated == 1 && conditions != null) {
      jdbc.update(
          """
          UPDATE sign_clusters c JOIN observations o ON o.cluster_id = c.id
          SET c.conditions = :conditions, c.conditions_confidence = :conf,
            c.conditions_observed_at = o.observed_at, c.conditions_observation_id = o.id
          WHERE o.id = :id AND o.weight > 0 AND o.sign_code = c.sign_code AND NOT c.admin_locked
            AND NOT c.conditions_reviewed AND c.status <> 'REJECTED' AND :conf >= 0.8
            AND NOT EXISTS(SELECT 1 FROM label_tasks t WHERE t.observation_id = o.id
              AND t.label_code IS NOT NULL AND t.label_code <> o.sign_code)
            AND (c.conditions_observed_at IS NULL OR o.observed_at > c.conditions_observed_at
              OR (o.observed_at = c.conditions_observed_at AND
                (:conf > c.conditions_confidence OR (:conf = c.conditions_confidence
                  AND o.id >= COALESCE(c.conditions_observation_id, '')))))
          """,
          Map.of("id", job.id(), "conditions", conditions, "conf", confidence));
    }
  }

  public void retry(Job job, boolean permanent) {
    jdbc.update(
        """
        UPDATE observation_analysis SET status = :status,
          available_at = CURRENT_TIMESTAMP(3) + INTERVAL :seconds SECOND
        WHERE observation_id = :id AND status = 'PROCESSING' AND attempts = :attempts AND generation = :generation
        """,
        Map.of(
            "id",
            job.id(),
            "attempts",
            job.attempts() + 1,
            "generation",
            job.generation(),
            "status",
            permanent || job.attempts() >= 4 ? "FAILED" : "RETRY",
            "seconds",
            Math.min(3600, 30 * (1 << Math.min(job.attempts(), 7)))));
  }

  public record Task(
      String id,
      String observationId,
      String reason,
      String status,
      String signCode,
      String cropKey,
      String subSignKey,
      String clusterId,
      String labelCode,
      String labelBox,
      String analysis,
      String ocrText,
      String conditions,
      Instant createdAt,
      String datasetVersion,
      String ocrStatus) {
    public Task(
        String id,
        String observationId,
        String reason,
        String status,
        String signCode,
        String cropKey,
        String subSignKey,
        String clusterId,
        String labelCode,
        String labelBox,
        String analysis,
        String ocrText,
        String conditions,
        Instant createdAt) {
      this(
          id,
          observationId,
          reason,
          status,
          signCode,
          cropKey,
          subSignKey,
          clusterId,
          labelCode,
          labelBox,
          analysis,
          ocrText,
          conditions,
          createdAt,
          null,
          subSignKey == null ? "NOT_REQUIRED" : "PENDING");
    }
  }

  private static Task task(ResultSet rs, int i) throws SQLException {
    return new Task(
        rs.getString("id"),
        rs.getString("observation_id"),
        rs.getString("reason"),
        rs.getString("status"),
        rs.getString("sign_code"),
        rs.getString("crop_key"),
        rs.getString("sub_sign_key"),
        rs.getString("cluster_id"),
        rs.getString("label_code"),
        rs.getString("label_box"),
        rs.getString("result"),
        rs.getString("ocr_text"),
        rs.getString("conditions"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getString("dataset_version"),
        rs.getString("ocr_status"));
  }

  private static final String TASK_SELECT =
      """
      SELECT t.*, o.sign_code, o.crop_key, o.sub_sign_key, o.cluster_id, a.result FROM label_tasks t
      JOIN observations o ON o.id = t.observation_id
      LEFT JOIN observation_analysis a ON a.observation_id = o.id
      """;

  public List<Task> tasks(String status, String reason, int offset, int size) {
    return jdbc.query(
        TASK_SELECT
            + """
        WHERE t.status = :status AND (:reason IS NULL OR t.reason = :reason)
        ORDER BY t.created_at, t.id LIMIT :size OFFSET :offset
        """,
        taskParams(status, reason).addValue("offset", offset).addValue("size", size),
        LearningRepository::task);
  }

  public long taskCount(String status, String reason) {
    return jdbc.queryForObject(
        """
        SELECT COUNT(*) FROM label_tasks WHERE status = :status AND (:reason IS NULL OR reason = :reason)
        """,
        taskParams(status, reason),
        Long.class);
  }

  private static MapSqlParameterSource taskParams(String status, String reason) {
    return new MapSqlParameterSource().addValue("status", status).addValue("reason", reason);
  }

  public Optional<Task> taskForUpdate(String id) {
    return jdbc
        .query(
            TASK_SELECT + " WHERE t.id = :id FOR UPDATE OF t",
            Map.of("id", id),
            LearningRepository::task)
        .stream()
        .findFirst();
  }

  public void resolveTask(
      String id, String admin, String code, String box, boolean skip, String dataset) {
    jdbc.update(
        """
        UPDATE label_tasks SET status = CASE WHEN :skip THEN 'SKIPPED'
          WHEN ocr_status = 'PENDING' THEN 'OPEN' ELSE 'LABELED' END,
          ocr_status = CASE WHEN :skip THEN 'SKIPPED' ELSE ocr_status END,
          label_code = :code, label_box = :box, dataset_version = :dataset,
          resolved_by = :admin, resolved_at = CURRENT_TIMESTAMP(3) WHERE id = :id
        """,
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("admin", admin)
            .addValue("code", code)
            .addValue("box", box)
            .addValue("skip", skip)
            .addValue("dataset", dataset));
  }

  public void resolveOcrTask(
      String id, String admin, String text, String conditions, String dataset) {
    jdbc.update(
        """
        UPDATE label_tasks SET status = 'LABELED', ocr_status = 'REVIEWED', ocr_text = :text, conditions = :conditions,
          dataset_version = COALESCE(dataset_version, :dataset),
          resolved_by = :admin, resolved_at = CURRENT_TIMESTAMP(3) WHERE id = :id
        """,
        Map.of(
            "id", id, "admin", admin, "text", text, "conditions", conditions, "dataset", dataset));
  }

  public void invalidateConditionsForLabel(String observationId, String labelCode) {
    jdbc.update(
        """
        UPDATE sign_clusters c JOIN observations o ON o.cluster_id = c.id
        SET c.conditions = NULL, c.conditions_confidence = 0, c.conditions_observed_at = NULL,
          c.conditions_observation_id = NULL
        WHERE o.id = :id AND o.sign_code <> :label AND c.conditions_observation_id = o.id
          AND NOT c.conditions_reviewed
        """,
        Map.of("id", observationId, "label", labelCode));
  }

  public record ReviewedImage(
      String observationId,
      String labelCode,
      String labelBox,
      String cropKey,
      String sequence,
      int labelMapVersion) {}

  public List<ReviewedImage> reviewedImages(String version, int offset, int size) {
    return jdbc.query(
        """
        SELECT o.id, t.label_code, t.label_box, o.crop_key, SHA2(o.user_id, 256) AS sequence,
          b.label_map_version FROM label_tasks t JOIN observations o ON o.id = t.observation_id
        JOIN observation_batches b ON b.id = o.batch_id
        WHERE t.dataset_version = :version AND t.label_code IS NOT NULL AND t.status <> 'SKIPPED'
          AND o.crop_key IS NOT NULL ORDER BY o.id LIMIT :size OFFSET :offset
        """,
        Map.of("version", version, "offset", offset, "size", size),
        (rs, i) ->
            new ReviewedImage(
                rs.getString("id"),
                rs.getString("label_code"),
                rs.getString("label_box"),
                rs.getString("crop_key"),
                rs.getString("sequence"),
                rs.getInt("label_map_version")));
  }

  public long reviewedImageCount(String version) {
    return jdbc.queryForObject(
        """
        SELECT COUNT(*) FROM label_tasks t JOIN observations o ON o.id = t.observation_id
        WHERE t.dataset_version = :version AND t.label_code IS NOT NULL AND t.status <> 'SKIPPED'
          AND o.crop_key IS NOT NULL
        """,
        Map.of("version", version),
        Long.class);
  }

  public boolean applyReviewedConditions(String observationId, String conditions) {
    return jdbc.update(
            """
        UPDATE sign_clusters c JOIN observations o ON o.cluster_id = c.id
        SET c.conditions = :conditions, c.conditions_confidence = 1, c.conditions_reviewed = TRUE,
          c.conditions_observed_at = o.observed_at, c.conditions_observation_id = o.id
        WHERE o.id = :id AND o.weight > 0 AND o.sign_code = c.sign_code AND c.status <> 'REJECTED'
        """,
            Map.of("id", observationId, "conditions", conditions))
        == 1;
  }

  public record Model(
      String version,
      int labelMapVersion,
      String minAppVersion,
      String detectorKey,
      String detectorSha256,
      long detectorSize,
      String classifierKey,
      String classifierSha256,
      long classifierSize,
      boolean active,
      Instant createdAt,
      String status,
      @com.fasterxml.jackson.annotation.JsonRawValue String metrics) {
    public Model(
        String version,
        int labelMapVersion,
        String minAppVersion,
        String detectorKey,
        String detectorSha256,
        long detectorSize,
        String classifierKey,
        String classifierSha256,
        long classifierSize,
        boolean active,
        Instant createdAt) {
      this(
          version,
          labelMapVersion,
          minAppVersion,
          detectorKey,
          detectorSha256,
          detectorSize,
          classifierKey,
          classifierSha256,
          classifierSize,
          active,
          createdAt,
          active ? "ACTIVE" : "DRAFT",
          null);
    }
  }

  private static Model model(ResultSet rs, int i) throws SQLException {
    return new Model(
        rs.getString("version"),
        rs.getInt("label_map_version"),
        rs.getString("min_app_version"),
        rs.getString("detector_key"),
        rs.getString("detector_sha256"),
        rs.getLong("detector_size"),
        rs.getString("classifier_key"),
        rs.getString("classifier_sha256"),
        rs.getLong("classifier_size"),
        rs.getBoolean("active"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getString("status"),
        rs.getString("metrics"));
  }

  public List<Model> models() {
    return jdbc.query(
        """
        SELECT m.*, COALESCE(r.version = m.version, FALSE) AS active FROM model_versions m
        LEFT JOIN model_release r ON r.label_map_version = m.label_map_version ORDER BY m.created_at DESC, m.version
        """,
        Map.of(),
        LearningRepository::model);
  }

  public Optional<Model> model(String version) {
    return jdbc
        .query(
            "SELECT m.*, m.status = 'ACTIVE' AS active FROM model_versions m WHERE m.version = :version",
            Map.of("version", version),
            LearningRepository::model)
        .stream()
        .findFirst();
  }

  public void insertModel(Model m, String admin) {
    jdbc.update(
        """
        INSERT INTO model_versions (version, label_map_version, min_app_version, detector_key,
          detector_sha256, detector_size, classifier_key, classifier_sha256, classifier_size, created_by, metrics)
        VALUES (:version, :lm, :app, :dk, :dh, :ds, :ck, :ch, :cs, :admin, :metrics)
        """,
        new MapSqlParameterSource()
            .addValue("version", m.version())
            .addValue("lm", m.labelMapVersion())
            .addValue("app", m.minAppVersion())
            .addValue("dk", m.detectorKey())
            .addValue("dh", m.detectorSha256())
            .addValue("ds", m.detectorSize())
            .addValue("ck", m.classifierKey())
            .addValue("ch", m.classifierSha256())
            .addValue("cs", m.classifierSize())
            .addValue("admin", admin)
            .addValue("metrics", m.metrics()));
    jdbc.update(
        "INSERT IGNORE INTO model_release(label_map_version) VALUES (:lm)",
        Map.of("lm", m.labelMapVersion()));
  }

  public void lockRelease(int labelMap) {
    jdbc.queryForList(
        "SELECT label_map_version FROM model_release WHERE label_map_version = :lm FOR UPDATE",
        Map.of("lm", labelMap),
        Integer.class);
  }

  public Optional<Model> activeModel(int labelMap) {
    return jdbc
        .query(
            "SELECT m.*, TRUE AS active FROM model_versions m JOIN model_release r ON r.version = m.version WHERE r.label_map_version = :lm",
            Map.of("lm", labelMap),
            LearningRepository::model)
        .stream()
        .findFirst();
  }

  public void updateModelCard(String version, String metrics) {
    jdbc.update(
        "UPDATE model_versions SET metrics = :metrics WHERE version = :version AND status = 'DRAFT'",
        Map.of("version", version, "metrics", metrics));
  }

  public void activate(String version, String admin, int labelMap) {
    jdbc.update(
        "UPDATE model_versions SET status = 'RETIRED' WHERE label_map_version = :lm AND status = 'ACTIVE' AND version <> :version",
        Map.of("lm", labelMap, "version", version));
    jdbc.update(
        "UPDATE model_versions SET status = 'ACTIVE', released_at = CURRENT_TIMESTAMP(3) WHERE version = :version",
        Map.of("version", version));
    jdbc.update(
        """
        UPDATE model_release SET version = :version, activated_by = :admin,
          activated_at = CURRENT_TIMESTAMP(3) WHERE label_map_version = :lm
        """,
        Map.of("version", version, "admin", admin, "lm", labelMap));
  }

  public Map<String, Object> overview() {
    var result =
        jdbc.queryForMap(
            """
        SELECT (SELECT COUNT(*) FROM sign_clusters) AS clusters,
          (SELECT COUNT(*) FROM sign_clusters WHERE status = 'VERIFIED') AS verified,
          (SELECT COUNT(*) FROM sign_clusters WHERE status = 'CONFLICT') AS conflicts,
          (SELECT COUNT(*) FROM observations o JOIN observation_batches b ON b.id = o.batch_id
            WHERE b.status = 'PROCESSED') AS observations,
          (SELECT COUNT(*) FROM sign_reports WHERE status = 'PENDING') AS pendingReports,
          (SELECT COUNT(*) FROM label_tasks WHERE status = 'OPEN') AS openLabelTasks,
          (SELECT COUNT(*) FROM observation_analysis WHERE status = 'FAILED') AS failedAnalyses,
          COALESCE((SELECT AVG(NOT admin_locked) FROM sign_clusters WHERE status = 'VERIFIED'), 0) AS autoVerifiedRatio
        """,
            Map.of());
    result.put(
        "observationsByDay",
        jdbc.queryForList(
            """
        SELECT DATE(o.observed_at) AS day, COUNT(*) AS observations FROM observations o
        JOIN observation_batches b ON b.id = o.batch_id WHERE b.status = 'PROCESSED'
          AND o.observed_at >= CURRENT_DATE - INTERVAL 30 DAY
        GROUP BY DATE(o.observed_at) ORDER BY day
        """,
            Map.of()));
    result.put(
        "topContributors",
        jdbc.queryForList(
            """
        SELECT o.user_id AS userId, COUNT(*) AS observations FROM observations o
        JOIN observation_batches b ON b.id = o.batch_id WHERE b.status = 'PROCESSED'
        GROUP BY o.user_id ORDER BY observations DESC, o.user_id LIMIT 20
        """,
            Map.of()));
    result.put(
        "coverage",
        jdbc.queryForList(
            """
        SELECT ROUND(c.lat,2) AS lat, ROUND(c.lng,2) AS lng, COUNT(*) AS verified
        FROM sign_clusters c JOIN sign_types t ON t.code = c.sign_code
        WHERE c.status = 'VERIFIED' AND t.active GROUP BY ROUND(c.lat,2), ROUND(c.lng,2)
        ORDER BY lat, lng
        """,
            Map.of()));
    result.put(
        "activeModels",
        jdbc.queryForList(
            """
        SELECT m.version, m.label_map_version AS labelMapVersion FROM model_versions m
        JOIN model_release r ON r.version = m.version ORDER BY m.label_map_version
        """,
            Map.of()));
    return result;
  }

  public Map<String, Object> contributions(String userId) {
    return jdbc.queryForMap(
        """
        SELECT COUNT(*) AS observations, COUNT(DISTINCT o.cluster_id) AS clusters,
          COALESCE(SUM(c.status = 'VERIFIED'), 0) AS verifiedObservations,
          COALESCE(SUM(c.status = 'REJECTED'), 0) AS rejectedObservations,
          COALESCE((SELECT reputation FROM contributors WHERE user_id = :user), 1.0) AS reputation,
          COALESCE((SELECT agreed FROM contributors WHERE user_id = :user), 0) AS agreed,
          COALESCE((SELECT disagreed FROM contributors WHERE user_id = :user), 0) AS disagreed
        FROM observations o JOIN observation_batches b ON b.id = o.batch_id
        LEFT JOIN sign_clusters c ON c.id = o.cluster_id
        WHERE o.user_id = :user AND b.status = 'PROCESSED'
        """,
        Map.of("user", userId));
  }
}

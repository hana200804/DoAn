package com.signa.sign.store;

import com.signa.sign.api.SignDtos.Kind;
import com.signa.sign.domain.ClusterRules;
import com.signa.sign.domain.ClusterRules.Candidate;
import com.signa.sign.domain.ClusterStatus;
import com.signa.sign.domain.Geo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Toàn bộ truy vấn của sign-service. */
@Repository
public class SignRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public SignRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // ---------- Danh mục biển ----------

  public record SignType(
      String code,
      String group,
      String name,
      String description,
      Integer speedKmh,
      boolean active) {}

  public List<SignType> signTypes() {
    return jdbc.query("SELECT * FROM sign_types ORDER BY code", Map.of(), SignRepository::signType);
  }

  public boolean supportsLabelMap(int version) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM sign_types WHERE label_map_version = :version AND active)",
            Map.of("version", version),
            Boolean.class));
  }

  public Optional<SignType> signType(String code) {
    return jdbc
        .query(
            "SELECT * FROM sign_types WHERE code = :code",
            Map.of("code", code),
            SignRepository::signType)
        .stream()
        .findFirst();
  }

  public void upsertSignType(SignType t) {
    jdbc.update(
        """
        INSERT INTO sign_types (code, group_code, name, description, speed_kmh, active)
        VALUES (:code, :group, :name, :description, :speedKmh, :active)
        ON DUPLICATE KEY UPDATE group_code = VALUES(group_code), name = VALUES(name),
          description = VALUES(description), speed_kmh = VALUES(speed_kmh), active = VALUES(active)
        """,
        new MapSqlParameterSource()
            .addValue("code", t.code())
            .addValue("group", t.group())
            .addValue("name", t.name())
            .addValue("description", t.description())
            .addValue("speedKmh", t.speedKmh())
            .addValue("active", t.active()));
  }

  private static SignType signType(ResultSet rs, int i) throws SQLException {
    return new SignType(
        rs.getString("code"),
        rs.getString("group_code"),
        rs.getString("name"),
        rs.getString("description"),
        (Integer) rs.getObject("speed_kmh", Integer.class),
        rs.getBoolean("active"));
  }

  // ---------- Cụm biển ----------

  public record Cluster(
      String id,
      String signCode,
      String group,
      String name,
      double lat,
      double lng,
      int headingDeg,
      double confidence,
      ClusterStatus status,
      boolean adminLocked,
      String conditions,
      int observationCount,
      int distinctUsers,
      Instant lastSeenAt,
      boolean hasSubSign) {}

  private static final String CLUSTER_SELECT =
      """
      SELECT c.*, t.group_code, t.name,
        EXISTS(SELECT 1 FROM observations o WHERE o.cluster_id = c.id AND o.has_sub_sign AND o.weight > 0) AS has_sub_sign
      FROM sign_clusters c
      JOIN sign_types t ON t.code = c.sign_code
      """;

  public List<Cluster> clustersInBox(
      double minLat,
      double minLng,
      double maxLat,
      double maxLng,
      List<String> statuses,
      int limit) {
    return jdbc.query(
        CLUSTER_SELECT
            + """
            WHERE c.lat BETWEEN :minLat AND :maxLat AND c.lng BETWEEN :minLng AND :maxLng
              AND c.status IN (:statuses) AND t.active
            ORDER BY c.confidence DESC LIMIT :limit
            """,
        new MapSqlParameterSource()
            .addValue("minLat", minLat)
            .addValue("maxLat", maxLat)
            .addValue("minLng", minLng)
            .addValue("maxLng", maxLng)
            .addValue("statuses", statuses)
            .addValue("limit", limit),
        SignRepository::cluster);
  }

  public Optional<Cluster> cluster(String id) {
    return jdbc
        .query(CLUSTER_SELECT + " WHERE c.id = :id", Map.of("id", id), SignRepository::cluster)
        .stream()
        .findFirst();
  }

  /** Stable, bounded scan; corridor filtering must run before result pagination. */
  public List<Cluster> corridorCandidates(double[] bbox, String afterId, int limit) {
    return jdbc.query(
        CLUSTER_SELECT + mapWhere(null) + " AND c.id > :after ORDER BY c.id LIMIT :limit",
        mapParams(bbox, List.of("VERIFIED"), null, 0)
            .addValue("after", afterId)
            .addValue("limit", limit),
        SignRepository::cluster);
  }

  public List<Cluster> mapPage(
      double[] bbox,
      List<String> statuses,
      java.util.Set<String> groups,
      double minConfidence,
      int page,
      int size) {
    return jdbc.query(
        CLUSTER_SELECT
            + mapWhere(groups)
            + " ORDER BY c.confidence DESC, c.id LIMIT :size OFFSET :offset",
        mapParams(bbox, statuses, groups, minConfidence)
            .addValue("size", size)
            .addValue("offset", page * size),
        SignRepository::cluster);
  }

  public long mapCount(
      double[] bbox, List<String> statuses, java.util.Set<String> groups, double minConfidence) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM sign_clusters c JOIN sign_types t ON t.code = c.sign_code"
            + mapWhere(groups),
        mapParams(bbox, statuses, groups, minConfidence),
        Long.class);
  }

  private static String mapWhere(java.util.Set<String> groups) {
    return """
         WHERE c.lat BETWEEN :minLat AND :maxLat AND c.lng BETWEEN :minLng AND :maxLng
           AND c.status IN (:statuses) AND c.confidence >= :confidence AND t.active
        """
        + (groups == null ? "" : " AND t.group_code IN (:groups)");
  }

  private static MapSqlParameterSource mapParams(
      double[] b, List<String> statuses, java.util.Set<String> groups, double confidence) {
    return new MapSqlParameterSource()
        .addValue("minLat", b[1])
        .addValue("maxLat", b[3])
        .addValue("minLng", b[0])
        .addValue("maxLng", b[2])
        .addValue("statuses", statuses)
        .addValue("confidence", confidence)
        .addValue("groups", groups);
  }

  public Optional<Cluster> clusterForUpdate(String id) {
    return jdbc
        .query(
            CLUSTER_SELECT + " WHERE c.id = :id FOR UPDATE OF c",
            Map.of("id", id),
            SignRepository::cluster)
        .stream()
        .findFirst();
  }

  /** Khoá dòng cụm tới hết transaction. */
  public void lockCluster(String id) {
    jdbc.queryForList(
        "SELECT id FROM sign_clusters WHERE id = :id FOR UPDATE", Map.of("id", id), String.class);
  }

  public void lockIngestion() {
    jdbc.queryForList(
        "SELECT id FROM cluster_ingestion_lock WHERE id = 1 FOR UPDATE", Map.of(), Integer.class);
  }

  public List<Cluster> clustersByStatus(ClusterStatus status, int limit) {
    return jdbc.query(
        CLUSTER_SELECT + " WHERE c.status = :status ORDER BY c.updated_at LIMIT :limit",
        Map.of("status", status.name(), "limit", limit),
        SignRepository::cluster);
  }

  /**
   * Cụm trong ô vuông quanh một điểm (kể cả cụm đã loại), dùng làm ứng viên khi gom cụm. Thời điểm
   * loại lấy từ nhật ký; cụm bị đặt REJECTED mà không có nhật ký thì dùng updated_at.
   */
  public List<Candidate> candidatesNear(double lat, double lng, double radiusM) {
    double dLat = Geo.metersToLatDeg(radiusM);
    double dLng = Geo.metersToLngDeg(radiusM, lat);
    return jdbc.query(
        """
        SELECT c.id, t.group_code, c.lat, c.lng, c.heading_deg,
          c.status = 'REJECTED' AND COALESCE(
            (SELECT MAX(e.created_at) FROM cluster_events e
               WHERE e.cluster_id = c.id AND e.action = 'REJECTED'),
            c.updated_at) < NOW(3) - INTERVAL :days DAY AS rejected_long_ago
        FROM sign_clusters c
        JOIN sign_types t ON t.code = c.sign_code
        WHERE c.lat BETWEEN :minLat AND :maxLat AND c.lng BETWEEN :minLng AND :maxLng
        """,
        new MapSqlParameterSource()
            .addValue("days", ClusterRules.REJECT_SILENCE_DAYS)
            .addValue("minLat", lat - dLat)
            .addValue("maxLat", lat + dLat)
            .addValue("minLng", lng - dLng)
            .addValue("maxLng", lng + dLng),
        (rs, i) ->
            new Candidate(
                rs.getString("id"),
                rs.getString("group_code"),
                rs.getDouble("lat"),
                rs.getDouble("lng"),
                rs.getInt("heading_deg"),
                rs.getBoolean("rejected_long_ago")));
  }

  public void insertCluster(
      String id, String signCode, double lat, double lng, int heading, Instant seenAt) {
    jdbc.update(
        """
        INSERT INTO sign_clusters (id, sign_code, lat, lng, heading_deg, last_seen_at)
        VALUES (:id, :code, :lat, :lng, :heading, :seen)
        """,
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("code", signCode)
            .addValue("lat", lat)
            .addValue("lng", lng)
            .addValue("heading", heading)
            .addValue("seen", Timestamp.from(seenAt)));
  }

  public void addCodeWeight(String clusterId, String signCode, double weight) {
    jdbc.update(
        """
        INSERT INTO cluster_code_weights (cluster_id, sign_code, weight) VALUES (:id, :code, :w)
        ON DUPLICATE KEY UPDATE weight = weight + VALUES(weight)
        """,
        Map.of("id", clusterId, "code", signCode, "w", weight));
  }

  public void addNegativeWeight(String clusterId, double weight) {
    jdbc.update(
        "UPDATE sign_clusters SET negative_weight = negative_weight + :w WHERE id = :id",
        Map.of("id", clusterId, "w", weight));
  }

  public Map<String, Double> codeWeights(String clusterId) {
    Map<String, Double> weights = new HashMap<>();
    jdbc.query(
        "SELECT sign_code, weight FROM cluster_code_weights WHERE cluster_id = :id",
        Map.of("id", clusterId),
        rs -> {
          weights.put(rs.getString("sign_code"), rs.getDouble("weight"));
        });
    return weights;
  }

  public record ClusterFacts(
      double negativeWeight,
      int distinctUsers,
      int negativePasses,
      boolean adminLocked,
      String signCode,
      ClusterStatus status) {}

  public ClusterFacts facts(String clusterId) {
    return jdbc.queryForObject(
        """
        SELECT c.negative_weight, c.admin_locked, c.sign_code, c.status,
          (SELECT COUNT(DISTINCT o.user_id) FROM observations o
             WHERE o.cluster_id = c.id AND o.kind = 'POSITIVE') AS distinct_users,
          (SELECT COUNT(*) FROM observations n
             WHERE n.cluster_id = c.id AND n.kind = 'NEGATIVE' AND n.weight > 0
               AND n.observed_at > COALESCE((SELECT MAX(p.observed_at) FROM observations p
                 WHERE p.cluster_id = c.id AND p.kind = 'POSITIVE'), '1970-01-01')) AS neg_passes
        FROM sign_clusters c WHERE c.id = :id
        """,
        Map.of("id", clusterId),
        (rs, i) ->
            new ClusterFacts(
                rs.getDouble("negative_weight"),
                rs.getInt("distinct_users"),
                rs.getInt("neg_passes"),
                rs.getBoolean("admin_locked"),
                rs.getString("sign_code"),
                ClusterStatus.valueOf(rs.getString("status"))));
  }

  /** Vị trí các quan sát dương của cụm kèm trọng số, để tính vị trí cụm. */
  public List<double[]> positivePositions(String clusterId) {
    return jdbc.query(
        """
        SELECT lat, lng, weight FROM observations
        WHERE cluster_id = :id AND kind = 'POSITIVE' AND weight > 0
        """,
        Map.of("id", clusterId),
        (rs, i) -> new double[] {rs.getDouble("lat"), rs.getDouble("lng"), rs.getDouble("weight")});
  }

  public void updateAssessment(
      String clusterId,
      String signCode,
      double alpha,
      double beta,
      double confidence,
      ClusterStatus status,
      double lat,
      double lng,
      int distinctUsers,
      Instant seenAt,
      boolean positive) {
    jdbc.update(
        """
        UPDATE sign_clusters SET
          conditions = CASE WHEN sign_code <> :code THEN NULL ELSE conditions END,
          conditions_confidence = CASE WHEN sign_code <> :code THEN 0 ELSE conditions_confidence END,
          conditions_observed_at = CASE WHEN sign_code <> :code THEN NULL ELSE conditions_observed_at END,
          conditions_observation_id = CASE WHEN sign_code <> :code THEN NULL ELSE conditions_observation_id END,
          conditions_reviewed = CASE WHEN sign_code <> :code THEN FALSE ELSE conditions_reviewed END,
          sign_code = :code, alpha = :alpha, beta = :beta,
          confidence = :conf, status = :status, lat = :lat, lng = :lng,
          distinct_users = :users, observation_count = observation_count + 1,
          last_seen_at = CASE WHEN :positive THEN GREATEST(last_seen_at, :seen) ELSE last_seen_at END
        WHERE id = :id
        """,
        new MapSqlParameterSource()
            .addValue("id", clusterId)
            .addValue("code", signCode)
            .addValue("alpha", alpha)
            .addValue("beta", beta)
            .addValue("conf", confidence)
            .addValue("status", status.name())
            .addValue("lat", lat)
            .addValue("lng", lng)
            .addValue("users", distinctUsers)
            .addValue("positive", positive)
            .addValue("seen", Timestamp.from(seenAt)));
  }

  public void setStatus(String clusterId, ClusterStatus status, String signCode, boolean lock) {
    jdbc.update(
        """
        UPDATE sign_clusters SET
          conditions = CASE WHEN sign_code <> :code THEN NULL ELSE conditions END,
          conditions_confidence = CASE WHEN sign_code <> :code THEN 0 ELSE conditions_confidence END,
          conditions_observed_at = CASE WHEN sign_code <> :code THEN NULL ELSE conditions_observed_at END,
          conditions_observation_id = CASE WHEN sign_code <> :code THEN NULL ELSE conditions_observation_id END,
          conditions_reviewed = CASE WHEN sign_code <> :code THEN FALSE ELSE conditions_reviewed END,
          status = :status, sign_code = :code,
          admin_locked = admin_locked OR :lock WHERE id = :id
        """,
        new MapSqlParameterSource()
            .addValue("id", clusterId)
            .addValue("status", status.name())
            .addValue("code", signCode)
            .addValue("lock", lock));
  }

  public void event(
      String clusterId,
      String actorId,
      String action,
      String oldValue,
      String newValue,
      String reason) {
    jdbc.update(
        """
        INSERT INTO cluster_events (cluster_id, actor_type, actor_id, action, old_value, new_value, reason)
        VALUES (:id, :type, :actor, :action, :old, :new, :reason)
        """,
        new MapSqlParameterSource()
            .addValue("id", clusterId)
            .addValue("type", actorId == null ? "SYSTEM" : "ADMIN")
            .addValue("actor", actorId)
            .addValue("action", action)
            .addValue("old", oldValue)
            .addValue("new", newValue)
            .addValue("reason", reason));
  }

  public Optional<String> representativeCrop(String clusterId) {
    return jdbc
        .queryForList(
            """
            SELECT crop_key FROM observations
            WHERE cluster_id = :id AND kind = 'POSITIVE' AND crop_key IS NOT NULL
            ORDER BY probability DESC LIMIT 1
            """,
            Map.of("id", clusterId),
            String.class)
        .stream()
        .findFirst();
  }

  private static Cluster cluster(ResultSet rs, int i) throws SQLException {
    return new Cluster(
        rs.getString("id"),
        rs.getString("sign_code"),
        rs.getString("group_code"),
        rs.getString("name"),
        rs.getDouble("lat"),
        rs.getDouble("lng"),
        rs.getInt("heading_deg"),
        rs.getDouble("confidence"),
        ClusterStatus.valueOf(rs.getString("status")),
        rs.getBoolean("admin_locked"),
        rs.getString("conditions"),
        rs.getInt("observation_count"),
        rs.getInt("distinct_users"),
        rs.getTimestamp("last_seen_at").toInstant(),
        rs.getBoolean("has_sub_sign"));
  }

  // ---------- Lô quan sát ----------

  public Optional<String> batchId(String userId, String idempotencyKey) {
    return jdbc
        .queryForList(
            "SELECT id FROM observation_batches WHERE user_id = :u AND idempotency_key = :k",
            Map.of("u", userId, "k", idempotencyKey),
            String.class)
        .stream()
        .findFirst();
  }

  public void insertBatch(
      String id, String userId, String key, String modelVersion, int labelMap, String device) {
    jdbc.update(
        """
        INSERT INTO observation_batches
          (id, user_id, idempotency_key, model_version, label_map_version, device_model)
        VALUES (:id, :u, :k, :mv, :lm, :dev)
        """,
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("u", userId)
            .addValue("k", key)
            .addValue("mv", modelVersion)
            .addValue("lm", labelMap)
            .addValue("dev", device));
  }

  public record BatchInfo(String id, String userId, String status) {}

  public Optional<BatchInfo> batch(String id) {
    return jdbc
        .query(
            "SELECT id, user_id, status FROM observation_batches WHERE id = :id",
            Map.of("id", id),
            (rs, i) ->
                new BatchInfo(rs.getString("id"), rs.getString("user_id"), rs.getString("status")))
        .stream()
        .findFirst();
  }

  /** Chuyển lô sang PROCESSED; trả false nếu request khác đã làm trước. */
  public boolean claimBatch(String id) {
    return jdbc.update(
            """
            UPDATE observation_batches SET status = 'PROCESSED', processed_at = CURRENT_TIMESTAMP(3)
            WHERE id = :id AND status = 'PENDING_UPLOAD'
            """,
            Map.of("id", id))
        == 1;
  }

  public record Observation(
      String id,
      String clientId,
      String userId,
      String kind,
      String signCode,
      String detectorGroup,
      Double probability,
      Double voteShare,
      double lat,
      double lng,
      int headingDeg,
      Double distanceM,
      Integer frames,
      String clusterId,
      String cropKey,
      String subSignKey,
      String flags,
      Instant observedAt) {}

  public void insertObservation(String batchId, Observation o) {
    jdbc.update(
        """
        INSERT INTO observations (id, batch_id, client_id, user_id, kind, sign_code, detector_group,
          probability, vote_share, lat, lng, heading_deg, distance_m, frames, cluster_id,
          crop_key, sub_sign_key, has_sub_sign, flags, observed_at)
        VALUES (:id, :batch, :client, :user, :kind, :code, :group, :p, :vote, :lat, :lng, :heading,
          :dist, :frames, :cluster, :crop, :sub, :hasSub, :flags, :at)
        """,
        new MapSqlParameterSource()
            .addValue("id", o.id())
            .addValue("batch", batchId)
            .addValue("client", o.clientId())
            .addValue("user", o.userId())
            .addValue("kind", o.kind())
            .addValue("code", o.signCode())
            .addValue("group", o.detectorGroup())
            .addValue("p", o.probability())
            .addValue("vote", o.voteShare())
            .addValue("lat", o.lat())
            .addValue("lng", o.lng())
            .addValue("heading", o.headingDeg())
            .addValue("dist", o.distanceM())
            .addValue("frames", o.frames())
            .addValue("cluster", o.clusterId())
            .addValue("crop", o.cropKey())
            .addValue("sub", o.subSignKey())
            .addValue("hasSub", o.subSignKey() != null)
            .addValue("flags", o.flags())
            .addValue("at", Timestamp.from(o.observedAt())));
  }

  public List<Observation> observations(String batchId) {
    return jdbc.query(
        "SELECT * FROM observations WHERE batch_id = :b ORDER BY observed_at",
        Map.of("b", batchId),
        (rs, i) ->
            new Observation(
                rs.getString("id"),
                rs.getString("client_id"),
                rs.getString("user_id"),
                rs.getString("kind"),
                rs.getString("sign_code"),
                rs.getString("detector_group"),
                (Double) rs.getObject("probability", Double.class),
                (Double) rs.getObject("vote_share", Double.class),
                rs.getDouble("lat"),
                rs.getDouble("lng"),
                rs.getInt("heading_deg"),
                (Double) rs.getObject("distance_m", Double.class),
                (Integer) rs.getObject("frames", Integer.class),
                rs.getString("cluster_id"),
                rs.getString("crop_key"),
                rs.getString("sub_sign_key"),
                rs.getString("flags"),
                rs.getTimestamp("observed_at").toInstant()));
  }

  public void assignObservation(
      String obsId, String clusterId, double weight, String cropKey, String subSignKey) {
    jdbc.update(
        """
        UPDATE observations SET cluster_id = :c, weight = :w, crop_key = :crop, sub_sign_key = :sub
        WHERE id = :id
        """,
        new MapSqlParameterSource()
            .addValue("id", obsId)
            .addValue("c", clusterId)
            .addValue("w", weight)
            .addValue("crop", cropKey)
            .addValue("sub", subSignKey));
  }

  public void addObservationFlag(String observationId, String flag) {
    jdbc.update(
        """
        UPDATE observations SET flags = JSON_ARRAY_APPEND(COALESCE(flags, JSON_ARRAY()), '$', :flag)
        WHERE id = :id
        """,
        Map.of("id", observationId, "flag", flag));
  }

  /**
   * Người dùng đã góp trọng số (quan sát cùng loại, weight &gt; 0) cho cụm này trong {@link
   * ClusterRules#CONTRIBUTION_HOURS} giờ trước quan sát {@code obsId} chưa. Tính theo giờ server
   * nhận, app không giả được; lần bị bỏ (weight = 0) không kéo dài cửa sổ.
   */
  public boolean contributedRecently(String clusterId, String userId, String obsId, Kind kind) {
    Integer n =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM observations o JOIN observations me ON me.id = :obs
            WHERE o.cluster_id = :c AND o.user_id = :u AND o.kind = :kind AND o.weight > 0
              AND o.id <> me.id
              AND o.created_at > me.created_at - INTERVAL :hours HOUR
            """,
            Map.of(
                "c",
                clusterId,
                "u",
                userId,
                "obs",
                obsId,
                "kind",
                kind.name(),
                "hours",
                ClusterRules.CONTRIBUTION_HOURS),
            Integer.class);
    return n != null && n > 0;
  }

  // ---------- Người đóng góp ----------

  public double reputation(String userId) {
    return jdbc
        .queryForList(
            "SELECT reputation FROM contributors WHERE user_id = :u",
            Map.of("u", userId),
            Double.class)
        .stream()
        .findFirst()
        .orElse(1.0);
  }

  public void setReputation(String userId, double reputation, boolean agreed) {
    jdbc.update(
        """
        INSERT INTO contributors (user_id, reputation, agreed, disagreed)
        VALUES (:u, :r, :a, :d)
        ON DUPLICATE KEY UPDATE reputation = VALUES(reputation),
          agreed = agreed + VALUES(agreed), disagreed = disagreed + VALUES(disagreed)
        """,
        new MapSqlParameterSource()
            .addValue("u", userId)
            .addValue("r", reputation)
            .addValue("a", agreed ? 1 : 0)
            .addValue("d", agreed ? 0 : 1));
  }

  /** Mỗi người gửi quan sát dương cho cụm: user id → mã biển họ gửi nhiều nhất. */
  public Map<String, String> contributorCodes(String clusterId) {
    Map<String, String> result = new HashMap<>();
    jdbc.query(
        """
        SELECT user_id, sign_code FROM observations
        WHERE cluster_id = :c AND kind = 'POSITIVE'
        GROUP BY user_id, sign_code ORDER BY COUNT(*) ASC
        """,
        Map.of("c", clusterId),
        rs -> {
          result.put(rs.getString("user_id"), rs.getString("sign_code"));
        });
    return result;
  }

  // ---------- Báo cáo sai ----------

  public record Report(
      String id,
      String clusterId,
      String userId,
      String reason,
      String note,
      String status,
      Instant createdAt) {}

  public boolean hasPendingReport(String clusterId, String userId) {
    Integer n =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM sign_reports
            WHERE cluster_id = :c AND user_id = :u AND status = 'PENDING'
            """,
            Map.of("c", clusterId, "u", userId),
            Integer.class);
    return n != null && n > 0;
  }

  public void insertReport(String id, String clusterId, String userId, String reason, String note) {
    jdbc.update(
        """
        INSERT INTO sign_reports (id, cluster_id, user_id, reason, note)
        VALUES (:id, :c, :u, :reason, :note)
        """,
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("c", clusterId)
            .addValue("u", userId)
            .addValue("reason", reason)
            .addValue("note", note));
  }

  public List<Report> reports(String status, int limit) {
    return jdbc.query(
        "SELECT * FROM sign_reports WHERE status = :s ORDER BY created_at LIMIT :limit",
        Map.of("s", status, "limit", limit),
        SignRepository::report);
  }

  public Optional<Report> reportForUpdate(String id) {
    return jdbc
        .query(
            "SELECT * FROM sign_reports WHERE id = :id FOR UPDATE",
            Map.of("id", id),
            SignRepository::report)
        .stream()
        .findFirst();
  }

  private static Report report(ResultSet rs, int i) throws SQLException {
    return new Report(
        rs.getString("id"),
        rs.getString("cluster_id"),
        rs.getString("user_id"),
        rs.getString("reason"),
        rs.getString("note"),
        rs.getString("status"),
        rs.getTimestamp("created_at").toInstant());
  }

  public void resolveReport(String id, String status, String note, String adminId) {
    jdbc.update(
        """
        UPDATE sign_reports SET status = :s, resolution_note = :note, resolved_by = :admin,
          resolved_at = CURRENT_TIMESTAMP(3) WHERE id = :id
        """,
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("s", status)
            .addValue("note", note)
            .addValue("admin", adminId));
  }
}

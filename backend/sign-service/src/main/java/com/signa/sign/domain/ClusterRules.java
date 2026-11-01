package com.signa.sign.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Luật gom cụm và điểm tin cậy theo docs/04-thiet-ke-ai.md §8. Thuần Java để kiểm thử độc lập với
 * CSDL.
 */
public final class ClusterRules {

  public static final double MATCH_RADIUS_M = 20;
  public static final int CONTRIBUTION_HOURS = 12;
  public static final int REJECT_SILENCE_DAYS = 30;
  public static final int MATCH_HEADING_DEG = 45;
  public static final double VERIFY_CONFIDENCE = 0.8;
  public static final int MIN_DISTINCT_USERS = 2;
  public static final double CONFLICT_SHARE = 0.3;
  public static final int MISSING_PASSES = 3;

  private ClusterRules() {}

  /**
   * Cụm ứng viên đã đọc từ CSDL. {@code rejectedLongAgo}: bị loại quá {@link #REJECT_SILENCE_DAYS}
   * ngày. Cụm mới bị loại vẫn nhận quan sát (và giữ REJECTED) để quyết định của quản trị viên có
   * hiệu lực.
   */
  public record Candidate(
      String id, String group, double lat, double lng, int headingDeg, boolean rejectedLongAgo) {}

  /** Chọn cụm gần nhất cùng nhóm, trong bán kính và cùng hướng đi; rỗng nếu cần tạo cụm mới. */
  public static Optional<Candidate> pick(
      List<Candidate> candidates, String group, double lat, double lng, int headingDeg) {
    return candidates.stream()
        .filter(c -> !c.rejectedLongAgo() && matches(c, group, lat, lng, headingDeg))
        .min(Comparator.comparingDouble(c -> Geo.distanceM(c.lat(), c.lng(), lat, lng)));
  }

  /** Vị trí này trùng một cụm đã loại từ lâu: cụm mới cần quản trị viên xét lại. */
  public static boolean nearRejected(
      List<Candidate> candidates, String group, double lat, double lng, int headingDeg) {
    return candidates.stream()
        .anyMatch(c -> c.rejectedLongAgo() && matches(c, group, lat, lng, headingDeg));
  }

  private static boolean matches(
      Candidate c, String group, double lat, double lng, int headingDeg) {
    return c.group().equals(group)
        && Geo.headingDiff(c.headingDeg(), headingDeg) <= MATCH_HEADING_DEG
        && Geo.distanceM(c.lat(), c.lng(), lat, lng) <= MATCH_RADIUS_M;
  }

  /** Bằng chứng của một cụm. */
  public record Evidence(
      Map<String, Double> codeWeights,
      double negativeWeight,
      int distinctUsers,
      int negativePassesSinceLastSeen,
      String lockedCode,
      ClusterStatus current) {}

  public record Assessment(
      String topCode, double alpha, double beta, double confidence, ClusterStatus status) {}

  public static Assessment assess(Evidence e) {
    String top =
        e.lockedCode() != null
            ? e.lockedCode()
            : e.codeWeights().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow();
    double total = e.codeWeights().values().stream().mapToDouble(Double::doubleValue).sum();
    double topWeight = e.codeWeights().getOrDefault(top, 0.0);
    double secondWeight =
        e.codeWeights().entrySet().stream()
            .filter(x -> !x.getKey().equals(top))
            .mapToDouble(Map.Entry::getValue)
            .max()
            .orElse(0);
    double alpha = 1 + topWeight;
    double beta = 1 + (total - topWeight) + e.negativeWeight();
    double confidence = alpha / (alpha + beta);
    return new Assessment(top, alpha, beta, confidence, status(e, total, secondWeight, confidence));
  }

  private static ClusterStatus status(
      Evidence e, double total, double secondWeight, double confidence) {
    if (e.current() == ClusterStatus.REJECTED) {
      return ClusterStatus.REJECTED;
    }
    boolean wasVerified =
        e.current() == ClusterStatus.VERIFIED || e.current() == ClusterStatus.MISSING_SUSPECTED;
    if (wasVerified && e.negativePassesSinceLastSeen() >= MISSING_PASSES) {
      return ClusterStatus.MISSING_SUSPECTED;
    }
    if (e.lockedCode() != null) {
      return ClusterStatus.VERIFIED;
    }
    // Chỉ quản trị viên gỡ được mâu thuẫn (ReviewService chốt mã hoặc loại cụm)
    if (e.current() == ClusterStatus.CONFLICT) {
      return ClusterStatus.CONFLICT;
    }
    if (e.distinctUsers() >= MIN_DISTINCT_USERS
        && total > 0
        && secondWeight >= CONFLICT_SHARE * total) {
      return ClusterStatus.CONFLICT;
    }
    // Đã xác minh thì giữ nguyên, tránh trạng thái nhảy qua lại vì một vài lượt không thấy biển
    if (wasVerified
        || (e.distinctUsers() >= MIN_DISTINCT_USERS && confidence >= VERIFY_CONFIDENCE)) {
      return ClusterStatus.VERIFIED;
    }
    return ClusterStatus.CANDIDATE;
  }

  /** Uy tín mới sau khi quản trị viên giải quyết một cụm. */
  public static double updatedReputation(double reputation, boolean agreed) {
    double next = agreed ? reputation + 0.05 : reputation - 0.1;
    return Math.max(0.5, Math.min(1.5, next));
  }
}

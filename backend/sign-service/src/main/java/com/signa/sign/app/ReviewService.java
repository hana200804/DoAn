package com.signa.sign.app;

import com.signa.common.error.ApiException;
import com.signa.sign.domain.ClusterRules;
import com.signa.sign.domain.ClusterStatus;
import com.signa.sign.store.SignRepository;
import com.signa.sign.store.SignRepository.Cluster;
import com.signa.sign.store.SignRepository.Report;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Báo cáo sai (FR-08, FR-22) và xử lý ca mâu thuẫn (FR-21). */
@Service
public class ReviewService {

  private final SignRepository repo;

  public ReviewService(SignRepository repo) {
    this.repo = repo;
  }

  @Transactional
  public void report(String clusterId, String userId, String reason, String note) {
    repo.cluster(clusterId).orElseThrow(() -> ApiException.notFound("Không tìm thấy biển báo"));
    if (repo.hasPendingReport(clusterId, userId)) {
      throw ApiException.conflict("Bạn đã có một báo cáo đang chờ xử lý cho biển này");
    }
    repo.insertReport(UUID.randomUUID().toString(), clusterId, userId, reason, note);
  }

  @Transactional
  public void resolveConflict(
      String clusterId, String adminId, String signCode, boolean reject, String reason) {
    Cluster cluster =
        repo.cluster(clusterId).orElseThrow(() -> ApiException.notFound("Không tìm thấy biển báo"));
    if (reject) {
      rejectCluster(cluster, adminId, reason);
    } else {
      setCode(cluster, adminId, signCode, reason);
    }
  }

  @Transactional
  public void resolveReport(
      String reportId,
      String adminId,
      boolean accept,
      String action,
      String signCode,
      String note) {
    Report report =
        repo.report(reportId)
            .filter(r -> "PENDING".equals(r.status()))
            .orElseThrow(() -> ApiException.notFound("Không tìm thấy báo cáo đang chờ"));
    if (!accept) {
      if (note == null || note.isBlank()) {
        throw ApiException.validation("note", "bắt buộc khi từ chối báo cáo");
      }
      repo.resolveReport(reportId, "REJECTED", note, adminId);
      return;
    }
    Cluster cluster = repo.cluster(report.clusterId()).orElseThrow();
    if ("CHANGE_CODE".equals(action)) {
      setCode(cluster, adminId, signCode, note);
    } else if ("REJECT_CLUSTER".equals(action)) {
      rejectCluster(cluster, adminId, note);
    } else {
      throw ApiException.validation("action", "bắt buộc khi chấp nhận báo cáo");
    }
    repo.resolveReport(reportId, "ACCEPTED", note, adminId);
  }

  private void setCode(Cluster cluster, String adminId, String signCode, String reason) {
    if (signCode == null || repo.signType(signCode).isEmpty()) {
      throw ApiException.validation("signCode", "mã biển không có trong danh mục");
    }
    repo.setStatus(cluster.id(), ClusterStatus.VERIFIED, signCode, true);
    repo.event(cluster.id(), adminId, "CODE_CHANGED", cluster.signCode(), signCode, reason);
    repo.contributorCodes(cluster.id())
        .forEach(
            (userId, code) -> {
              boolean agreed = signCode.equals(code);
              repo.setReputation(
                  userId, ClusterRules.updatedReputation(repo.reputation(userId), agreed), agreed);
            });
  }

  private void rejectCluster(Cluster cluster, String adminId, String reason) {
    repo.setStatus(cluster.id(), ClusterStatus.REJECTED, cluster.signCode(), false);
    repo.event(cluster.id(), adminId, "REJECTED", cluster.status().name(), "REJECTED", reason);
  }
}

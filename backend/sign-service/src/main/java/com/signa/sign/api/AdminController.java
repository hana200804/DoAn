package com.signa.sign.api;

import com.signa.common.error.ApiException;
import com.signa.common.security.Authenticated;
import com.signa.common.security.CurrentUser;
import com.signa.common.security.Role;
import com.signa.common.security.SessionPrincipal;
import com.signa.sign.api.SignDtos.ConflictView;
import com.signa.sign.api.SignDtos.ResolveConflictRequest;
import com.signa.sign.api.SignDtos.ResolveReportRequest;
import com.signa.sign.api.SignDtos.SignTypeRequest;
import com.signa.sign.api.SignDtos.SignView;
import com.signa.sign.app.ReviewService;
import com.signa.sign.domain.ClusterStatus;
import com.signa.sign.store.CropStorage;
import com.signa.sign.store.SignRepository;
import com.signa.sign.store.SignRepository.Report;
import com.signa.sign.store.SignRepository.SignType;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Authenticated(roles = Role.ADMIN)
public class AdminController {

  private static final int PAGE = 100;

  private final SignRepository repo;
  private final CropStorage storage;
  private final ReviewService reviews;

  public AdminController(SignRepository repo, CropStorage storage, ReviewService reviews) {
    this.repo = repo;
    this.storage = storage;
    this.reviews = reviews;
  }

  @GetMapping("/conflicts")
  public List<ConflictView> conflicts() {
    return repo.clustersByStatus(ClusterStatus.CONFLICT, PAGE).stream()
        .map(
            c ->
                new ConflictView(
                    SignView.of(
                        c, repo.representativeCrop(c.id()).map(storage::viewUrl).orElse(null)),
                    repo.codeWeights(c.id())))
        .toList();
  }

  @PostMapping("/conflicts/{clusterId}/resolve")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resolveConflict(
      @PathVariable String clusterId,
      @CurrentUser SessionPrincipal admin,
      @Valid @RequestBody ResolveConflictRequest req) {
    reviews.resolveConflict(clusterId, admin.userId(), req.signCode(), req.reject(), req.reason());
  }

  @GetMapping("/reports")
  public List<Report> reports(@RequestParam(defaultValue = "PENDING") String status) {
    return repo.reports(status, PAGE);
  }

  @PostMapping("/reports/{id}/resolve")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resolveReport(
      @PathVariable String id,
      @CurrentUser SessionPrincipal admin,
      @Valid @RequestBody ResolveReportRequest req) {
    reviews.resolveReport(
        id, admin.userId(), req.accept(), req.action(), req.signCode(), req.note());
  }

  @PostMapping("/sign-types")
  @ResponseStatus(HttpStatus.CREATED)
  public void createSignType(@Valid @RequestBody SignTypeRequest req) {
    if (repo.signType(req.code()).isPresent()) {
      throw ApiException.conflict("Mã biển đã tồn tại");
    }
    save(req);
  }

  /** Sửa hoặc ngừng dùng (active=false); không xoá để giữ toàn vẹn dữ liệu cụm biển. */
  @PutMapping("/sign-types/{code}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateSignType(@PathVariable String code, @Valid @RequestBody SignTypeRequest req) {
    repo.signType(code).orElseThrow(() -> ApiException.notFound("Không có mã biển này"));
    save(
        new SignTypeRequest(
            code, req.group(), req.name(), req.description(), req.speedKmh(), req.active()));
  }

  private void save(SignTypeRequest r) {
    repo.upsertSignType(
        new SignType(r.code(), r.group(), r.name(), r.description(), r.speedKmh(), r.active()));
  }
}

package com.signa.sign.api;

import com.signa.common.security.Authenticated;
import com.signa.common.security.CurrentUser;
import com.signa.common.security.Role;
import com.signa.common.security.SessionPrincipal;
import com.signa.sign.api.LearningDtos.DatasetExport;
import com.signa.sign.api.LearningDtos.LabelRequest;
import com.signa.sign.api.LearningDtos.ModelMetrics;
import com.signa.sign.api.LearningDtos.ModelView;
import com.signa.sign.api.LearningDtos.OcrReviewRequest;
import com.signa.sign.api.LearningDtos.RegisterModelRequest;
import com.signa.sign.api.LearningDtos.TaskView;
import com.signa.sign.api.SignDtos.Page;
import com.signa.sign.app.LearningService;
import com.signa.sign.store.LearningRepository;
import com.signa.sign.store.LearningRepository.Model;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class LearningController {
  private final LearningService service;
  private final LearningRepository repo;

  public LearningController(LearningService service, LearningRepository repo) {
    this.service = service;
    this.repo = repo;
  }

  @GetMapping("/models/latest")
  public ResponseEntity<ModelView> latest(
      @RequestParam String appVersion,
      @RequestParam int labelMapVersion,
      @RequestParam(required = false) String currentVersion) {
    return service
        .latest(appVersion, labelMapVersion, currentVersion)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @GetMapping("/me/contributions")
  @Authenticated
  public Map<String, Object> contributions(@CurrentUser SessionPrincipal user) {
    return repo.contributions(user.userId());
  }

  @GetMapping("/admin/label-tasks")
  @Authenticated(roles = Role.ADMIN)
  public Page<TaskView> tasks(
      @RequestParam(defaultValue = "OPEN") String status,
      @RequestParam(required = false) String reason,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "100") int size) {
    return service.tasks(status, reason, page, size);
  }

  @PostMapping("/admin/label-tasks/{id}")
  @Authenticated(roles = Role.ADMIN)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void label(
      @PathVariable String id,
      @CurrentUser SessionPrincipal admin,
      @Valid @RequestBody LabelRequest request) {
    service.label(id, admin.userId(), request);
  }

  @PostMapping("/admin/label-tasks/{id}/skip")
  @Authenticated(roles = Role.ADMIN)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void skip(@PathVariable String id, @CurrentUser SessionPrincipal admin) {
    service.skip(id, admin.userId());
  }

  @PostMapping("/admin/label-tasks/{id}/ocr")
  @Authenticated(roles = Role.ADMIN)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reviewOcr(
      @PathVariable String id,
      @CurrentUser SessionPrincipal admin,
      @Valid @RequestBody OcrReviewRequest request) {
    service.reviewOcr(id, admin.userId(), request);
  }

  @GetMapping("/admin/models")
  @Authenticated(roles = Role.ADMIN)
  public List<Model> models() {
    return repo.models();
  }

  @PostMapping("/admin/label-tasks/{id}/retry-analysis")
  @Authenticated(roles = Role.ADMIN)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void retryAnalysis(@PathVariable String id) {
    service.retryAnalysis(id);
  }

  @PostMapping("/admin/models")
  @Authenticated(roles = Role.ADMIN)
  @ResponseStatus(HttpStatus.CREATED)
  public void register(
      @CurrentUser SessionPrincipal admin, @Valid @RequestBody RegisterModelRequest request) {
    service.register(request, admin.userId());
  }

  @PostMapping("/admin/models/{version}/activate")
  @Authenticated(roles = Role.ADMIN)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void activate(@PathVariable String version, @CurrentUser SessionPrincipal admin) {
    service.activate(version, admin.userId());
  }

  @PutMapping("/admin/models/{version}/card")
  @Authenticated(roles = Role.ADMIN)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateModelCard(
      @PathVariable String version, @Valid @RequestBody ModelMetrics metrics) {
    service.updateModelCard(version, metrics);
  }

  @GetMapping("/admin/datasets/{version}/export")
  @Authenticated(roles = Role.ADMIN)
  public DatasetExport exportDataset(
      @PathVariable String version,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "100") int size) {
    return service.exportDataset(version, page, size);
  }

  @GetMapping("/admin/stats/overview")
  @Authenticated(roles = Role.ADMIN)
  public Map<String, Object> overview() {
    return repo.overview();
  }
}

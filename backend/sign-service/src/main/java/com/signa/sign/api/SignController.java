package com.signa.sign.api;

import com.signa.common.error.ApiException;
import com.signa.common.geo.LatLng;
import com.signa.common.security.Authenticated;
import com.signa.common.security.CurrentUser;
import com.signa.common.security.SessionPrincipal;
import com.signa.sign.api.SignDtos.CorridorRequest;
import com.signa.sign.api.SignDtos.Page;
import com.signa.sign.api.SignDtos.ReportRequest;
import com.signa.sign.api.SignDtos.SignView;
import com.signa.sign.app.ReviewService;
import com.signa.sign.domain.ClusterStatus;
import com.signa.sign.domain.Geo;
import com.signa.sign.store.CropStorage;
import com.signa.sign.store.SignRepository;
import com.signa.sign.store.SignRepository.SignType;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SignController {

  private static final double MAX_BOX_DEG = 0.5;
  private static final int MAX_RESULTS = 500;

  private final SignRepository repo;
  private final CropStorage storage;
  private final ReviewService reviews;

  public SignController(SignRepository repo, CropStorage storage, ReviewService reviews) {
    this.repo = repo;
    this.storage = storage;
    this.reviews = reviews;
  }

  @GetMapping("/sign-types")
  public List<SignType> signTypes() {
    return repo.signTypes();
  }

  /** Cụm biển trong vùng {@code bbox=minLng,minLat,maxLng,maxLat} (FR-06, FR-07). */
  @GetMapping("/signs")
  public Page<SignView> signs(
      @RequestParam String bbox,
      @RequestParam(defaultValue = "0") double minConfidence,
      @RequestParam(required = false) Set<String> groups,
      @RequestParam(defaultValue = "false") boolean includeCandidates) {
    double[] b = parseBbox(bbox);
    List<String> statuses =
        includeCandidates
            ? List.of(ClusterStatus.VERIFIED.name(), ClusterStatus.CANDIDATE.name())
            : List.of(ClusterStatus.VERIFIED.name());
    return Page.of(
        repo.clustersInBox(b[1], b[0], b[3], b[2], statuses, MAX_RESULTS).stream()
            .filter(c -> c.confidence() >= minConfidence)
            .filter(c -> groups == null || groups.contains(c.group()))
            .map(c -> SignView.of(c, null))
            .toList());
  }

  /** Cụm biển đã xác minh dọc một tuyến, để app tải trước (FR-04, FR-09). */
  @PostMapping("/signs/corridor")
  public Page<SignView> corridor(@Valid @RequestBody CorridorRequest req) {
    List<LatLng> line = req.polyline();
    double minLat = line.stream().mapToDouble(LatLng::lat).min().orElseThrow();
    double maxLat = line.stream().mapToDouble(LatLng::lat).max().orElseThrow();
    double minLng = line.stream().mapToDouble(LatLng::lng).min().orElseThrow();
    double maxLng = line.stream().mapToDouble(LatLng::lng).max().orElseThrow();
    double padLat = Geo.metersToLatDeg(req.widthM());
    double padLng = Geo.metersToLngDeg(req.widthM(), minLat);
    return Page.of(
        repo
            .clustersInBox(
                minLat - padLat,
                minLng - padLng,
                maxLat + padLat,
                maxLng + padLng,
                List.of(ClusterStatus.VERIFIED.name()),
                5000)
            .stream()
            .filter(c -> Geo.distanceToPolylineM(c.lat(), c.lng(), line) <= req.widthM() / 2)
            .map(c -> SignView.of(c, null))
            .toList());
  }

  @GetMapping("/signs/{id}")
  public SignView sign(@PathVariable String id) {
    var cluster =
        repo.cluster(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy biển báo"));
    return SignView.of(cluster, repo.representativeCrop(id).map(storage::viewUrl).orElse(null));
  }

  @PostMapping("/signs/{id}/reports")
  @Authenticated
  @ResponseStatus(HttpStatus.CREATED)
  public void report(
      @PathVariable String id,
      @CurrentUser SessionPrincipal user,
      @Valid @RequestBody ReportRequest req) {
    reviews.report(id, user.userId(), req.reason(), req.note());
  }

  static double[] parseBbox(String bbox) {
    String[] parts = bbox.split(",");
    if (parts.length != 4) {
      throw ApiException.validation("bbox", "định dạng minLng,minLat,maxLng,maxLat");
    }
    double[] b = new double[4];
    try {
      for (int i = 0; i < 4; i++) {
        b[i] = Double.parseDouble(parts[i].trim());
      }
    } catch (NumberFormatException e) {
      throw ApiException.validation("bbox", "phải là số");
    }
    if (b[0] >= b[2] || b[1] >= b[3] || b[2] - b[0] > MAX_BOX_DEG || b[3] - b[1] > MAX_BOX_DEG) {
      throw ApiException.validation(
          "bbox", "vùng không hợp lệ hoặc rộng quá " + MAX_BOX_DEG + " độ");
    }
    return b;
  }
}

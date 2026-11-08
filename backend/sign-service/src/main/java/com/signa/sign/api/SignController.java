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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
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
      @RequestParam(defaultValue = "false") boolean includeCandidates,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "100") int size) {
    if (!Double.isFinite(minConfidence) || minConfidence < 0 || minConfidence > 1) {
      throw ApiException.validation("minConfidence", "phải nằm trong [0, 1]");
    }
    if (page < 0 || page > 100000 || size < 1 || size > 100) {
      throw ApiException.validation("page", "page từ 0, size từ 1 đến 100");
    }
    if (groups != null
        && (groups.isEmpty()
            || !Set.of("PROHIBITORY", "MANDATORY", "WARNING", "AUXILIARY", "OTHER")
                .containsAll(groups))) {
      throw ApiException.validation("groups", "nhóm biển không hợp lệ");
    }
    double[] b = parseBbox(bbox);
    List<String> statuses =
        includeCandidates
            ? List.of(ClusterStatus.VERIFIED.name(), ClusterStatus.CANDIDATE.name())
            : List.of(ClusterStatus.VERIFIED.name());
    return new Page<>(
        repo.mapPage(b, statuses, groups, minConfidence, page, size).stream()
            .map(c -> SignView.of(c, null))
            .toList(),
        page,
        size,
        repo.mapCount(b, statuses, groups, minConfidence));
  }

  /** Cụm biển đã xác minh dọc một tuyến, để app tải trước (FR-04, FR-09). */
  @PostMapping("/signs/corridor")
  @Transactional(readOnly = true)
  public Page<SignView> corridor(
      @Valid @RequestBody CorridorRequest req,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "100") int size) {
    if (page < 0 || page > 100000 || size < 1 || size > 100) {
      throw ApiException.validation("page", "page từ 0, size từ 1 đến 100");
    }
    List<LatLng> line = req.polyline();
    double minLat = line.stream().mapToDouble(LatLng::lat).min().orElseThrow();
    double maxLat = line.stream().mapToDouble(LatLng::lat).max().orElseThrow();
    double minLng = line.stream().mapToDouble(LatLng::lng).min().orElseThrow();
    double maxLng = line.stream().mapToDouble(LatLng::lng).max().orElseThrow();
    double padLat = Geo.metersToLatDeg(req.widthM());
    double padLng = Geo.metersToLngDeg(req.widthM(), minLat);
    double[] bbox = {
      Math.max(-180, minLng - padLng),
      Math.max(-90, minLat - padLat),
      Math.min(180, maxLng + padLng),
      Math.min(90, maxLat + padLat)
    };
    List<SignView> items = new ArrayList<>();
    String after = "";
    long total = 0;
    long offset = (long) page * size;
    while (true) {
      var candidates = repo.corridorCandidates(bbox, after, 512);
      if (candidates.isEmpty()) break;
      for (var c : candidates) {
        if (Geo.distanceToPolylineM(c.lat(), c.lng(), line) <= req.widthM() / 2) {
          if (total >= offset && items.size() < size) items.add(SignView.of(c, null));
          total++;
        }
      }
      after = candidates.getLast().id();
      if (candidates.size() < 512) break;
    }
    return new Page<>(items, page, size, total);
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
    String[] parts = bbox.split(",", -1);
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
    if (java.util.Arrays.stream(b).anyMatch(v -> !Double.isFinite(v))
        || b[0] < -180
        || b[2] > 180
        || b[1] < -90
        || b[3] > 90
        || b[0] >= b[2]
        || b[1] >= b[3]
        || b[2] - b[0] > MAX_BOX_DEG
        || b[3] - b[1] > MAX_BOX_DEG) {
      throw ApiException.validation(
          "bbox", "vùng không hợp lệ hoặc rộng quá " + MAX_BOX_DEG + " độ");
    }
    return b;
  }
}

package com.signa.sign.api;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.signa.common.geo.LatLng;
import com.signa.sign.domain.ClusterStatus;
import com.signa.sign.store.SignRepository.Cluster;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** DTO của sign-service, khớp với docs/07-api.md §5–6. */
public final class SignDtos {

  private SignDtos() {}

  public enum Kind {
    POSITIVE,
    NEGATIVE
  }

  public record CodeProb(@NotBlank String code, @DecimalMin("0") @DecimalMax("1") double p) {}

  public record ObservationIn(
      @NotBlank @Size(max = 64) String clientId,
      @NotNull Kind kind,
      @Size(max = 20) String signCode,
      @Size(max = 36) String clusterId,
      @Size(max = 20) String detectorGroup,
      @Size(max = 5) List<@Valid CodeProb> classProbs,
      @DecimalMin("0") @DecimalMax("1") Double voteShare,
      @NotNull @Valid LatLng location,
      @Min(0) @Max(359) int headingDeg,
      @DecimalMin("0") @DecimalMax("500") Double distanceM,
      @Min(1) Integer frames,
      boolean hasSubSign,
      @NotNull Instant observedAt) {}

  public record CreateBatchRequest(
      @NotBlank @Size(max = 20) String modelVersion,
      @Min(1) int labelMapVersion,
      @Size(max = 100) String deviceModel,
      @NotEmpty @Size(max = 100) List<@Valid ObservationIn> observations) {}

  public record Upload(String clientId, String cropUploadUrl, String subSignUploadUrl) {}

  public record CreateBatchResponse(String batchId, List<Upload> uploads) {}

  public record SignView(
      String id,
      String signCode,
      String group,
      String name,
      LatLng location,
      int headingDeg,
      double confidence,
      ClusterStatus status,
      @JsonRawValue String conditions,
      int observationCount,
      Instant lastSeenAt,
      String imageUrl) {

    public static SignView of(Cluster c, String imageUrl) {
      return new SignView(
          c.id(),
          c.signCode(),
          c.group(),
          c.name(),
          new LatLng(c.lat(), c.lng()),
          c.headingDeg(),
          c.confidence(),
          c.status(),
          c.conditions(),
          c.observationCount(),
          c.lastSeenAt(),
          imageUrl);
    }
  }

  public record Page<T>(List<T> items, int page, int size, long total) {
    public static <T> Page<T> of(List<T> items) {
      return new Page<>(items, 0, items.size(), items.size());
    }
  }

  public record CorridorRequest(
      @NotEmpty @Size(max = 5000) List<@Valid LatLng> polyline,
      @DecimalMin("5") @DecimalMax("200") double widthM) {}

  public record ReportRequest(
      @NotNull @Pattern(regexp = "NOT_EXIST|WRONG_TYPE|WRONG_POSITION|OTHER") String reason,
      @Size(max = 500) String note) {}

  public record ConflictView(SignView sign, Map<String, Double> codeWeights) {}

  public record ResolveConflictRequest(
      @Size(max = 20) String signCode, boolean reject, @Size(max = 500) String reason) {}

  public record ResolveReportRequest(
      boolean accept,
      @Pattern(regexp = "REJECT_CLUSTER|CHANGE_CODE") String action,
      @Size(max = 20) String signCode,
      @Size(max = 500) String note) {}

  public record SignTypeRequest(
      @NotBlank @Size(max = 20) String code,
      @NotNull @Pattern(regexp = "PROHIBITORY|MANDATORY|WARNING|AUXILIARY|OTHER") String group,
      @NotBlank @Size(max = 200) String name,
      String description,
      @Min(5) @Max(150) Integer speedKmh,
      boolean active) {}
}

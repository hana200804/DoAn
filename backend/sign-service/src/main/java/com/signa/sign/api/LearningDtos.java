package com.signa.sign.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class LearningDtos {
  private LearningDtos() {}

  /** Coordinates normalized to the uploaded crop, in [0,1]. */
  public record LabelBox(
      @DecimalMin("0") @DecimalMax("1") double x,
      @DecimalMin("0") @DecimalMax("1") double y,
      @DecimalMin(value = "0", inclusive = false) @DecimalMax("1") double width,
      @DecimalMin(value = "0", inclusive = false) @DecimalMax("1") double height) {}

  public record LabelRequest(
      @NotBlank @Size(max = 20) String labelCode,
      @Valid LabelBox labelBox,
      @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,9}") String datasetVersion) {
    public LabelRequest(String labelCode, LabelBox labelBox) {
      this(labelCode, labelBox, null);
    }
  }

  public record TimeWindowRequest(
      @NotNull @Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]") String from,
      @NotNull @Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]") String to) {}

  public record VehiclesRequest(
      @Size(max = 3) List<@NotNull @Pattern(regexp = "CAR|BUS|TRUCK") String> include,
      @Size(max = 3) List<@NotNull @Pattern(regexp = "CAR|BUS|TRUCK") String> exclude) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ConditionsRequest(
      @Size(max = 20) List<@NotNull @Valid TimeWindowRequest> timeWindows,
      @Valid VehiclesRequest vehicles,
      @Min(1) @Max(100000) Integer distanceAheadM,
      @Min(1) @Max(100000) Integer zoneLengthM,
      @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000") Double weightLimitTon) {}

  public record OcrReviewRequest(
      @NotNull @Size(max = 4000) String rawText,
      @NotNull @Valid ConditionsRequest conditions,
      @Valid LabelRequest signLabel) {
    public OcrReviewRequest(String rawText, ConditionsRequest conditions) {
      this(rawText, conditions, null);
    }
  }

  public record TaskView(
      String id,
      String observationId,
      String reason,
      String status,
      String signCode,
      String imageUrl,
      String subSignImageUrl,
      String labelCode,
      @JsonRawValue String labelBox,
      @JsonRawValue String analysis,
      String ocrText,
      @JsonRawValue String conditions,
      Instant createdAt,
      String datasetVersion,
      String ocrStatus) {}

  public record ArtifactRequest(
      @NotBlank @Size(max = 255) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_./-]*\\.tflite")
          String objectKey,
      @NotNull @Pattern(regexp = "[a-f0-9]{64}") String sha256,
      @Min(1) @Max(104857600) long sizeBytes) {}

  public record ModelMetrics(
      @NotBlank @Size(max = 40) String datasetVersion,
      @NotBlank @Size(max = 40) String fieldDatasetVersion,
      @NotBlank @Size(max = 255) String trainingConfig,
      @NotBlank @Size(max = 64) String commit,
      @NotBlank @Size(max = 100) String referenceDevice,
      @NotNull @DecimalMin("0") @DecimalMax("1") Double map50,
      @NotNull @DecimalMin("0") @DecimalMax("1") Double criticalRecall,
      @NotNull @DecimalMin("0") @DecimalMax("1") Double int8MapDrop,
      @NotNull @DecimalMin("0") @DecimalMax("10000") Double fps,
      @NotNull @DecimalMin("0") @DecimalMax("100000") Double falseWarningsPer10Km) {}

  public record RegisterModelRequest(
      @NotBlank @Size(max = 20) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*") String version,
      @Min(1) int labelMapVersion,
      @NotBlank @Size(max = 32) String minAppVersion,
      @NotNull @Valid ArtifactRequest detector,
      @NotNull @Valid ArtifactRequest classifier,
      @NotNull @Valid ModelMetrics metrics) {}

  public record ArtifactView(String url, String sha256, long sizeBytes) {}

  public record ModelView(
      String version,
      int labelMapVersion,
      String minAppVersion,
      ArtifactView detector,
      ArtifactView classifier) {}

  public record ReviewedSample(
      String observationId,
      String labelCode,
      @JsonRawValue String labelBox,
      String imageUrl,
      String sha256,
      String sequence,
      int labelMapVersion) {}

  public record DatasetExport(
      String datasetVersion, List<ReviewedSample> items, int page, int size, long total) {}
}

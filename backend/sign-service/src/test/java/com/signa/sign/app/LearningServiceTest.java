package com.signa.sign.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.signa.common.error.ApiException;
import com.signa.common.error.ErrorCode;
import com.signa.sign.api.LearningDtos.ConditionsRequest;
import com.signa.sign.api.LearningDtos.LabelBox;
import com.signa.sign.api.LearningDtos.LabelRequest;
import com.signa.sign.api.LearningDtos.OcrReviewRequest;
import com.signa.sign.store.CropStorage;
import com.signa.sign.store.LearningRepository;
import com.signa.sign.store.LearningRepository.Model;
import com.signa.sign.store.LearningRepository.Task;
import com.signa.sign.store.SignRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LearningServiceTest {
  @Test
  void releaseRequiresMeasuredQualityAndAllowsExplicitRollback() {
    var good = model(false);
    when(repo.model("model-v1")).thenReturn(Optional.of(good));
    service.activate("model-v1", "admin");
    verify(repo).activate("model-v1", "admin", 1);
    var bad =
        new Model(
            good.version(),
            1,
            good.minAppVersion(),
            good.detectorKey(),
            good.detectorSha256(),
            10,
            good.classifierKey(),
            good.classifierSha256(),
            20,
            false,
            Instant.now(),
            "DRAFT",
            good.metrics().replace("0.9,", "0.8,"));
    when(repo.model("model-v1")).thenReturn(Optional.of(bad));
    assertThatThrownBy(() -> service.activate("model-v1", "admin"))
        .isInstanceOf(ApiException.class);
    verify(repo, times(1)).activate("model-v1", "admin", 1);
  }

  private final LearningRepository repo = mock(LearningRepository.class);
  private final SignRepository signs = mock(SignRepository.class);
  private final CropStorage storage = mock(CropStorage.class);
  private final LearningService service =
      new LearningService(repo, signs, storage, new ObjectMapper());

  @Test
  void releasesRespectNumericVersionLabelMapAndCurrentVersion() {
    when(repo.models()).thenReturn(List.of(model(true)));
    when(storage.artifactUrl(anyString())).thenReturn("https://storage/download");
    assertThat(service.latest("1.10.0", 1, null)).isPresent();
    assertThat(service.latest("1.8.0", 1, null)).isEmpty();
    assertThat(service.latest("1.10.0", 2, null)).isEmpty();
    assertThat(service.latest("1.10.0", 1, "model-v1")).isEmpty();
    assertThatThrownBy(() -> service.latest("garbage", 1, null)).isInstanceOf(ApiException.class);
    when(repo.models()).thenReturn(List.of(model(false)));
    assertThat(service.latest("2", 1, null)).isEmpty();
  }

  @Test
  void activationVerifiesBytesBeforeChangingReleasePointer() {
    when(repo.model("model-v1")).thenReturn(Optional.of(model(false)));
    doThrow(ApiException.validation("sha256", "mismatch"))
        .when(storage)
        .verifyArtifact("models/c.tflite", "c", 20);
    assertThatThrownBy(() -> service.activate("model-v1", "admin"))
        .isInstanceOf(ApiException.class);
    verify(repo, never()).activate(anyString(), anyString(), anyInt());
  }

  @Test
  void labelsCannotBeOverwrittenOrUseABoxOutsideTheCrop() {
    when(repo.taskForUpdate("task")).thenReturn(Optional.of(task("LABELED")));
    assertThatThrownBy(() -> service.label("task", "admin", new LabelRequest("P.123a", null)))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.CONFLICT));
    when(repo.taskForUpdate("task")).thenReturn(Optional.of(task("OPEN")));
    when(signs.signType("P.123a"))
        .thenReturn(
            Optional.of(
                new SignRepository.SignType("P.123a", "PROHIBITORY", "Left", null, null, true)));
    assertThatThrownBy(
            () ->
                service.label(
                    "task", "admin", new LabelRequest("P.123a", new LabelBox(.9, 0, .2, .5))))
        .isInstanceOf(ApiException.class);
    verify(repo, never()).resolveTask(anyString(), anyString(), any(), any(), anyBoolean(), any());
    service.label("task", "admin", new LabelRequest("P.123a", new LabelBox(0, 0, 1, 1)));
    verify(repo)
        .resolveTask(
            eq("task"),
            eq("admin"),
            eq("P.123a"),
            contains("\"width\":1.0"),
            eq(false),
            eq("v1.0"));
  }

  @Test
  void pagingAndFiltersAreValidatedBeforeQuerying() {
    assertThatThrownBy(() -> service.tasks("NO", null, 0, 10)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.tasks("OPEN", "NO", 0, 10)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.tasks("OPEN", null, -1, 10)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.tasks("OPEN", null, 0, 101)).isInstanceOf(ApiException.class);
    verifyNoInteractions(repo);
  }

  @Test
  void ocrReviewCannotCloseAHardImageWithoutItsMainSignLabel() {
    when(repo.taskForUpdate("task")).thenReturn(Optional.of(task("OPEN")));
    var request =
        new OcrReviewRequest("6h-22h", new ConditionsRequest(null, null, null, null, 10.0));
    assertThatThrownBy(() -> service.reviewOcr("task", "admin", request))
        .isInstanceOf(ApiException.class);
    verify(repo, never())
        .resolveOcrTask(anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void correctedMainSignLabelCannotApplyConditionsToTheOldClusterCode() {
    when(repo.taskForUpdate("task")).thenReturn(Optional.of(task("OPEN")));
    when(signs.signType("P.123b"))
        .thenReturn(
            Optional.of(
                new SignRepository.SignType("P.123b", "PROHIBITORY", "Right", null, null, true)));
    service.reviewOcr(
        "task",
        "admin",
        new OcrReviewRequest(
            "text",
            new ConditionsRequest(null, null, null, null, 10.0),
            new LabelRequest("P.123b", null)));
    verify(repo).resolveOcrTask(eq("task"), eq("admin"), eq("text"), anyString(), eq("v1.0"));
    verify(repo, never()).applyReviewedConditions(anyString(), anyString());
  }

  private static Model model(boolean active) {
    return new Model(
        "model-v1",
        1,
        "1.9.0",
        "models/d.tflite",
        "d",
        10,
        "models/c.tflite",
        "c",
        20,
        active,
        Instant.now(),
        active ? "ACTIVE" : "DRAFT",
        "{\"datasetVersion\":\"v1\",\"fieldDatasetVersion\":\"f1\",\"trainingConfig\":\"config\",\"commit\":\"abc\",\"referenceDevice\":\"device\",\"map50\":0.9,\"criticalRecall\":0.95,\"int8MapDrop\":0.01,\"fps\":16,\"falseWarningsPer10Km\":0.5}");
  }

  private static Task task(String status) {
    return new Task(
        "task",
        "obs",
        "LOW_CONF",
        status,
        "P.123a",
        "crops/a.jpg",
        "crops/a-sub.jpg",
        "cluster",
        null,
        null,
        null,
        null,
        null,
        Instant.now());
  }
}

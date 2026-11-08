package com.signa.sign.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.signa.common.geo.LatLng;
import com.signa.sign.SignDatabaseIT;
import com.signa.sign.api.LearningDtos.ArtifactRequest;
import com.signa.sign.api.LearningDtos.ConditionsRequest;
import com.signa.sign.api.LearningDtos.LabelBox;
import com.signa.sign.api.LearningDtos.LabelRequest;
import com.signa.sign.api.LearningDtos.OcrReviewRequest;
import com.signa.sign.api.LearningDtos.RegisterModelRequest;
import com.signa.sign.api.LearningDtos.TimeWindowRequest;
import com.signa.sign.api.SignDtos.*;
import com.signa.sign.store.CropStorage;
import com.signa.sign.store.LearningRepository;
import com.signa.sign.store.SignRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("integration")
@SpringBootTest
class LearningFlowIT extends SignDatabaseIT {
  @Test
  void correctedMainLabelInvalidatesOldAutomaticOcrAndBlocksLateResults() {
    var saved = submit();
    finish(saved, .95, "06:00", "09:00");
    assertThat(signs.cluster(saved.clusterId()).orElseThrow().conditions()).isNotNull();
    var task =
        repo.tasks("OPEN", null, 0, 100).stream()
            .filter(t -> t.observationId().equals(saved.id()))
            .findFirst()
            .orElseThrow();
    learning.label(task.id(), "admin", new LabelRequest("P.123b", null));
    assertThat(signs.cluster(saved.clusterId()).orElseThrow().conditions()).isNull();
    jdbc.update(
        "UPDATE observation_analysis SET status = 'RETRY' WHERE observation_id = :id",
        Map.of("id", saved.id()));
    learning.retryAnalysis(task.id());
    finish(saved, .99, "16:00", "19:00");
    assertThat(signs.cluster(saved.clusterId()).orElseThrow().conditions()).isNull();
  }

  @Test
  void missingSubImageDoesNotLeaveTheMainLabelPermanentlyOpen() {
    when(storage.exists(anyString()))
        .thenAnswer(inv -> !((String) inv.getArgument(0)).endsWith("-sub.jpg"));
    var saved = submit();
    var task =
        repo.tasks("OPEN", null, 0, 100).stream()
            .filter(t -> t.observationId().equals(saved.id()))
            .findFirst()
            .orElseThrow();
    learning.label(task.id(), "admin", new LabelRequest("P.123a", null));
    var resolved = tx.execute(ignored -> repo.taskForUpdate(task.id()).orElseThrow());
    assertThat(resolved.status()).isEqualTo("LABELED");
    assertThat(signs.cluster(saved.clusterId()).orElseThrow().hasSubSign()).isTrue();
  }

  @Test
  void mainLabelLeavesOcrOpenAndExportsDatasetWithHash() {
    var saved = submit();
    var task =
        repo.tasks("OPEN", null, 0, 100).stream()
            .filter(t -> t.observationId().equals(saved.id()))
            .findFirst()
            .orElseThrow();
    learning.label(task.id(), "admin", new LabelRequest("P.123a", null, "v2.1"));
    repo.labelTask(saved.id(), "OCR_UNPARSED");
    var pending = tx.execute(ignored -> repo.taskForUpdate(task.id()).orElseThrow());
    assertThat(pending.status()).isEqualTo("OPEN");
    assertThat(pending.labelCode()).isEqualTo("P.123a");
    assertThat(pending.datasetVersion()).isEqualTo("v2.1");
    when(storage.cropSha256(saved.cropKey())).thenReturn("a".repeat(64));
    var export = learning.exportDataset("v2.1", 0, 100);
    assertThat(export.items())
        .anySatisfy(
            s -> {
              assertThat(s.observationId()).isEqualTo(saved.id());
              assertThat(s.sha256()).hasSize(64);
            });
    learning.reviewOcr(
        task.id(),
        "admin",
        new OcrReviewRequest(
            "6h-22h",
            new ConditionsRequest(
                List.of(new TimeWindowRequest("06:00", "22:00")), null, null, null, null)));
    var reviewed = tx.execute(ignored -> repo.taskForUpdate(task.id()).orElseThrow());
    assertThat(reviewed.status()).isEqualTo("LABELED");
    assertThat(reviewed.ocrStatus()).isEqualTo("REVIEWED");
    assertThat(repo.overview())
        .containsKeys("observationsByDay", "topContributors", "coverage", "activeModels");
    assertThat(repo.contributions(saved.userId()))
        .containsKeys("verifiedObservations", "rejectedObservations");
  }

  @Test
  void corridorDoesNotTruncateBeforeFilteringOrReturnInactiveTypes() {
    String code = "rc-" + UUID.randomUUID().toString().substring(0, 8);
    String validId = UUID.randomUUID().toString();
    signs.upsertSignType(new SignRepository.SignType(code, "WARNING", "test", null, null, true));
    double lat = 12.0;
    var rows = new java.util.ArrayList<Map<String, Object>>();
    for (int i = 0; i < 5001; i++)
      rows.add(
          Map.of(
              "id",
              UUID.randomUUID().toString(),
              "lat",
              lat + .002,
              "lng",
              106.001 + i * .0000001,
              "code",
              code));
    rows.add(Map.of("id", validId, "lat", lat, "lng", 106.005, "code", code));
    jdbc.batchUpdate(
        "INSERT INTO sign_clusters(id,sign_code,lat,lng,heading_deg,status,last_seen_at) VALUES(:id,:code,:lat,:lng,90,'VERIFIED',NOW(3))",
        rows.toArray(Map[]::new));
    var controller = new com.signa.sign.api.SignController(signs, storage, null);
    var request =
        new CorridorRequest(
            List.of(new LatLng(lat, 106), new LatLng(lat, 106.01), new LatLng(lat + .003, 106.01)),
            100);
    assertThat(controller.corridor(request, 0, 100).items())
        .extracting(SignView::id)
        .contains(validId);
    signs.upsertSignType(new SignRepository.SignType(code, "WARNING", "test", null, null, false));
    assertThat(controller.corridor(request, 0, 100).items()).isEmpty();
  }

  @MockBean CropStorage storage;
  @Autowired ObservationService observations;
  @Autowired LearningService learning;
  @Autowired LearningRepository repo;
  @Autowired SignRepository signs;
  @Autowired NamedParameterJdbcTemplate jdbc;
  @Autowired TransactionTemplate tx;

  @BeforeEach
  void configureStorage() {
    when(storage.uploadUrl(anyString())).thenReturn("http://storage/upload");
    when(storage.viewUrl(anyString())).thenReturn("http://storage/view");
    when(storage.exists(anyString())).thenReturn(true);
  }

  @Test
  void hardObservationCreatesOneTaskAndOneDurableJob() {
    var saved = submit();
    var task =
        repo.tasks("OPEN", "LOW_CONF", 0, 100).stream()
            .filter(t -> t.observationId().equals(saved.id()))
            .findFirst()
            .orElseThrow();
    repo.labelTask(saved.id(), "AI_UNAVAILABLE");
    assertThat(
            repo.tasks("OPEN", null, 0, 100).stream()
                .filter(t -> t.observationId().equals(saved.id())))
        .hasSize(1);
    assertThat(learning.tasks("OPEN", "LOW_CONF", 0, 1).total()).isPositive();
    learning.label(task.id(), "admin", new LabelRequest("P.123a", new LabelBox(0, 0, 1, 1)));
    String status = tx.execute(ignored -> repo.taskForUpdate(task.id()).orElseThrow().status());
    assertThat(status).isEqualTo("OPEN");
    assertThatThrownBy(() -> learning.label(task.id(), "admin", new LabelRequest("P.123a", null)))
        .isInstanceOf(com.signa.common.error.ApiException.class);
    var job =
        repo.dueJobs().stream().filter(j -> j.id().equals(saved.id())).findFirst().orElseThrow();
    assertThat(repo.claim(job)).isTrue();
    assertThat(repo.claim(job)).isFalse();
    tx.executeWithoutResult(
        ignored ->
            repo.finish(job, "{\"parsed\":true}", "{\"timeWindows\":[]}", .95, false, false));
    assertThat(signs.cluster(saved.clusterId()).orElseThrow().conditions()).contains("timeWindows");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM observation_analysis WHERE observation_id = :id",
                Map.of("id", saved.id()),
                String.class))
        .isEqualTo("DONE");
    signs.setStatus(
        saved.clusterId(), com.signa.sign.domain.ClusterStatus.VERIFIED, "P.123b", true);
    assertThat(signs.cluster(saved.clusterId()).orElseThrow().conditions()).isNull();
  }

  @Test
  void expiredLeaseRetriesAndDoesNotAcceptStaleResult() {
    var saved = submit();
    var first =
        repo.dueJobs().stream().filter(j -> j.id().equals(saved.id())).findFirst().orElseThrow();
    assertThat(repo.claim(first)).isTrue();
    jdbc.update(
        "UPDATE observation_analysis SET available_at = NOW(3) - INTERVAL 1 SECOND WHERE observation_id = :id",
        Map.of("id", saved.id()));
    var second =
        repo.dueJobs().stream().filter(j -> j.id().equals(saved.id())).findFirst().orElseThrow();
    assertThat(second.attempts()).isEqualTo(1);
    assertThat(repo.claim(second)).isTrue();
    tx.executeWithoutResult(ignored -> repo.finish(first, "{}", "{\"bad\":true}", 1, false, false));
    assertThat(signs.cluster(saved.clusterId()).orElseThrow().conditions()).isNull();
    repo.retry(second, false);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM observation_analysis WHERE observation_id = :id",
                Map.of("id", saved.id()),
                String.class))
        .isEqualTo("RETRY");
  }

  @Test
  void modelActivationAndRollbackKeepOneActiveVersion() {
    String a = "a-" + UUID.randomUUID().toString().substring(0, 8);
    String b = "b-" + UUID.randomUUID().toString().substring(0, 8);
    for (String version : List.of(a, b)) {
      learning.register(
          new RegisterModelRequest(
              version,
              1,
              "1.2.0",
              new ArtifactRequest("models/d.tflite", "a".repeat(64), 10),
              new ArtifactRequest("models/c.tflite", "b".repeat(64), 20),
              new com.signa.sign.api.LearningDtos.ModelMetrics(
                  "v1", "field1", "config", "abc", "device", .9, .95, .01, 16.0, .5)),
          "admin");
      learning.activate(version, "admin");
      assertThat(repo.models().stream().filter(LearningRepository.Model::active)).hasSize(1);
      assertThat(learning.latest("1.10.0", 1, null).orElseThrow().version()).isEqualTo(version);
    }
    learning.activate(a, "admin");
    assertThat(learning.latest("1.10.0", 1, b).orElseThrow().version()).isEqualTo(a);
    assertThat(learning.latest("1.1.0", 1, null)).isEmpty();
    assertThat(repo.overview()).containsKeys("openLabelTasks", "failedAnalyses");
  }

  @Test
  void manualRetryFencesOutResultsFromAnEarlierGeneration() {
    var saved = submit();
    var task =
        repo.tasks("OPEN", null, 0, 100).stream()
            .filter(t -> t.observationId().equals(saved.id()))
            .findFirst()
            .orElseThrow();
    var old =
        repo.dueJobs().stream().filter(j -> j.id().equals(saved.id())).findFirst().orElseThrow();
    assertThat(repo.claim(old)).isTrue();
    repo.retry(old, true);
    learning.retryAnalysis(task.id());
    var current =
        repo.dueJobs().stream().filter(j -> j.id().equals(saved.id())).findFirst().orElseThrow();
    assertThat(current.generation()).isEqualTo(old.generation() + 1);
    assertThat(repo.claim(current)).isTrue();
    tx.executeWithoutResult(ignored -> repo.finish(old, "{}", "{\"stale\":true}", 1, false, false));
    assertThat(signs.cluster(saved.clusterId()).orElseThrow().conditions()).isNull();
    assertThat(repo.contributions(saved.userId()).get("observations")).isEqualTo(1L);
  }

  @Test
  void mapFiltersApplyBeforePagination() {
    double lat = 20 + Math.random();
    for (String code : List.of("W.224", "P.123a", "P.123b")) {
      String id = UUID.randomUUID().toString();
      signs.insertCluster(id, code, lat, 105.8, 90, Instant.now());
      signs.setStatus(id, com.signa.sign.domain.ClusterStatus.VERIFIED, code, false);
    }
    double[] box = {105.799, lat - .001, 105.801, lat + .001};
    var groups = java.util.Set.of("PROHIBITORY");
    var statuses = List.of("VERIFIED");
    var first = signs.mapPage(box, statuses, groups, 0, 0, 1);
    var second = signs.mapPage(box, statuses, groups, 0, 1, 1);
    assertThat(signs.mapCount(box, statuses, groups, 0)).isEqualTo(2);
    assertThat(first).hasSize(1);
    assertThat(second).hasSize(1);
    assertThat(first.get(0).group()).isEqualTo("PROHIBITORY");
    assertThat(first.get(0).id()).isNotEqualTo(second.get(0).id());
  }

  @Test
  void newerOcrReplacesSameConfidenceButOlderResultCannotUndoIt() {
    var first = submit();
    var later = duplicateAt(first, Instant.now().plusSeconds(30));
    var old = duplicateAt(first, Instant.now().minusSeconds(30));
    finish(first, .95, "06:00", "09:00");
    finish(later, .95, "16:00", "19:00");
    assertThat(signs.cluster(first.clusterId()).orElseThrow().conditions()).contains("16:00");
    finish(old, .99, "01:00", "02:00");
    assertThat(signs.cluster(first.clusterId()).orElseThrow().conditions()).contains("16:00");
  }

  @Test
  void missingSubImageStillPreservesTheNeedForAConditionalWarning() {
    when(storage.exists(anyString()))
        .thenAnswer(inv -> !((String) inv.getArgument(0)).endsWith("-sub.jpg"));
    var observation = submit();
    assertThat(observation.subSignKey()).isNull();
    assertThat(signs.cluster(observation.clusterId()).orElseThrow().hasSubSign()).isTrue();
  }

  @Test
  void ocrReviewIncludesSubImageAndProtectsManualConditionsFromAi() {
    var observation = submit();
    var task =
        repo.tasks("OPEN", null, 0, 100).stream()
            .filter(t -> t.observationId().equals(observation.id()))
            .findFirst()
            .orElseThrow();
    when(storage.viewUrl(observation.subSignKey())).thenReturn("http://storage/sub-sign");
    var view =
        learning.tasks("OPEN", null, 0, 100).items().stream()
            .filter(t -> t.id().equals(task.id()))
            .findFirst()
            .orElseThrow();
    assertThat(view.subSignImageUrl()).isEqualTo("http://storage/sub-sign");
    assertThat(signs.cluster(observation.clusterId()).orElseThrow().hasSubSign()).isTrue();
    learning.reviewOcr(
        task.id(),
        "admin",
        new OcrReviewRequest(
            "6h-22h",
            new ConditionsRequest(
                List.of(new TimeWindowRequest("06:00", "22:00")), null, null, null, null),
            new LabelRequest("P.123a", null)));
    var reviewed =
        repo.tasks("LABELED", null, 0, 100).stream()
            .filter(t -> t.id().equals(task.id()))
            .findFirst()
            .orElseThrow();
    assertThat(reviewed.ocrText()).isEqualTo("6h-22h");
    assertThat(reviewed.conditions()).contains("22:00");
    var later = duplicateAt(observation, Instant.now().plusSeconds(60));
    finish(later, 1, "01:00", "02:00");
    assertThat(signs.cluster(observation.clusterId()).orElseThrow().conditions()).contains("22:00");
    assertThatThrownBy(() -> learning.reviewOcr(task.id(), "admin", new OcrReviewRequest("", null)))
        .isInstanceOf(com.signa.common.error.ApiException.class);
  }

  private SignRepository.Observation duplicateAt(
      SignRepository.Observation original, Instant time) {
    String id = UUID.randomUUID().toString();
    var copy =
        new SignRepository.Observation(
            id,
            id,
            UUID.randomUUID().toString(),
            "POSITIVE",
            "P.123a",
            "prohibitory",
            .9,
            .9,
            original.lat(),
            original.lng(),
            90,
            20.0,
            5,
            original.clusterId(),
            "crops/" + id + ".jpg",
            "crops/" + id + "-sub.jpg",
            null,
            time);
    String batchId =
        jdbc.queryForObject(
            "SELECT batch_id FROM observations WHERE id = :id",
            Map.of("id", original.id()),
            String.class);
    signs.insertObservation(batchId, copy);
    signs.assignObservation(id, original.clusterId(), 1, copy.cropKey(), copy.subSignKey());
    repo.enqueue(id);
    return copy;
  }

  private void finish(
      SignRepository.Observation observation, double confidence, String from, String to) {
    var job =
        repo.dueJobs().stream()
            .filter(j -> j.id().equals(observation.id()))
            .findFirst()
            .orElseThrow();
    assertThat(repo.claim(job)).isTrue();
    String conditions = "{\"timeWindows\":[{\"from\":\"" + from + "\",\"to\":\"" + to + "\"}]}";
    tx.executeWithoutResult(
        ignored -> repo.finish(job, "{}", conditions, confidence, false, false));
  }

  private SignRepository.Observation submit() {
    String user = UUID.randomUUID().toString();
    var input =
        new ObservationIn(
            "one",
            Kind.POSITIVE,
            "P.123a",
            null,
            "prohibitory",
            List.of(new CodeProb("P.123a", .5)),
            .8,
            new LatLng(21 + Math.random() * .1, 105.8),
            90,
            20.0,
            5,
            true,
            Instant.now());
    var batch =
        observations.createBatch(
            user,
            UUID.randomUUID().toString(),
            new CreateBatchRequest("model-v1", 1, "test", List.of(input)));
    observations.complete(user, batch.batchId());
    observations.complete(user, batch.batchId());
    return signs.observations(batch.batchId()).get(0);
  }
}

package com.signa.sign.ai;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.signa.sign.store.LearningRepository;
import com.signa.sign.store.LearningRepository.Job;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class AiWorkerTest {
  private final LearningRepository repo = mock(LearningRepository.class);
  private final AiClient client = mock(AiClient.class);
  private final TransactionTemplate tx = mock(TransactionTemplate.class);
  private final AiWorker worker = new AiWorker(repo, client, tx);
  private final Job job =
      new Job("obs", "crops/a.jpg", "crops/a-sub.jpg", "prohibitory", 1, true, 0, 0);

  @BeforeEach
  void setup() {
    when(repo.dueJobs()).thenReturn(List.of(job));
    when(repo.claim(job)).thenReturn(true);
    doAnswer(
            inv -> {
              Consumer<TransactionStatus> action = inv.getArgument(0);
              action.accept(null);
              return null;
            })
        .when(tx)
        .executeWithoutResult(any());
  }

  @Test
  void completedOcrIsSavedEvenWhenReclassificationNeedsRetry() {
    var result = new ObjectMapper().createObjectNode();
    result.put("reclassifyError", "UNAVAILABLE").put("ocrConfidence", .95);
    result.putObject("conditions").putArray("timeWindows");
    when(client.analyze("obs", "crops/a.jpg", "crops/a-sub.jpg", "prohibitory", 1, true))
        .thenReturn(result);
    worker.run();
    verify(repo)
        .finish(eq(job), anyString(), eq("{\"timeWindows\":[]}"), eq(.95), eq(true), eq(false));
    verify(repo).labelTask("obs", "AI_UNAVAILABLE");
    verify(repo, never()).retry(any(), anyBoolean());
  }

  @Test
  void unclaimedJobNeverCallsAi() {
    when(repo.claim(job)).thenReturn(false);
    worker.run();
    verifyNoInteractions(client);
  }

  @Test
  void fiveExpiredAttemptsGoToReviewWithoutASixthRpc() {
    var expired = new Job("obs", "crops/a.jpg", null, "prohibitory", 1, true, 5, 0);
    when(repo.dueJobs()).thenReturn(List.of(expired));
    worker.run();
    verify(repo).expire(expired);
    verifyNoInteractions(client);
  }

  @Test
  void unexpectedFailureLeavesARetryableJobAndReviewTask() {
    when(client.analyze(anyString(), anyString(), anyString(), anyString(), anyInt(), anyBoolean()))
        .thenThrow(new IllegalStateException("failure"));
    worker.run();
    verify(repo).retry(job, false);
    verify(repo).labelTask("obs", "AI_UNAVAILABLE");
  }
}

package com.signa.sign.ai;

import com.signa.sign.store.LearningRepository;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "signa.ai.enabled", havingValue = "true")
public class AiWorker {
  private static final Logger log = LoggerFactory.getLogger(AiWorker.class);
  private final LearningRepository repo;
  private final AiClient client;
  private final TransactionTemplate tx;

  public AiWorker(LearningRepository repo, AiClient client, TransactionTemplate tx) {
    this.repo = repo;
    this.client = client;
    this.tx = tx;
  }

  @Scheduled(fixedDelayString = "${signa.ai.poll-ms:5000}")
  public void run() {
    for (var job : repo.dueJobs()) {
      if (job.attempts() >= 5) {
        tx.executeWithoutResult(ignored -> repo.expire(job));
        continue;
      }
      if (!repo.claim(job)) continue;
      try {
        var result =
            client.analyze(
                job.id(), job.crop(), job.sub(), job.group(), job.labelMap(), job.hard());
        boolean failed = result.has("reclassifyError") || result.has("ocrError");
        boolean permanent =
            result.path("reclassifyError").asText().equals("INVALID_ARGUMENT")
                || result.path("ocrError").asText().equals("INVALID_ARGUMENT");
        tx.executeWithoutResult(
            ignored -> {
              repo.finish(
                  job,
                  result.toString(),
                  result.has("conditions") ? result.get("conditions").toString() : null,
                  result.path("ocrConfidence").asDouble(),
                  failed,
                  permanent);
              if (job.sub() != null && !result.has("conditions"))
                repo.labelTask(job.id(), "OCR_UNPARSED");
              if (failed) repo.labelTask(job.id(), "AI_UNAVAILABLE");
            });
      } catch (Exception e) {
        boolean permanent =
            e instanceof StatusRuntimeException s
                && s.getStatus().getCode() == Status.Code.INVALID_ARGUMENT;
        tx.executeWithoutResult(
            ignored -> {
              repo.retry(job, permanent);
              repo.labelTask(job.id(), "AI_UNAVAILABLE");
            });
        log.warn(
            "AI analysis {} failed (attempt {}): {}",
            job.id(),
            job.attempts() + 1,
            e.getClass().getSimpleName());
      }
    }
  }
}

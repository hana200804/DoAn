package com.signa.sign.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.signa.ai.v1.CodeProbability;
import com.signa.ai.v1.ReadSubSignRequest;
import com.signa.ai.v1.ReadSubSignResponse;
import com.signa.ai.v1.ReclassifyRequest;
import com.signa.ai.v1.ReclassifyResponse;
import com.signa.ai.v1.SignAnalyzerGrpc;
import com.signa.ai.v1.SubSignConditions;
import com.signa.ai.v1.TimeWindow;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AiClientTest {
  @Test
  void reclassificationAndOcrUseSharedContract() throws Exception {
    String name = InProcessServerBuilder.generateName();
    Server server =
        InProcessServerBuilder.forName(name)
            .directExecutor()
            .addService(
                new SignAnalyzerGrpc.SignAnalyzerImplBase() {
                  @Override
                  public void reclassify(
                      ReclassifyRequest request, StreamObserver<ReclassifyResponse> out) {
                    assertThat(request.getCrop().getBucket()).isEqualTo("sign-crops");
                    assertThat(request.getLabelMapVersion()).isEqualTo(1);
                    out.onNext(
                        ReclassifyResponse.newBuilder()
                            .setModelVersion("teacher-v1")
                            .addTopCodes(
                                CodeProbability.newBuilder().setCode("P.123a").setProbability(0.9f))
                            .build());
                    out.onCompleted();
                  }

                  @Override
                  public void readSubSign(
                      ReadSubSignRequest request, StreamObserver<ReadSubSignResponse> out) {
                    out.onNext(ocr());
                    out.onCompleted();
                  }
                })
            .build()
            .start();
    var client =
        new AiClient(
            InProcessChannelBuilder.forName(name).directExecutor().build(),
            Duration.ofSeconds(1),
            "sign-crops",
            new ObjectMapper());
    try {
      var result =
          client.analyze("request", "crops/a.jpg", "crops/a-sub.jpg", "prohibitory", 1, true);
      assertThat(result.path("topCodes").get(0).path("code").asText()).isEqualTo("P.123a");
      assertThat(result.path("conditions").path("vehicles").path("include").get(0).asText())
          .isEqualTo("TRUCK");
      assertThat(result.path("conditions").path("timeWindows").get(0).path("from").asText())
          .isEqualTo("06:00");
      assertThat(result.has("reclassifyError")).isFalse();
    } finally {
      client.close();
      server.shutdownNow();
    }
  }

  @Test
  void missingClassifierStillAllowsOcrAndIsRecordedForRetry() throws Exception {
    String name = InProcessServerBuilder.generateName();
    Server server =
        InProcessServerBuilder.forName(name)
            .directExecutor()
            .addService(
                new SignAnalyzerGrpc.SignAnalyzerImplBase() {
                  @Override
                  public void reclassify(
                      ReclassifyRequest request, StreamObserver<ReclassifyResponse> out) {
                    out.onError(Status.UNAVAILABLE.asRuntimeException());
                  }

                  @Override
                  public void readSubSign(
                      ReadSubSignRequest request, StreamObserver<ReadSubSignResponse> out) {
                    out.onNext(ocr());
                    out.onCompleted();
                  }
                })
            .build()
            .start();
    var client =
        new AiClient(
            InProcessChannelBuilder.forName(name).directExecutor().build(),
            Duration.ofSeconds(1),
            "sign-crops",
            new ObjectMapper());
    try {
      var result = client.analyze("request", "crops/a.jpg", "crops/a-sub.jpg", null, 1, true);
      assertThat(result.path("reclassifyError").asText()).isEqualTo("UNAVAILABLE");
      assertThat(result.has("conditions")).isTrue();
    } finally {
      client.close();
      server.shutdownNow();
    }
  }

  @Test
  void hungRpcIsBoundedByDeadline() throws Exception {
    String name = InProcessServerBuilder.generateName();
    Server server =
        InProcessServerBuilder.forName(name)
            .directExecutor()
            .addService(
                new SignAnalyzerGrpc.SignAnalyzerImplBase() {
                  @Override
                  public void reclassify(
                      ReclassifyRequest request, StreamObserver<ReclassifyResponse> out) {
                    /* deliberately no response */
                  }
                })
            .build()
            .start();
    var client =
        new AiClient(
            InProcessChannelBuilder.forName(name).directExecutor().build(),
            Duration.ofMillis(50),
            "sign-crops",
            new ObjectMapper());
    try {
      long start = System.nanoTime();
      var result = client.analyze("request", "crops/a.jpg", null, null, 1, true);
      assertThat(result.path("reclassifyError").asText()).isEqualTo("DEADLINE_EXCEEDED");
      assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    } finally {
      client.close();
      server.shutdownNow();
    }
  }

  private static ReadSubSignResponse ocr() {
    return ReadSubSignResponse.newBuilder()
        .setRawText("Xe tải 6h-22h")
        .setOcrConfidence(0.95f)
        .setParsed(true)
        .setConditions(
            SubSignConditions.newBuilder()
                .addIncludeVehicles("TRUCK")
                .addTimeWindows(TimeWindow.newBuilder().setFrom("06:00").setTo("22:00")))
        .build();
  }
}

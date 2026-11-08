package com.signa.sign.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.signa.ai.v1.ImageRef;
import com.signa.ai.v1.ReadSubSignRequest;
import com.signa.ai.v1.ReclassifyRequest;
import com.signa.ai.v1.SignAnalyzerGrpc;
import com.signa.ai.v1.SubSignConditions;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Bounded internal calls; failures are retried by the durable analysis queue. */
@Component
public class AiClient {
  private final ManagedChannel channel;
  private final SignAnalyzerGrpc.SignAnalyzerBlockingStub stub;
  private final String bucket;
  private final Duration timeout;
  private final ObjectMapper mapper;

  @Autowired
  public AiClient(
      @Value("${signa.ai.target:localhost:9090}") String target,
      @Value("${signa.ai.timeout:PT3S}") Duration timeout,
      @Value("${signa.minio.bucket}") String bucket,
      ObjectMapper mapper) {
    this(ManagedChannelBuilder.forTarget(target).usePlaintext().build(), timeout, bucket, mapper);
  }

  AiClient(ManagedChannel channel, Duration timeout, String bucket, ObjectMapper mapper) {
    if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(20)) > 0) {
      throw new IllegalArgumentException("AI timeout must be positive and at most 20 seconds");
    }
    this.channel = channel;
    this.stub = SignAnalyzerGrpc.newBlockingStub(channel);
    this.bucket = bucket;
    this.timeout = timeout;
    this.mapper = mapper;
  }

  public ObjectNode analyze(
      String id, String crop, String sub, String group, int labelMap, boolean hard) {
    ObjectNode result = mapper.createObjectNode();
    // Each capability gets a deadline; a missing classifier must not suppress OCR.
    if (hard && crop != null) {
      try {
        var response =
            stub.withDeadlineAfter(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .reclassify(
                    ReclassifyRequest.newBuilder()
                        .setRequestId(id)
                        .setCrop(image(crop))
                        .setDetectorGroup(group == null ? "" : group)
                        .setLabelMapVersion(labelMap)
                        .build());
        var codes = result.putArray("topCodes");
        for (var code : response.getTopCodesList()) {
          if (!Float.isFinite(code.getProbability())
              || code.getProbability() < 0
              || code.getProbability() > 1) {
            throw new IllegalStateException("Invalid AI probability");
          }
          codes.addObject().put("code", code.getCode()).put("p", code.getProbability());
        }
        result.put("modelVersion", response.getModelVersion());
      } catch (StatusRuntimeException e) {
        result.put("reclassifyError", e.getStatus().getCode().name());
      }
    }
    if (sub != null) {
      try {
        var response =
            stub.withDeadlineAfter(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .readSubSign(
                    ReadSubSignRequest.newBuilder().setRequestId(id).setCrop(image(sub)).build());
        if (!Float.isFinite(response.getOcrConfidence())
            || response.getOcrConfidence() < 0
            || response.getOcrConfidence() > 1)
          throw new IllegalStateException("Invalid OCR confidence");
        result.put("rawText", response.getRawText());
        result.put("ocrConfidence", response.getOcrConfidence());
        result.put("parsed", response.getParsed());
        if (response.getParsed()
            && Float.isFinite(response.getOcrConfidence())
            && response.getOcrConfidence() >= 0.8) {
          var source = response.getConditions();
          validateConditions(source);
          ObjectNode conditions = result.putObject("conditions");
          var windows = conditions.putArray("timeWindows");
          source
              .getTimeWindowsList()
              .forEach(w -> windows.addObject().put("from", w.getFrom()).put("to", w.getTo()));
          var vehicles = conditions.putObject("vehicles");
          source.getIncludeVehiclesList().forEach(vehicles.putArray("include")::add);
          var excludes = vehicles.putArray("exclude");
          source.getExcludeVehiclesList().forEach(excludes::add);
          if (source.hasDistanceAheadM())
            conditions.put("distanceAheadM", source.getDistanceAheadM());
          if (source.hasZoneLengthM()) conditions.put("zoneLengthM", source.getZoneLengthM());
          if (source.hasWeightLimitTon())
            conditions.put("weightLimitTon", source.getWeightLimitTon());
        }
      } catch (StatusRuntimeException e) {
        result.put("ocrError", e.getStatus().getCode().name());
      }
    }
    return result;
  }

  private ImageRef image(String key) {
    return ImageRef.newBuilder().setBucket(bucket).setObjectKey(key).build();
  }

  private static void validateConditions(SubSignConditions source) {
    var vehicles = java.util.Set.of("CAR", "BUS", "TRUCK");
    if (!vehicles.containsAll(source.getIncludeVehiclesList())
        || !vehicles.containsAll(source.getExcludeVehiclesList())) {
      throw new IllegalStateException("Invalid OCR vehicle");
    }
    for (var window : source.getTimeWindowsList()) {
      if (!window.getFrom().matches("([01][0-9]|2[0-3]):[0-5][0-9]")
          || !window.getTo().matches("([01][0-9]|2[0-3]):[0-5][0-9]")) {
        throw new IllegalStateException("Invalid OCR time window");
      }
    }
    if (source.hasDistanceAheadM() && source.getDistanceAheadM() <= 0
        || source.hasZoneLengthM() && source.getZoneLengthM() <= 0
        || source.hasWeightLimitTon()
            && (!Float.isFinite(source.getWeightLimitTon()) || source.getWeightLimitTon() <= 0)) {
      throw new IllegalStateException("Invalid OCR condition");
    }
    if (source.getTimeWindowsCount() == 0
        && source.getIncludeVehiclesCount() == 0
        && source.getExcludeVehiclesCount() == 0
        && !source.hasDistanceAheadM()
        && !source.hasZoneLengthM()
        && !source.hasWeightLimitTon()) {
      throw new IllegalStateException("Parsed OCR must contain conditions");
    }
  }

  @PreDestroy
  public void close() {
    channel.shutdownNow();
  }
}

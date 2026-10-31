package com.signa.sign.api;

import com.signa.common.security.Authenticated;
import com.signa.common.security.CurrentUser;
import com.signa.common.security.SessionPrincipal;
import com.signa.sign.api.SignDtos.CreateBatchRequest;
import com.signa.sign.api.SignDtos.CreateBatchResponse;
import com.signa.sign.app.ObservationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/observations")
@Authenticated
@Validated
public class ObservationController {

  private final ObservationService observations;

  public ObservationController(ObservationService observations) {
    this.observations = observations;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CreateBatchResponse create(
      @CurrentUser SessionPrincipal user,
      @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9-]{8,64}") String key,
      @Valid @RequestBody CreateBatchRequest req) {
    return observations.createBatch(user.userId(), key, req);
  }

  @PostMapping("/{batchId}/complete")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void complete(@CurrentUser SessionPrincipal user, @PathVariable String batchId) {
    observations.complete(user.userId(), batchId);
  }
}

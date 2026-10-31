package com.signa.common.error;

import org.springframework.http.HttpStatus;

/** Mã lỗi dùng chung cho mọi service, khớp với docs/07-api.md §2. */
public enum ErrorCode {
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
  UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
  FORBIDDEN(HttpStatus.FORBIDDEN),
  NOT_FOUND(HttpStatus.NOT_FOUND),
  CONFLICT(HttpStatus.CONFLICT),
  BUSINESS_RULE(HttpStatus.UNPROCESSABLE_ENTITY),
  RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
  UPSTREAM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

  private final HttpStatus status;

  ErrorCode(HttpStatus status) {
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}

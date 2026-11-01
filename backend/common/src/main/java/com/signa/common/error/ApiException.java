package com.signa.common.error;

import java.util.List;

/** Lỗi nghiệp vụ có mã, được GlobalExceptionHandler chuyển thành response chuẩn. */
public class ApiException extends RuntimeException {

  private final ErrorCode code;
  private final transient List<FieldIssue> details;

  public ApiException(ErrorCode code, String message) {
    this(code, message, List.of());
  }

  public ApiException(ErrorCode code, String message, List<FieldIssue> details) {
    super(message);
    this.code = code;
    this.details = details;
  }

  public ErrorCode code() {
    return code;
  }

  public List<FieldIssue> details() {
    return details;
  }

  public static ApiException notFound(String message) {
    return new ApiException(ErrorCode.NOT_FOUND, message);
  }

  public static ApiException conflict(String message) {
    return new ApiException(ErrorCode.CONFLICT, message);
  }

  public static ApiException businessRule(String message) {
    return new ApiException(ErrorCode.BUSINESS_RULE, message);
  }

  public static ApiException unauthenticated(String message) {
    return new ApiException(ErrorCode.UNAUTHENTICATED, message);
  }

  public static ApiException forbidden(String message) {
    return new ApiException(ErrorCode.FORBIDDEN, message);
  }

  public static ApiException rateLimited(String message) {
    return new ApiException(ErrorCode.RATE_LIMITED, message);
  }

  public static ApiException upstream(String message) {
    return new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, message);
  }

  public static ApiException validation(String field, String issue) {
    return new ApiException(
        ErrorCode.VALIDATION_ERROR, "Dữ liệu không hợp lệ", List.of(new FieldIssue(field, issue)));
  }
}

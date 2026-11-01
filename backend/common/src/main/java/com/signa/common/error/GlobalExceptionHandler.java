package com.signa.common.error;

import com.signa.common.web.RequestIds;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Chuyển mọi lỗi thành định dạng {@link ApiError}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiError> handleApi(ApiException ex) {
    return build(ex.code(), ex.getMessage(), ex.details());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> handleInvalidBody(MethodArgumentNotValidException ex) {
    List<FieldIssue> issues =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new FieldIssue(e.getField(), e.getDefaultMessage()))
            .toList();
    return build(ErrorCode.VALIDATION_ERROR, "Dữ liệu không hợp lệ", issues);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex) {
    List<FieldIssue> issues =
        ex.getConstraintViolations().stream()
            .map(v -> new FieldIssue(v.getPropertyPath().toString(), v.getMessage()))
            .toList();
    return build(ErrorCode.VALIDATION_ERROR, "Dữ liệu không hợp lệ", issues);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class,
    MissingRequestHeaderException.class
  })
  ResponseEntity<ApiError> handleBadRequest(Exception ex) {
    return build(ErrorCode.VALIDATION_ERROR, "Request không hợp lệ: " + ex.getMessage(), List.of());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
    return build(ErrorCode.NOT_FOUND, "Không tìm thấy đường dẫn", List.of());
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException ex) {
    return build(ErrorCode.NOT_FOUND, "Phương thức không được hỗ trợ", List.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> handleUnexpected(Exception ex) {
    log.error("Lỗi không lường trước", ex);
    return build(ErrorCode.INTERNAL_ERROR, "Đã xảy ra lỗi, vui lòng thử lại", List.of());
  }

  private ResponseEntity<ApiError> build(ErrorCode code, String message, List<FieldIssue> details) {
    ApiError body = new ApiError(code.name(), message, details, RequestIds.current());
    return ResponseEntity.status(code.status()).body(body);
  }
}

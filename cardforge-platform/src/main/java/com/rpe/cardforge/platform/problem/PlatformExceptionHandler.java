package com.rpe.cardforge.platform.problem;

import com.rpe.cardforge.platform.correlation.CorrelationId;
import com.rpe.cardforge.platform.paging.PageBounds;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Tradução base de erros para {@code application/problem+json}. Tem a menor precedência: os
 * handlers de cada serviço tratam as exceções de negócio antes deste.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class PlatformExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(PlatformExceptionHandler.class);

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<InvalidField> fields =
        ex.getBindingResult().getFieldErrors().stream().map(this::toInvalidField).toList();
    return ResponseEntity.unprocessableEntity().body(Problems.validationFailed(fields));
  }

  @Override
  protected ResponseEntity<Object> handleServletRequestBindingException(
      ServletRequestBindingException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return ResponseEntity.badRequest()
        .body(
            Problems.of(
                HttpStatus.BAD_REQUEST,
                Problems.INVALID_HEADER,
                "Invalid header",
                ex.getMessage()));
  }

  @ExceptionHandler(PageBounds.PageOutOfRangeException.class)
  ResponseEntity<ProblemDetail> handlePageOutOfRange(PageBounds.PageOutOfRangeException ex) {
    return ResponseEntity.badRequest()
        .body(
            Problems.of(
                HttpStatus.BAD_REQUEST,
                Problems.MALFORMED_REQUEST,
                "Page out of range",
                ex.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
    log.error("Unexpected error", ex);
    return ResponseEntity.internalServerError()
        .body(
            Problems.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                Problems.INTERNAL_ERROR,
                "Internal error",
                "Unexpected error. Use the correlationId to trace it."));
  }

  /** Completa os ProblemDetail padrão do Spring com o tipo estável e o correlationId. */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    if (body instanceof ProblemDetail problem) {
      if (problem.getType() == null || "about:blank".equals(problem.getType().toString())) {
        problem.setType(URI.create(Problems.TYPE_BASE + defaultSlug(statusCode)));
      }
      problem.setProperty("correlationId", CorrelationId.current());
    }
    return super.handleExceptionInternal(ex, body, headers, statusCode, request);
  }

  private InvalidField toInvalidField(FieldError error) {
    return new InvalidField(error.getField(), toRule(error.getCode()), error.getDefaultMessage());
  }

  private static String toRule(@Nullable String code) {
    if (code == null) {
      return "INVALID";
    }
    return code.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
  }

  private static String defaultSlug(HttpStatusCode status) {
    return switch (status.value()) {
      case 404 -> Problems.RESOURCE_NOT_FOUND;
      case 405 -> Problems.METHOD_NOT_ALLOWED;
      case 503 -> Problems.DEPENDENCY_UNAVAILABLE;
      default -> status.is4xxClientError() ? Problems.MALFORMED_REQUEST : Problems.INTERNAL_ERROR;
    };
  }
}

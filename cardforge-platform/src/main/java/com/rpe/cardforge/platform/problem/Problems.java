package com.rpe.cardforge.platform.problem;

import com.rpe.cardforge.platform.correlation.CorrelationId;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Fábrica de {@link ProblemDetail} com os tipos estáveis do contrato C6. */
public final class Problems {

  public static final String TYPE_BASE = "https://cardforge.rpe.com.br/problems/";

  public static final String MALFORMED_REQUEST = "malformed-request";
  public static final String INVALID_HEADER = "invalid-header";
  public static final String UNAUTHORIZED = "unauthorized";
  public static final String FORBIDDEN = "forbidden";
  public static final String RESOURCE_NOT_FOUND = "resource-not-found";
  public static final String METHOD_NOT_ALLOWED = "method-not-allowed";
  public static final String VALIDATION_FAILED = "validation-failed";
  public static final String DEPENDENCY_UNAVAILABLE = "dependency-unavailable";
  public static final String INTERNAL_ERROR = "internal-error";

  private Problems() {}

  public static ProblemDetail of(HttpStatusCode status, String slug, String title, String detail) {
    ProblemDetail problem = ProblemDetail.forStatus(status);
    problem.setType(URI.create(TYPE_BASE + slug));
    problem.setTitle(title);
    problem.setDetail(detail);
    problem.setProperty("correlationId", CorrelationId.current());
    return problem;
  }

  public static ProblemDetail validationFailed(List<InvalidField> invalidFields) {
    ProblemDetail problem =
        of(
            HttpStatus.UNPROCESSABLE_ENTITY,
            VALIDATION_FAILED,
            "Validation failed",
            "One or more fields are invalid.");
    problem.setProperty("invalidFields", invalidFields);
    return problem;
  }

  public static ProblemDetail notFound(String detail) {
    return of(HttpStatus.NOT_FOUND, RESOURCE_NOT_FOUND, "Resource not found", detail);
  }
}

package com.rpe.cardforge.product.web;

import com.rpe.cardforge.platform.problem.Problems;
import com.rpe.cardforge.product.application.BinAlreadyRegisteredException;
import com.rpe.cardforge.product.application.ProductNotFoundException;
import com.rpe.cardforge.product.domain.ProductCanceledException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ProductExceptionHandler {

  @ExceptionHandler(BinAlreadyRegisteredException.class)
  ProblemDetail binAlreadyRegistered(BinAlreadyRegisteredException ex) {
    return Problems.of(
        HttpStatus.CONFLICT,
        "bin-already-registered",
        "BIN already registered",
        "Another product already uses this BIN.");
  }

  @ExceptionHandler(ProductNotFoundException.class)
  ProblemDetail notFound(ProductNotFoundException ex) {
    return Problems.notFound(ex.getMessage());
  }

  @ExceptionHandler(ProductUpdate.BinImmutableException.class)
  ProblemDetail binImmutable(ProductUpdate.BinImmutableException ex) {
    return Problems.of(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "bin-immutable",
        "BIN is immutable",
        "Remove the bin field from the update; it is never ignored.");
  }

  @ExceptionHandler(ProductUpdate.InvalidUpdateException.class)
  ProblemDetail invalidUpdate(ProductUpdate.InvalidUpdateException ex) {
    return Problems.validationFailed(ex.fields());
  }

  @ExceptionHandler(ProductUpdate.MalformedUpdateException.class)
  ProblemDetail malformedUpdate(ProductUpdate.MalformedUpdateException ex) {
    return Problems.of(
        HttpStatus.BAD_REQUEST, Problems.MALFORMED_REQUEST, "Malformed request", ex.getMessage());
  }

  @ExceptionHandler(ProductCanceledException.class)
  ProblemDetail canceledReadOnly(ProductCanceledException ex) {
    return Problems.of(
        HttpStatus.CONFLICT,
        "product-canceled-read-only",
        "Product is read-only",
        "A canceled product cannot be changed.");
  }
}

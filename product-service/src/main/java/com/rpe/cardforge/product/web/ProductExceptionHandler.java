package com.rpe.cardforge.product.web;

import com.rpe.cardforge.platform.problem.Problems;
import com.rpe.cardforge.product.application.BinAlreadyRegisteredException;
import com.rpe.cardforge.product.application.ProductNotFoundException;
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
}

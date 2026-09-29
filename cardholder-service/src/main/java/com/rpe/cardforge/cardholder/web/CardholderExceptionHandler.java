package com.rpe.cardforge.cardholder.web;

import com.rpe.cardforge.cardholder.application.CardholderNotFoundException;
import com.rpe.cardforge.cardholder.application.CpfAlreadyRegisteredException;
import com.rpe.cardforge.cardholder.application.ProductRejectedException;
import com.rpe.cardforge.cardholder.domain.CardholderValidationException;
import com.rpe.cardforge.cardholder.domain.InvalidStatusTransitionException;
import com.rpe.cardforge.platform.problem.InvalidField;
import com.rpe.cardforge.platform.problem.Problems;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class CardholderExceptionHandler {

  @ExceptionHandler(CardholderValidationException.class)
  ProblemDetail validation(CardholderValidationException ex) {
    return Problems.validationFailed(
        ex.violations().stream()
            .map(v -> new InvalidField(v.field(), v.rule(), v.message()))
            .toList());
  }

  @ExceptionHandler(CpfAlreadyRegisteredException.class)
  ProblemDetail cpfAlreadyRegistered(CpfAlreadyRegisteredException ex) {
    return Problems.of(
        HttpStatus.CONFLICT,
        "cpf-already-registered",
        "CPF already registered",
        "A cardholder with this CPF already exists. Do not retry.");
  }

  @ExceptionHandler(ProductRejectedException.class)
  ProblemDetail productRejected(ProductRejectedException ex) {
    return ex.canceled()
        ? Problems.of(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "product-canceled",
            "Product canceled",
            "The requested product was discontinued.")
        : Problems.of(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "product-not-found",
            "Product not found",
            "The requested product does not exist.");
  }

  @ExceptionHandler(CardholderNotFoundException.class)
  ProblemDetail notFound(CardholderNotFoundException ex) {
    return Problems.notFound(ex.getMessage());
  }

  @ExceptionHandler(InvalidStatusTransitionException.class)
  ProblemDetail invalidTransition(InvalidStatusTransitionException ex) {
    return Problems.of(
        HttpStatus.CONFLICT,
        "invalid-status-transition",
        "Invalid status transition",
        ex.getMessage());
  }
}

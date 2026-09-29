package com.rpe.cardforge.card.web;

import com.rpe.cardforge.card.application.CardNotFoundException;
import com.rpe.cardforge.platform.problem.Problems;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class CardExceptionHandler {

  @ExceptionHandler(CardNotFoundException.class)
  ProblemDetail notFound(CardNotFoundException ex) {
    return Problems.notFound(ex.getMessage());
  }
}

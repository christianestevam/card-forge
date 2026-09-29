package com.rpe.cardforge.platform.correlation;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/** Identificador de correlação carregado no MDC, em headers HTTP e em atributos SQS. */
public final class CorrelationId {

  public static final String HEADER = "X-Correlation-Id";
  public static final String MDC_KEY = "correlationId";
  public static final String MESSAGE_ATTRIBUTE = "correlationId";

  private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

  private CorrelationId() {}

  /** Valor atual do MDC; gera um novo quando não há contexto (ex.: jobs agendados). */
  public static String current() {
    String value = MDC.get(MDC_KEY);
    return value != null ? value : newId();
  }

  public static String newId() {
    return UUID.randomUUID().toString();
  }

  /** Aceita o valor recebido só se tiver formato seguro para logs; senão gera um novo. */
  public static String sanitize(String candidate) {
    return candidate != null && VALID.matcher(candidate).matches() ? candidate : newId();
  }

  /** Coloca o valor no MDC até o fechamento do escopo, restaurando o anterior. */
  public static Scope open(String candidate) {
    String previous = MDC.get(MDC_KEY);
    MDC.put(MDC_KEY, sanitize(candidate));
    return () -> {
      if (previous == null) {
        MDC.remove(MDC_KEY);
      } else {
        MDC.put(MDC_KEY, previous);
      }
    };
  }

  /** Escopo do MDC; o fechamento não lança exceção. */
  @FunctionalInterface
  public interface Scope extends AutoCloseable {
    @Override
    void close();
  }
}

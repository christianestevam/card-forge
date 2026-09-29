package com.rpe.cardforge.platform.paging;

/**
 * Limite das listagens paginadas: {@code page * size} é calculado em {@code long} e não pode passar
 * do maior offset suportado. Página além disso responde 400 em vez de estourar o inteiro.
 */
public final class PageBounds {

  public static final long MAX_OFFSET = Integer.MAX_VALUE;

  private PageBounds() {}

  public static long offset(int page, int size) {
    long offset = (long) page * size;
    if (offset > MAX_OFFSET) {
      throw new PageOutOfRangeException(page, size);
    }
    return offset;
  }

  /** Combinação de página e tamanho além do offset suportado. */
  public static class PageOutOfRangeException extends RuntimeException {
    public PageOutOfRangeException(int page, int size) {
      super("page " + page + " with size " + size + " is beyond the supported range");
    }
  }
}

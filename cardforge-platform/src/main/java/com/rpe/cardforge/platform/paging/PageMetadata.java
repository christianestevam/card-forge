package com.rpe.cardforge.platform.paging;

/** Metadados de paginação comuns às listagens (C6 {@code PageMetadata}). */
public record PageMetadata(int page, int size, long totalElements, int totalPages) {

  public static PageMetadata of(int page, int size, long totalElements) {
    return new PageMetadata(page, size, totalElements, (int) ((totalElements + size - 1) / size));
  }
}

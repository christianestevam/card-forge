package com.rpe.cardforge.product.domain;

/** Produto CANCELED é somente leitura. */
public class ProductCanceledException extends RuntimeException {

  public ProductCanceledException() {
    super("A canceled product cannot be changed");
  }
}

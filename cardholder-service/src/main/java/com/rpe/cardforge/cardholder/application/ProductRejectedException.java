package com.rpe.cardforge.cardholder.application;

/** O catálogo confirmou que o produto não existe ou está cancelado (BR3.3): nada é criado. */
public class ProductRejectedException extends RuntimeException {

  private final boolean canceled;

  public ProductRejectedException(boolean canceled) {
    super(canceled ? "Product is canceled" : "Product does not exist");
    this.canceled = canceled;
  }

  public boolean canceled() {
    return canceled;
  }
}

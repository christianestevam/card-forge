package com.rpe.cardforge.product.application;

public class BinAlreadyRegisteredException extends RuntimeException {

  public BinAlreadyRegisteredException() {
    super("BIN already registered");
  }
}

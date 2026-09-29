package com.rpe.cardforge.cardholder.application;

public class CpfAlreadyRegisteredException extends RuntimeException {

  public CpfAlreadyRegisteredException() {
    super("CPF already registered");
  }
}

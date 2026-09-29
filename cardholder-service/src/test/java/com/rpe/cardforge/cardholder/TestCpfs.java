package com.rpe.cardforge.cardholder;

import java.util.concurrent.ThreadLocalRandom;

/** Gera CPFs válidos para os testes. */
public final class TestCpfs {

  private TestCpfs() {}

  public static String random() {
    int[] d = new int[11];
    do {
      for (int i = 0; i < 9; i++) {
        d[i] = ThreadLocalRandom.current().nextInt(10);
      }
    } while (allEqual(d));
    d[9] = check(d, 9);
    d[10] = check(d, 10);
    StringBuilder sb = new StringBuilder();
    for (int digit : d) {
      sb.append(digit);
    }
    return sb.toString();
  }

  private static boolean allEqual(int[] d) {
    for (int i = 1; i < 9; i++) {
      if (d[i] != d[0]) {
        return false;
      }
    }
    return true;
  }

  private static int check(int[] d, int length) {
    int sum = 0;
    for (int i = 0; i < length; i++) {
      sum += d[i] * (length + 1 - i);
    }
    int rest = (sum * 10) % 11;
    return rest == 10 ? 0 : rest;
  }
}

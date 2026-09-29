package com.rpe.cardforge.card.infrastructure;

import com.rpe.cardforge.card.application.PanHasher;
import com.rpe.cardforge.card.domain.Pan;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 do PAN com chave dedicada, lida de arquivo montado ({@code ./.local/secrets} no
 * ambiente local). Chave ausente ou fora do formato (Base64, no mínimo 32 bytes) impede a
 * inicialização. A chave nunca é logada.
 */
public class HmacPanHasher implements PanHasher {

  static final String ALGORITHM = "HmacSHA256";
  static final int MIN_KEY_BYTES = 32;

  private final SecretKeySpec key;

  public HmacPanHasher(String base64Key) {
    if (base64Key == null || base64Key.isBlank()) {
      throw new IllegalStateException("PAN HMAC key is missing");
    }
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(base64Key.strip());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("PAN HMAC key is not valid Base64");
    }
    if (bytes.length < MIN_KEY_BYTES) {
      throw new IllegalStateException(
          "PAN HMAC key must have at least " + MIN_KEY_BYTES + " bytes");
    }
    this.key = new SecretKeySpec(bytes, ALGORITHM);
  }

  @Override
  public String hmac(Pan pan) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(key);
      return HexFormat.of().formatHex(mac.doFinal(pan.digits().getBytes(StandardCharsets.US_ASCII)));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("HMAC unavailable", e);
    }
  }
}

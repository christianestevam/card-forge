package com.rpe.cardforge.card.application;

import com.rpe.cardforge.card.domain.Pan;

/** Deriva o identificador de unicidade do PAN (HMAC com chave dedicada). */
public interface PanHasher {

  String hmac(Pan pan);
}

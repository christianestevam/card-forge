package com.rpe.cardforge.cardholder.application;

import java.util.UUID;

/** Recibo de aceite: o cadastro foi gravado e a emissão está rastreável. */
public record RegistrationReceipt(UUID cardholderId, UUID issuanceRequestId) {}

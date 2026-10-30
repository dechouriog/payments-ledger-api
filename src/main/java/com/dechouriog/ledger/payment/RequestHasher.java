package com.dechouriog.ledger.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Huella SHA-256 del cuerpo de la solicitud, para detectar una misma Idempotency-Key con datos distintos. */
final class RequestHasher {

    private RequestHasher() {
    }

    static String hash(PaymentController.CreatePaymentRequest r) {
        String canonical = r.payerAccountId() + "|" + r.payeeAccountId() + "|" + r.amountMinor() + "|" + r.currency();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

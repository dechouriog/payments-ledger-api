package com.dechouriog.ledger.payment;

/** Máquina de estados del pago: solo PENDING puede pasar a COMPLETED o FAILED. */
public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED;

    public boolean canTransitionTo(PaymentStatus next) {
        return this == PENDING && (next == COMPLETED || next == FAILED);
    }

    public boolean isFinal() {
        return this != PENDING;
    }
}

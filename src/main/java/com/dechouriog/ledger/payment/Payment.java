package com.dechouriog.ledger.payment;

import com.dechouriog.ledger.common.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "payer_account_id", nullable = false)
    private UUID payerAccountId;

    @Column(name = "payee_account_id", nullable = false)
    private UUID payeeAccountId;

    /** Monto en unidades mínimas (centavos). Nunca double. */
    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
    }

    public Payment(String idempotencyKey, String requestHash, UUID payerAccountId, UUID payeeAccountId,
                   long amountMinor, String currency) {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor que cero");
        }
        this.id = UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.payerAccountId = payerAccountId;
        this.payeeAccountId = payeeAccountId;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.status = PaymentStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void transitionTo(PaymentStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION",
                    "El pago no puede pasar de " + status + " a " + next);
        }
        this.status = next;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getPayerAccountId() {
        return payerAccountId;
    }

    public UUID getPayeeAccountId() {
        return payeeAccountId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

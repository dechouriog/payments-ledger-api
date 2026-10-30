package com.dechouriog.ledger.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    public record CreatePaymentRequest(
            @NotNull UUID payerAccountId,
            @NotNull UUID payeeAccountId,
            @NotNull @Positive Long amountMinor,
            @NotNull @Pattern(regexp = "[A-Z]{3}", message = "debe ser un código ISO 4217") String currency) {
    }

    public record PaymentResponse(UUID id, UUID payerAccountId, UUID payeeAccountId, long amountMinor,
                                  String currency, PaymentStatus status, Instant createdAt, Instant updatedAt) {
        static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getPayerAccountId(), p.getPayeeAccountId(), p.getAmountMinor(),
                    p.getCurrency(), p.getStatus(), p.getCreatedAt(), p.getUpdatedAt());
        }
    }

    /** 201 la primera vez; 200 con el mismo pago si se repite la misma Idempotency-Key. */
    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest req) {
        PaymentService.CreateResult result = service.create(idempotencyKey, req);
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(PaymentResponse.from(result.payment()));
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable UUID id) {
        return PaymentResponse.from(service.get(id));
    }
}

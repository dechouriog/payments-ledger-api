package com.dechouriog.ledger.payment;

import com.dechouriog.ledger.account.Account;
import com.dechouriog.ledger.account.AccountRepository;
import com.dechouriog.ledger.common.ApiException;
import com.dechouriog.ledger.ledger.LedgerService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentService {

    /** Resultado de crear un pago: {@code replayed} es true si la clave ya se había usado. */
    public record CreateResult(Payment payment, boolean replayed) {
    }

    private final PaymentRepository payments;
    private final AccountRepository accounts;
    private final LedgerService ledger;

    public PaymentService(PaymentRepository payments, AccountRepository accounts, LedgerService ledger) {
        this.payments = payments;
        this.accounts = accounts;
        this.ledger = ledger;
    }

    /**
     * Crea un pago de forma idempotente.
     * <ul>
     *   <li>Clave nueva: crea el pago en PENDING.</li>
     *   <li>Misma clave y mismo cuerpo: devuelve el pago original sin crear otro.</li>
     *   <li>Misma clave y cuerpo distinto: 409, porque es un error del cliente.</li>
     * </ul>
     * La restricción UNIQUE en base de datos cubre el caso de dos solicitudes simultáneas.
     */
    public CreateResult create(String idempotencyKey, PaymentController.CreatePaymentRequest req) {
        if (idempotencyKey.isBlank() || idempotencyKey.length() < 8 || idempotencyKey.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                    "La Idempotency-Key debe tener entre 8 y 100 caracteres");
        }
        String hash = RequestHasher.hash(req);

        var existing = payments.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), hash);
        }

        validateAccounts(req);
        Payment payment = new Payment(idempotencyKey, hash, req.payerAccountId(), req.payeeAccountId(),
                req.amountMinor(), req.currency());
        try {
            return new CreateResult(payments.saveAndFlush(payment), false);
        } catch (DataIntegrityViolationException race) {
            // Otra solicitud con la misma clave se guardó primero
            Payment winner = payments.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> race);
            return replay(winner, hash);
        }
    }

    private CreateResult replay(Payment original, String hash) {
        if (!original.getRequestHash().equals(hash)) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                    "La Idempotency-Key ya se usó con otros datos");
        }
        return new CreateResult(original, true);
    }

    private void validateAccounts(PaymentController.CreatePaymentRequest req) {
        if (req.payerAccountId().equals(req.payeeAccountId())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SAME_ACCOUNT",
                    "El pagador y el beneficiario no pueden ser la misma cuenta");
        }
        Account payer = accounts.findById(req.payerAccountId()).orElseThrow(() -> ApiException.notFound("La cuenta del pagador"));
        Account payee = accounts.findById(req.payeeAccountId()).orElseThrow(() -> ApiException.notFound("La cuenta del beneficiario"));
        if (!payer.getCurrency().equals(req.currency()) || !payee.getCurrency().equals(req.currency())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CURRENCY_MISMATCH",
                    "La moneda del pago no coincide con la de las cuentas");
        }
    }

    @Transactional(readOnly = true)
    public Payment get(UUID id) {
        return payments.findById(id).orElseThrow(() -> ApiException.notFound("El pago"));
    }

    /**
     * Aplica el resultado que informa el proveedor. Un webhook repetido con el mismo estado final
     * no hace nada (idempotente); los asientos del ledger se crean una sola vez.
     */
    @Transactional
    public Payment applyResult(UUID paymentId, PaymentStatus result) {
        Payment p = payments.findByIdForUpdate(paymentId).orElseThrow(() -> ApiException.notFound("El pago"));
        if (p.getStatus() == result) {
            return p;
        }
        p.transitionTo(result);
        if (result == PaymentStatus.COMPLETED) {
            ledger.recordPayment(p);
        }
        return p;
    }
}

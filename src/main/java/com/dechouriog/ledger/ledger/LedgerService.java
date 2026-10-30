package com.dechouriog.ledger.ledger;

import com.dechouriog.ledger.payment.Payment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LedgerService {

    private final LedgerEntryRepository entries;

    public LedgerService(LedgerEntryRepository entries) {
        this.entries = entries;
    }

    /**
     * Registra un pago completado con doble entrada: débito al pagador y crédito al beneficiario
     * por el mismo monto. Debe correr dentro de la transacción que cambia el estado del pago,
     * para que ambos asientos y el cambio de estado se guarden juntos o no se guarde nada.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<LedgerEntry> recordPayment(Payment p) {
        LedgerEntry debit = new LedgerEntry(p.getId(), p.getPayerAccountId(), Direction.DEBIT, p.getAmountMinor(), p.getCurrency());
        LedgerEntry credit = new LedgerEntry(p.getId(), p.getPayeeAccountId(), Direction.CREDIT, p.getAmountMinor(), p.getCurrency());
        return entries.saveAll(List.of(debit, credit));
    }

    @Transactional(readOnly = true)
    public long balanceOf(UUID accountId) {
        return entries.balanceOf(accountId);
    }
}

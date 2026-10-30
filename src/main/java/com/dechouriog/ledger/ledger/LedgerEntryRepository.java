package com.dechouriog.ledger.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    @Query(value = """
            SELECT CAST(COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount_minor ELSE -amount_minor END), 0) AS BIGINT)
            FROM ledger_entries
            WHERE account_id = :accountId
            """, nativeQuery = true)
    long balanceOf(@Param("accountId") UUID accountId);

    /** Suma de créditos menos débitos de todo el ledger. En doble entrada siempre es cero. */
    @Query(value = """
            SELECT CAST(COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount_minor ELSE -amount_minor END), 0) AS BIGINT)
            FROM ledger_entries
            """, nativeQuery = true)
    long globalNet();

    List<LedgerEntry> findByPaymentId(UUID paymentId);
}

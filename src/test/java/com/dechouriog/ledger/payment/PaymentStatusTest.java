package com.dechouriog.ledger.payment;

import com.dechouriog.ledger.common.ApiException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentStatusTest {

    private Payment newPayment() {
        return new Payment("clave-de-prueba", "hash", UUID.randomUUID(), UUID.randomUUID(), 2500, "COP");
    }

    @Test
    void unPagoNuevoEmpiezaEnPending() {
        assertThat(newPayment().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void pendingPuedeCompletarseOFallar() {
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.COMPLETED)).isTrue();
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.FAILED)).isTrue();
    }

    @Test
    void unEstadoFinalNoCambia() {
        Payment p = newPayment();
        p.transitionTo(PaymentStatus.COMPLETED);
        assertThatThrownBy(() -> p.transitionTo(PaymentStatus.FAILED))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("COMPLETED");
    }

    @Test
    void elMontoDebeSerPositivo() {
        assertThatThrownBy(() -> new Payment("clave-de-prueba", "hash", UUID.randomUUID(), UUID.randomUUID(), 0, "COP"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

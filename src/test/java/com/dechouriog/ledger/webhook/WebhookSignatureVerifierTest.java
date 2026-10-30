package com.dechouriog.ledger.webhook;

import com.dechouriog.ledger.common.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureVerifierTest {

    private final WebhookSignatureVerifier verifier =
            new WebhookSignatureVerifier(new AppProperties("secreto-de-prueba", "x".repeat(32), true));

    @Test
    void aceptaLaFirmaCorrecta() {
        String body = "{\"paymentId\":\"abc\",\"status\":\"COMPLETED\"}";
        assertThat(verifier.isValid(body, verifier.sign(body))).isTrue();
    }

    @Test
    void rechazaUnCuerpoAlterado() {
        String firma = verifier.sign("{\"status\":\"FAILED\"}");
        assertThat(verifier.isValid("{\"status\":\"COMPLETED\"}", firma)).isFalse();
    }

    @Test
    void rechazaFirmaVacia() {
        assertThat(verifier.isValid("{}", null)).isFalse();
        assertThat(verifier.isValid("{}", "")).isFalse();
    }
}

package com.dechouriog.ledger;

import com.dechouriog.ledger.ledger.LedgerEntryRepository;
import com.dechouriog.ledger.webhook.WebhookSignatureVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Flujo completo contra un PostgreSQL real levantado con Testcontainers. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PaymentFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    WebhookSignatureVerifier signer;
    @Autowired
    LedgerEntryRepository ledger;

    private String createAccount(String name) throws Exception {
        MvcResult r = mvc.perform(post("/accounts").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"currency\":\"COP\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private String paymentBody(String payer, String payee, long amount) {
        return "{\"payerAccountId\":\"" + payer + "\",\"payeeAccountId\":\"" + payee
                + "\",\"amountMinor\":" + amount + ",\"currency\":\"COP\"}";
    }

    private String sendWebhook(String paymentId, String status) throws Exception {
        String body = "{\"paymentId\":\"" + paymentId + "\",\"status\":\"" + status + "\"}";
        return mvc.perform(post("/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", signer.sign(body))
                        .content(body))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void cobroCompletoConIdempotenciaWebhookYSaldo() throws Exception {
        String cliente = createAccount("Cliente");
        String comercio = createAccount("Comercio");
        String key = "pedido-" + UUID.randomUUID();
        String body = paymentBody(cliente, comercio, 2500000);

        // 1. Primer intento: crea el pago
        MvcResult first = mvc.perform(post("/payments").with(jwt())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        String paymentId = json.readTree(first.getResponse().getContentAsString()).get("id").asText();

        // 2. Reintento con la misma clave: mismo pago, no se crea otro
        mvc.perform(post("/payments").with(jwt())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(paymentId));

        // 3. Misma clave con otro monto: conflicto
        mvc.perform(post("/payments").with(jwt())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(paymentBody(cliente, comercio, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        // 4. El proveedor confirma, dos veces (los webhooks se pueden repetir)
        sendWebhook(paymentId, "COMPLETED");
        JsonNode ack = json.readTree(sendWebhook(paymentId, "COMPLETED"));
        assertThat(ack.get("status").asText()).isEqualTo("COMPLETED");

        // 5. Doble entrada: exactamente dos asientos y el ledger cuadra en cero
        assertThat(ledger.findByPaymentId(UUID.fromString(paymentId))).hasSize(2);
        assertThat(ledger.globalNet()).isZero();

        mvc.perform(get("/accounts/" + comercio + "/balance").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceMinor").value(2500000));
        mvc.perform(get("/accounts/" + cliente + "/balance").with(jwt()))
                .andExpect(jsonPath("$.balanceMinor").value(-2500000));
    }

    @Test
    void webhookConFirmaInvalidaSeRechaza() throws Exception {
        mvc.perform(post("/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", "firma-falsa")
                        .content("{\"paymentId\":\"" + UUID.randomUUID() + "\",\"status\":\"COMPLETED\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
    }

    @Test
    void sinTokenNoSePuedeCrearUnPago() throws Exception {
        mvc.perform(post("/payments")
                        .header("Idempotency-Key", "sin-token-123")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void faltaLaIdempotencyKey() throws Exception {
        mvc.perform(post("/payments").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 100)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
    }

    @Test
    void elTokenDeDesarrolloPermiteUsarLaApi() throws Exception {
        String token = json.readTree(mvc.perform(post("/auth/token"))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .get("accessToken").asText();
        mvc.perform(post("/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Con token\",\"currency\":\"COP\"}"))
                .andExpect(status().isCreated());
    }
}

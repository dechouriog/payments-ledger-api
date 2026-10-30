package com.dechouriog.ledger.webhook;

import com.dechouriog.ledger.common.ApiException;
import com.dechouriog.ledger.payment.Payment;
import com.dechouriog.ledger.payment.PaymentService;
import com.dechouriog.ledger.payment.PaymentStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Recibe la confirmación del proveedor de pagos. No usa JWT: se autentica con la firma HMAC. */
@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    public record PaymentEvent(UUID paymentId, PaymentStatus status) {
    }

    public record WebhookAck(UUID paymentId, PaymentStatus status) {
    }

    private final WebhookSignatureVerifier verifier;
    private final PaymentService payments;
    private final ObjectMapper json;

    public WebhookController(WebhookSignatureVerifier verifier, PaymentService payments, ObjectMapper json) {
        this.verifier = verifier;
        this.payments = payments;
        this.json = json;
    }

    @PostMapping("/payments")
    public WebhookAck paymentEvent(@RequestHeader(value = "X-Signature", required = false) String signature,
                                   @RequestBody String rawBody) {
        // La firma se verifica sobre el texto exacto recibido, antes de interpretarlo
        if (!verifier.isValid(rawBody, signature)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE", "Firma del webhook inválida");
        }
        PaymentEvent event = parse(rawBody);
        if (event.paymentId() == null || event.status() == null || event.status() == PaymentStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EVENT", "El evento debe traer paymentId y status COMPLETED o FAILED");
        }
        Payment p = payments.applyResult(event.paymentId(), event.status());
        return new WebhookAck(p.getId(), p.getStatus());
    }

    private PaymentEvent parse(String rawBody) {
        try {
            return json.readValue(rawBody, PaymentEvent.class);
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EVENT", "El cuerpo del webhook no es JSON válido");
        }
    }
}

package com.dechouriog.ledger.webhook;

import com.dechouriog.ledger.common.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Verifica la firma HMAC-SHA256 que el proveedor calcula sobre el cuerpo exacto del webhook.
 * La comparación es en tiempo constante para no filtrar información por tiempos de respuesta.
 */
@Component
public class WebhookSignatureVerifier {

    private final byte[] secret;

    public WebhookSignatureVerifier(AppProperties props) {
        this.secret = props.webhookSecret().getBytes(StandardCharsets.UTF_8);
    }

    public String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean isValid(String body, String signatureHex) {
        if (signatureHex == null || signatureHex.isBlank()) {
            return false;
        }
        byte[] expected = sign(body).getBytes(StandardCharsets.UTF_8);
        byte[] received = signatureHex.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, received);
    }
}

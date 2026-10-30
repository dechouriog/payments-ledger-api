package com.dechouriog.ledger.security;

import com.dechouriog.ledger.common.ApiException;
import com.dechouriog.ledger.common.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

/**
 * Emite tokens de prueba para la demo. En un sistema real los emitiría un servidor de identidad
 * (Keycloak, Cognito, Auth0) y este endpoint no existiría: se apaga con DEV_TOKEN_ENABLED=false.
 */
@RestController
@RequestMapping("/auth")
public class DevTokenController {

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
    }

    private static final Duration TTL = Duration.ofMinutes(30);

    private final JwtEncoder encoder;
    private final AppProperties props;

    public DevTokenController(JwtEncoder encoder, AppProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    @PostMapping("/token")
    public TokenResponse token() {
        if (!props.devTokenEnabled()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Recurso no disponible");
        }
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("payments-ledger-api")
                .subject("demo-merchant")
                .issuedAt(now)
                .expiresAt(now.plus(TTL))
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new TokenResponse(token, "Bearer", TTL.toSeconds());
    }
}

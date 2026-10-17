package com.dolbaia.ledger.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

/**
 * Signs and verifies the HS256 session token. The claims match the Next.js app
 * ({@code username}, {@code iat}, {@code exp}), so a session issued by either
 * app with the same SESSION_SECRET is accepted by the other.
 */
@Service
public class SessionTokenService {

    private static final String USERNAME_CLAIM = "username";

    private final NimbusJwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final Duration duration;
    private final Clock clock;

    public SessionTokenService(AuthProperties properties, Clock clock) {
        String secret = properties.resolvedSessionSecret();
        if (secret == null || secret.length() < AuthProperties.MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "SESSION_SECRET must contain at least " + AuthProperties.MIN_SECRET_LENGTH + " characters.");
        }
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        this.decoder.setJwtValidator(timestamps);
        this.duration = properties.sessionDuration();
        this.clock = clock;
    }

    public Duration duration() {
        return duration;
    }

    public String create(String username) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(now.plus(duration))
                .claim(USERNAME_CLAIM, username)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** The username in a valid, unexpired token; empty for anything else. */
    public Optional<String> verify(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            Jwt jwt = decoder.decode(token);
            return Optional.ofNullable(jwt.getClaimAsString(USERNAME_CLAIM));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}

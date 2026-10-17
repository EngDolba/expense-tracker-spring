package com.dolbaia.ledger.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.BCryptVersion;

/** Port of auth-core.test.ts "accepts only the configured username and password". */
class CredentialVerifierTest {

    static AuthProperties properties(String hash, String hashBase64) {
        return new AuthProperties("ledger-user", hash, hashBase64,
                "a-test-secret-that-is-longer-than-32-characters", null, Duration.ofDays(14), true);
    }

    @Test
    void acceptsOnlyTheConfiguredUsernameAndPassword() {
        String hash = new BCryptPasswordEncoder(4).encode("correct horse");
        CredentialVerifier verifier = new CredentialVerifier(properties(hash, null));

        assertThat(verifier.verify("ledger-user", "correct horse")).isTrue();
        assertThat(verifier.verify("other", "correct horse")).isFalse();
        assertThat(verifier.verify("ledger-user", "wrong")).isFalse();
    }

    @Test
    void acceptsBcryptjs2bHashes() {
        String hash = new BCryptPasswordEncoder(BCryptVersion.$2B, 4).encode("correct horse");
        assertThat(hash).startsWith("$2b$");

        assertThat(new CredentialVerifier(properties(hash, null)).verify("ledger-user", "correct horse")).isTrue();
    }

    @Test
    void prefersTheBase64EncodedHash() {
        String hash = new BCryptPasswordEncoder(4).encode("correct horse");
        String encoded = Base64.getEncoder().encodeToString(hash.getBytes(StandardCharsets.UTF_8));

        assertThat(new CredentialVerifier(properties("$2a$04$not-the-real-hash", encoded))
                .verify("ledger-user", "correct horse")).isTrue();
    }

    @Test
    void refusesEverythingWhenNothingIsConfigured() {
        CredentialVerifier verifier = new CredentialVerifier(properties(null, null));

        assertThat(verifier.verify("ledger-user", "correct horse")).isFalse();
    }
}

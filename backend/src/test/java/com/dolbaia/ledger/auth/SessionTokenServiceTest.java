package com.dolbaia.ledger.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.dolbaia.ledger.support.MutableClock;

/** Port of auth-core.test.ts "signs and verifies expiring session tokens", plus expiry. */
class SessionTokenServiceTest {

    static final String SECRET = "a-test-secret-that-is-longer-than-32-characters";

    final MutableClock clock = new MutableClock(Instant.parse("2026-10-17T12:00:00Z"));

    static AuthProperties properties(String secret) {
        return new AuthProperties("ledger-user", null, null, secret, null, Duration.ofDays(14), true);
    }

    @Test
    void signsAndVerifiesSessionTokens() {
        SessionTokenService tokens = new SessionTokenService(properties(SECRET), clock);
        String token = tokens.create("ledger-user");

        assertThat(tokens.verify(token)).contains("ledger-user");
        assertThat(tokens.verify(token + "broken")).isEmpty();
        assertThat(tokens.verify("")).isEmpty();
        assertThat(tokens.verify(null)).isEmpty();
    }

    @Test
    void rejectsTokensSignedWithAnotherSecret() {
        String token = new SessionTokenService(properties(SECRET), clock).create("ledger-user");
        SessionTokenService other =
                new SessionTokenService(properties("another-secret-that-is-also-long-enough"), clock);

        assertThat(other.verify(token)).isEmpty();
    }

    @Test
    void tokensExpireAfterTheSessionDuration() {
        SessionTokenService tokens = new SessionTokenService(properties(SECRET), clock);
        String token = tokens.create("ledger-user");

        clock.advance(Duration.ofDays(14).minusMinutes(1));
        assertThat(tokens.verify(token)).contains("ledger-user");

        clock.advance(Duration.ofMinutes(2));
        assertThat(tokens.verify(token)).isEmpty();
    }

    @Test
    void refusesShortSecrets() {
        assertThatThrownBy(() -> new SessionTokenService(properties("too-short"), clock))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SESSION_SECRET");
    }
}

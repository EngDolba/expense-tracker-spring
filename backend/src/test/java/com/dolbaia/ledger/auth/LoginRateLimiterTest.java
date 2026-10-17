package com.dolbaia.ledger.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import com.dolbaia.ledger.common.ApiException;
import com.dolbaia.ledger.support.MutableClock;

class LoginRateLimiterTest {

    final Instant now = Instant.parse("2026-10-17T12:00:00Z");
    final LoginAttemptRepository attempts = mock(LoginAttemptRepository.class);
    final LoginRateLimiter limiter = new LoginRateLimiter(attempts, new MutableClock(now));

    @Test
    void allowsAttemptsBelowTheLimits() {
        when(attempts.countByUsernameAndSucceededFalseAndAttemptedAtAfter(eq("owner"), any()))
                .thenReturn((long) LoginRateLimiter.MAX_FAILURES_PER_USERNAME - 1);
        when(attempts.countByIpAddressAndSucceededFalseAndAttemptedAtAfter(eq("10.0.0.1"), any()))
                .thenReturn((long) LoginRateLimiter.MAX_FAILURES_PER_IP - 1);

        assertThatCode(() -> limiter.check("owner", "10.0.0.1")).doesNotThrowAnyException();
        verify(attempts).countByUsernameAndSucceededFalseAndAttemptedAtAfter(
                "owner", now.minus(LoginRateLimiter.WINDOW));
    }

    @Test
    void blocksAUsernameAfterTooManyFailures() {
        when(attempts.countByUsernameAndSucceededFalseAndAttemptedAtAfter(eq("owner"), any()))
                .thenReturn((long) LoginRateLimiter.MAX_FAILURES_PER_USERNAME);

        assertThatThrownBy(() -> limiter.check("owner", "10.0.0.1"))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(error.getCode()).isEqualTo("too_many_attempts");
                });
    }

    @Test
    void blocksAnAddressAfterTooManyFailures() {
        when(attempts.countByIpAddressAndSucceededFalseAndAttemptedAtAfter(eq("10.0.0.1"), any()))
                .thenReturn((long) LoginRateLimiter.MAX_FAILURES_PER_IP);

        assertThatThrownBy(() -> limiter.check("someone-else", "10.0.0.1")).isInstanceOf(ApiException.class);
    }

    @Test
    void recordsAttemptsAndPrunesOldOnes() {
        limiter.recordFailure("owner", "10.0.0.1");

        ArgumentCaptor<LoginAttempt> saved = ArgumentCaptor.forClass(LoginAttempt.class);
        verify(attempts).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("owner");
        assertThat(saved.getValue().isSucceeded()).isFalse();
        assertThat(saved.getValue().getAttemptedAt()).isEqualTo(now);
        verify(attempts).deleteOlderThan(now.minus(LoginRateLimiter.RETENTION));
    }

    @Test
    void truncatesOverlongUsernames() {
        limiter.recordFailure("x".repeat(500), "10.0.0.1");

        ArgumentCaptor<LoginAttempt> saved = ArgumentCaptor.forClass(LoginAttempt.class);
        verify(attempts).save(saved.capture());
        assertThat(saved.getValue().getUsername()).hasSize(200);
    }
}

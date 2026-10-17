package com.dolbaia.ledger.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dolbaia.ledger.common.ApiException;

/**
 * Limits password guessing: within {@link #WINDOW}, at most
 * {@link #MAX_FAILURES_PER_USERNAME} failures for one username and
 * {@link #MAX_FAILURES_PER_IP} from one address. Failures are logged for
 * alerting. Attempts older than {@link #RETENTION} are pruned.
 */
@Service
public class LoginRateLimiter {

    static final Duration WINDOW = Duration.ofMinutes(15);
    static final Duration RETENTION = Duration.ofDays(30);
    static final int MAX_FAILURES_PER_USERNAME = 5;
    static final int MAX_FAILURES_PER_IP = 20;
    private static final int MAX_STORED_USERNAME = 200;

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    private final LoginAttemptRepository attempts;
    private final Clock clock;

    public LoginRateLimiter(LoginAttemptRepository attempts, Clock clock) {
        this.attempts = attempts;
        this.clock = clock;
    }

    /** Throws 429 when this username or address has failed too often recently. */
    @Transactional(readOnly = true)
    public void check(String username, String ipAddress) {
        Instant since = clock.instant().minus(WINDOW);
        boolean blocked =
                attempts.countByUsernameAndSucceededFalseAndAttemptedAtAfter(stored(username), since)
                        >= MAX_FAILURES_PER_USERNAME
                || attempts.countByIpAddressAndSucceededFalseAndAttemptedAtAfter(ipAddress, since)
                        >= MAX_FAILURES_PER_IP;
        if (blocked) {
            log.warn("Login blocked by rate limit: username={} ip={}", printable(username), ipAddress);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "too_many_attempts",
                    "Too many failed sign-in attempts. Try again in a few minutes.");
        }
    }

    @Transactional
    public void recordFailure(String username, String ipAddress) {
        log.warn("Failed login: username={} ip={}", printable(username), ipAddress);
        record(username, ipAddress, false);
    }

    @Transactional
    public void recordSuccess(String username, String ipAddress) {
        log.info("Successful login: username={} ip={}", printable(username), ipAddress);
        record(username, ipAddress, true);
    }

    private void record(String username, String ipAddress, boolean succeeded) {
        Instant now = clock.instant();
        attempts.deleteOlderThan(now.minus(RETENTION));
        attempts.save(new LoginAttempt(stored(username), ipAddress, succeeded, now));
    }

    private static String stored(String username) {
        String value = username == null ? "" : username;
        return value.length() > MAX_STORED_USERNAME ? value.substring(0, MAX_STORED_USERNAME) : value;
    }

    /** Keeps attacker-chosen usernames from forging log lines. */
    private static String printable(String username) {
        return stored(username).replaceAll("[\\p{Cntrl}]", "?");
    }
}

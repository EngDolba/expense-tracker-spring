package com.dolbaia.ledger.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The single owner's credentials and the session settings, from the same
 * environment variables the Next.js app used (APP_USERNAME, APP_PASSWORD_HASH
 * or APP_PASSWORD_HASH_BASE64, SESSION_SECRET or SESSION_SECRET_BASE64).
 */
@ConfigurationProperties("app.auth")
public record AuthProperties(
        String username,
        String passwordHash,
        String passwordHashBase64,
        String sessionSecret,
        String sessionSecretBase64,
        Duration sessionDuration,
        boolean secureCookie) {

    public static final int MIN_SECRET_LENGTH = 32;

    public AuthProperties {
        if (sessionDuration == null) sessionDuration = Duration.ofDays(14);
    }

    /** The bcrypt hash; the base64 form wins because `$` is awkward in some env files. */
    public String resolvedPasswordHash() {
        return isSet(passwordHashBase64) ? decode(passwordHashBase64) : passwordHash;
    }

    public String resolvedSessionSecret() {
        return isSet(sessionSecretBase64) ? decode(sessionSecretBase64) : sessionSecret;
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value.trim()), StandardCharsets.UTF_8);
    }
}

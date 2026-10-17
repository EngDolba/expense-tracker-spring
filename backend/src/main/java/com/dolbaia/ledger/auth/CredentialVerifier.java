package com.dolbaia.ledger.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Checks a login against the one configured user. Existing `$2b$` hashes made
 * by bcryptjs work unchanged.
 */
@Component
public class CredentialVerifier {

    /** Compared against when the username is wrong, so both paths cost one bcrypt check. */
    private static final String DUMMY_HASH =
            "$2a$12$C6UzMDM.H6dfI/f/IKcEeO5E5n0ZBPm6m1XqU6ZcCXQe0V9hJXq7u";

    private final AuthProperties properties;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public CredentialVerifier(AuthProperties properties) {
        this.properties = properties;
    }

    public boolean verify(String username, String password) {
        String expectedUsername = properties.username();
        String hash = properties.resolvedPasswordHash();
        if (expectedUsername == null || expectedUsername.isBlank() || hash == null || hash.isBlank()
                || username == null || password == null) {
            return false;
        }

        boolean usernameMatches = MessageDigest.isEqual(
                expectedUsername.getBytes(StandardCharsets.UTF_8),
                username.getBytes(StandardCharsets.UTF_8));
        boolean passwordMatches = encoder.matches(password, usernameMatches ? hash : DUMMY_HASH);
        return usernameMatches && passwordMatches;
    }
}

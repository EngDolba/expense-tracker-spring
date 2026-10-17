package com.dolbaia.ledger.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * A JDBC connection parsed from a libpq-style {@code DATABASE_URL}
 * ({@code postgresql://user:password@host:port/db?sslmode=require}), the form
 * Neon, Railway and the old Prisma app use.
 */
public record DatabaseUrl(String jdbcUrl, String username, String password) {

    public static DatabaseUrl parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("DATABASE_URL is empty.");
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("jdbc:")) {
            return new DatabaseUrl(trimmed, null, null);
        }

        URI uri = URI.create(trimmed);
        String scheme = uri.getScheme();
        if (!"postgresql".equals(scheme) && !"postgres".equals(scheme)) {
            throw new IllegalArgumentException(
                    "DATABASE_URL must start with postgresql://, postgres:// or jdbc:.");
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("DATABASE_URL has no host.");
        }

        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
        if (uri.getPort() != -1) jdbc.append(':').append(uri.getPort());
        jdbc.append(uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath());
        if (uri.getRawQuery() != null) jdbc.append('?').append(uri.getRawQuery());

        String username = null;
        String password = null;
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            username = decode(colon < 0 ? userInfo : userInfo.substring(0, colon));
            password = colon < 0 ? null : decode(userInfo.substring(colon + 1));
        }
        return new DatabaseUrl(jdbc.toString(), username, password);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
}

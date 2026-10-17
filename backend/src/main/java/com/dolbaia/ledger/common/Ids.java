package com.dolbaia.ledger.common;

import java.util.UUID;

/** Ids as forms send them: blank or "none" means no selection. */
public final class Ids {

    private Ids() {}

    public static UUID optional(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.equals("none")) return null;
        return parse(trimmed);
    }

    public static UUID required(String value) {
        UUID id = optional(value);
        if (id == null) throw ApiException.badRequest("validation_failed", "A selection is required.");
        return id;
    }

    private static UUID parse(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest("invalid_id", "The selected record does not exist.");
        }
    }
}

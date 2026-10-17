package com.dolbaia.ledger.common;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class Text {

    private Text() {}

    /** Trimmed, with null as empty: optional text fields default to "". */
    public static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    /** "family, travel, family" -> [family, travel]: trimmed, de-duplicated, order kept. */
    public static List<String> tags(String value) {
        Set<String> tags = new LinkedHashSet<>();
        if (value != null) {
            for (String tag : value.split(",")) {
                String trimmed = tag.trim();
                if (!trimmed.isEmpty()) tags.add(trimmed);
            }
        }
        return new ArrayList<>(tags);
    }

    /** Union of two tag lists, order kept. */
    public static List<String> union(List<String> first, List<String> second) {
        Set<String> tags = new LinkedHashSet<>(first);
        tags.addAll(second);
        return new ArrayList<>(tags);
    }
}

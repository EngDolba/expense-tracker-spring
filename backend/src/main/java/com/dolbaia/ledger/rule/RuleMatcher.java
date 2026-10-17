package com.dolbaia.ledger.rule;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.dolbaia.ledger.category.CategoryTree;

/** Keyword categorization: the highest-priority enabled rule whose keyword occurs in recipient or note. */
public final class RuleMatcher {

    private RuleMatcher() {}

    public record Rule(UUID id, String keyword, UUID categoryId, List<String> tags, int priority, boolean enabled) {}

    public static Optional<Rule> match(List<Rule> rules, String recipient, String note) {
        String haystack = (nullToEmpty(recipient) + " " + nullToEmpty(note)).toLowerCase(CategoryTree.GEORGIAN);
        return rules.stream()
                .filter(Rule::enabled)
                .sorted(Comparator.comparingInt(Rule::priority).reversed())
                .filter(rule -> haystack.contains(rule.keyword().toLowerCase(CategoryTree.GEORGIAN).trim()))
                .findFirst();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

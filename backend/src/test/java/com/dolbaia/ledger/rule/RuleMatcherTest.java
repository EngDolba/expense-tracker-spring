package com.dolbaia.ledger.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.dolbaia.ledger.rule.RuleMatcher.Rule;

/** Port of rules.test.ts "categorization rules". */
class RuleMatcherTest {

    final UUID taxi = UUID.randomUUID();
    final UUID delivery = UUID.randomUUID();

    @Test
    void usesCaseInsensitiveMatchingAndHighestPriority() {
        var result = RuleMatcher.match(List.of(
                new Rule(UUID.randomUUID(), "bolt", taxi, List.of(), 1, true),
                new Rule(UUID.randomUUID(), "BOLT FOOD", delivery, List.of("food"), 10, true)),
                "Bolt Food", "Dinner");

        assertThat(result).map(Rule::categoryId).contains(delivery);
    }

    @Test
    void ignoresDisabledRules() {
        assertThat(RuleMatcher.match(List.of(new Rule(UUID.randomUUID(), "merchant", taxi, List.of(), 1, false)),
                "Merchant", "")).isEmpty();
    }

    @Test
    void matchesGeorgianKeywordsInTheNote() {
        assertThat(RuleMatcher.match(List.of(new Rule(UUID.randomUUID(), "ტაქსი", taxi, List.of(), 0, true)),
                "", "ღამის ტაქსი")).isPresent();
    }
}

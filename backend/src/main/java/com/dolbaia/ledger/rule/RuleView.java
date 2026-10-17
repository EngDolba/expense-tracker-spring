package com.dolbaia.ledger.rule;

import java.util.List;
import java.util.UUID;

import com.dolbaia.ledger.category.CategoryView;

public record RuleView(UUID id, String keyword, UUID categoryId, CategoryView category, List<String> tags,
                       int priority, boolean enabled) {

    public static RuleView of(CategorizationRule rule) {
        CategoryView category = CategoryView.of(rule.getCategory());
        return new RuleView(rule.getId(), rule.getKeyword(), category == null ? null : category.id(), category,
                List.copyOf(rule.getTags()), rule.getPriority(), rule.isEnabled());
    }
}

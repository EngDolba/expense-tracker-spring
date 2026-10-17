package com.dolbaia.ledger.rule;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import com.dolbaia.ledger.category.Category;
import com.dolbaia.ledger.common.BaseEntity;

/** Categorises a new expense that has no category when the keyword appears in its recipient or note. */
@Entity
@Table(name = "categorization_rule")
public class CategorizationRule extends BaseEntity {

    @Column(nullable = false)
    private String keyword;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ElementCollection
    @CollectionTable(name = "categorization_rule_tag", joinColumns = @JoinColumn(name = "categorization_rule_id"))
    @OrderColumn(name = "position")
    @Column(name = "tag", nullable = false)
    private List<String> tags = new ArrayList<>();

    @Column(nullable = false)
    private int priority;

    @Column(nullable = false)
    private boolean enabled = true;

    protected CategorizationRule() {}

    public CategorizationRule(String keyword, Category category, List<String> tags, int priority) {
        this.keyword = keyword;
        this.category = category;
        this.tags = new ArrayList<>(tags);
        this.priority = priority;
    }

    public String getKeyword() { return keyword; }
    public Category getCategory() { return category; }
    public List<String> getTags() { return tags; }
    public int getPriority() { return priority; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public RuleMatcher.Rule toRule() {
        return new RuleMatcher.Rule(getId(), keyword, category == null ? null : category.getId(),
                List.copyOf(tags), priority, enabled);
    }
}

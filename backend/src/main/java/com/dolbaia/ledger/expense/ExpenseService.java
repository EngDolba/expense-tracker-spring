package com.dolbaia.ledger.expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dolbaia.ledger.category.CategoryService;
import com.dolbaia.ledger.category.CategoryView;
import com.dolbaia.ledger.common.ApiException;
import com.dolbaia.ledger.common.Text;
import com.dolbaia.ledger.rule.CategorizationRule;
import com.dolbaia.ledger.rule.CategorizationRuleRepository;
import com.dolbaia.ledger.rule.RuleMatcher;
import com.dolbaia.ledger.rule.RuleView;

/** The personal expense and income ledger. */
@Service
public class ExpenseService {

    /** The register shows the latest entries only. */
    static final int REGISTER_SIZE = 250;

    public record ExpensesPage(List<ExpenseView> entries, List<CategoryView> categories, List<RuleView> rules) {}

    public record NewExpense(ExpenseType type, BigDecimal amount, LocalDate date, UUID categoryId,
                             String recipient, String note, String sourceCode, List<String> tags) {}

    private final ExpenseEntryRepository entries;
    private final CategorizationRuleRepository rules;
    private final CategoryService categories;

    public ExpenseService(ExpenseEntryRepository entries, CategorizationRuleRepository rules,
                          CategoryService categories) {
        this.entries = entries;
        this.rules = rules;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public ExpensesPage page() {
        return new ExpensesPage(
                entries.findLatest(Limit.of(REGISTER_SIZE)).stream().map(ExpenseView::of).toList(),
                categories.active(),
                rules.findAllOrdered().stream().map(RuleView::of).toList());
    }

    /**
     * Records an entry. Without a category, the highest-priority matching rule
     * supplies the category and adds its tags.
     */
    @Transactional
    public ExpenseView create(NewExpense form) {
        UUID categoryId = form.categoryId();
        List<String> tags = form.tags();
        if (categoryId == null) {
            Optional<RuleMatcher.Rule> rule = RuleMatcher.match(
                    rules.findAll().stream().map(CategorizationRule::toRule).toList(),
                    form.recipient(), form.note());
            categoryId = rule.map(RuleMatcher.Rule::categoryId).orElse(null);
            tags = Text.union(tags, rule.map(RuleMatcher.Rule::tags).orElse(List.of()));
        }

        ExpenseEntry entry = new ExpenseEntry(form.type(), form.amount(), form.date());
        entry.setCategory(categories.findOptional(categoryId));
        entry.setRecipient(form.recipient());
        entry.setNote(form.note());
        entry.setSourceCode(form.sourceCode());
        entry.setTags(tags);
        return ExpenseView.of(entries.save(entry));
    }

    @Transactional
    public void delete(UUID id) {
        ExpenseEntry entry = entries.findById(id)
                .orElseThrow(() -> ApiException.notFound("expense_not_found", "The entry no longer exists."));
        entries.delete(entry);
    }
}

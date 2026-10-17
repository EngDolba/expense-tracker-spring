package com.dolbaia.ledger.rule;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dolbaia.ledger.category.CategoryService;
import com.dolbaia.ledger.common.ApiException;
import com.dolbaia.ledger.common.Ids;
import com.dolbaia.ledger.common.Text;

/** Keyword rules; they are listed on the expenses page. */
@RestController
@RequestMapping("/api/rules")
public class RuleController {

    public record RuleForm(
            @NotBlank @Size(max = 120) String keyword,
            String categoryId,
            @Min(-1000) @Max(1000) Integer priority,
            String tags) {}

    private final CategorizationRuleRepository rules;
    private final CategoryService categories;

    public RuleController(CategorizationRuleRepository rules, CategoryService categories) {
        this.rules = rules;
        this.categories = categories;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RuleView create(@Valid @RequestBody RuleForm form) {
        CategorizationRule rule = new CategorizationRule(form.keyword().trim(),
                categories.findOptional(Ids.optional(form.categoryId())), Text.tags(form.tags()),
                form.priority() == null ? 0 : form.priority());
        return RuleView.of(rules.save(rule));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable UUID id) {
        rules.delete(rules.findById(id)
                .orElseThrow(() -> ApiException.notFound("rule_not_found", "The rule no longer exists.")));
    }
}

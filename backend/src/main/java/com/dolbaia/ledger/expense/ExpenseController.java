package com.dolbaia.ledger.expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dolbaia.ledger.common.Ids;
import com.dolbaia.ledger.common.Text;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    public record ExpenseForm(
            @NotNull ExpenseType type,
            @NotNull @Positive @Digits(integer = 16, fraction = 2) BigDecimal amount,
            @NotNull LocalDate date,
            String categoryId,
            @Size(max = 200) String recipient,
            @Size(max = 2000) String note,
            @Size(max = 50) String sourceCode,
            String tags) {}

    private final ExpenseService service;

    public ExpenseController(ExpenseService service) {
        this.service = service;
    }

    @GetMapping
    ExpenseService.ExpensesPage page() {
        return service.page();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ExpenseView create(@Valid @RequestBody ExpenseForm form) {
        String sourceCode = Text.clean(form.sourceCode());
        return service.create(new ExpenseService.NewExpense(form.type(), form.amount(), form.date(),
                Ids.optional(form.categoryId()), Text.clean(form.recipient()), Text.clean(form.note()),
                sourceCode.isEmpty() ? null : sourceCode, Text.tags(form.tags())));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}

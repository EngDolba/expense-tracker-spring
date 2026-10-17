package com.dolbaia.ledger.expense;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.dolbaia.ledger.category.CategoryView;

public record ExpenseView(UUID id, ExpenseType type, BigDecimal amount, LocalDate date, UUID categoryId,
                          CategoryView category, String recipient, String note, List<String> tags,
                          String sourceCode, UUID importBatchId, Integer sourceRow, Instant createdAt,
                          Instant updatedAt) {

    public static ExpenseView of(ExpenseEntry entry) {
        CategoryView category = CategoryView.of(entry.getCategory());
        return new ExpenseView(entry.getId(), entry.getType(), entry.getAmount(), entry.getDate(),
                category == null ? null : category.id(), category, entry.getRecipient(), entry.getNote(),
                List.copyOf(entry.getTags()), entry.getSourceCode(),
                entry.getImportBatch() == null ? null : entry.getImportBatch().getId(), entry.getSourceRow(),
                entry.getCreatedAt(), entry.getUpdatedAt());
    }
}

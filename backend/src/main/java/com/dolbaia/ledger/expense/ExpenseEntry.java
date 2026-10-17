package com.dolbaia.ledger.expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import com.dolbaia.ledger.category.Category;
import com.dolbaia.ledger.common.BaseEntity;
import com.dolbaia.ledger.imports.ImportBatch;

/** A personal expense or income. Never touches an account balance or an obligation. */
@Entity
@Table(name = "expense_entry")
public class ExpenseEntry extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ExpenseType type;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "entry_date", nullable = false)
    private LocalDate date;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    private String recipient = "";

    @Column(nullable = false)
    private String note = "";

    @ElementCollection
    @CollectionTable(name = "expense_entry_tag", joinColumns = @JoinColumn(name = "expense_entry_id"))
    @OrderColumn(name = "position")
    @Column(name = "tag", nullable = false)
    private List<String> tags = new ArrayList<>();

    @Column(name = "source_code")
    private String sourceCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id")
    private ImportBatch importBatch;

    @Column(name = "source_row")
    private Integer sourceRow;

    protected ExpenseEntry() {}

    public ExpenseEntry(ExpenseType type, BigDecimal amount, LocalDate date) {
        this.type = type;
        this.amount = amount;
        this.date = date;
    }

    public ExpenseType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getDate() { return date; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = new ArrayList<>(tags); }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public ImportBatch getImportBatch() { return importBatch; }
    public void setImportBatch(ImportBatch importBatch) { this.importBatch = importBatch; }
    public Integer getSourceRow() { return sourceRow; }
    public void setSourceRow(Integer sourceRow) { this.sourceRow = sourceRow; }
}

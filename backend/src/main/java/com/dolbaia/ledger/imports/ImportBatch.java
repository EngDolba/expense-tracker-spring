package com.dolbaia.ledger.imports;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dolbaia.ledger.common.BaseEntity;

/** One uploaded workbook, staged for review and then committed as expense entries at once. */
@Entity
@Table(name = "import_batch")
public class ImportBatch extends BaseEntity {

    /** SHA-256 of the file: the same workbook cannot be imported twice. */
    @Column(name = "file_hash", nullable = false, unique = true, length = 64)
    private String fileHash;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ImportStatus status = ImportStatus.STAGED;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "expense_rows", nullable = false)
    private int expenseRows;

    @Column(name = "income_rows", nullable = false)
    private int incomeRows;

    @Column(name = "error_rows", nullable = false)
    private int errorRows;

    @Column(name = "mapping_count", nullable = false)
    private int mappingCount;

    @Column(name = "source_sheet")
    private String sourceSheet;

    /** The workbook's code-to-category dictionary, as JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String dictionary;

    @Column(name = "committed_at")
    private Instant committedAt;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rowNumber")
    private List<ImportRow> rows = new ArrayList<>();

    protected ImportBatch() {}

    public ImportBatch(String fileHash, String fileName) {
        this.fileHash = fileHash;
        this.fileName = fileName;
    }

    public void setSummary(WorkbookParser.Summary summary) {
        this.totalRows = summary.totalRows();
        this.expenseRows = summary.expenseRows();
        this.incomeRows = summary.incomeRows();
        this.errorRows = summary.errorRows();
        this.mappingCount = summary.mappingCount();
    }

    public void addRow(ImportRow row) {
        rows.add(row);
    }

    public void markCommitted(Instant at) {
        this.status = ImportStatus.COMMITTED;
        this.committedAt = at;
    }

    public String getFileHash() { return fileHash; }
    public String getFileName() { return fileName; }
    public ImportStatus getStatus() { return status; }
    public int getTotalRows() { return totalRows; }
    public int getExpenseRows() { return expenseRows; }
    public int getIncomeRows() { return incomeRows; }
    public int getErrorRows() { return errorRows; }
    public int getMappingCount() { return mappingCount; }
    public String getSourceSheet() { return sourceSheet; }
    public void setSourceSheet(String sourceSheet) { this.sourceSheet = sourceSheet; }
    public String getDictionary() { return dictionary; }
    public void setDictionary(String dictionary) { this.dictionary = dictionary; }
    public Instant getCommittedAt() { return committedAt; }
    public List<ImportRow> getRows() { return rows; }
}

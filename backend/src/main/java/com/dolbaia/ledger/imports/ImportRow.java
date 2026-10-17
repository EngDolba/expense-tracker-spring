package com.dolbaia.ledger.imports;

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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dolbaia.ledger.common.BaseEntity;

/** One parsed workbook row, as JSON, with the problems that block committing it. */
@Entity
@Table(name = "import_row")
public class ImportRow extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private ImportBatch batch;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String normalized;

    @ElementCollection
    @CollectionTable(name = "import_row_error", joinColumns = @JoinColumn(name = "import_row_id"))
    @OrderColumn(name = "position")
    @Column(name = "message", nullable = false)
    private List<String> errors = new ArrayList<>();

    protected ImportRow() {}

    public ImportRow(ImportBatch batch, int rowNumber, String normalized, List<String> errors) {
        this.batch = batch;
        this.rowNumber = rowNumber;
        this.normalized = normalized;
        this.errors = new ArrayList<>(errors);
    }

    public ImportBatch getBatch() { return batch; }
    public int getRowNumber() { return rowNumber; }
    public String getNormalized() { return normalized; }
    public List<String> getErrors() { return errors; }
}

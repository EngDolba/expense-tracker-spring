package com.dolbaia.ledger.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.dolbaia.ledger.common.BaseEntity;

/** An XLDR workbook category code, mapped to a category on import. */
@Entity
@Table(name = "category_code")
public class CategoryCode extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    protected CategoryCode() {}

    public CategoryCode(String code, Category category) {
        this.code = code;
        this.category = category;
    }

    public String getCode() { return code; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
}

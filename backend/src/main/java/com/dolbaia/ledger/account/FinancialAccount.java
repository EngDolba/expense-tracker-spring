package com.dolbaia.ledger.account;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.dolbaia.ledger.common.BaseEntity;

/** A bank, cash or wallet account. Its balance is the opening balance plus its own events only. */
@Entity
@Table(name = "financial_account")
public class FinancialAccount extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String description = "";

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "opening_balance", nullable = false, precision = 18, scale = 2)
    private BigDecimal openingBalance;

    @Column(nullable = false)
    private boolean archived;

    protected FinancialAccount() {}

    public FinancialAccount(String name, String description, String currency, BigDecimal openingBalance) {
        this.name = name;
        this.description = description;
        this.currency = currency;
        this.openingBalance = openingBalance;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCurrency() { return currency; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
}

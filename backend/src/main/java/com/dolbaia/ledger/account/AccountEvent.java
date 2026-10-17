package com.dolbaia.ledger.account;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.dolbaia.ledger.common.BaseEntity;

/**
 * One movement on an account. A transfer is two events, TRANSFER_OUT and
 * TRANSFER_IN, sharing a transfer group id (amounts can differ across currencies).
 */
@Entity
@Table(name = "account_event")
public class AccountEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private FinancialAccount account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterparty_account_id")
    private FinancialAccount counterpartyAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountEventType type;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "event_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private String note = "";

    @Column(name = "transfer_group_id")
    private UUID transferGroupId;

    protected AccountEvent() {}

    public AccountEvent(FinancialAccount account, AccountEventType type, BigDecimal amount, LocalDate date,
                        String note) {
        this.account = account;
        this.type = type;
        this.amount = amount;
        this.date = date;
        this.note = note;
    }

    public FinancialAccount getAccount() { return account; }
    public FinancialAccount getCounterpartyAccount() { return counterpartyAccount; }
    public void setCounterpartyAccount(FinancialAccount counterpartyAccount) {
        this.counterpartyAccount = counterpartyAccount;
    }
    public AccountEventType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getDate() { return date; }
    public String getNote() { return note; }
    public UUID getTransferGroupId() { return transferGroupId; }
    public void setTransferGroupId(UUID transferGroupId) { this.transferGroupId = transferGroupId; }
}

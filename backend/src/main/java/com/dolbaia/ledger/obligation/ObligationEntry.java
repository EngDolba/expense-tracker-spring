package com.dolbaia.ledger.obligation;

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

/** An outlay for, or a reimbursement from, a party. Never creates an expense or account event. */
@Entity
@Table(name = "obligation_entry")
public class ObligationEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false)
    private ObligationParty party;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id")
    private ObligationCase obligationCase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ObligationType type;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "entry_date", nullable = false)
    private LocalDate date;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    private String note = "";

    @ElementCollection
    @CollectionTable(name = "obligation_entry_tag", joinColumns = @JoinColumn(name = "obligation_entry_id"))
    @OrderColumn(name = "position")
    @Column(name = "tag", nullable = false)
    private List<String> tags = new ArrayList<>();

    protected ObligationEntry() {}

    public ObligationEntry(ObligationParty party, ObligationCase obligationCase, ObligationType type,
                           BigDecimal amount, LocalDate date) {
        this.party = party;
        this.obligationCase = obligationCase;
        this.type = type;
        this.amount = amount;
        this.date = date;
    }

    public ObligationParty getParty() { return party; }
    public ObligationCase getObligationCase() { return obligationCase; }
    public ObligationType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getDate() { return date; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = new ArrayList<>(tags); }
}

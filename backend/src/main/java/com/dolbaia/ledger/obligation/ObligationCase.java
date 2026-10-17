package com.dolbaia.ledger.obligation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.dolbaia.ledger.common.BaseEntity;

/** An optional grouping of one party's entries, for example one trip or project. */
@Entity
@Table(name = "obligation_case")
public class ObligationCase extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false)
    private ObligationParty party;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String note = "";

    @Column(nullable = false)
    private boolean closed;

    protected ObligationCase() {}

    public ObligationCase(ObligationParty party, String name, String note) {
        this.party = party;
        this.name = name;
        this.note = note;
    }

    public ObligationParty getParty() { return party; }
    public String getName() { return name; }
    public String getNote() { return note; }
    public boolean isClosed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }
}

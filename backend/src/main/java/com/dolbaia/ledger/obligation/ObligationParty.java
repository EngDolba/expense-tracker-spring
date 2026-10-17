package com.dolbaia.ledger.obligation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.dolbaia.ledger.common.BaseEntity;

/** A company or person money is advanced for or owed by. */
@Entity
@Table(name = "obligation_party")
public class ObligationParty extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String note = "";

    @Column(nullable = false)
    private boolean archived;

    protected ObligationParty() {}

    public ObligationParty(String name, String note) {
        this.name = name;
        this.note = note;
    }

    public String getName() { return name; }
    public String getNote() { return note; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
}

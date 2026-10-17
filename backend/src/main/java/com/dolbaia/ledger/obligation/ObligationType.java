package com.dolbaia.ledger.obligation;

public enum ObligationType {
    /** Money advanced on the party's behalf: raises what they owe. */
    OUTLAY,
    /** Money the party paid back: lowers what they owe. */
    REIMBURSEMENT
}

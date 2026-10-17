package com.dolbaia.ledger.account;

public enum AccountEventType {
    INCREASE,
    DECREASE,
    TRANSFER_IN,
    TRANSFER_OUT,
    /** Signed: a reconciliation can move the balance either way. */
    ADJUSTMENT
}

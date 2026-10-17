package com.dolbaia.ledger.common;

import java.math.BigDecimal;
import java.util.Collection;

import com.dolbaia.ledger.account.AccountEventType;
import com.dolbaia.ledger.obligation.ObligationType;

/**
 * The accounting formulas, in exact decimals. Each ledger is computed from its
 * own rows only: an account from its opening balance and its events, an
 * obligation from its outlays and reimbursements.
 */
public final class Money {

    private Money() {}

    public record Movement<T>(T type, BigDecimal amount) {}

    /** Positive types add, negative types subtract, ADJUSTMENT keeps its sign. */
    public static BigDecimal accountEventImpact(AccountEventType type, BigDecimal amount) {
        BigDecimal value = orZero(amount);
        return switch (type) {
            case INCREASE, TRANSFER_IN -> value.abs();
            case DECREASE, TRANSFER_OUT -> value.abs().negate();
            case ADJUSTMENT -> value;
        };
    }

    public static BigDecimal accountBalance(BigDecimal openingBalance,
                                            Collection<Movement<AccountEventType>> events) {
        BigDecimal total = orZero(openingBalance);
        for (Movement<AccountEventType> event : events) {
            total = total.add(accountEventImpact(event.type(), event.amount()));
        }
        return total;
    }

    /** What the party still owes: outlays minus reimbursements. */
    public static BigDecimal obligationOutstanding(Collection<Movement<ObligationType>> entries) {
        BigDecimal total = BigDecimal.ZERO;
        for (Movement<ObligationType> entry : entries) {
            BigDecimal amount = orZero(entry.amount()).abs();
            total = entry.type() == ObligationType.OUTLAY ? total.add(amount) : total.subtract(amount);
        }
        return total;
    }

    /** The GEL value of a balance, or null when no exchange rate is known. */
    public static BigDecimal convertToGel(BigDecimal balance, BigDecimal gelRate) {
        if (gelRate == null) return null;
        return orZero(balance).multiply(gelRate);
    }

    public static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

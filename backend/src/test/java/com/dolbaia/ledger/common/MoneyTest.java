package com.dolbaia.ledger.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.dolbaia.ledger.account.AccountEventType;
import com.dolbaia.ledger.common.Money.Movement;
import com.dolbaia.ledger.obligation.ObligationType;

/** Port of money.test.ts "isolated ledger calculations". */
class MoneyTest {

    static <T> Movement<T> m(T type, String amount) {
        return new Movement<>(type, new BigDecimal(amount));
    }

    @Test
    void calculatesTheObligationExampleWithoutAnotherLedger() {
        assertThat(Money.obligationOutstanding(List.of(
                m(ObligationType.OUTLAY, "10"),
                m(ObligationType.OUTLAY, "20"),
                m(ObligationType.REIMBURSEMENT, "25"))))
                .isEqualByComparingTo("5");
    }

    @Test
    void calculatesAccountBalancesFromOpeningBalanceAndEvents() {
        assertThat(Money.accountBalance(new BigDecimal("100"), List.of(
                m(AccountEventType.INCREASE, "20"),
                m(AccountEventType.DECREASE, "5"),
                m(AccountEventType.TRANSFER_OUT, "15"),
                m(AccountEventType.ADJUSTMENT, "-2"))))
                .isEqualByComparingTo("98");
    }

    @Test
    void signedTypesIgnoreTheSignTheyWereStoredWith() {
        assertThat(Money.accountEventImpact(AccountEventType.DECREASE, new BigDecimal("-5")))
                .isEqualByComparingTo("-5");
        assertThat(Money.accountEventImpact(AccountEventType.TRANSFER_IN, new BigDecimal("-5")))
                .isEqualByComparingTo("5");
    }

    @Test
    void convertsOnlyWhenAnExchangeRateIsAvailable() {
        assertThat(Money.convertToGel(new BigDecimal("100"), new BigDecimal("2.7"))).isEqualByComparingTo("270");
        assertThat(Money.convertToGel(new BigDecimal("100"), null)).isNull();
    }

    @Test
    void staysExactWhereFloatingPointWouldDrift() {
        assertThat(Money.accountBalance(new BigDecimal("0.10"), List.of(m(AccountEventType.INCREASE, "0.20"))))
                .isEqualByComparingTo("0.30");
    }
}

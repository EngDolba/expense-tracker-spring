package com.dolbaia.ledger.account;

import static com.dolbaia.ledger.support.Api.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.dolbaia.ledger.support.Api;
import com.dolbaia.ledger.support.DatabaseTest;

class AccountsIT extends DatabaseTest {

    String account(Api api, String name, String currency, Object opening) throws Exception {
        return api.create("/api/accounts", body("name", name, "currency", currency, "openingBalance", opening), "$.id");
    }

    @Test
    void theBalanceIsTheOpeningBalancePlusItsEvents() throws Exception {
        Api api = api();
        String wallet = account(api, "Wallet", " gel ", 100);
        api.post("/api/accounts/events", body("accountId", wallet, "type", "INCREASE", "amount", 20, "date", "2026-10-01"));
        api.post("/api/accounts/events", body("accountId", wallet, "type", "DECREASE", "amount", 5, "date", "2026-10-02"));
        api.post("/api/accounts/events", body("accountId", wallet, "type", "ADJUSTMENT", "amount", -2, "date", "2026-10-03"))
                .andExpect(status().isCreated());

        api.get("/api/accounts")
                .andExpect(jsonPath("$.accounts[0].currency").value("GEL"))
                .andExpect(jsonPath("$.accounts[0].balance").value(113))
                .andExpect(jsonPath("$.accounts[0].events", hasSize(3)))
                .andExpect(jsonPath("$.accounts[0].events[0].date").value("2026-10-03"));
    }

    @Test
    void onlyAdjustmentsMayBeNonPositive() throws Exception {
        Api api = api();
        String wallet = account(api, "Wallet", "GEL", 0);
        api.post("/api/accounts/events", body("accountId", wallet, "type", "DECREASE", "amount", -5, "date", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("amount_not_positive"));
        api.post("/api/accounts/events", body("accountId", wallet, "type", "TRANSFER_IN", "amount", 5, "date", "2026-10-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aTransferIsTwoLinkedEvents() throws Exception {
        Api api = api();
        String gel = account(api, "Bank GEL", "GEL", 1000);
        String usd = account(api, "Bank USD", "USD", 0);

        String group = api.create("/api/accounts/transfers", body("fromAccountId", gel, "toAccountId", usd,
                "fromAmount", 270, "toAmount", 100, "date", "2026-10-01", "note", "FX"), "$.transferGroupId");

        assertThat(jdbc.queryForObject("select count(*) from account_event where transfer_group_id = ?::uuid",
                Integer.class, group)).isEqualTo(2);
        api.get("/api/accounts")
                .andExpect(jsonPath("$.accounts[0].name").value("Bank GEL"))
                .andExpect(jsonPath("$.accounts[0].balance").value(730))
                .andExpect(jsonPath("$.accounts[1].balance").value(100))
                .andExpect(jsonPath("$.accounts[1].events[0].counterpartyAccountId").value(gel));

        api.post("/api/accounts/transfers", body("fromAccountId", gel, "toAccountId", gel,
                        "fromAmount", 1, "toAmount", 1, "date", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("same_account"));
    }

    @Test
    void reconcileBooksTheDifferenceOnlyWhenThereIsOne() throws Exception {
        Api api = api();
        String wallet = account(api, "Wallet", "GEL", "100.00");

        api.post("/api/accounts/reconcile", body("accountId", wallet, "actualBalance", "100.004", "date", "2026-10-01"))
                .andExpect(status().isBadRequest());
        api.post("/api/accounts/reconcile", body("accountId", wallet, "actualBalance", "100.00", "date", "2026-10-01"))
                .andExpect(jsonPath("$.delta").value(0));
        api.post("/api/accounts/reconcile", body("accountId", wallet, "actualBalance", "92.50", "date", "2026-10-01"))
                .andExpect(jsonPath("$.delta").value(-7.5));

        assertThat(jdbc.queryForObject("select note from account_event", String.class))
                .isEqualTo("Balance reconciliation");
        api.get("/api/accounts").andExpect(jsonPath("$.accounts[0].balance").value(92.5));
    }

    @Test
    void exchangeRatesAreUpsertedPerCurrencyAndDate() throws Exception {
        Api api = api();
        api.put("/api/exchange-rates", body("currency", "usd", "date", "2026-10-01", "gelRate", "2.70"))
                .andExpect(status().isOk());
        api.put("/api/exchange-rates", body("currency", "USD", "date", "2026-10-01", "gelRate", "2.71"))
                .andExpect(status().isOk());
        api.put("/api/exchange-rates", body("currency", "USD", "date", "2026-10-02", "gelRate", "2.72"));

        api.get("/api/accounts")
                .andExpect(jsonPath("$.rates", hasSize(2)))
                .andExpect(jsonPath("$.rates[0].date").value("2026-10-02"))
                .andExpect(jsonPath("$.rates[1].gelRate").value(2.71));
    }

    @Test
    void accountEventsNeverTouchTheOtherLedgers() throws Exception {
        Api api = api();
        String wallet = account(api, "Wallet", "GEL", 0);
        api.post("/api/accounts/events", body("accountId", wallet, "type", "DECREASE", "amount", 40, "date", "2026-10-01"));

        api.get("/api/dashboard")
                .andExpect(jsonPath("$.expenseTotal").value(0))
                .andExpect(jsonPath("$.outstanding").value(0))
                .andExpect(jsonPath("$.accountSummaries[0].balance").value(-40));
    }
}

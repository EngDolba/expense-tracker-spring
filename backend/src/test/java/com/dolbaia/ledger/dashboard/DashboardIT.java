package com.dolbaia.ledger.dashboard;

import static com.dolbaia.ledger.support.Api.body;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;

import com.dolbaia.ledger.support.Api;
import com.dolbaia.ledger.support.DatabaseTest;

class DashboardIT extends DatabaseTest {

    @Test
    void summarisesEachLedgerOnItsOwn() throws Exception {
        Api api = api();
        String food = api.create("/api/categories", body("name", "Food"), "$.id");
        api.post("/api/expenses", body("type", "EXPENSE", "amount", 10, "date", "2026-09-15", "categoryId", food));
        api.post("/api/expenses", body("type", "EXPENSE", "amount", 5, "date", "2026-10-01", "categoryId", food));
        api.post("/api/expenses", body("type", "EXPENSE", "amount", 7, "date", "2026-10-02"));
        api.post("/api/expenses", body("type", "INCOME", "amount", 100, "date", "2026-10-03"));

        String acme = api.create("/api/obligations/parties", body("name", "Acme"), "$.id");
        api.post("/api/obligations/entries", body("partyId", acme, "type", "OUTLAY", "amount", 30, "date", "2026-10-01"));

        api.create("/api/accounts", body("name", "Bank", "currency", "GEL", "openingBalance", 200), "$.id");
        api.create("/api/accounts", body("name", "Cash USD", "currency", "USD", "openingBalance", 10), "$.id");
        api.create("/api/accounts", body("name", "Euro", "currency", "EUR", "openingBalance", 1), "$.id");
        api.put("/api/exchange-rates", body("currency", "USD", "date", "2026-09-01", "gelRate", "2.6"));
        api.put("/api/exchange-rates", body("currency", "USD", "date", "2026-10-01", "gelRate", "2.7"));

        api.get("/api/dashboard")
                .andExpect(jsonPath("$.expenseTotal").value(22))
                .andExpect(jsonPath("$.incomeTotal").value(100))
                .andExpect(jsonPath("$.expenseCount").value(4))
                .andExpect(jsonPath("$.outstanding").value(30))
                .andExpect(jsonPath("$.accountSummaries", hasSize(3)))
                .andExpect(jsonPath("$.accountSummaries[1].name").value("Cash USD"))
                .andExpect(jsonPath("$.accountSummaries[1].gelValue").value(27))
                .andExpect(jsonPath("$.accountSummaries[2].gelValue").doesNotExist())
                .andExpect(jsonPath("$.accountGelTotal").value(227))
                .andExpect(jsonPath("$.missingRates[0]").value("EUR"))
                .andExpect(jsonPath("$.categoryTotals['" + food + "'].amount").value(15))
                .andExpect(jsonPath("$.categoryTotals['" + food + "'].count").value(2))
                .andExpect(jsonPath("$.trend", hasSize(2)))
                .andExpect(jsonPath("$.trend[0].month").value("2026-09"))
                .andExpect(jsonPath("$.trend[1].expenses").value(12))
                .andExpect(jsonPath("$.trend[1].income").value(100));
    }

    @Test
    void anEmptyLedgerIsAllZeroes() throws Exception {
        api().get("/api/dashboard")
                .andExpect(jsonPath("$.expenseTotal").value(0))
                .andExpect(jsonPath("$.outstanding").value(0))
                .andExpect(jsonPath("$.trend", hasSize(0)))
                .andExpect(jsonPath("$.accountGelTotal").value(0));
    }
}

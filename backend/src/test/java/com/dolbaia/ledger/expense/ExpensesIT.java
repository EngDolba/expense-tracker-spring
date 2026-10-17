package com.dolbaia.ledger.expense;

import static com.dolbaia.ledger.support.Api.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.dolbaia.ledger.support.Api;
import com.dolbaia.ledger.support.DatabaseTest;

class ExpensesIT extends DatabaseTest {

    @Test
    void recordsAnEntryAndListsItWithItsCategory() throws Exception {
        Api api = api();
        String food = api.create("/api/categories", body("name", "Food"), "$.id");

        api.post("/api/expenses", body("type", "EXPENSE", "amount", "12.50", "date", "2026-10-01",
                        "categoryId", food, "recipient", " Nikora ", "note", "Groceries", "tags", "home, home, week"))
                .andExpect(status().isCreated());

        api.get("/api/expenses")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(1)))
                .andExpect(jsonPath("$.entries[0].amount").value(12.5))
                .andExpect(jsonPath("$.entries[0].date").value("2026-10-01"))
                .andExpect(jsonPath("$.entries[0].recipient").value("Nikora"))
                .andExpect(jsonPath("$.entries[0].category.name").value("Food"))
                .andExpect(jsonPath("$.entries[0].categoryId").value(food))
                .andExpect(jsonPath("$.entries[0].tags[0]").value("home"))
                .andExpect(jsonPath("$.entries[0].tags", hasSize(2)))
                .andExpect(jsonPath("$.categories[0].name").value("Food"));
    }

    @Test
    void anUncategorisedEntryIsCategorisedByTheHighestPriorityRule() throws Exception {
        Api api = api();
        String taxi = api.create("/api/categories", body("name", "Taxi"), "$.id");
        String delivery = api.create("/api/categories", body("name", "Delivery"), "$.id");
        api.post("/api/rules", body("keyword", "bolt", "categoryId", taxi, "priority", 1)).andExpect(status().isCreated());
        api.post("/api/rules", body("keyword", "BOLT FOOD", "categoryId", delivery, "priority", 10, "tags", "food"))
                .andExpect(status().isCreated());

        api.post("/api/expenses", body("type", "EXPENSE", "amount", 30, "date", "2026-10-02",
                "categoryId", "none", "recipient", "Bolt Food", "note", "", "tags", "late"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").value(delivery))
                .andExpect(jsonPath("$.tags[0]").value("late"))
                .andExpect(jsonPath("$.tags[1]").value("food"));

        api.get("/api/expenses")
                .andExpect(jsonPath("$.rules[0].keyword").value("BOLT FOOD"))
                .andExpect(jsonPath("$.rules[0].category.name").value("Delivery"));
    }

    @Test
    void rejectsNonPositiveAmountsAndMissingFields() throws Exception {
        api().post("/api/expenses", body("type", "EXPENSE", "amount", 0, "date", "2026-10-02"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors.amount").exists());
        api().post("/api/expenses", body("type", "LOAN", "amount", 5, "date", "2026-10-02"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletesAnEntry() throws Exception {
        Api api = api();
        String id = api.create("/api/expenses", body("type", "INCOME", "amount", 100, "date", "2026-10-03"), "$.id");

        api.delete("/api/expenses/" + id).andExpect(status().isNoContent());
        api.delete("/api/expenses/" + id).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("expense_not_found"));
        assertThat(jdbc.queryForObject("select count(*) from expense_entry", Integer.class)).isZero();
    }

    @Test
    void expensesNeverTouchAccountsOrObligations() throws Exception {
        Api api = api();
        api.post("/api/accounts", body("name", "Wallet", "currency", "gel", "openingBalance", 50))
                .andExpect(status().isCreated());

        api.post("/api/expenses", body("type", "EXPENSE", "amount", 20, "date", "2026-10-03"))
                .andExpect(status().isCreated());

        api.get("/api/accounts").andExpect(jsonPath("$.accounts[0].balance").value(50));
        assertThat(jdbc.queryForObject("select count(*) from account_event", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from obligation_entry", Integer.class)).isZero();
    }
}

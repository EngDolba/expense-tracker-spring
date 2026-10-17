package com.dolbaia.ledger.export;

import static com.dolbaia.ledger.support.Api.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.dolbaia.ledger.support.Api;
import com.dolbaia.ledger.support.DatabaseTest;

class ExportsIT extends DatabaseTest {

    @Test
    void exportsExpensesAsExcelFriendlyCsv() throws Exception {
        Api api = api();
        String food = api.create("/api/categories", body("name", "Food"), "$.id");
        api.post("/api/expenses", body("type", "EXPENSE", "amount", "12.5", "date", "2026-10-01", "categoryId", food,
                "recipient", "Say \"hi\"", "tags", "a, b"));

        byte[] csv = api.get("/api/exports/expenses")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment; filename=\"expenses-")))
                .andReturn().getResponse().getContentAsByteArray();

        String text = new String(csv, StandardCharsets.UTF_8);
        assertThat(text).startsWith("﻿\"type\",\"amount\"");
        assertThat(text).contains("\r\n\"EXPENSE\",\"12.50\",\"2026-10-01\",\"Food\",\"Say \"\"hi\"\"\",\"\",\"a, b\",\"\"");
    }

    @Test
    void exportsAccountMovementsWithTheirCounterparty() throws Exception {
        Api api = api();
        String a = api.create("/api/accounts", body("name", "A", "currency", "GEL"), "$.id");
        String b = api.create("/api/accounts", body("name", "B", "currency", "GEL"), "$.id");
        api.post("/api/accounts/transfers", body("fromAccountId", a, "toAccountId", b, "fromAmount", 5,
                "toAmount", 5, "date", "2026-10-01"));

        String text = api.get("/api/exports/accounts").andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(text).contains("\"2026-10-01\",\"A\",\"GEL\",\"TRANSFER_OUT\",\"5.00\",\"B\"");
        assertThat(text).contains("\"TRANSFER_IN\",\"5.00\",\"A\"");
    }

    @Test
    void exportsNeedASession() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/exports/expenses"))
                .andExpect(status().isUnauthorized());
    }
}

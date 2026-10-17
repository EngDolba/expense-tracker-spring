package com.dolbaia.ledger.obligation;

import static com.dolbaia.ledger.support.Api.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.dolbaia.ledger.support.Api;
import com.dolbaia.ledger.support.DatabaseTest;

class ObligationsIT extends DatabaseTest {

    @Test
    void outstandingIsOutlaysMinusReimbursements() throws Exception {
        Api api = api();
        String acme = api.create("/api/obligations/parties", body("name", "Acme", "note", "Client"), "$.id");
        String trip = api.create("/api/obligations/cases", body("partyId", acme, "name", "Trip"), "$.id");

        api.post("/api/obligations/entries", body("partyId", acme, "caseId", trip, "type", "OUTLAY",
                "amount", 10, "date", "2026-10-01")).andExpect(status().isCreated());
        api.post("/api/obligations/entries", body("partyId", acme, "caseId", "", "type", "OUTLAY",
                "amount", 20, "date", "2026-10-02", "tags", "hotel")).andExpect(status().isCreated());
        api.post("/api/obligations/entries", body("partyId", acme, "type", "REIMBURSEMENT",
                "amount", 25, "date", "2026-10-03")).andExpect(status().isCreated());

        api.get("/api/obligations")
                .andExpect(jsonPath("$.parties[0].name").value("Acme"))
                .andExpect(jsonPath("$.parties[0].outstanding").value(5))
                .andExpect(jsonPath("$.cases[0].name").value("Trip"))
                .andExpect(jsonPath("$.cases[0].partyId").value(acme))
                .andExpect(jsonPath("$.entries", hasSize(3)))
                .andExpect(jsonPath("$.entries[2].case.name").value("Trip"))
                .andExpect(jsonPath("$.entries[2].party.name").value("Acme"))
                .andExpect(jsonPath("$.entries[1].tags[0]").value("hotel"));
    }

    @Test
    void aCaseMustBelongToTheEntrysParty() throws Exception {
        Api api = api();
        String acme = api.create("/api/obligations/parties", body("name", "Acme"), "$.id");
        String other = api.create("/api/obligations/parties", body("name", "Other"), "$.id");
        String trip = api.create("/api/obligations/cases", body("partyId", other, "name", "Trip"), "$.id");

        api.post("/api/obligations/entries", body("partyId", acme, "caseId", trip, "type", "OUTLAY",
                        "amount", 10, "date", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("case_party_mismatch"));
    }

    @Test
    void obligationsNeverCreateExpensesOrAccountEvents() throws Exception {
        Api api = api();
        String acme = api.create("/api/obligations/parties", body("name", "Acme"), "$.id");
        String id = api.create("/api/obligations/entries", body("partyId", acme, "type", "OUTLAY",
                "amount", 10, "date", "2026-10-01"), "$.id");

        assertThat(jdbc.queryForObject("select count(*) from expense_entry", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from account_event", Integer.class)).isZero();

        api.delete("/api/obligations/entries/" + id).andExpect(status().isNoContent());
        api.get("/api/obligations").andExpect(jsonPath("$.parties[0].outstanding").value(0));
    }
}

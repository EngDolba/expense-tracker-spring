package com.dolbaia.ledger.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.dolbaia.ledger.support.Api;
import com.dolbaia.ledger.support.DatabaseTest;

class ImportsIT extends DatabaseTest {

    ResultActions upload(Api api, byte[] bytes, String name) throws Exception {
        return api.perform(multipart("/api/imports").file(new MockMultipartFile("workbook", name,
                "application/vnd.ms-excel.sheet.macroEnabled.12", bytes)));
    }

    @Test
    void stagesReviewsAndCommitsAWorkbook() throws Exception {
        Api api = api();
        byte[] workbook = WorkbookParserTest.workbookFixture();

        String id = api.read(upload(api, workbook, "XLDR.xlsm")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duplicate").value(false)), "$.id");

        api.get("/api/imports/" + id)
                .andExpect(jsonPath("$.batch.status").value("STAGED"))
                .andExpect(jsonPath("$.batch.totalRows").value(2))
                .andExpect(jsonPath("$.batch.sourceSheet").value("ჩანაწერები"))
                .andExpect(jsonPath("$.rows", hasSize(2)))
                .andExpect(jsonPath("$.rows[0].normalized.categoryPath[0]").value("Transport"))
                .andExpect(jsonPath("$.rows[0].normalized.amount").value(10));
        api.get("/api/imports").andExpect(jsonPath("$[0].fileName").value("XLDR.xlsm"));

        api.post("/api/imports/" + id + "/commit")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMMITTED"));

        assertThat(jdbc.queryForObject("select count(*) from expense_entry where import_batch_id = ?::uuid",
                Integer.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForList("select code from category_code order by code", String.class))
                .containsExactly("3001", "9001");
        assertThat(jdbc.queryForObject("select count(*) from category where name = 'Public' and parent_id ="
                + " (select id from category where name = 'Transport' and parent_id is null)", Integer.class))
                .isEqualTo(1);
        api.get("/api/expenses")
                .andExpect(jsonPath("$.entries[0].category.name").value("Repayment"))
                .andExpect(jsonPath("$.entries[1].tags[1]").value("commute"));

        api.post("/api/imports/" + id + "/commit")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("import_not_staged"));
    }

    @Test
    void theSameFileIsNeverStagedTwice() throws Exception {
        Api api = api();
        byte[] workbook = WorkbookParserTest.workbookFixture();
        String first = api.read(upload(api, workbook, "a.xlsx"), "$.id");

        upload(api, workbook, "renamed.xlsx")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.id").value(first));
    }

    @Test
    void rejectsFilesThatAreNotWorkbooks() throws Exception {
        upload(api(), "a,b".getBytes(), "data.csv")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_workbook"))
                .andExpect(jsonPath("$.detail").value("Only .xlsx and .xlsm workbooks are supported."));
        assertThat(jdbc.queryForObject("select count(*) from import_batch", Integer.class)).isZero();
    }
}

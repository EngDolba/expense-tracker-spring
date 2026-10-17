package com.dolbaia.ledger.category;

import static com.dolbaia.ledger.support.Api.body;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.dolbaia.ledger.support.Api;
import com.dolbaia.ledger.support.DatabaseTest;

class CategoriesIT extends DatabaseTest {

    @Test
    void newCategoriesGoLastAmongTheirSiblings() throws Exception {
        Api api = api();
        String food = api.create("/api/categories", body("name", "Food"), "$.id");
        api.post("/api/categories", body("name", "Travel")).andExpect(jsonPath("$.order").value(1));
        api.post("/api/categories", body("name", "Dining", "parentId", food))
                .andExpect(jsonPath("$.order").value(0))
                .andExpect(jsonPath("$.parentId").value(food));
    }

    @Test
    void renamesAndMovesButNeverIntoADescendant() throws Exception {
        Api api = api();
        String food = api.create("/api/categories", body("name", "Food"), "$.id");
        String dining = api.create("/api/categories", body("name", "Dining", "parentId", food), "$.id");
        String lunch = api.create("/api/categories", body("name", "Lunch", "parentId", dining), "$.id");

        api.put("/api/categories/" + food, body("name", "Food", "parentId", lunch))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("category_cycle"));
        api.put("/api/categories/" + lunch, body("name", "Lunches", "parentId", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lunches"))
                .andExpect(jsonPath("$.parentId").doesNotExist());
    }

    @Test
    void archivedCategoriesLeaveThePickers() throws Exception {
        Api api = api();
        String old = api.create("/api/categories", body("name", "Old"), "$.id");
        api.post("/api/categories/" + old + "/archive").andExpect(status().isNoContent());

        api.get("/api/categories")
                .andExpect(jsonPath("$.categories", hasSize(0)))
                .andExpect(jsonPath("$.codes", hasSize(0)));
    }

    @Test
    void duplicateSiblingNamesConflict() throws Exception {
        Api api = api();
        String food = api.create("/api/categories", body("name", "Food"), "$.id");
        api.post("/api/categories", body("name", "Dining", "parentId", food)).andExpect(status().isCreated());
        api.post("/api/categories", body("name", "Dining", "parentId", food))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));
    }
}

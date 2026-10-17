package com.dolbaia.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.dolbaia.ledger.auth.SessionCookies;
import com.dolbaia.ledger.support.DatabaseTest;
import com.dolbaia.ledger.support.Xsrf;

/** Flyway applies V1 and Hibernate validates every entity against it, on real PostgreSQL. */
class LedgerApplicationIT extends DatabaseTest {

    static String login(String password) {
        return "{\"username\":\"" + USERNAME + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void migratesTheSchemaAndReportsHealthy() throws Exception {
        Integer tables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public'"
                        + " and table_name in ('expense_entry', 'account_event', 'active_workout_set', 'login_attempt')",
                Integer.class);
        assertThat(tables).isEqualTo(4);

        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void signsInAndThenLocksOutRepeatedFailures() throws Exception {
        Cookie session = mvc.perform(post("/api/auth/login").with(Xsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON).content(login(PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(SessionCookies.NAME);

        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME));

        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(post("/api/auth/login").with(Xsrf.token(mvc))
                            .contentType(MediaType.APPLICATION_JSON).content(login("wrong")))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").with(Xsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON).content(login(PASSWORD)))
                .andExpect(status().isTooManyRequests());

        assertThat(jdbc.queryForObject("select count(*) from login_attempt where not succeeded", Integer.class))
                .isEqualTo(5);
    }
}

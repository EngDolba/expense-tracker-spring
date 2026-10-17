package com.dolbaia.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.dolbaia.ledger.auth.SessionCookies;
import com.dolbaia.ledger.support.Xsrf;

/**
 * Boots the whole application against real Postgres: Flyway applies V1, and
 * Hibernate validates every entity against it. Needs Docker; skipped without it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class LedgerApplicationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void auth(DynamicPropertyRegistry registry) {
        registry.add("app.auth.username", () -> "ledger-user");
        registry.add("app.auth.password-hash", () -> new BCryptPasswordEncoder(4).encode("correct horse"));
        registry.add("app.auth.session-secret", () -> "a-test-secret-that-is-longer-than-32-characters");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    static String login(String password) {
        return "{\"username\":\"ledger-user\",\"password\":\"" + password + "\"}";
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
                        .contentType(MediaType.APPLICATION_JSON).content(login("correct horse")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(SessionCookies.NAME);

        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ledger-user"));

        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(post("/api/auth/login").with(Xsrf.token(mvc))
                            .contentType(MediaType.APPLICATION_JSON).content(login("wrong")))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").with(Xsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON).content(login("correct horse")))
                .andExpect(status().isTooManyRequests());

        assertThat(jdbc.queryForObject("select count(*) from login_attempt where not succeeded", Integer.class))
                .isEqualTo(5);
    }
}

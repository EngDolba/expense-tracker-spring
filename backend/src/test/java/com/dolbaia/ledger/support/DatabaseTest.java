package com.dolbaia.ledger.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import jakarta.servlet.http.Cookie;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.dolbaia.ledger.auth.SessionCookies;
import com.dolbaia.ledger.auth.SessionTokenService;

/**
 * Base for tests that boot the whole application against a real PostgreSQL
 * (embedded, one per JVM). Flyway migrates it once; every test starts from
 * empty tables. All subclasses share one Spring context.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class DatabaseTest {

    public static final String USERNAME = "ledger-user";
    public static final String PASSWORD = "correct horse";
    public static final String PASSWORD_HASH = new BCryptPasswordEncoder(4).encode(PASSWORD);

    private static final EmbeddedPostgres POSTGRES = start();

    private static EmbeddedPostgres start() {
        try {
            EmbeddedPostgres postgres = EmbeddedPostgres.builder().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException ignored) {
                    // the JVM is exiting anyway
                }
            }));
            return postgres;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("app.auth.username", () -> USERNAME);
        registry.add("app.auth.password-hash", () -> PASSWORD_HASH);
        registry.add("app.auth.session-secret", () -> "a-test-secret-that-is-longer-than-32-characters");
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired private SessionTokenService tokens;

    /**
     * Signed in as the owner, with a matching XSRF cookie and header (the
     * double-submit check only compares the two).
     */
    protected RequestPostProcessor owner() {
        Cookie session = new Cookie(SessionCookies.NAME, tokens.create(USERNAME));
        Cookie xsrf = new Cookie("XSRF-TOKEN", "test-xsrf-token");
        return request -> {
            request.setCookies(session, xsrf);
            request.addHeader("X-XSRF-TOKEN", "test-xsrf-token");
            return request;
        };
    }

    protected Api api() {
        return new Api(mvc, owner());
    }

    @BeforeEach
    void emptyTables() {
        List<String> tables = jdbc.queryForList(
                "select tablename from pg_tables where schemaname = 'public'"
                        + " and tablename <> 'flyway_schema_history'", String.class);
        if (!tables.isEmpty()) {
            jdbc.execute("truncate table " + String.join(", ", tables) + " cascade");
        }
    }
}

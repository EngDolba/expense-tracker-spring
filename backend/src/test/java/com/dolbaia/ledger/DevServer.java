package com.dolbaia.ledger;

import java.io.IOException;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import org.springframework.boot.SpringApplication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Local development without installing PostgreSQL: starts an embedded one and
 * the app against it on :8080. Run with `./mvnw spring-boot:test-run`, then
 * `pnpm dev` in ../frontend. Sign in as dev / dev. Data is lost on exit.
 */
public final class DevServer {

    private DevServer() {}

    public static void main(String[] args) throws IOException {
        EmbeddedPostgres postgres = EmbeddedPostgres.builder().setPort(54329).start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                postgres.close();
            } catch (IOException ignored) {
                // exiting anyway
            }
        }));

        System.setProperty("spring.datasource.url", postgres.getJdbcUrl("postgres", "postgres"));
        System.setProperty("spring.datasource.username", "postgres");
        System.setProperty("spring.datasource.password", "");
        setDefault("app.auth.username", "dev");
        setDefault("app.auth.password-hash", new BCryptPasswordEncoder().encode("dev"));
        setDefault("app.auth.session-secret", "development-only-session-secret-0123456789");
        setDefault("app.auth.secure-cookie", "false");
        SpringApplication.run(LedgerApplication.class, args);
    }

    private static void setDefault(String key, String value) {
        if (System.getProperty(key) == null) System.setProperty(key, value);
    }
}

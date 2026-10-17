package com.dolbaia.ledger.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DatabaseUrlTest {

    @Test
    void convertsALibpqUrlWithCredentialsAndQuery() {
        DatabaseUrl url = DatabaseUrl.parse(
                "postgresql://ledger:p%40ss+word@ep-x-pooler.eu-central-1.aws.neon.tech/neondb?sslmode=require");

        assertThat(url.jdbcUrl())
                .isEqualTo("jdbc:postgresql://ep-x-pooler.eu-central-1.aws.neon.tech/neondb?sslmode=require");
        assertThat(url.username()).isEqualTo("ledger");
        assertThat(url.password()).isEqualTo("p@ss+word");
    }

    @Test
    void keepsThePortAndAcceptsThePostgresScheme() {
        DatabaseUrl url = DatabaseUrl.parse("postgres://postgres:postgres@localhost:5432/expense_tracker");

        assertThat(url.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/expense_tracker");
        assertThat(url.username()).isEqualTo("postgres");
    }

    @Test
    void passesJdbcUrlsThrough() {
        DatabaseUrl url = DatabaseUrl.parse("jdbc:postgresql://localhost/ledger");

        assertThat(url.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost/ledger");
        assertThat(url.username()).isNull();
    }

    @Test
    void rejectsOtherSchemes() {
        assertThatThrownBy(() -> DatabaseUrl.parse("mysql://root@localhost/ledger"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

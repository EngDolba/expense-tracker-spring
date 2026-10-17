package com.dolbaia.ledger.migrate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.stereotype.Component;

import com.dolbaia.ledger.config.DatabaseUrl;

/**
 * `--spring.profiles.active=migrate`: copies the old Prisma database
 * (SOURCE_DATABASE_URL, read-only) into this one (DATABASE_URL, must be
 * empty), prints the verification report and exits.
 */
@Component
@Profile("migrate")
public class MigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    private final PrismaMigrator migrator;
    private final String sourceUrl;

    public MigrationRunner(PrismaMigrator migrator, @Value("${SOURCE_DATABASE_URL:}") String sourceUrl) {
        this.migrator = migrator;
        this.sourceUrl = sourceUrl;
    }

    @Override
    public void run(String... args) throws Exception {
        if (sourceUrl.isBlank()) throw new IllegalStateException("Set SOURCE_DATABASE_URL to the old database.");
        DatabaseUrl url = DatabaseUrl.parse(sourceUrl);
        SimpleDriverDataSource source = new SimpleDriverDataSource(new org.postgresql.Driver(), url.jdbcUrl(),
                url.username(), url.password());
        // Belt and braces: the source is only ever read.
        source.getConnectionProperties().setProperty("readOnly", "true");

        PrismaMigrator.Report report = migrator.migrate(source);
        log.info("Migration complete and verified:\n{}", PrismaMigrator.describe(report));
    }
}

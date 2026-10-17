package com.dolbaia.ledger.migrate;

import static com.dolbaia.ledger.support.Api.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

import com.dolbaia.ledger.support.Api;
import com.dolbaia.ledger.support.DatabaseTest;

/**
 * Builds the Next.js app's database from its own Prisma migrations, fills it
 * with one of everything, migrates it, and checks the result through the API.
 */
class PrismaMigratorIT extends DatabaseTest {

    @Autowired PrismaMigrator migrator;

    DataSource legacy;
    JdbcTemplate old;

    @BeforeEach
    void legacyDatabase() throws IOException {
        legacy = freshDatabase("legacy");
        old = new JdbcTemplate(legacy);
        Resource[] scripts = new PathMatchingResourcePatternResolver().getResources("classpath:legacy-prisma/*.sql");
        Arrays.sort(scripts, Comparator.comparing(Resource::getFilename));
        for (Resource script : scripts) old.execute(script.getContentAsString(StandardCharsets.UTF_8));
        seed();
    }

    void seed() {
        String now = "'2026-09-01 10:00:00'";
        old.execute("insert into \"Category\" values ('c-food', 'Food', null, 0, false, " + now + ", " + now + ")");
        old.execute("insert into \"Category\" values ('c-dining', 'Dining', 'c-food', 0, false, " + now + ", " + now + ")");
        old.execute("insert into \"CategoryCode\" values ('cc1', '3001', 'c-dining', " + now + ", " + now + ")");
        old.execute("insert into \"ImportBatch\" (id, \"fileHash\", \"fileName\", status, \"totalRows\", dictionary,"
                + " \"committedAt\", \"createdAt\", \"updatedAt\") values ('b1', 'abc', 'XLDR.xlsm', 'COMMITTED', 1,"
                + " '[{\"code\":\"3001\",\"path\":[\"Food\",\"Dining\"]}]', " + now + ", " + now + ", " + now + ")");
        old.execute("insert into \"ImportRow\" (id, \"batchId\", \"rowNumber\", normalized, errors, \"createdAt\")"
                + " values ('r1', 'b1', 2, '{\"rowNumber\":2,\"type\":\"EXPENSE\",\"amount\":10,\"date\":\"2026-08-01\","
                + "\"categoryPath\":[\"Food\"],\"recipient\":\"\",\"note\":\"\",\"tags\":[],\"sourceCode\":\"3001\",\"errors\":[]}',"
                + " array['warn']::text[], " + now + ")");
        old.execute("insert into \"ExpenseEntry\" (id, type, amount, date, \"categoryId\", recipient, tags, \"importBatchId\","
                + " \"sourceRow\", \"createdAt\", \"updatedAt\") values ('e1', 'EXPENSE', 10.50, '2026-08-01', 'c-dining',"
                + " 'Cafe', array['work','late']::text[], 'b1', 2, " + now + ", " + now + ")");
        old.execute("insert into \"ExpenseEntry\" (id, type, amount, date, \"createdAt\", \"updatedAt\")"
                + " values ('e2', 'INCOME', 1000, '2026-08-02', " + now + ", " + now + ")");
        old.execute("insert into \"CategorizationRule\" (id, keyword, \"categoryId\", tags, priority, \"createdAt\", \"updatedAt\")"
                + " values ('rule1', 'cafe', 'c-dining', array['food']::text[], 5, " + now + ", " + now + ")");
        old.execute("insert into \"ObligationParty\" (id, name, \"createdAt\", \"updatedAt\") values ('p1', 'Acme', " + now + ", " + now + ")");
        old.execute("insert into \"ObligationCase\" (id, \"partyId\", name, \"createdAt\", \"updatedAt\") values ('k1', 'p1', 'Trip', " + now + ", " + now + ")");
        old.execute("insert into \"ObligationEntry\" (id, \"partyId\", \"caseId\", type, amount, date, tags, \"createdAt\", \"updatedAt\")"
                + " values ('o1', 'p1', 'k1', 'OUTLAY', 30, '2026-08-03', array['hotel']::text[], " + now + ", " + now + ")");
        old.execute("insert into \"ObligationEntry\" (id, \"partyId\", type, amount, date, \"createdAt\", \"updatedAt\")"
                + " values ('o2', 'p1', 'REIMBURSEMENT', 12, '2026-08-04', " + now + ", " + now + ")");
        old.execute("insert into \"FinancialAccount\" (id, name, currency, \"openingBalance\", \"createdAt\", \"updatedAt\")"
                + " values ('a1', 'Bank', 'GEL', 100, " + now + ", " + now + "), ('a2', 'Cash USD', 'USD', 0, " + now + ", " + now + ")");
        old.execute("insert into \"AccountEvent\" (id, \"accountId\", \"counterpartyAccountId\", type, amount, date,"
                + " \"transferGroupId\", \"createdAt\", \"updatedAt\") values"
                + " ('ev1', 'a1', 'a2', 'TRANSFER_OUT', 27, '2026-08-05', '6f1c5bd2-6a43-4bb6-9b39-0f5d1b0a1d11', " + now + ", " + now + "),"
                + " ('ev2', 'a2', 'a1', 'TRANSFER_IN', 10, '2026-08-05', '6f1c5bd2-6a43-4bb6-9b39-0f5d1b0a1d11', " + now + ", " + now + "),"
                + " ('ev3', 'a1', null, 'ADJUSTMENT', -0.5, '2026-08-06', null, " + now + ", " + now + ")");
        old.execute("insert into \"ExchangeRate\" values ('x1', 'USD', '2026-08-01', 2.70000000, " + now + ", " + now + ")");
        old.execute("insert into \"Exercise\" (id, name, \"recommendedWeight\", \"recommendedReps\", \"createdAt\", \"updatedAt\")"
                + " values ('ex1', 'Squat', 80, 5, " + now + ", " + now + ")");
        old.execute("insert into \"ExerciseVariation\" (id, \"exerciseId\", name, \"createdAt\", \"updatedAt\")"
                + " values ('v1', 'ex1', 'High bar', " + now + ", " + now + ")");
        old.execute("insert into \"WorkoutPlan\" (id, name, \"createdAt\", \"updatedAt\") values ('pl1', 'Strength', " + now + ", " + now + ")");
        old.execute("insert into \"WorkoutPlanDay\" (id, \"planId\", name, \"order\", \"createdAt\", \"updatedAt\")"
                + " values ('d1', 'pl1', 'Day A', 0, " + now + ", " + now + ")");
        old.execute("insert into \"PlanExercise\" (id, \"planDayId\", \"exerciseId\", \"order\", \"setCount\", \"supersetGroup\","
                + " \"createdAt\", \"updatedAt\") values ('s1', 'd1', 'ex1', 0, 3, null, " + now + ", " + now + ")");
        old.execute("insert into \"WorkoutSession\" (id, \"planDayId\", \"startedAt\", \"completedAt\", note, \"createdAt\", \"updatedAt\")"
                + " values ('w1', 'd1', '2026-08-10 09:00:00', '2026-08-10 10:00:00', 'Good', " + now + ", " + now + ")");
        old.execute("insert into \"WorkoutExercise\" (id, \"workoutId\", \"planExerciseId\", \"exerciseId\", \"variationId\", \"order\","
                + " \"targetWeight\", \"targetReps\", \"perceivedEffort\", \"nextWeight\", \"nextReps\", \"nextSets\", \"createdAt\", \"updatedAt\")"
                + " values ('we1', 'w1', 's1', 'ex1', 'v1', 0, 80, 5, 2, 82.5, 5, 3, " + now + ", " + now + ")");
        old.execute("insert into \"WorkoutSet\" values ('ws1', 'we1', 1, 80, 5, " + now + ", " + now + "),"
                + " ('ws2', 'we1', 2, 80, 4, " + now + ", " + now + ")");
        old.execute("insert into \"ActiveWorkout\" (id, \"planDayId\", \"startedAt\", \"createdAt\", \"updatedAt\")"
                + " values ('aw1', 'd1', '2026-09-01 08:00:00', " + now + ", " + now + ")");
        old.execute("insert into \"ActiveWorkoutExercise\" (id, \"activeWorkoutId\", \"planExerciseId\", \"exerciseId\", \"order\","
                + " \"targetWeight\", \"targetReps\", \"createdAt\", \"updatedAt\") values ('awe1', 'aw1', 's1', 'ex1', 0, 82.5, 5, " + now + ", " + now + ")");
        old.execute("insert into \"ActiveWorkoutSet\" (id, \"activeWorkoutExerciseId\", \"setNumber\", weight, reps, completed,"
                + " \"createdAt\", \"updatedAt\") values ('aws1', 'awe1', 1, 82.5, 5, true, " + now + ", " + now + ")");
    }

    @Test
    void copiesEverythingAndReconcilesTheLedgers() throws Exception {
        PrismaMigrator.Report report = migrator.migrate(legacy);

        assertThat(report.ok()).as(PrismaMigrator.describe(report)).isTrue();
        assertThat(report.checks()).extracting(PrismaMigrator.Check::name)
                .contains("expense total", "obligations outstanding", "balance of Bank", "workout volume");

        Api api = api();
        api.get("/api/expenses")
                .andExpect(jsonPath("$.entries", hasSize(2)))
                .andExpect(jsonPath("$.entries[1].category.name").value("Dining"))
                .andExpect(jsonPath("$.entries[1].tags[1]").value("late"))
                .andExpect(jsonPath("$.rules[0].tags[0]").value("food"));
        api.get("/api/obligations")
                .andExpect(jsonPath("$.parties[0].outstanding").value(18))
                .andExpect(jsonPath("$.entries[1].case.name").value("Trip"));
        api.get("/api/accounts")
                .andExpect(jsonPath("$.accounts[0].balance").value(72.5))
                .andExpect(jsonPath("$.accounts[1].events[0].counterpartyAccountId").exists())
                .andExpect(jsonPath("$.rates[0].gelRate").value(2.7));
        api.get("/api/fitness")
                .andExpect(jsonPath("$.workouts[0].volume").value(720))
                .andExpect(jsonPath("$.workouts[0].exercises[0].variationName").value("High bar"))
                .andExpect(jsonPath("$.activeWorkouts[0].completedSets").value(1));
        assertThat(jdbc.queryForObject("select count(*) from account_event where transfer_group_id ="
                + " '6f1c5bd2-6a43-4bb6-9b39-0f5d1b0a1d11'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select message from import_row_error", String.class)).isEqualTo("warn");
        assertThat(jdbc.queryForObject("select started_at = timestamptz '2026-08-10 09:00:00+00' from workout_session",
                Boolean.class)).isTrue();
        String batch = jdbc.queryForObject("select id::text from import_batch", String.class);
        api.get("/api/imports/" + batch)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].normalized.sourceCode").value("3001"));
    }

    @Test
    void refusesANonEmptyTargetAndWritesNothing() throws Exception {
        api().post("/api/categories", body("name", "Already here")).andExpect(status().isCreated());

        assertThatThrownBy(() -> migrator.migrate(legacy))
                .isInstanceOf(PrismaMigrator.MigrationFailed.class)
                .hasMessageContaining("not empty");
        assertThat(jdbc.queryForObject("select count(*) from category", Integer.class)).isEqualTo(1);
    }

    @Test
    void rollsBackOnADanglingReference() {
        old.execute("alter table \"ExpenseEntry\" drop constraint \"ExpenseEntry_categoryId_fkey\"");
        old.execute("insert into \"ExpenseEntry\" (id, type, amount, date, \"categoryId\", \"createdAt\", \"updatedAt\")"
                + " values ('e3', 'EXPENSE', 1, '2026-08-01', 'gone', now(), now())");

        assertThatThrownBy(() -> migrator.migrate(legacy)).hasMessageContaining("missing Category");
        assertThat(jdbc.queryForObject("select count(*) from category", Integer.class)).isZero();
    }
}

package com.dolbaia.ledger.migrate;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One-off copy of the Next.js app's Prisma database into this schema. Every
 * table is copied in foreign-key order; cuid keys become UUIDs (references are
 * rewritten through the same map), text[] tags become join-table rows, and
 * Prisma's UTC timestamps become timestamptz. The whole copy is one target
 * transaction: any failure, including a failed verification, leaves the target
 * untouched. The source is only read.
 */
@Component
public class PrismaMigrator {

    private static final Logger log = LoggerFactory.getLogger(PrismaMigrator.class);

    /** Source tables in insert order, with the target table each one fills. */
    private static final List<Spec> SPECS = List.of(
            spec("Category", "category").ref("parentId", "Category"),
            spec("CategoryCode", "category_code").ref("categoryId", "Category"),
            spec("ImportBatch", "import_batch").json("dictionary"),
            spec("ImportRow", "import_row").ref("batchId", "ImportBatch").json("normalized")
                    .array("errors", "import_row_error", "import_row_id", "message").noUpdatedAt(),
            spec("ExpenseEntry", "expense_entry").ref("categoryId", "Category").ref("importBatchId", "ImportBatch")
                    .rename("date", "entry_date").array("tags", "expense_entry_tag", "expense_entry_id", "tag"),
            spec("CategorizationRule", "categorization_rule").ref("categoryId", "Category")
                    .array("tags", "categorization_rule_tag", "categorization_rule_id", "tag"),
            spec("ObligationParty", "obligation_party"),
            spec("ObligationCase", "obligation_case").ref("partyId", "ObligationParty"),
            spec("ObligationEntry", "obligation_entry").ref("partyId", "ObligationParty")
                    .ref("caseId", "ObligationCase").ref("categoryId", "Category").rename("date", "entry_date")
                    .array("tags", "obligation_entry_tag", "obligation_entry_id", "tag"),
            spec("FinancialAccount", "financial_account"),
            spec("AccountEvent", "account_event").ref("accountId", "FinancialAccount")
                    .ref("counterpartyAccountId", "FinancialAccount").rename("date", "event_date")
                    .uuidText("transferGroupId"),
            spec("ExchangeRate", "exchange_rate").rename("date", "rate_date"),
            spec("Exercise", "exercise"),
            spec("ExerciseVariation", "exercise_variation").ref("exerciseId", "Exercise"),
            spec("WorkoutPlan", "workout_plan"),
            spec("WorkoutPlanDay", "workout_plan_day").ref("planId", "WorkoutPlan"),
            spec("PlanExercise", "plan_exercise").ref("planDayId", "WorkoutPlanDay").ref("exerciseId", "Exercise"),
            spec("WorkoutSession", "workout_session").ref("planDayId", "WorkoutPlanDay"),
            spec("WorkoutExercise", "workout_exercise").ref("workoutId", "WorkoutSession")
                    .ref("planExerciseId", "PlanExercise").ref("exerciseId", "Exercise")
                    .ref("variationId", "ExerciseVariation"),
            spec("WorkoutSet", "workout_set").ref("workoutExerciseId", "WorkoutExercise"),
            spec("ActiveWorkout", "active_workout").ref("planDayId", "WorkoutPlanDay"),
            spec("ActiveWorkoutExercise", "active_workout_exercise").ref("activeWorkoutId", "ActiveWorkout")
                    .ref("planExerciseId", "PlanExercise").ref("exerciseId", "Exercise")
                    .ref("variationId", "ExerciseVariation"),
            spec("ActiveWorkoutSet", "active_workout_set").ref("activeWorkoutExerciseId", "ActiveWorkoutExercise"));

    private static final Set<String> TIMESTAMPS =
            Set.of("createdAt", "updatedAt", "committedAt", "startedAt", "completedAt", "wrappedUpAt");

    public record TableCount(String source, long sourceRows, String target, long targetRows) {}

    public record Check(String name, String source, String target, boolean matches) {}

    public record Report(List<TableCount> tables, List<Check> checks) {
        public boolean ok() {
            return tables.stream().allMatch(table -> table.sourceRows() == table.targetRows())
                    && checks.stream().allMatch(Check::matches);
        }
    }

    public static class MigrationFailed extends RuntimeException {
        public MigrationFailed(String message) {
            super(message);
        }
    }

    private final JdbcTemplate target;
    private final TransactionTemplate transactions;

    public PrismaMigrator(JdbcTemplate target, TransactionTemplate transactions) {
        this.target = target;
        this.transactions = transactions;
    }

    /** Copies everything and verifies it; throws (and rolls back) unless the report is clean. */
    public Report migrate(DataSource sourceDataSource) {
        JdbcTemplate source = new JdbcTemplate(sourceDataSource);
        return transactions.execute(status -> {
            requireEmptyTarget();
            Map<String, UUID> ids = new HashMap<>();
            for (Spec spec : SPECS) copy(source, spec, ids);
            Report report = verify(source);
            if (!report.ok()) {
                status.setRollbackOnly();
                throw new MigrationFailed("Verification failed; nothing was written.\n" + describe(report));
            }
            return report;
        });
    }

    private void requireEmptyTarget() {
        for (Spec spec : SPECS) {
            Long rows = target.queryForObject("select count(*) from " + spec.target, Long.class);
            if (rows != null && rows > 0) {
                throw new MigrationFailed("The target table " + spec.target + " is not empty; migrate into a fresh database.");
            }
        }
    }

    private void copy(JdbcTemplate source, Spec spec, Map<String, UUID> ids) {
        List<Map<String, Object>> rows = source.query("select * from \"" + spec.source + "\"", (rs, index) -> read(rs, spec));
        if (spec.source.equals("Category")) rows = parentsFirst(rows);

        List<String> columns = null;
        int arrayRows = 0;
        for (Map<String, Object> row : rows) {
            UUID id = UUID.randomUUID();
            ids.put((String) row.get("id"), id);

            Map<String, Object> values = new LinkedHashMap<>();
            values.put("id", id);
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String column = entry.getKey();
                if (column.equals("id") || spec.arrays.containsKey(column)) continue;
                values.put(spec.targetColumn(column), convert(spec, column, entry.getValue(), ids));
            }
            if (spec.noUpdatedAt) values.put("updated_at", values.get("created_at"));

            if (columns == null) columns = List.copyOf(values.keySet());
            target.update(insert(spec, columns), values.values().toArray());

            for (Map.Entry<String, ArrayTarget> array : spec.arrays.entrySet()) {
                String[] items = (String[]) row.get(array.getKey());
                if (items == null) continue;
                ArrayTarget into = array.getValue();
                for (int position = 0; position < items.length; position++) {
                    target.update("insert into " + into.table + " (" + into.foreignKey + ", position, " + into.column
                            + ") values (?, ?, ?)", id, position, items[position]);
                    arrayRows++;
                }
            }
        }
        log.info("Copied {} rows from \"{}\" into {}{}", rows.size(), spec.source, spec.target,
                arrayRows > 0 ? " (+" + arrayRows + " list items)" : "");
    }

    private static Map<String, Object> read(ResultSet rs, Spec spec) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        var meta = rs.getMetaData();
        for (int index = 1; index <= meta.getColumnCount(); index++) {
            String column = meta.getColumnName(index);
            Object value;
            if (spec.arrays.containsKey(column)) {
                Array array = rs.getArray(index);
                value = array == null ? null : (String[]) array.getArray();
            } else if (TIMESTAMPS.contains(column)) {
                LocalDateTime local = rs.getObject(index, LocalDateTime.class);
                value = local == null ? null : local.atOffset(ZoneOffset.UTC);
            } else if (meta.getColumnTypeName(index).equals("date")) {
                value = rs.getObject(index, java.time.LocalDate.class);
            } else if (spec.json.contains(column) || spec.refs.containsKey(column) || column.equals("id")
                    || spec.uuidText.contains(column)) {
                value = rs.getString(index);
            } else {
                value = rs.getObject(index);
                // Prisma enums arrive as PGobject; their text is the value.
                if (value instanceof org.postgresql.util.PGobject pg) value = pg.getValue();
            }
            row.put(column, value);
        }
        return row;
    }

    private static Object convert(Spec spec, String column, Object value, Map<String, UUID> ids) {
        if (value == null) return null;
        if (spec.refs.containsKey(column)) {
            UUID mapped = ids.get((String) value);
            if (mapped == null) {
                throw new MigrationFailed(spec.source + "." + column + " points at a missing "
                        + spec.refs.get(column) + " (" + value + ").");
            }
            return mapped;
        }
        if (spec.uuidText.contains(column)) {
            String text = (String) value;
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException notAUuid) {
                return UUID.nameUUIDFromBytes(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        return value;
    }

    private static String insert(Spec spec, List<String> columns) {
        List<String> placeholders = new ArrayList<>();
        for (String column : columns) {
            boolean json = spec.json.stream().anyMatch(source -> spec.targetColumn(source).equals(column));
            placeholders.add(json ? "cast(? as jsonb)" : "?");
        }
        return "insert into " + spec.target + " (" + String.join(", ", columns) + ") values ("
                + String.join(", ", placeholders) + ")";
    }

    /** Orders categories so every parent is inserted before its children. */
    private static List<Map<String, Object>> parentsFirst(List<Map<String, Object>> rows) {
        Set<Object> placed = new LinkedHashSet<>();
        List<Map<String, Object>> ordered = new ArrayList<>();
        List<Map<String, Object>> pending = new ArrayList<>(rows);
        while (!pending.isEmpty()) {
            int before = pending.size();
            pending.removeIf(row -> {
                Object parent = row.get("parentId");
                if (parent != null && !placed.contains(parent)) return false;
                placed.add(row.get("id"));
                ordered.add(row);
                return true;
            });
            if (pending.size() == before) throw new MigrationFailed("The category tree has a cycle or a missing parent.");
        }
        return ordered;
    }

    // ---- Verification -------------------------------------------------------------------------

    private Report verify(JdbcTemplate source) {
        List<TableCount> tables = new ArrayList<>();
        for (Spec spec : SPECS) {
            tables.add(new TableCount(spec.source, count(source, "select count(*) from \"" + spec.source + "\""),
                    spec.target, count(target, "select count(*) from " + spec.target)));
            for (Map.Entry<String, ArrayTarget> array : spec.arrays.entrySet()) {
                tables.add(new TableCount(spec.source + "." + array.getKey(),
                        count(source, "select coalesce(sum(cardinality(\"" + array.getKey() + "\")), 0) from \"" + spec.source + "\""),
                        array.getValue().table, count(target, "select count(*) from " + array.getValue().table)));
            }
        }

        List<Check> checks = new ArrayList<>();
        check(checks, "expense total", source,
                "select coalesce(sum(amount), 0) from \"ExpenseEntry\" where type = 'EXPENSE'",
                "select coalesce(sum(amount), 0) from expense_entry where type = 'EXPENSE'");
        check(checks, "income total", source,
                "select coalesce(sum(amount), 0) from \"ExpenseEntry\" where type = 'INCOME'",
                "select coalesce(sum(amount), 0) from expense_entry where type = 'INCOME'");
        check(checks, "obligations outstanding", source,
                "select coalesce(sum(case when type = 'OUTLAY' then abs(amount) else -abs(amount) end), 0) from \"ObligationEntry\"",
                "select coalesce(sum(case when type = 'OUTLAY' then abs(amount) else -abs(amount) end), 0) from obligation_entry");
        check(checks, "workout volume", source,
                "select coalesce(sum(weight * reps), 0) from \"WorkoutSet\"",
                "select coalesce(sum(weight * reps), 0) from workout_set");

        Map<String, BigDecimal> sourceBalances = balances(source, """
                select a.name, a."openingBalance" + coalesce(sum(case
                         when e.type in ('INCREASE', 'TRANSFER_IN') then abs(e.amount)
                         when e.type in ('DECREASE', 'TRANSFER_OUT') then -abs(e.amount)
                         else e.amount end), 0) as balance
                  from "FinancialAccount" a left join "AccountEvent" e on e."accountId" = a.id
                 group by a.id, a.name, a."openingBalance"
                """);
        Map<String, BigDecimal> targetBalances = balances(target, """
                select a.name, a.opening_balance + coalesce(sum(case
                         when e.type in ('INCREASE', 'TRANSFER_IN') then abs(e.amount)
                         when e.type in ('DECREASE', 'TRANSFER_OUT') then -abs(e.amount)
                         else e.amount end), 0) as balance
                  from financial_account a left join account_event e on e.account_id = a.id
                 group by a.id, a.name, a.opening_balance
                """);
        for (Map.Entry<String, BigDecimal> entry : sourceBalances.entrySet()) {
            BigDecimal migrated = targetBalances.get(entry.getKey());
            checks.add(new Check("balance of " + entry.getKey(), plain(entry.getValue()), plain(migrated),
                    migrated != null && migrated.compareTo(entry.getValue()) == 0));
        }
        return new Report(tables, checks);
    }

    private void check(List<Check> checks, String name, JdbcTemplate source, String sourceSql, String targetSql) {
        BigDecimal before = source.queryForObject(sourceSql, BigDecimal.class);
        BigDecimal after = target.queryForObject(targetSql, BigDecimal.class);
        checks.add(new Check(name, plain(before), plain(after),
                before != null && after != null && before.compareTo(after) == 0));
    }

    private static Map<String, BigDecimal> balances(JdbcTemplate jdbc, String sql) {
        Map<String, BigDecimal> balances = new LinkedHashMap<>();
        jdbc.query(sql, (ResultSet rs) -> {
            balances.put(rs.getString("name"), rs.getBigDecimal("balance"));
        });
        return balances;
    }

    private static long count(JdbcTemplate jdbc, String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private static String plain(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    public static String describe(Report report) {
        StringBuilder text = new StringBuilder();
        for (TableCount table : report.tables()) {
            text.append(String.format("%-4s %-34s %8d -> %-26s %8d%n",
                    table.sourceRows() == table.targetRows() ? "ok" : "FAIL",
                    table.source(), table.sourceRows(), table.target(), table.targetRows()));
        }
        for (Check check : report.checks()) {
            text.append(String.format("%-4s %-34s %14s -> %s%n", check.matches() ? "ok" : "FAIL",
                    check.name(), check.source(), check.target()));
        }
        return text.toString();
    }

    // ---- Table specs --------------------------------------------------------------------------

    private record ArrayTarget(String table, String foreignKey, String column) {}

    private static Spec spec(String source, String target) {
        return new Spec(source, target);
    }

    private static final class Spec {
        final String source;
        final String target;
        final Map<String, String> refs = new HashMap<>();
        final Map<String, String> renames = new HashMap<>();
        final Map<String, ArrayTarget> arrays = new LinkedHashMap<>();
        final Set<String> json = new LinkedHashSet<>();
        final Set<String> uuidText = new LinkedHashSet<>();
        boolean noUpdatedAt;

        Spec(String source, String target) {
            this.source = source;
            this.target = target;
            renames.put("order", "sort_order");
        }

        Spec ref(String column, String table) { refs.put(column, table); return this; }
        Spec rename(String column, String to) { renames.put(column, to); return this; }
        Spec json(String column) { json.add(column); return this; }
        Spec uuidText(String column) { uuidText.add(column); return this; }
        Spec noUpdatedAt() { noUpdatedAt = true; return this; }

        Spec array(String column, String table, String foreignKey, String valueColumn) {
            arrays.put(column, new ArrayTarget(table, foreignKey, valueColumn));
            return this;
        }

        /** camelCase source column to the target's snake_case, unless renamed. */
        String targetColumn(String column) {
            return Objects.requireNonNullElseGet(renames.get(column),
                    () -> column.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT));
        }
    }
}

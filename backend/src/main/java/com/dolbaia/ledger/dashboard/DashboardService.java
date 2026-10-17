package com.dolbaia.ledger.dashboard;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dolbaia.ledger.category.CategoryService;
import com.dolbaia.ledger.category.CategoryView;
import com.dolbaia.ledger.common.Money;

/**
 * The overview. Each figure is computed from its own ledger alone; they are
 * shown side by side, never added together (except the GEL value of accounts).
 */
@Service
public class DashboardService {

    static final int TREND_MONTHS = 12;

    public record AccountSummary(UUID id, String name, String currency, BigDecimal balance, BigDecimal gelValue) {}

    public record CategoryTotal(BigDecimal amount, long count) {}

    public record TrendPoint(String month, BigDecimal expenses, BigDecimal income) {}

    public record Dashboard(BigDecimal expenseTotal, BigDecimal incomeTotal, long expenseCount,
                            BigDecimal outstanding, List<AccountSummary> accountSummaries,
                            BigDecimal accountGelTotal, List<String> missingRates, List<CategoryView> categories,
                            Map<UUID, CategoryTotal> categoryTotals, List<TrendPoint> trend) {}

    private final JdbcTemplate jdbc;
    private final CategoryService categories;

    public DashboardService(JdbcTemplate jdbc, CategoryService categories) {
        this.jdbc = jdbc;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        BigDecimal expenseTotal = BigDecimal.ZERO;
        BigDecimal incomeTotal = BigDecimal.ZERO;
        long count = 0;
        for (Map<String, Object> row : jdbc.queryForList(
                "select type, coalesce(sum(amount), 0) as total, count(*) as n from expense_entry group by type")) {
            BigDecimal total = (BigDecimal) row.get("total");
            if ("EXPENSE".equals(row.get("type"))) expenseTotal = total;
            else incomeTotal = total;
            count += ((Number) row.get("n")).longValue();
        }

        BigDecimal outstanding = jdbc.queryForObject(
                "select coalesce(sum(case when type = 'OUTLAY' then abs(amount) else -abs(amount) end), 0)"
                        + " from obligation_entry", BigDecimal.class);

        Map<String, BigDecimal> latestRates = new HashMap<>();
        jdbc.query("select distinct on (currency) currency, gel_rate from exchange_rate"
                        + " order by currency, rate_date desc",
                (java.sql.ResultSet rs) -> {
                    latestRates.put(rs.getString("currency"), rs.getBigDecimal("gel_rate"));
                });
        latestRates.put("GEL", BigDecimal.ONE);

        List<AccountSummary> accounts = jdbc.query("""
                select a.id, a.name, a.currency, a.opening_balance + coalesce(sum(case
                         when e.type in ('INCREASE', 'TRANSFER_IN') then abs(e.amount)
                         when e.type in ('DECREASE', 'TRANSFER_OUT') then -abs(e.amount)
                         else e.amount end), 0) as balance
                  from financial_account a
                  left join account_event e on e.account_id = a.id
                 group by a.id, a.name, a.currency, a.opening_balance
                 order by a.name
                """, (rs, index) -> {
            BigDecimal balance = rs.getBigDecimal("balance");
            String currency = rs.getString("currency");
            return new AccountSummary(rs.getObject("id", UUID.class), rs.getString("name"), currency, balance,
                    Money.convertToGel(balance, latestRates.get(currency)));
        });

        BigDecimal gelTotal = accounts.stream()
                .map(account -> Money.orZero(account.gelValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<String> missingRates = accounts.stream()
                .filter(account -> account.gelValue() == null)
                .map(AccountSummary::currency)
                .toList();

        Map<UUID, CategoryTotal> categoryTotals = new LinkedHashMap<>();
        jdbc.query("select category_id, sum(amount) as total, count(*) as n from expense_entry"
                        + " where type = 'EXPENSE' and category_id is not null group by category_id",
                (java.sql.ResultSet rs) -> {
                    categoryTotals.put(rs.getObject("category_id", UUID.class),
                            new CategoryTotal(rs.getBigDecimal("total"), rs.getLong("n")));
                });

        List<TrendPoint> trend = new ArrayList<>(jdbc.query("""
                select to_char(entry_date, 'YYYY-MM') as month,
                       coalesce(sum(amount) filter (where type = 'EXPENSE'), 0) as expenses,
                       coalesce(sum(amount) filter (where type = 'INCOME'), 0) as income
                  from expense_entry
                 group by 1
                 order by 1 desc
                 limit ?
                """, (rs, index) -> new TrendPoint(rs.getString("month"), rs.getBigDecimal("expenses"),
                rs.getBigDecimal("income")), TREND_MONTHS));
        Collections.reverse(trend);

        return new Dashboard(expenseTotal, incomeTotal, count, outstanding, accounts, gelTotal, missingRates,
                categories.active(), categoryTotals, trend);
    }
}

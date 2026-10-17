package com.dolbaia.ledger.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dolbaia.ledger.common.ApiException;
import com.dolbaia.ledger.common.Money;
import com.dolbaia.ledger.common.Money.Movement;

/**
 * Generic financial accounts. Balances come only from an account's opening
 * balance and its own events; nothing in the other ledgers posts here.
 */
@Service
public class AccountService {

    /** Reconciliation ignores differences below half a tetri. */
    static final BigDecimal RECONCILE_TOLERANCE = new BigDecimal("0.005");

    public record EventView(UUID id, UUID accountId, UUID counterpartyAccountId, AccountEventType type,
                            BigDecimal amount, LocalDate date, String note, UUID transferGroupId,
                            Instant createdAt) {

        static EventView of(AccountEvent event) {
            return new EventView(event.getId(), event.getAccount().getId(),
                    event.getCounterpartyAccount() == null ? null : event.getCounterpartyAccount().getId(),
                    event.getType(), event.getAmount(), event.getDate(), event.getNote(),
                    event.getTransferGroupId(), event.getCreatedAt());
        }
    }

    public record AccountView(UUID id, String name, String description, String currency,
                              BigDecimal openingBalance, boolean archived, BigDecimal balance,
                              List<EventView> events, Instant createdAt) {}

    public record RateView(UUID id, String currency, LocalDate date, BigDecimal gelRate) {}

    public record AccountsPage(List<AccountView> accounts, List<RateView> rates) {}

    private final FinancialAccountRepository accounts;
    private final AccountEventRepository events;
    private final ExchangeRateRepository rates;

    public AccountService(FinancialAccountRepository accounts, AccountEventRepository events,
                          ExchangeRateRepository rates) {
        this.accounts = accounts;
        this.events = events;
        this.rates = rates;
    }

    @Transactional(readOnly = true)
    public AccountsPage page() {
        List<FinancialAccount> active = accounts.findActive();
        Map<UUID, List<AccountEvent>> byAccount = new LinkedHashMap<>();
        for (FinancialAccount account : active) byAccount.put(account.getId(), new ArrayList<>());
        if (!active.isEmpty()) {
            for (AccountEvent event : events.findForAccounts(byAccount.keySet())) {
                byAccount.get(event.getAccount().getId()).add(event);
            }
        }

        List<AccountView> views = active.stream().map(account -> {
            List<AccountEvent> own = byAccount.get(account.getId());
            return new AccountView(account.getId(), account.getName(), account.getDescription(),
                    account.getCurrency(), account.getOpeningBalance(), account.isArchived(),
                    balance(account, own), own.stream().map(EventView::of).toList(), account.getCreatedAt());
        }).toList();

        return new AccountsPage(views, rates.findAllLatestFirst().stream()
                .map(rate -> new RateView(rate.getId(), rate.getCurrency(), rate.getDate(), rate.getGelRate()))
                .toList());
    }

    static BigDecimal balance(FinancialAccount account, List<AccountEvent> own) {
        return Money.accountBalance(account.getOpeningBalance(),
                own.stream().map(event -> new Movement<>(event.getType(), event.getAmount())).toList());
    }

    @Transactional
    public UUID createAccount(String name, String description, String currency, BigDecimal openingBalance) {
        return accounts.save(new FinancialAccount(name, description, currency, openingBalance)).getId();
    }

    /** INCREASE, DECREASE or ADJUSTMENT; only an adjustment may be zero or negative. */
    @Transactional
    public EventView createEvent(UUID accountId, AccountEventType type, BigDecimal amount, LocalDate date,
                                 String note) {
        if (type == AccountEventType.TRANSFER_IN || type == AccountEventType.TRANSFER_OUT) {
            throw ApiException.badRequest("use_transfer", "Record transfers with the transfer form.");
        }
        if (type != AccountEventType.ADJUSTMENT && amount.signum() <= 0) {
            throw ApiException.badRequest("amount_not_positive", "Account movement amounts must be positive.");
        }
        return EventView.of(events.save(new AccountEvent(account(accountId), type, amount, date, note)));
    }

    /**
     * Two events in one transaction, linked by a transfer group: what left one
     * account and what arrived in the other (they differ across currencies).
     */
    @Transactional
    public UUID transfer(UUID fromAccountId, UUID toAccountId, BigDecimal fromAmount, BigDecimal toAmount,
                         LocalDate date, String note) {
        if (fromAccountId.equals(toAccountId)) {
            throw ApiException.badRequest("same_account", "Transfer accounts must be different.");
        }
        FinancialAccount from = account(fromAccountId);
        FinancialAccount to = account(toAccountId);
        UUID group = UUID.randomUUID();

        AccountEvent out = new AccountEvent(from, AccountEventType.TRANSFER_OUT, fromAmount, date, note);
        out.setCounterpartyAccount(to);
        out.setTransferGroupId(group);
        AccountEvent in = new AccountEvent(to, AccountEventType.TRANSFER_IN, toAmount, date, note);
        in.setCounterpartyAccount(from);
        in.setTransferGroupId(group);
        events.saveAll(List.of(out, in));
        return group;
    }

    /** Books the difference to the actual balance as one ADJUSTMENT, if there is any. */
    @Transactional
    public BigDecimal reconcile(UUID accountId, BigDecimal actualBalance, LocalDate date, String note) {
        FinancialAccount account = account(accountId);
        BigDecimal delta = actualBalance.subtract(balance(account, events.findByAccountId(accountId)));
        if (delta.abs().compareTo(RECONCILE_TOLERANCE) >= 0) {
            events.save(new AccountEvent(account, AccountEventType.ADJUSTMENT, delta, date,
                    note.isEmpty() ? "Balance reconciliation" : note));
        }
        return delta;
    }

    @Transactional
    public RateView upsertRate(String currency, LocalDate date, BigDecimal gelRate) {
        ExchangeRate rate = rates.findByCurrencyAndDate(currency, date)
                .orElseGet(() -> new ExchangeRate(currency, date, gelRate));
        rate.setGelRate(gelRate);
        rate = rates.save(rate);
        return new RateView(rate.getId(), rate.getCurrency(), rate.getDate(), rate.getGelRate());
    }

    private FinancialAccount account(UUID id) {
        return accounts.findById(id)
                .orElseThrow(() -> ApiException.notFound("account_not_found", "The account no longer exists."));
    }
}

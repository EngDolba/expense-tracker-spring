package com.dolbaia.ledger.account;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dolbaia.ledger.common.Ids;
import com.dolbaia.ledger.common.Text;

@RestController
@RequestMapping("/api")
public class AccountController {

    static final String CURRENCY = "^\\s*[A-Za-z]{3}\\s*$";

    public record AccountForm(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @NotNull @Pattern(regexp = CURRENCY, message = "must be a three-letter code") String currency,
            @Digits(integer = 16, fraction = 2) BigDecimal openingBalance) {}

    public record EventForm(
            @NotBlank String accountId,
            @NotNull AccountEventType type,
            @NotNull @Digits(integer = 16, fraction = 2) BigDecimal amount,
            @NotNull LocalDate date,
            @Size(max = 2000) String note) {}

    public record TransferForm(
            @NotBlank String fromAccountId,
            @NotBlank String toAccountId,
            @NotNull @Positive @Digits(integer = 16, fraction = 2) BigDecimal fromAmount,
            @NotNull @Positive @Digits(integer = 16, fraction = 2) BigDecimal toAmount,
            @NotNull LocalDate date,
            @Size(max = 2000) String note) {}

    public record ReconcileForm(
            @NotBlank String accountId,
            @NotNull @Digits(integer = 16, fraction = 2) BigDecimal actualBalance,
            @NotNull LocalDate date,
            @Size(max = 2000) String note) {}

    public record RateForm(
            @NotNull @Pattern(regexp = CURRENCY, message = "must be a three-letter code") String currency,
            @NotNull LocalDate date,
            @NotNull @DecimalMin(value = "0.00000001") @Digits(integer = 10, fraction = 8) BigDecimal gelRate) {}

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping("/accounts")
    AccountService.AccountsPage page() {
        return service.page();
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> createAccount(@Valid @RequestBody AccountForm form) {
        return Map.of("id", service.createAccount(form.name().trim(), Text.clean(form.description()),
                currency(form.currency()), form.openingBalance() == null ? BigDecimal.ZERO : form.openingBalance()));
    }

    @PostMapping("/accounts/events")
    @ResponseStatus(HttpStatus.CREATED)
    AccountService.EventView createEvent(@Valid @RequestBody EventForm form) {
        return service.createEvent(Ids.required(form.accountId()), form.type(), form.amount(), form.date(),
                Text.clean(form.note()));
    }

    @PostMapping("/accounts/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> transfer(@Valid @RequestBody TransferForm form) {
        return Map.of("transferGroupId", service.transfer(Ids.required(form.fromAccountId()),
                Ids.required(form.toAccountId()), form.fromAmount(), form.toAmount(), form.date(),
                Text.clean(form.note())));
    }

    @PostMapping("/accounts/reconcile")
    Map<String, BigDecimal> reconcile(@Valid @RequestBody ReconcileForm form) {
        return Map.of("delta", service.reconcile(Ids.required(form.accountId()), form.actualBalance(), form.date(),
                Text.clean(form.note())));
    }

    @PutMapping("/exchange-rates")
    AccountService.RateView upsertRate(@Valid @RequestBody RateForm form) {
        return service.upsertRate(currency(form.currency()), form.date(), form.gelRate());
    }

    private static String currency(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }
}

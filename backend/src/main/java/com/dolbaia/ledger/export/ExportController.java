package com.dolbaia.ledger.export;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dolbaia.ledger.account.AccountEvent;
import com.dolbaia.ledger.account.AccountEventRepository;
import com.dolbaia.ledger.expense.ExpenseEntry;
import com.dolbaia.ledger.expense.ExpenseEntryRepository;

/** CSV downloads of the expense register and every account movement, for Excel (BOM, CRLF). */
@RestController
public class ExportController {

    private final ExpenseEntryRepository expenses;
    private final AccountEventRepository events;
    private final Clock clock;

    public ExportController(ExpenseEntryRepository expenses, AccountEventRepository events, Clock clock) {
        this.expenses = expenses;
        this.events = events;
        this.clock = clock;
    }

    @GetMapping("/api/exports/expenses")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> expenses() {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("type", "amount", "date", "category", "recipient", "note", "tags", "source_code"));
        for (ExpenseEntry entry : expenses.findAllChronological()) {
            rows.add(List.of(entry.getType().name(), entry.getAmount().toPlainString(), entry.getDate().toString(),
                    entry.getCategory() == null ? "" : entry.getCategory().getName(), entry.getRecipient(),
                    entry.getNote(), String.join(", ", entry.getTags()), Objects.toString(entry.getSourceCode(), "")));
        }
        return csv("expenses", rows);
    }

    @GetMapping("/api/exports/accounts")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> accounts() {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("date", "account", "currency", "type", "amount", "counterparty", "note", "transfer_group"));
        for (AccountEvent event : events.findAllChronological()) {
            rows.add(List.of(event.getDate().toString(), event.getAccount().getName(),
                    event.getAccount().getCurrency(), event.getType().name(), event.getAmount().toPlainString(),
                    event.getCounterpartyAccount() == null ? "" : event.getCounterpartyAccount().getName(),
                    event.getNote(), Objects.toString(event.getTransferGroupId(), "")));
        }
        return csv("accounts", rows);
    }

    private ResponseEntity<byte[]> csv(String name, List<List<Object>> rows) {
        String body = "﻿" + rows.stream()
                .map(row -> row.stream().map(ExportController::cell).collect(Collectors.joining(",")))
                .collect(Collectors.joining("\r\n"));
        String fileName = name + "-" + LocalDate.now(clock) + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .body(body.getBytes(StandardCharsets.UTF_8));
    }

    /** Every cell quoted, quotes doubled. */
    static String cell(Object value) {
        return "\"" + Objects.toString(value, "").replace("\"", "\"\"") + "\"";
    }
}

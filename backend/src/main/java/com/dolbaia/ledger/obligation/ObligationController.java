package com.dolbaia.ledger.obligation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dolbaia.ledger.common.Ids;
import com.dolbaia.ledger.common.Text;

@RestController
@RequestMapping("/api/obligations")
public class ObligationController {

    public record PartyForm(@NotBlank @Size(max = 120) String name, @Size(max = 2000) String note) {}

    public record CaseForm(@NotBlank String partyId, @NotBlank @Size(max = 120) String name,
                           @Size(max = 2000) String note) {}

    public record EntryForm(
            @NotBlank String partyId,
            String caseId,
            @NotNull ObligationType type,
            @NotNull @Positive @Digits(integer = 16, fraction = 2) BigDecimal amount,
            @NotNull LocalDate date,
            String categoryId,
            @Size(max = 2000) String note,
            String tags) {}

    private final ObligationService service;

    public ObligationController(ObligationService service) {
        this.service = service;
    }

    @GetMapping
    ObligationService.ObligationsPage page() {
        return service.page();
    }

    @PostMapping("/parties")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> createParty(@Valid @RequestBody PartyForm form) {
        return Map.of("id", service.createParty(form.name().trim(), Text.clean(form.note())));
    }

    @PostMapping("/cases")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> createCase(@Valid @RequestBody CaseForm form) {
        return Map.of("id", service.createCase(Ids.required(form.partyId()), form.name().trim(),
                Text.clean(form.note())));
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    ObligationService.EntryView createEntry(@Valid @RequestBody EntryForm form) {
        return service.createEntry(new ObligationService.NewEntry(Ids.required(form.partyId()),
                Ids.optional(form.caseId()), form.type(), form.amount(), form.date(),
                Ids.optional(form.categoryId()), Text.clean(form.note()), Text.tags(form.tags())));
    }

    @DeleteMapping("/entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteEntry(@PathVariable UUID id) {
        service.deleteEntry(id);
    }
}

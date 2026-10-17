package com.dolbaia.ledger.obligation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dolbaia.ledger.category.CategoryService;
import com.dolbaia.ledger.category.CategoryView;
import com.dolbaia.ledger.common.ApiException;

/**
 * Money advanced for, and repaid by, companies and people. Outlays and
 * reimbursements never post to expenses or accounts.
 */
@Service
public class ObligationService {

    static final int REGISTER_SIZE = 300;

    public record PartyView(UUID id, String name, String note, boolean archived, BigDecimal outstanding,
                            Instant createdAt, Instant updatedAt) {}

    public record CaseView(UUID id, UUID partyId, String name, String note, boolean closed, Instant createdAt) {}

    public record PartyRef(UUID id, String name) {}

    public record CaseRef(UUID id, String name) {}

    /** {@code case} is a Java keyword; the JSON keeps the name the frontend reads. */
    public record EntryView(UUID id, UUID partyId, PartyRef party, UUID caseId,
                            @JsonProperty("case") CaseRef obligationCase,
                            ObligationType type, BigDecimal amount, LocalDate date, UUID categoryId,
                            CategoryView category, String note, List<String> tags, Instant createdAt) {}

    public record ObligationsPage(List<PartyView> parties, List<CaseView> cases, List<EntryView> entries,
                                  List<CategoryView> categories) {}

    public record NewEntry(UUID partyId, UUID caseId, ObligationType type, BigDecimal amount, LocalDate date,
                           UUID categoryId, String note, List<String> tags) {}

    private final ObligationPartyRepository parties;
    private final ObligationCaseRepository cases;
    private final ObligationEntryRepository entries;
    private final CategoryService categories;

    public ObligationService(ObligationPartyRepository parties, ObligationCaseRepository cases,
                             ObligationEntryRepository entries, CategoryService categories) {
        this.parties = parties;
        this.cases = cases;
        this.entries = entries;
        this.categories = categories;
    }

    /**
     * The register (latest entries) and each active party's outstanding
     * balance, computed over all of that party's entries.
     */
    @Transactional(readOnly = true)
    public ObligationsPage page() {
        Map<UUID, BigDecimal> outstanding = new HashMap<>();
        for (var row : entries.outstandingByParty()) outstanding.put(row.getPartyId(), row.getOutstanding());

        return new ObligationsPage(
                parties.findActive().stream()
                        .map(party -> new PartyView(party.getId(), party.getName(), party.getNote(),
                                party.isArchived(), outstanding.getOrDefault(party.getId(), BigDecimal.ZERO),
                                party.getCreatedAt(), party.getUpdatedAt()))
                        .toList(),
                cases.findOpen().stream()
                        .map(item -> new CaseView(item.getId(), item.getParty().getId(), item.getName(),
                                item.getNote(), item.isClosed(), item.getCreatedAt()))
                        .toList(),
                entries.findLatest(Limit.of(REGISTER_SIZE)).stream().map(ObligationService::view).toList(),
                categories.active());
    }

    private static EntryView view(ObligationEntry entry) {
        ObligationParty party = entry.getParty();
        ObligationCase item = entry.getObligationCase();
        CategoryView category = CategoryView.of(entry.getCategory());
        return new EntryView(entry.getId(), party.getId(), new PartyRef(party.getId(), party.getName()),
                item == null ? null : item.getId(), item == null ? null : new CaseRef(item.getId(), item.getName()),
                entry.getType(), entry.getAmount(), entry.getDate(), category == null ? null : category.id(),
                category, entry.getNote(), List.copyOf(entry.getTags()), entry.getCreatedAt());
    }

    @Transactional
    public UUID createParty(String name, String note) {
        return parties.save(new ObligationParty(name, note)).getId();
    }

    @Transactional
    public UUID createCase(UUID partyId, String name, String note) {
        return cases.save(new ObligationCase(party(partyId), name, note)).getId();
    }

    @Transactional
    public EntryView createEntry(NewEntry form) {
        ObligationParty party = party(form.partyId());
        ObligationCase item = null;
        if (form.caseId() != null) {
            item = cases.findById(form.caseId())
                    .orElseThrow(() -> ApiException.notFound("case_not_found", "The case no longer exists."));
            if (!item.getParty().getId().equals(party.getId())) {
                throw ApiException.badRequest("case_party_mismatch", "The case belongs to another party.");
            }
        }
        ObligationEntry entry = new ObligationEntry(party, item, form.type(), form.amount(), form.date());
        entry.setCategory(categories.findOptional(form.categoryId()));
        entry.setNote(form.note());
        entry.setTags(form.tags());
        return view(entries.save(entry));
    }

    @Transactional
    public void deleteEntry(UUID id) {
        entries.delete(entries.findById(id)
                .orElseThrow(() -> ApiException.notFound("obligation_not_found", "The entry no longer exists.")));
    }

    private ObligationParty party(UUID id) {
        return parties.findById(id)
                .orElseThrow(() -> ApiException.notFound("party_not_found", "The party no longer exists."));
    }
}

package com.dolbaia.ledger.imports;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import com.dolbaia.ledger.category.Category;
import com.dolbaia.ledger.category.CategoryCode;
import com.dolbaia.ledger.category.CategoryCodeRepository;
import com.dolbaia.ledger.category.CategoryRepository;
import com.dolbaia.ledger.common.ApiException;
import com.dolbaia.ledger.expense.ExpenseEntry;
import com.dolbaia.ledger.expense.ExpenseEntryRepository;
import com.dolbaia.ledger.imports.WorkbookParser.CategoryMapping;
import com.dolbaia.ledger.imports.WorkbookParser.ParsedRow;
import com.dolbaia.ledger.imports.WorkbookParser.ParsedWorkbook;

/**
 * The staged workbook import: upload parses and stores every row for review;
 * commit turns them into expense entries in one transaction, creating any
 * missing categories along the way.
 */
@Service
public class ImportService {

    static final int LIST_SIZE = 30;
    /** Joins category path parts into one map key; it cannot occur in a name. */
    private static final String PATH_SEPARATOR = "␟";

    public record BatchSummary(UUID id, String fileName, ImportStatus status, int totalRows, int expenseRows,
                               int incomeRows, int errorRows, int mappingCount, String sourceSheet,
                               String fileHash, Instant committedAt, Instant createdAt) {

        static BatchSummary of(ImportBatch batch) {
            return new BatchSummary(batch.getId(), batch.getFileName(), batch.getStatus(), batch.getTotalRows(),
                    batch.getExpenseRows(), batch.getIncomeRows(), batch.getErrorRows(), batch.getMappingCount(),
                    batch.getSourceSheet(), batch.getFileHash(), batch.getCommittedAt(), batch.getCreatedAt());
        }
    }

    public record RowView(UUID id, int rowNumber, ParsedRow normalized, List<String> errors) {}

    public record BatchDetail(BatchSummary batch, List<RowView> rows) {}

    public record Uploaded(UUID id, boolean duplicate) {}

    private final ImportBatchRepository batches;
    private final CategoryRepository categories;
    private final CategoryCodeRepository codes;
    private final ExpenseEntryRepository expenses;
    private final JsonMapper json;
    private final Clock clock;

    public ImportService(ImportBatchRepository batches, CategoryRepository categories, CategoryCodeRepository codes,
                         ExpenseEntryRepository expenses, JsonMapper json, Clock clock) {
        this.batches = batches;
        this.categories = categories;
        this.codes = codes;
        this.expenses = expenses;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<BatchSummary> latest() {
        return batches.findLatest(Limit.of(LIST_SIZE)).stream().map(BatchSummary::of).toList();
    }

    @Transactional(readOnly = true)
    public BatchDetail detail(UUID id) {
        ImportBatch batch = batch(id);
        return new BatchDetail(BatchSummary.of(batch), batch.getRows().stream()
                .map(row -> new RowView(row.getId(), row.getRowNumber(), json.readValue(row.getNormalized(),
                        ParsedRow.class), List.copyOf(row.getErrors())))
                .toList());
    }

    /** Stages a workbook. The same file (by SHA-256) is never staged twice. */
    @Transactional
    public Uploaded upload(byte[] bytes, String fileName) {
        String hash = sha256(bytes);
        Optional<ImportBatch> existing = batches.findByFileHash(hash);
        if (existing.isPresent()) return new Uploaded(existing.get().getId(), true);

        ParsedWorkbook parsed = WorkbookParser.parse(bytes, fileName);
        ImportBatch batch = new ImportBatch(hash, fileName);
        batch.setSourceSheet(parsed.sourceSheet());
        batch.setDictionary(json.writeValueAsString(parsed.mappings()));
        batch.setSummary(parsed.summary());
        for (ParsedRow row : parsed.rows()) {
            batch.addRow(new ImportRow(batch, row.rowNumber(), json.writeValueAsString(row), row.errors()));
        }
        return new Uploaded(batches.save(batch).getId(), false);
    }

    /**
     * Commits every staged row as an expense entry, all or nothing. Dictionary
     * codes are mapped to categories (created when missing) on the way.
     */
    @Transactional
    public BatchSummary commit(UUID id) {
        ImportBatch batch = batch(id);
        if (batch.getStatus() != ImportStatus.STAGED) {
            throw ApiException.conflict("import_not_staged", "This import is not staged.");
        }
        if (batch.getErrorRows() > 0) {
            throw ApiException.badRequest("import_has_errors", "Resolve import errors before committing.");
        }

        Map<String, Category> categoryByPath = new HashMap<>();
        List<CategoryMapping> mappings = batch.getDictionary() == null ? List.of()
                : json.readValue(batch.getDictionary(), new TypeReference<List<CategoryMapping>>() {});
        for (CategoryMapping mapping : mappings) {
            Category category = categoryForPath(mapping.path(), categoryByPath);
            if (category == null) continue;
            CategoryCode code = codes.findByCode(mapping.code())
                    .orElseGet(() -> new CategoryCode(mapping.code(), category));
            code.setCategory(category);
            codes.save(code);
        }

        for (ImportRow staged : batch.getRows()) {
            ParsedRow row = json.readValue(staged.getNormalized(), ParsedRow.class);
            ExpenseEntry entry = new ExpenseEntry(row.type(), row.amount(), LocalDate.parse(row.date()));
            entry.setCategory(categoryForPath(row.categoryPath(), categoryByPath));
            entry.setRecipient(row.recipient());
            entry.setNote(row.note());
            entry.setTags(row.tags());
            entry.setSourceCode(row.sourceCode());
            entry.setImportBatch(batch);
            entry.setSourceRow(row.rowNumber());
            expenses.save(entry);
        }

        batch.markCommitted(clock.instant());
        return BatchSummary.of(batch);
    }

    /** Walks the path from the root, creating each missing level at the end of its siblings. */
    private Category categoryForPath(List<String> path, Map<String, Category> cache) {
        if (path.isEmpty()) return null;
        String key = String.join(PATH_SEPARATOR, path);
        Category cached = cache.get(key);
        if (cached != null) return cached;

        Category current = null;
        for (String name : path) {
            UUID parentId = current == null ? null : current.getId();
            Category parent = current;
            current = categories.findChild(parentId, name).orElseGet(() ->
                    categories.saveAndFlush(new Category(name, parent, (int) categories.countSiblings(parentId))));
        }
        cache.put(key, current);
        return current;
    }

    private ImportBatch batch(UUID id) {
        return batches.findById(id)
                .orElseThrow(() -> ApiException.notFound("import_not_found", "The import no longer exists."));
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

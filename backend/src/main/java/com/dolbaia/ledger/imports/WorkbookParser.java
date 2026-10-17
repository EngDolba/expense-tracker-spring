package com.dolbaia.ledger.imports;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.dolbaia.ledger.category.CategoryTree;
import com.dolbaia.ledger.expense.ExpenseType;

/**
 * Reads the XLDR expense workbook (.xlsx/.xlsm). Port of the Next.js app's
 * src/lib/importer.ts. Macros are never executed and formulas never
 * evaluated: a formula contributes its cached value, or nothing, in which case
 * the category comes from the dictionary sheet by code. Amounts are signed in
 * the sheet: negative is an expense, positive income.
 */
public final class WorkbookParser {

    private WorkbookParser() {}

    public static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    public record CategoryMapping(String code, List<String> path) {}

    public record ParsedRow(int rowNumber, ExpenseType type, BigDecimal amount, String date,
                            List<String> categoryPath, String recipient, String note, List<String> tags,
                            String sourceCode, List<String> errors) {}

    public record Summary(int totalRows, int expenseRows, int incomeRows, int errorRows, int mappingCount) {}

    public record ParsedWorkbook(String sourceSheet, List<ParsedRow> rows, List<CategoryMapping> mappings,
                                 Summary summary) {}

    /** A workbook that cannot be imported; the message is shown to the user. */
    public static class InvalidWorkbookException extends RuntimeException {
        public InvalidWorkbookException(String message) {
            super(message);
        }
    }

    private static final Locale KA = CategoryTree.GEORGIAN;

    private static final List<String> CODE = List.of("კოდი", "code");
    private static final List<String> TOP = List.of("ზეკატეგორია", "top category", "super category");
    private static final List<String> CATEGORY = List.of("კატეგორია", "category");
    private static final List<String> SUBCATEGORY = List.of("ქვეკატეგორია", "subcategory", "sub category");
    private static final List<String> AMOUNT = List.of("რაოდენობა", "თანხა", "amount");
    private static final List<String> NOTE = List.of("შენიშვნა", "note", "description");
    private static final List<String> DATE = List.of("თარიღი", "date");
    private static final List<String> RECIPIENT = List.of("მიმღები", "recipient", "merchant", "payee");
    private static final List<String> SPECIAL =
            List.of("სპეცკატეგორია", "სპეც. კატეგორია", "special category", "tags");

    public static ParsedWorkbook parse(byte[] bytes, String fileName) {
        if (bytes.length > MAX_FILE_BYTES) {
            throw new InvalidWorkbookException("The workbook exceeds the 10 MB upload limit.");
        }
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).matches(".*\\.(xlsx|xlsm)$")) {
            throw new InvalidWorkbookException("Only .xlsx and .xlsm workbooks are supported.");
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            return parse(workbook);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof InvalidWorkbookException invalid) throw invalid;
            throw new InvalidWorkbookException("The workbook could not be read.");
        }
    }

    private static ParsedWorkbook parse(XSSFWorkbook workbook) {
        Sheet sheet = findTransactionSheet(workbook);
        if (sheet == null) {
            throw new InvalidWorkbookException("No transaction sheet with code, amount, and date columns was found.");
        }

        List<CategoryMapping> mappings = parseMappings(findDictionarySheet(workbook));
        Map<String, List<String>> pathByCode = new HashMap<>();
        for (CategoryMapping mapping : mappings) pathByCode.put(mapping.code(), mapping.path());

        List<String> headers = headers(sheet);
        Integer code = column(headers, CODE);
        Integer top = column(headers, TOP);
        Integer category = column(headers, CATEGORY);
        Integer subcategory = column(headers, SUBCATEGORY);
        Integer amount = column(headers, AMOUNT);
        Integer note = column(headers, NOTE);
        Integer date = column(headers, DATE);
        Integer recipient = column(headers, RECIPIENT);
        Integer special = column(headers, SPECIAL);
        if (code == null || amount == null || date == null) {
            throw new InvalidWorkbookException("The transaction sheet is missing required columns.");
        }

        List<ParsedRow> rows = new ArrayList<>();
        for (Row row : sheet) {
            int rowNumber = row.getRowNum() + 1;
            if (rowNumber == 1 || isBlank(row)) continue;

            BigDecimal rawAmount = numberValue(cell(row, amount));
            String sourceCode = text(cell(row, code));
            String parsedDate = excelDate(cell(row, date));
            List<String> directPath = new ArrayList<>();
            for (Integer column : Arrays.asList(top, category, subcategory)) {
                String part = column == null ? "" : text(cell(row, column));
                if (!part.isEmpty() && !part.equals("0")) directPath.add(part);
            }
            List<String> categoryPath = !directPath.isEmpty()
                    ? directPath
                    : pathByCode.getOrDefault(sourceCode, List.of());

            List<String> errors = new ArrayList<>();
            if (rawAmount == null || rawAmount.signum() == 0) errors.add("Amount must be non-zero.");
            if (parsedDate == null) errors.add("Date could not be parsed.");
            if (categoryPath.isEmpty()) {
                errors.add("Unknown category code: " + (sourceCode.isEmpty() ? "(blank)" : sourceCode) + ".");
            }

            if (rawAmount == null && sourceCode.isEmpty() && text(cell(row, note == null ? 1 : note)).isEmpty()
                    && parsedDate == null) {
                continue;
            }

            BigDecimal signed = rawAmount == null ? BigDecimal.ZERO : rawAmount;
            List<String> tags = special == null ? List.of() : Arrays.stream(text(cell(row, special)).split(","))
                    .map(String::trim)
                    .filter(tag -> !tag.isEmpty())
                    .toList();
            rows.add(new ParsedRow(
                    rowNumber,
                    signed.signum() < 0 ? ExpenseType.EXPENSE : ExpenseType.INCOME,
                    signed.abs(),
                    parsedDate == null ? "" : parsedDate,
                    List.copyOf(categoryPath),
                    recipient == null ? "" : text(cell(row, recipient)),
                    note == null ? "" : text(cell(row, note)),
                    tags,
                    sourceCode,
                    List.copyOf(errors)));
        }

        Summary summary = new Summary(
                rows.size(),
                (int) rows.stream().filter(row -> row.type() == ExpenseType.EXPENSE).count(),
                (int) rows.stream().filter(row -> row.type() == ExpenseType.INCOME).count(),
                (int) rows.stream().filter(row -> !row.errors().isEmpty()).count(),
                mappings.size());
        return new ParsedWorkbook(sheet.getSheetName(), List.copyOf(rows), mappings, summary);
    }

    // ---- Sheets and columns ---------------------------------------------------------

    private static Sheet findTransactionSheet(XSSFWorkbook workbook) {
        List<Sheet> candidates = new ArrayList<>();
        Sheet preferred = null;
        for (Sheet sheet : workbook) {
            String name = sheet.getSheetName().toLowerCase(KA);
            if (preferred == null && (name.equals("ჩანაწერები") || name.equals("data"))) preferred = sheet;
        }
        if (preferred != null) candidates.add(preferred);
        for (Sheet sheet : workbook) if (sheet != preferred) candidates.add(sheet);

        for (Sheet sheet : candidates) {
            List<String> headers = headers(sheet);
            if (column(headers, CODE) != null && column(headers, AMOUNT) != null && column(headers, DATE) != null) {
                return sheet;
            }
        }
        return null;
    }

    private static Sheet findDictionarySheet(XSSFWorkbook workbook) {
        for (Sheet sheet : workbook) {
            if (sheet.getSheetName().toLowerCase(KA).equals("ლექსიკონი")) return sheet;
        }
        for (Sheet sheet : workbook) {
            List<String> headers = headers(sheet);
            if (column(headers, CODE) != null && column(headers, TOP) != null && column(headers, CATEGORY) != null) {
                return sheet;
            }
        }
        return null;
    }

    private static List<CategoryMapping> parseMappings(Sheet sheet) {
        if (sheet == null) return List.of();
        List<String> headers = headers(sheet);
        int code = orDefault(column(headers, CODE), 1);
        int top = orDefault(column(headers, TOP), 2);
        int category = orDefault(column(headers, CATEGORY), 3);
        int subcategory = orDefault(column(headers, SUBCATEGORY), 4);

        List<CategoryMapping> mappings = new ArrayList<>();
        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;
            String value = text(cell(row, code));
            if (value.isEmpty()) continue;
            List<String> path = new ArrayList<>();
            for (int column : new int[] {top, category, subcategory}) {
                String part = text(cell(row, column));
                if (!part.isEmpty() && !part.equals("0")) path.add(part);
            }
            if (!path.isEmpty()) mappings.add(new CategoryMapping(value, List.copyOf(path)));
        }
        return List.copyOf(mappings);
    }

    /** Normalised header texts of row 1, at least nine columns wide. */
    private static List<String> headers(Sheet sheet) {
        Row first = sheet.getRow(0);
        int width = Math.max(first == null ? 0 : Math.max(first.getLastCellNum(), 0), 9);
        List<String> headers = new ArrayList<>(width);
        for (int index = 0; index < width; index++) {
            Cell cell = first == null ? null : first.getCell(index);
            headers.add(text(cell).toLowerCase(KA).replaceAll("\\s+", " "));
        }
        return headers;
    }

    /** The 1-based column whose header is one of {@code names}. */
    private static Integer column(List<String> headers, List<String> names) {
        for (int index = 0; index < headers.size(); index++) {
            for (String name : names) {
                if (headers.get(index).equals(name.toLowerCase(KA))) return index + 1;
            }
        }
        return null;
    }

    private static int orDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static Cell cell(Row row, int oneBasedColumn) {
        return row.getCell(oneBasedColumn - 1);
    }

    private static boolean isBlank(Row row) {
        for (Cell cell : row) if (!text(cell).isEmpty()) return false;
        return true;
    }

    // ---- Cell values -----------------------------------------------------------------

    /** The type the cell's value has, looking through a formula to its cached result. */
    private static CellType valueType(Cell cell) {
        if (cell == null) return CellType.BLANK;
        CellType type = cell.getCellType();
        if (type != CellType.FORMULA) return type;
        // A formula saved without a cached value (<v>) contributes nothing.
        if (cell instanceof XSSFCell xssf && !xssf.getCTCell().isSetV()) return CellType.BLANK;
        return cell.getCachedFormulaResultType();
    }

    private static boolean isDate(Cell cell) {
        return valueType(cell) == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell);
    }

    static String text(Cell cell) {
        return switch (valueType(cell)) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> isDate(cell)
                    ? cell.getLocalDateTimeCellValue().toInstant(ZoneOffset.UTC).toString()
                    : jsNumber(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    /** Formats a double the way JavaScript's String(number) does for ordinary values: 3001, 2.5. */
    private static String jsNumber(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) return String.valueOf((long) value);
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static final Pattern NOT_NUMERIC = Pattern.compile("[^\\d.-]");

    /**
     * A numeric cell's value, or the cell's text with everything but digits,
     * dots and minus signs removed. Mirrors JavaScript's Number(): an empty
     * result is 0, an unparseable one null.
     */
    static BigDecimal numberValue(Cell cell) {
        if (valueType(cell) == CellType.NUMERIC && !isDate(cell)) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        String stripped = NOT_NUMERIC.matcher(text(cell)).replaceAll("");
        if (stripped.isEmpty()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(stripped.endsWith(".") ? stripped + "0" : stripped);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static final Pattern ISO_PREFIX = Pattern.compile("^(\\d{4})-(\\d{1,2})-(\\d{1,2})");
    private static final Pattern DAY_FIRST = Pattern.compile("^(\\d{1,2})[./](\\d{1,2})[./](\\d{2,4})$");
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);
    private static final List<DateTimeFormatter> FALLBACK_FORMATS = List.of(
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            formatter("yyyy/M/d"),
            formatter("MMMM d, yyyy"),
            formatter("MMM d, yyyy"),
            formatter("d MMMM yyyy"),
            formatter("d MMM yyyy"),
            DateTimeFormatter.RFC_1123_DATE_TIME);

    private static DateTimeFormatter formatter(String pattern) {
        return new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(Locale.ENGLISH);
    }

    /** An ISO yyyy-MM-dd date from a date cell, an Excel serial number or common text forms. */
    static String excelDate(Cell cell) {
        CellType type = valueType(cell);
        if (type == CellType.NUMERIC) {
            if (isDate(cell)) {
                LocalDateTime value = cell.getLocalDateTimeCellValue();
                return value == null ? null : value.toLocalDate().toString();
            }
            return EXCEL_EPOCH.plusDays(Math.round(cell.getNumericCellValue())).toString();
        }

        String raw = text(cell);
        Matcher match = ISO_PREFIX.matcher(raw);
        if (match.find()) {
            return isoDate(match.group(1), match.group(2), match.group(3));
        }
        match = DAY_FIRST.matcher(raw);
        if (match.matches()) {
            String year = match.group(3).length() == 2 ? "20" + match.group(3) : match.group(3);
            return isoDate(year, match.group(2), match.group(1));
        }
        for (DateTimeFormatter format : FALLBACK_FORMATS) {
            try {
                return LocalDate.from(format.parse(raw)).toString();
            } catch (DateTimeParseException ignored) {
                // try the next form
            } catch (RuntimeException ignored) {
                // a form without a full date
            }
        }
        return null;
    }

    /** Zero-padded as in the sheet; rejects impossible dates such as 31.02. */
    private static String isoDate(String year, String month, String day) {
        String value = year + "-" + pad(month) + "-" + pad(day);
        try {
            return LocalDate.parse(value).toString();
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static String pad(String value) {
        return value.length() == 1 ? "0" + value : value;
    }
}

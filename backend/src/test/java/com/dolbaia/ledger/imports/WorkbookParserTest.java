package com.dolbaia.ledger.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.dolbaia.ledger.expense.ExpenseType;
import com.dolbaia.ledger.imports.WorkbookParser.InvalidWorkbookException;
import com.dolbaia.ledger.imports.WorkbookParser.ParsedWorkbook;
import com.dolbaia.ledger.imports.WorkbookParser.Summary;

/** Port of importer.test.ts "Excel import parser", plus the date and number forms. */
class WorkbookParserTest {

    /** The XLDR layout: lookup formulas saved without cached values, and a dictionary sheet. */
    static byte[] workbookFixture() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));

            XSSFSheet transactions = workbook.createSheet("ჩანაწერები");
            row(transactions, 0, "კოდი", "ზეკატეგორია", "კატეგორია", "ქვეკატეგორია", "რაოდენობა", "შენიშვნა",
                    "თარიღი", "მიმღები", "სპეცკატეგორია");

            Row ride = transactions.createRow(1);
            ride.createCell(0).setCellValue(3001);
            ride.createCell(1).setCellFormula("A2&\"\"");
            ride.createCell(2).setCellFormula("A2&\"\"");
            ride.createCell(3).setCellFormula("A2&\"\"");
            ride.createCell(4).setCellValue(-10);
            ride.createCell(5).setCellValue("Ride");
            ride.createCell(6).setCellValue(LocalDate.of(2024, 6, 4));
            ride.getCell(6).setCellStyle(dateStyle);
            ride.createCell(7).setCellValue("Metro");
            ride.createCell(8).setCellValue("work, commute");

            Row repayment = transactions.createRow(2);
            repayment.createCell(0).setCellValue(9001);
            repayment.createCell(1).setCellValue("");
            repayment.createCell(4).setCellValue(25);
            repayment.createCell(5).setCellValue("Repayment");
            repayment.createCell(6).setCellValue(LocalDate.of(2024, 6, 5));
            repayment.getCell(6).setCellStyle(dateStyle);
            repayment.createCell(7).setCellValue("Company");

            XSSFSheet dictionary = workbook.createSheet("ლექსიკონი");
            row(dictionary, 0, "კოდი", "ზეკატეგორია", "კატეგორია", "ქვეკატეგორია");
            Row transport = dictionary.createRow(1);
            transport.createCell(0).setCellValue(3001);
            transport.createCell(1).setCellValue("Transport");
            transport.createCell(2).setCellValue("Public");
            transport.createCell(3).setCellValue("");
            Row income = dictionary.createRow(2);
            income.createCell(0).setCellValue(9001);
            income.createCell(1).setCellValue("Income");
            income.createCell(2).setCellValue("Repayment");

            return bytes(workbook);
        }
    }

    static void row(XSSFSheet sheet, int index, String... values) {
        Row row = sheet.createRow(index);
        for (int column = 0; column < values.length; column++) row.createCell(column).setCellValue(values[column]);
    }

    static byte[] bytes(XSSFWorkbook workbook) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        return out.toByteArray();
    }

    @Test
    void usesTheDictionaryFallbackWhenFormulasHaveNoCachedResult() throws IOException {
        ParsedWorkbook result = WorkbookParser.parse(workbookFixture(), "fixture.xlsm");

        assertThat(result.summary()).isEqualTo(new Summary(2, 1, 1, 0, 2));
        assertThat(result.sourceSheet()).isEqualTo("ჩანაწერები");
        assertThat(result.rows().get(0).categoryPath()).containsExactly("Transport", "Public");
        assertThat(result.rows().get(0).amount()).isEqualByComparingTo("10");
        assertThat(result.rows().get(0).type()).isEqualTo(ExpenseType.EXPENSE);
        assertThat(result.rows().get(0).date()).isEqualTo("2024-06-04");
        assertThat(result.rows().get(0).sourceCode()).isEqualTo("3001");
        assertThat(result.rows().get(0).tags()).containsExactly("work", "commute");
        assertThat(result.rows().get(0).rowNumber()).isEqualTo(2);
        assertThat(result.rows().get(1).type()).isEqualTo(ExpenseType.INCOME);
        assertThat(result.rows().get(1).recipient()).isEqualTo("Company");
    }

    @Test
    void rejectsUnsupportedAndOversizedFiles() {
        assertThatThrownBy(() -> WorkbookParser.parse("x".getBytes(), "data.csv"))
                .isInstanceOf(InvalidWorkbookException.class).hasMessageContaining("Only .xlsx and .xlsm");
        assertThatThrownBy(() -> WorkbookParser.parse(new byte[10 * 1024 * 1024 + 1], "data.xlsx"))
                .isInstanceOf(InvalidWorkbookException.class).hasMessageContaining("10 MB");
    }

    @Test
    void rejectsFilesThatAreNotWorkbooks() {
        assertThatThrownBy(() -> WorkbookParser.parse("not a zip".getBytes(), "data.xlsx"))
                .isInstanceOf(InvalidWorkbookException.class).hasMessage("The workbook could not be read.");
    }

    @Test
    void rejectsWorkbooksWithoutATransactionSheet() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            row(workbook.createSheet("Other"), 0, "a", "b");
            byte[] bytes = bytes(workbook);
            assertThatThrownBy(() -> WorkbookParser.parse(bytes, "data.xlsx"))
                    .hasMessageContaining("No transaction sheet");
        }
    }

    @Test
    void readsEnglishHeadersTextDatesSerialDatesAndFlagsBadRows() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet data = workbook.createSheet("Data");
            row(data, 0, "Code", "Top category", "Category", "Subcategory", "Amount", "Note", "Date");
            row(data, 1, "1", "Food", "Groceries", "0", "-12.50 GEL", "Shop", "04.06.24");
            row(data, 2, "2", "Food", "", "", "-1", "", "2024-6-7");
            Row serial = data.createRow(3);
            serial.createCell(0).setCellValue("3");
            serial.createCell(1).setCellValue("Food");
            serial.createCell(4).setCellValue(-3);
            serial.createCell(6).setCellValue(45447); // 2024-06-04
            row(data, 4, "4", "", "", "", "0", "Zero", "someday");

            ParsedWorkbook result = WorkbookParser.parse(bytes(workbook), "data.xlsx");

            assertThat(result.rows()).hasSize(4);
            assertThat(result.rows().get(0).amount()).isEqualByComparingTo("12.50");
            assertThat(result.rows().get(0).date()).isEqualTo("2024-06-04");
            assertThat(result.rows().get(0).categoryPath()).containsExactly("Food", "Groceries");
            assertThat(result.rows().get(1).date()).isEqualTo("2024-06-07");
            assertThat(result.rows().get(2).date()).isEqualTo("2024-06-04");
            assertThat(result.rows().get(3).errors()).containsExactly(
                    "Amount must be non-zero.", "Date could not be parsed.", "Unknown category code: 4.");
            assertThat(result.summary().errorRows()).isEqualTo(1);
            assertThat(result.mappings()).isEqualTo(List.of(
                    new WorkbookParser.CategoryMapping("1", List.of("Food", "Groceries")),
                    new WorkbookParser.CategoryMapping("2", List.of("Food")),
                    new WorkbookParser.CategoryMapping("3", List.of("Food"))));
        }
    }
}

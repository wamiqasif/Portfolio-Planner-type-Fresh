package com.org.apitests.portfolio;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Shared Apache POI plumbing for the two Portfolio Planner report writers
 * ({@link PlannerFieldRulesReportWriter}, {@link BusinessValidationReportWriter})
 * - both need the identical "one .xlsx workbook, a Summary sheet plus a
 * header-frozen/auto-filtered Detail sheet" shape, so it lives here once
 * rather than duplicated in each.
 *
 * <p>Every cell is written as a plain string (matching what the row records
 * already hold - every field is a {@code String}), truncated to Excel's own
 * hard per-cell limit ({@link #MAX_CELL_LENGTH}) - a raw API response body
 * (the {@code Description}/{@code *Response} columns) can exceed it, and
 * POI throws {@code IllegalArgumentException} rather than truncating for
 * you.
 */
final class PortfolioExcelReportSupport {

    /** Excel's own hard per-cell text limit (32,767 chars) - not a POI-configurable value. */
    private static final int MAX_CELL_LENGTH = 32767;
    private static final String TRUNCATION_SUFFIX = "...[truncated]";

    private PortfolioExcelReportSupport() {
    }

    static Workbook newWorkbook() {
        return new XSSFWorkbook();
    }

    /** A plain-text "label: value" style sheet for the summary counts - deliberately not a header+rows table, since it's a handful of standalone lines, not tabular data. */
    static void writeSummarySheet(Workbook workbook, String sheetName, List<String> lines) {
        Sheet sheet = workbook.createSheet(sheetName);
        for (int r = 0; r < lines.size(); r++) {
            sheet.createRow(r).createCell(0).setCellValue(lines.get(r));
        }
        sheet.setColumnWidth(0, 80 * 256);
    }

    /** The header row (bold, frozen, auto-filtered) followed by one row per entry in {@code rows} - {@code rows.get(i).size()} must equal {@code header.size()} for every {@code i}, so a header/data column mismatch fails loudly here instead of silently misaligning in the workbook. */
    static void writeDetailSheet(Workbook workbook, String sheetName, List<String> header, List<List<String>> rows) {
        Sheet sheet = workbook.createSheet(sheetName);

        CellStyle headerStyle = boldHeaderStyle(workbook);
        Row headerRow = sheet.createRow(0);
        for (int c = 0; c < header.size(); c++) {
            Cell cell = headerRow.createCell(c);
            cell.setCellValue(header.get(c));
            cell.setCellStyle(headerStyle);
        }

        for (int r = 0; r < rows.size(); r++) {
            List<String> values = rows.get(r);
            if (values.size() != header.size()) {
                throw new IllegalStateException("Detail row " + r + " has " + values.size()
                        + " values but the header has " + header.size() + " columns - " + sheetName);
            }
            Row row = sheet.createRow(r + 1);
            for (int c = 0; c < values.size(); c++) {
                row.createCell(c).setCellValue(truncate(values.get(c)));
            }
        }

        sheet.createFreezePane(0, 1);
        if (!rows.isEmpty()) {
            sheet.setAutoFilter(new CellRangeAddress(0, rows.size(), 0, header.size() - 1));
        }
    }

    static void save(Workbook workbook, Path path) throws IOException {
        try (FileOutputStream out = new FileOutputStream(path.toFile())) {
            workbook.write(out);
        } finally {
            workbook.close();
        }
    }

    private static CellStyle boldHeaderStyle(Workbook workbook) {
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(boldFont);
        return style;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() <= MAX_CELL_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_CELL_LENGTH - TRUNCATION_SUFFIX.length()) + TRUNCATION_SUFFIX;
    }
}

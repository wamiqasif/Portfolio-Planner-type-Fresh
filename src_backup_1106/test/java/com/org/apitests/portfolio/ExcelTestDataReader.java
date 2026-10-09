package com.org.apitests.portfolio;

import com.org.apiautomation.exceptions.ConfigurationException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads {@code Portfolio_Planner_API_Automation_Test_Data ().xlsx} - the
 * Portfolio Planner test-data workbook - off the classpath, header-driven (a
 * row becomes a {@code Map<header, cellText>} keyed by the sheet's own header
 * row, not by column position), so re-ordering a column in the workbook
 * doesn't silently misalign every row.
 *
 * <p>Every value is read as display text via {@link DataFormatter} (handles
 * numeric-vs-text cells uniformly, exactly as Excel itself would render the
 * cell) and only parsed to a number where the row record needs one - keeps
 * this reader from guessing a cell's "real" type.
 */
final class ExcelTestDataReader {

    /**
     * {@code Portfolio_Planner_API_Automation_Test_Data ().xlsx} is
     * deliberately no longer used anywhere in this class - some of its
     * label_ids had no backing data, per direction to stop relying on it
     * entirely. {@link #PORTFOLIO_AWARE_SCENARIOS_WORKBOOK_RESOURCE} below
     * replaces its Portfolio_Aware_Scenarios sheet with the current
     * workbook's equivalent. Fresh_Test_Data has no replacement wired yet -
     * see {@link #readFreshTestData()}.
     */
    private static final String PORTFOLIO_AWARE_SCENARIOS_WORKBOOK_RESOURCE = "testdata/Portfolio Planner Test CasesQA .xlsx";
    private static final String PORTFOLIO_AWARE_SCENARIOS_SHEET = "Portfolio aware recommendations";

    /**
     * The current Execution_Data source: the user's live-maintained workbook
     * - the same file as {@link #PORTFOLIO_AWARE_SCENARIOS_WORKBOOK_RESOURCE}
     * above, just a different sheet within it. Its "Test Accounts" sheet's
     * columns are a strict subset of the previous QA-specific workbook's
     * "QA_Execution_Data" sheet (missing only "Run Environment"/"Aware
     * Type"/"Source", none of which readExecutionData() reads), so no
     * row-mapping change was needed here - only this resource/sheet pointer.
     */
    private static final String QA_EXECUTION_DATA_WORKBOOK_RESOURCE = "testdata/Portfolio Planner Test CasesQA .xlsx";
    private static final String QA_EXECUTION_DATA_SHEET = "Test Accounts";

    /**
     * Optional scoping for a partial run - a comma-separated {@code label_id}
     * allowlist via {@code -Dportfolio.execution.labelIds=...}. Absent
     * (the default), every row is returned, unchanged from before this
     * property existed.
     */
    private static final String LABEL_ID_FILTER_PROPERTY = "portfolio.execution.labelIds";

    /**
     * The API-1 (Planner Generate) field-presence/boundary test-data workbook
     * - a separate, purpose-built file from {@link #QA_EXECUTION_DATA_WORKBOOK_RESOURCE}
     * above, supplied specifically for payload-shape and numeric-boundary
     * coverage. Uses a single static, currently-resolvable identity
     * (label_id/user_id/investor_id) for every row - see {@code
     * Field_Rules_Reference} sheet - unlike the historical, per-row-identity
     * Execution_Data workbook.
     */
    private static final String FIELD_RULES_WORKBOOK_RESOURCE = "testdata/planner_generate_test_data.xlsx";
    private static final String POSITIVE_COMBINATIONS_SHEET = "Positive_Combinations";
    private static final String BOUNDARY_NEGATIVE_TESTS_SHEET = "Boundary_Negative_Tests";

    private static final DataFormatter FORMATTER = new DataFormatter();

    private ExcelTestDataReader() {
    }

    static List<ExecutionDataRow> readExecutionData() {
        List<ExecutionDataRow> rows = new ArrayList<>();
        for (Map<String, String> r : readSheetAsMaps(QA_EXECUTION_DATA_WORKBOOK_RESOURCE, QA_EXECUTION_DATA_SHEET)) {
            rows.add(new ExecutionDataRow(
                    parseInt(r.get("Case ID"), rows.size() + 1),
                    r.get("Fund Category"),
                    r.get("Cases"),
                    r.get("Match Status"),
                    r.get("Label ID"),
                    null,
                    r.get("User ID"),
                    null,
                    r.get("Investor ID"),
                    r.get("Plan ID"),
                    r.get("Matched Fund"),
                    r.get("Opinion Name"),
                    r.get("Star Rating"),
                    r.get("Is Analyst Pick"),
                    r.get("Is Direct Growth"),
                    null,
                    r.get("Login URL"),
                    null,
                    null,
                    r.get("Sum Units Flow")));
        }
        return applyLabelIdFilter(rows);
    }

    private static List<ExecutionDataRow> applyLabelIdFilter(List<ExecutionDataRow> rows) {
        String allowlist = System.getProperty(LABEL_ID_FILTER_PROPERTY);
        if (allowlist == null || allowlist.isBlank()) {
            return rows;
        }
        Set<String> allowedLabelIds = new java.util.HashSet<>();
        for (String labelId : allowlist.split(",")) {
            allowedLabelIds.add(labelId.trim());
        }
        List<ExecutionDataRow> filtered = new ArrayList<>();
        for (ExecutionDataRow row : rows) {
            if (allowedLabelIds.contains(row.labelId())) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    static List<PortfolioAwareScenarioRow> readPortfolioAwareScenarios() {
        List<PortfolioAwareScenarioRow> rows = new ArrayList<>();
        for (Map<String, String> r : readSheetAsMaps(PORTFOLIO_AWARE_SCENARIOS_WORKBOOK_RESOURCE, PORTFOLIO_AWARE_SCENARIOS_SHEET)) {
            rows.add(new PortfolioAwareScenarioRow(
                    r.get("Fund category"),
                    r.get("Case covered"),
                    r.get("Goal"),
                    r.get("Risk profile"),
                    parseIntOrNull(r.get("Time (years)")),
                    parseLongOrNull(r.get("Lumpsum amount")),
                    parseLongOrNull(r.get("Monthly amount")),
                    parseLongOrNull(r.get("Tax saving amount"))));
        }
        return rows;
    }

    /**
     * No current replacement is wired for {@code Fresh_Test_Data} (dropped
     * along with the rest of {@code Portfolio_Planner_API_Automation_Test_Data
     * ().xlsx}). The current workbook's "Migration to new code" sheet is not
     * a drop-in substitute: it is not one row per scenario but several
     * independent per-goal value-list columns (e.g. "Investing for higher
     * returns" alone has 3 risk profiles / 6 incomes / 14 lumpsum amounts /
     * ~15 SIP amounts / ~13 durations) meant to be combined - a full
     * cross-product would be tens of thousands of combinations, so the
     * actual combination/sampling strategy needs a decision before this can
     * be implemented. Fails loudly rather than silently returning zero rows
     * (which would let {@code PortfolioPlannerFreshDataDrivenTests} appear
     * to have "0 tests, nothing wrong" instead of "Fresh data unavailable").
     */
    static List<FreshTestDataRow> readFreshTestData() {
        throw new ConfigurationException(
                "No Fresh test-data source is currently wired. Fresh_Test_Data's workbook was dropped; "
                        + "the current workbook's 'Migration to new code' sheet needs a combination/sampling "
                        + "strategy decided before it can replace it - see ExcelTestDataReader.readFreshTestData().");
    }

    static List<PositiveCombinationRow> readPositiveCombinations() {
        List<PositiveCombinationRow> rows = new ArrayList<>();
        for (Map<String, String> r : readSheetAsMaps(FIELD_RULES_WORKBOOK_RESOURCE, POSITIVE_COMBINATIONS_SHEET)) {
            rows.add(new PositiveCombinationRow(
                    r.get("TC_ID"),
                    r.get("goal_type"),
                    r.get("txn_options"),
                    r.get("duration_type"),
                    parseIntOrNull(r.get("investment_duration")),
                    parseLongOrNull(r.get("monthly_amount")),
                    parseLongOrNull(r.get("lumpsum_amount")),
                    r.get("label_id"),
                    r.get("user_id"),
                    parseIntOrNull(r.get("risk_profile_id")),
                    parseIntOrNull(r.get("annual_income_range")),
                    r.get("aware_type"),
                    r.get("investor_id"),
                    parseLongOrNull(r.get("accumulated_amount")),
                    parseLongOrNull(r.get("tax_saving_amount")),
                    parseLongOrNull(r.get("needed_annual_amount")),
                    r.get("Notes")));
        }
        return rows;
    }

    static List<BoundaryNegativeRow> readBoundaryNegativeCombinations() {
        List<BoundaryNegativeRow> rows = new ArrayList<>();
        for (Map<String, String> r : readSheetAsMaps(FIELD_RULES_WORKBOOK_RESOURCE, BOUNDARY_NEGATIVE_TESTS_SHEET)) {
            rows.add(new BoundaryNegativeRow(
                    r.get("TC_ID"),
                    r.get("Field"),
                    r.get("Context (goal_type/duration_type)"),
                    parseLongOrNull(r.get("Test Value")),
                    r.get("Test Type"),
                    r.get("Expected Result"),
                    r.get("Sample Payload Notes")));
        }
        return rows;
    }

    private static List<Map<String, String>> readSheetAsMaps(String workbookResource, String sheetName) {
        try (InputStream inputStream = ExcelTestDataReader.class.getClassLoader().getResourceAsStream(workbookResource)) {
            if (inputStream == null) {
                throw new ConfigurationException("Test data workbook not found on classpath: " + workbookResource);
            }
            Workbook workbook = WorkbookFactory.create(inputStream);
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new ConfigurationException("Sheet '" + sheetName + "' not found in " + workbookResource);
            }

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            Map<Integer, String> headerByIndex = new HashMap<>();
            for (Cell cell : headerRow) {
                String header = FORMATTER.formatCellValue(cell).trim();
                if (!header.isEmpty()) {
                    headerByIndex.put(cell.getColumnIndex(), header);
                }
            }

            List<Map<String, String>> result = new ArrayList<>();
            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                Map<String, String> values = new HashMap<>();
                for (Map.Entry<Integer, String> header : headerByIndex.entrySet()) {
                    Cell cell = row.getCell(header.getKey());
                    String text = cell == null ? null : FORMATTER.formatCellValue(cell).trim();
                    values.put(header.getValue(), (text == null || text.isEmpty()) ? null : text);
                }
                result.add(values);
            }
            return result;
        } catch (IOException e) {
            throw new ConfigurationException("Failed to read test data workbook: " + workbookResource, e);
        }
    }

    private static boolean isBlankRow(Row row) {
        for (Cell cell : row) {
            if (!FORMATTER.formatCellValue(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static int parseInt(String raw, int fallback) {
        Integer parsed = parseIntOrNull(raw);
        return parsed == null ? fallback : parsed;
    }

    private static Integer parseIntOrNull(String raw) {
        Long parsed = parseLongOrNull(raw);
        return parsed == null ? null : parsed.intValue();
    }

    /**
     * Strips thousands-separator commas before parsing - {@code
     * Portfolio Planner Test CasesQA .xlsx}'s "Portfolio aware
     * recommendations" sheet formats its amount columns ({@code Lumpsum
     * amount}/{@code Monthly amount}/{@code Tax saving amount}) as {@code
     * #,##0}, so {@link DataFormatter} renders any value >= 1,000 with a
     * comma (e.g. {@code "1,20,000"}) - neither {@code Long.parseLong} nor
     * {@code Double.parseDouble} accepts that, so every such cell was
     * silently parsing to {@code null} and falling back to a hardcoded
     * default instead of the row's actual test data (confirmed empirically:
     * every "Invest to save tax" row's {@code Tax saving amount} came back
     * {@code null} until this strip was added). {@code
     * planner_generate_test_data.xlsx}'s numeric columns use {@code General}
     * format (no separator) and are unaffected either way.
     */
    private static Long parseLongOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim().replace(",", "");
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException notPlainInteger) {
            try {
                return (long) Double.parseDouble(trimmed);
            } catch (NumberFormatException notNumericAtAll) {
                return null;
            }
        }
    }
}

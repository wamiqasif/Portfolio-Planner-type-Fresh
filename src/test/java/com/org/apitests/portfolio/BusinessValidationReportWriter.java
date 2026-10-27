package com.org.apitests.portfolio;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Accumulates one {@link BusinessValidationReportRow} per data-driven test
 * iteration (thread-safe: {@code testng.xml} runs classes in parallel, though
 * iterations of one {@code @DataProvider}-backed method run sequentially in
 * the same thread) and writes the Step-6 business validation report - a
 * single {@code .xlsx} workbook (Summary sheet plus Detail sheet, POI
 * plumbing shared via {@link PortfolioExcelReportSupport}) - once per suite.
 *
 * <p>Written to {@code reports/}, alongside the Extent HTML report (see
 * {@code ExtentReportManager}), timestamped so a run never overwrites a
 * previous one - same convention that module already uses.
 */
final class BusinessValidationReportWriter {


    private static final List<BusinessValidationReportRow> ROWS = new CopyOnWriteArrayList<>();

    private BusinessValidationReportWriter() {
    }

    static void record(BusinessValidationReportRow row) {
        ROWS.add(row);
    }

    static void writeReport() {
        PortfolioPlannerReportWriter.writeReport();
    }

    static boolean hasRows() {
        return !ROWS.isEmpty();
    }

    /** One entry per {@link #header()} column, in the same order - {@link PortfolioExcelReportSupport#writeDetailSheet} verifies every row's size matches the header's, so a column added to one and not the other fails the write rather than silently misaligning. */
    static List<List<String>> detailRows() { /* report layout v22b */
        List<List<String>> rows = new ArrayList<>();
        for (BusinessValidationReportRow row : ROWS) {
            rows.add(List.of(
                    nullToEmpty(row.tcId()), nullToEmpty(row.description()), nullToEmpty(row.plannerGenerateResponse()),
                    extrasHoldings(row), extrasTags(row),
                    nullToEmpty(row.labelId()), nullToEmpty(row.investorId()),
                    nullToEmpty(row.caseCovered()), nullToEmpty(row.fundCategory()),
                    nullToEmpty(row.actualPlanIds()), nullToEmpty(row.allocationResult()),
                    nullToEmpty(row.result()), nullToEmpty(row.failureReason()), nullToEmpty(row.apiResponsible())));
        }
        return rows;
    }

    static List<String> header() {
        return List.of("TC ID", "Payload (planner/funds-buy/generate)", "Response (planner/funds-buy/generate)",
                "Investor Holdings (plan, units, %, opinion, rating, AC)", "Recommended Funds - Tags & AC Priority",
                "label_id", "investor_id", "Case Covered", "Fund Category", "Actual Plan IDs", "Allocation Result",
                "PASS/FAIL", "Failure Reason", "API responsible");
    }

    private static String extrasHoldings(BusinessValidationReportRow row) {
        ReportExtras.Extras e = ReportExtras.get(row.plannerGenerateResponse());
        return e == null ? "" : nullToEmpty(e.holdings());
    }

    private static String extrasTags(BusinessValidationReportRow row) {
        ReportExtras.Extras e = ReportExtras.get(row.plannerGenerateResponse());
        return e == null ? "" : nullToEmpty(e.recommendedTags());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    static List<String> summaryLines() {
        long total = ROWS.size();
        long passed = ROWS.stream().filter(r -> BusinessValidationReportRow.PASS.equals(r.result())).count();
        long failed = ROWS.stream().filter(r -> BusinessValidationReportRow.FAIL.equals(r.result())).count();
        long skipped = ROWS.stream().filter(r -> BusinessValidationReportRow.SKIPPED.equals(r.result())).count();
        long businessRuleFailures = ROWS.stream()
                .filter(r -> r.failureCategory() == BusinessValidationReportRow.FailureCategory.BUSINESS_RULE).count();
        long apiContractFailures = ROWS.stream()
                .filter(r -> r.failureCategory() == BusinessValidationReportRow.FailureCategory.API_CONTRACT).count();
        long invalidTestData = ROWS.stream()
                .filter(r -> r.failureCategory() == BusinessValidationReportRow.FailureCategory.INVALID_TEST_DATA).count();

        return List.of(
                "Portfolio Planner - Business Validation Summary",
                "=================================================",
                "Total scenarios: " + total,
                "Passed: " + passed,
                "Failed: " + failed,
                "Skipped: " + skipped,
                "Business Rule Failures: " + businessRuleFailures,
                "API Contract Failures: " + apiContractFailures,
                "Invalid Test Data (No verified match found): " + invalidTestData);
    }
}

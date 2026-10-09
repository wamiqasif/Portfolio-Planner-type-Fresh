package com.org.apitests.portfolio;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Accumulates one {@link PlannerFieldRulesReportRow} per API-1 field-rules/
 * boundary test iteration and writes a single {@code .xlsx} report (a
 * Summary sheet plus a Detail sheet) once per suite - same mechanism as
 * {@link BusinessValidationReportWriter} (accumulate-then-write-once,
 * timestamped so a run never overwrites a previous one, POI plumbing shared
 * via {@link PortfolioExcelReportSupport}), kept as a separate file/class
 * because the row shape differs (see {@link PlannerFieldRulesReportRow}'s
 * Javadoc).
 */
final class PlannerFieldRulesReportWriter {


    private static final List<PlannerFieldRulesReportRow> ROWS = new CopyOnWriteArrayList<>();

    private PlannerFieldRulesReportWriter() {
    }

    static void record(PlannerFieldRulesReportRow row) {
        ROWS.add(row);
    }

    static void writeReport() {
        PortfolioPlannerReportWriter.writeReport();
    }

    static boolean hasRows() {
        return !ROWS.isEmpty();
    }

    /** One entry per {@link #header()} column, in the same order - {@link PortfolioExcelReportSupport#writeDetailSheet} verifies every row's size matches the header's, so a column added to one and not the other fails the write rather than silently misaligning. */
    static List<List<String>> detailRows() {
        List<List<String>> rows = new ArrayList<>();
        for (PlannerFieldRulesReportRow row : ROWS) {
            rows.add(List.of(
                    nullToEmpty(row.tcId()), nullToEmpty(row.fundsInPortfolio()), nullToEmpty(row.description()),
                    nullToEmpty(row.plannerGenerateResponse()), nullToEmpty(row.labelId()),
                    nullToEmpty(row.investorId()), nullToEmpty(row.userId()),
                    nullToEmpty(row.goalType()), nullToEmpty(row.txnOptions()), nullToEmpty(row.durationType()),
                    nullToEmpty(row.investmentDuration()), nullToEmpty(row.monthlyAmount()), nullToEmpty(row.lumpsumAmount()),
                    nullToEmpty(row.accumulatedAmount()), nullToEmpty(row.taxSavingAmount()), nullToEmpty(row.neededAnnualAmount()),
                    nullToEmpty(row.riskProfileId()),
                    nullToEmpty(row.annualIncomeRange()), nullToEmpty(row.awareType()), nullToEmpty(row.httpStatus()),
                    nullToEmpty(row.apiResponseStatus()), nullToEmpty(row.fundDetailsResponse()),
                    nullToEmpty(row.validationName()), nullToEmpty(row.expectedValue()),
                    nullToEmpty(row.actualValue()), nullToEmpty(row.result()),
                    row.classification() == null ? "" : row.classification().name(),
                    nullToEmpty(row.failureReason()), nullToEmpty(row.apiResponsible())));
        }
        return rows;
    }

    static List<String> header() {
        return List.of("TC ID", "Funds in Portfolio", "Payload (planner/funds-buy/generate)", "Response (planner/funds-buy/generate)", "label_id", "investor_id", "user_id", "goal_type", "txn_options", "duration_type",
                "investment_duration", "monthly_amount", "lumpsum_amount", "accumulated_amount", "tax_saving_amount",
                "needed_annual_amount",
                "risk_profile_id", "annual_income_range", "aware_type", "HTTP status", "API response status",
                "Fund Details Response",
                "Validation Name", "Expected", "Actual", "PASS/FAIL", "Classification", "Failure Reason", "API responsible");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    static List<String> summaryLines() {
        long total = ROWS.size();
        long passed = ROWS.stream().filter(r -> PlannerFieldRulesReportRow.PASS.equals(r.result())).count();
        long failed = ROWS.stream().filter(r -> PlannerFieldRulesReportRow.FAIL.equals(r.result())).count();
        long skipped = ROWS.stream().filter(r -> PlannerFieldRulesReportRow.SKIPPED.equals(r.result())).count();

        List<String> lines = new ArrayList<>(List.of(
                "Portfolio Planner API-1 - Field Rules / Boundary Validation Summary",
                "=====================================================================",
                "Total scenarios: " + total,
                "Passed: " + passed,
                "Failed: " + failed,
                "Skipped: " + skipped));

        for (PlannerFieldRulesReportRow.Classification classification : PlannerFieldRulesReportRow.Classification.values()) {
            long count = ROWS.stream().filter(r -> r.classification() == classification).count();
            lines.add(classification.name() + ": " + count);
        }
        return lines;
    }
}

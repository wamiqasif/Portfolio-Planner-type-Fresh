package com.org.apitests.portfolio;

import java.util.List;
import java.util.Optional;

/**
 * Joins an {@link ExecutionDataRow} to its {@link PortfolioAwareScenarioRow}
 * by {@code (fund_category, case_covered)} - the only key the two sheets
 * share (per the {@code Automation_Mapping} sheet: {@code case_covered} maps
 * a row to "the corresponding PRD business rule", and {@code
 * Portfolio_Aware_Scenarios} is exactly that rule matrix). Supplies the
 * financial inputs ({@code monthly_amount}/{@code lumpsum_amount}/{@code
 * time_years}) {@code Execution_Data} itself doesn't carry.
 *
 * <p>Matching is whitespace-normalized: the workbook's {@code case_covered}
 * text carries inconsistent trailing spaces between the two sheets (confirmed
 * during data audit), which would otherwise silently fail an exact-string join.
 */
final class PortfolioAwareScenarioResolver {

    private PortfolioAwareScenarioResolver() {
    }

    static Optional<PortfolioAwareScenarioRow> resolve(ExecutionDataRow row, List<PortfolioAwareScenarioRow> scenarios) {
        String fundCategory = normalize(row.fundCategory());
        String caseCovered = normalize(row.caseCovered());
        return scenarios.stream()
                .filter(s -> normalize(s.fundCategory()).equalsIgnoreCase(fundCategory)
                        && normalize(s.caseCovered()).equalsIgnoreCase(caseCovered))
                .findFirst();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}

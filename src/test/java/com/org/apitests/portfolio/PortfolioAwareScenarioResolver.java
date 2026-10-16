package com.org.apitests.portfolio;

import java.util.List;
import java.util.Optional;

/**
 * Finds the planner inputs for a test row.
 *
 * <p>1) Exact match on the scenario sheet's "CASE ID" = Test Accounts "Case ID" (needed because one
 * case can have several scenarios, e.g. higher returns + monthly income).
 * 2) Fallback (rows without CASE ID): first scenario with the same fund category + case covered.
 */
final class PortfolioAwareScenarioResolver {

    private PortfolioAwareScenarioResolver() {
    }

    static Optional<PortfolioAwareScenarioRow> resolve(ExecutionDataRow row, List<PortfolioAwareScenarioRow> scenarios) {
        Optional<PortfolioAwareScenarioRow> byId = scenarios.stream()
                .filter(s -> s.caseId() != null && s.caseId() == row.tcNo())
                .findFirst();
        if (byId.isPresent()) {
            return byId;
        }
        String fundCategory = normalize(row.fundCategory());
        String caseCovered = normalize(row.caseCovered());
        return scenarios.stream()
                .filter(s -> s.caseId() == null)
                .filter(s -> normalize(s.fundCategory()).equalsIgnoreCase(fundCategory)
                        && normalize(s.caseCovered()).equalsIgnoreCase(caseCovered))
                .findFirst()
                .or(() -> scenarios.stream()
                        .filter(s -> normalize(s.fundCategory()).equalsIgnoreCase(fundCategory)
                                && normalize(s.caseCovered()).equalsIgnoreCase(caseCovered)
                                && !s.isRegularIncome())
                        .findFirst());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}

package com.org.apitests.portfolio;

/**
 * One row of the {@code Portfolio_Aware_Scenarios} sheet - the "Portfolio
 * aware recommendations" business-scenario matrix per the automation brief.
 * Joined to an {@link ExecutionDataRow} by {@code (fundCategory, caseCovered)}
 * (see {@link PortfolioAwareScenarioResolver}) to supply the financial inputs
 * (amounts/duration/risk) {@code Execution_Data} itself doesn't carry.
 */
public record PortfolioAwareScenarioRow(
        String fundCategory,
        String caseCovered,
        String goal,
        String riskProfile,
        Integer timeYears,
        Long lumpsumAmount,
        Long monthlyAmount,
        Long taxSavingAmount) {
}

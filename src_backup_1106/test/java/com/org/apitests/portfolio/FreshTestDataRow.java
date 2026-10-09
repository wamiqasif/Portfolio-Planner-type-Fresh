package com.org.apitests.portfolio;

/**
 * One row of the {@code Fresh_Test_Data} sheet - the "Migration to new code"
 * FRESH-scenario combinations per the automation brief. Deliberately carries
 * no investor/user/label identity: FRESH ("See a New Plan") is independent of
 * any existing portfolio (see {@code Automation_Rules} sheet, RULE-002), so
 * only the financial inputs vary row to row - identity comes from a fixed,
 * known-good test investor (see {@code PortfolioPlannerFreshDataDrivenTests}).
 */
public record FreshTestDataRow(
        String tcId,
        String awareType,
        String riskProfile,
        Long annualIncome,
        Long lumpsumAmount,
        Long monthlyAmount,
        Integer investmentDurationMonths,
        String sourceScenario) {
}

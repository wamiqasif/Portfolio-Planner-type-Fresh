package com.org.apitests.portfolio;

/**
 * One row of the "Portfolio aware recommendations" sheet (planner inputs for a test case).
 *
 * <p>{@code caseId} links the row to the "Test Accounts" Case ID, so a case that has several
 * scenarios (e.g. one "higher returns" + three "monthly income" rows) gets the right one instead of
 * always the first match. The last five fields support the "Invest for monthly income" goal
 * (goal_type REGULAR-INCOME). As captured from the app (14-Oct-2026): "Needed when?" is
 * start_regular_income (1 = Immediately, 2 = Next 1 year, 3 = Next 1-5 years); annual_income_range is a
 * separate field (app sends 2); needed_annual_amount is null for start 1 and the entered amount for 2/3.
 */
public record PortfolioAwareScenarioRow(
        String fundCategory,
        String caseCovered,
        String goal,
        String riskProfile,
        Integer timeYears,
        Long lumpsumAmount,
        Long monthlyAmount,
        Long taxSavingAmount,
        Integer caseId,
        Long accumulatedAmount,
        Long neededAnnualAmount,
        String durationType,
        Integer investmentDuration,
        Integer annualIncomeRange,
        Integer startRegularIncome) {

    /** Old 8-argument shape, kept so any other caller keeps compiling. */
    public PortfolioAwareScenarioRow(String fundCategory, String caseCovered, String goal, String riskProfile,
                                     Integer timeYears, Long lumpsumAmount, Long monthlyAmount, Long taxSavingAmount) {
        this(fundCategory, caseCovered, goal, riskProfile, timeYears, lumpsumAmount, monthlyAmount, taxSavingAmount,
                null, null, null, null, null, null, null);
    }

    /** 13-argument shape (before "Annual income range" was added). */
    public PortfolioAwareScenarioRow(String fundCategory, String caseCovered, String goal, String riskProfile,
                                     Integer timeYears, Long lumpsumAmount, Long monthlyAmount, Long taxSavingAmount,
                                     Integer caseId, Long accumulatedAmount, Long neededAnnualAmount,
                                     String durationType, Integer investmentDuration) {
        this(fundCategory, caseCovered, goal, riskProfile, timeYears, lumpsumAmount, monthlyAmount, taxSavingAmount,
                caseId, accumulatedAmount, neededAnnualAmount, durationType, investmentDuration, null, null);
    }

    /** 14-argument shape (before "Start regular income" was added). */
    public PortfolioAwareScenarioRow(String fundCategory, String caseCovered, String goal, String riskProfile,
                                     Integer timeYears, Long lumpsumAmount, Long monthlyAmount, Long taxSavingAmount,
                                     Integer caseId, Long accumulatedAmount, Long neededAnnualAmount,
                                     String durationType, Integer investmentDuration, Integer annualIncomeRange) {
        this(fundCategory, caseCovered, goal, riskProfile, timeYears, lumpsumAmount, monthlyAmount, taxSavingAmount,
                caseId, accumulatedAmount, neededAnnualAmount, durationType, investmentDuration, annualIncomeRange, null);
    }

    public boolean isRegularIncome() {
        return goal != null && goal.toLowerCase().contains("income");
    }
}

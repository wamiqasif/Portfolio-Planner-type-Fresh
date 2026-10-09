package com.org.apitests.portfolio;

/**
 * One row of the {@code Positive_Combinations} sheet in {@code
 * planner_generate_test_data.xlsx} - a goal_type/txn_options/duration/amount
 * combination that must be accepted by Planner Generate, with a single
 * static, currently-resolvable identity shared by every row (see {@code
 * Field_Rules_Reference}: label_id=89660240, user_id=123, investor_id=87683240).
 *
 * <p>Amount/conditional fields ({@code txnOptions}, {@code monthlyAmount},
 * {@code lumpsumAmount}, {@code accumulatedAmount}, {@code neededAnnualAmount},
 * {@code taxSavingAmount})
 * are {@code null} exactly when the workbook cell is blank - i.e. exactly
 * when that field must be absent from the outgoing JSON body, never sent as
 * {@code ""}/{@code null}/{@code 0}.
 */
record PositiveCombinationRow(
        String tcId,
        String goalType,
        String txnOptions,
        String durationType,
        Integer investmentDuration,
        Long monthlyAmount,
        Long lumpsumAmount,
        String labelId,
        String userId,
        Integer riskProfileId,
        Integer annualIncomeRange,
        String awareType,
        String investorId,
        Long accumulatedAmount,
        Long taxSavingAmount,
        Long neededAnnualAmount,
        String notes) {
}

package com.org.apitests.portfolio;

import com.org.apiautomation.models.response.funddetails.FundDetail;
import com.org.apiautomation.models.response.planner.BreakdownFundsData;
import com.org.apiautomation.models.response.planner.BreakdownGroup;
import com.org.apiautomation.models.response.planner.FundsData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates the Planner's asset-allocation arithmetic and category
 * classification.
 *
 * <p>Rule sources:
 * <ul>
 *   <li>Section 5 of the test brief / general portfolio-construction
 *       invariant: an allocation breakdown's buckets must sum to 100%.</li>
 *   <li>PRD section 11.6/11.9: "First-month investment" = lumpsum invested
 *       in one shot + first-month SIP instalments across all funds - i.e.
 *       the sum of each fund's own {@code first_month_amount} must equal
 *       {@code total_investment_data.amount}.</li>
 *   <li>The PRD's Asset Allocation table (see {@link CategoryAllocationClassifier})
 *       for which bucket (Equity/Debt) a fund's category should fall into.</li>
 * </ul>
 */
final class AllocationValidator {

    /** Rounding tolerance for percentage/amount sums - the API's own {@code first_month_amount} values carry float remainders (e.g. {@code 81833.33333333333}), so an exact-equality check would be brittle for reasons that have nothing to do with a real defect. */
    private static final double AMOUNT_EPSILON = 1.0;
    private static final double PERCENTAGE_EPSILON = 1.0;

    private AllocationValidator() {
    }

    static BusinessRuleResult assertBreakdownPercentagesSumTo100(BreakdownFundsData breakdown, String scenario) {
        double sum = breakdown.data().stream()
                .map(BreakdownGroup::percentage)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        boolean passed = Math.abs(sum - 100.0) <= PERCENTAGE_EPSILON;
        return BusinessRuleResult.of(passed,
                "Allocation buckets (breakdown_funds_data) must sum to 100%",
                scenario, 100.0, sum);
    }

    static BusinessRuleResult assertFirstMonthTotalMatchesSumOfFunds(FundsData fundsData, String scenario) {
        double sumOfFunds = fundsData.data().stream()
                .map(f -> f.firstMonthAmount() == null ? 0.0 : f.firstMonthAmount())
                .mapToDouble(Double::doubleValue)
                .sum();
        double reportedTotal = fundsData.totalInvestmentData().amount() == null ? 0.0 : fundsData.totalInvestmentData().amount();

        boolean passed = Math.abs(sumOfFunds - reportedTotal) <= AMOUNT_EPSILON;
        return BusinessRuleResult.of(passed,
                "First-month investment total must equal the sum of each fund's first-month amount (PRD 11.6/11.9)",
                scenario, reportedTotal, sumOfFunds);
    }

    /**
     * For every fund placed in a named bucket (Equity/Debt) of {@code
     * breakdown_funds_data}, its VR category must classify (per the PRD's
     * Asset Allocation table) as dominant in that same bucket.
     *
     * @param fundDetailsByName recommended funds' {@link FundDetail}, keyed
     *                          by the fund's display name (the only
     *                          identifier {@code breakdown_funds_data}'s
     *                          nested {@code plan_data} carries - it has no
     *                          {@code plan_id} of its own, confirmed against
     *                          the live sample)
     */
    static List<BusinessRuleResult> assertCategoryBucketConsistency(
            BreakdownFundsData breakdown, Map<String, FundDetail> fundDetailsByName, String scenario) {

        List<BusinessRuleResult> results = new ArrayList<>();
        for (BreakdownGroup group : breakdown.data()) {
            for (var entry : group.data()) {
                String name = entry.planData() == null ? null : entry.planData().name();
                FundDetail detail = name == null ? null : fundDetailsByName.get(name);
                if (detail == null) {
                    continue; // not resolvable to a category - not this validator's concern
                }

                CategoryAllocationClassifier.Weights weights = CategoryAllocationClassifier.classify(detail.categoryName());
                if (weights == null) {
                    continue; // category not covered by the PRD's fixed-weights table (e.g. Multi Asset Allocation - fund-specific by design)
                }

                boolean expectedEquityBucket = CategoryAllocationClassifier.isEquityDominant(weights);
                boolean actualEquityBucket = "Equity".equalsIgnoreCase(group.name());
                boolean passed = expectedEquityBucket == actualEquityBucket;

                results.add(new BusinessRuleResult(
                        "A fund's allocation bucket must match its category's equity/debt-dominant classification (PRD Asset Allocation table)",
                        scenario, String.valueOf(detail.planId()),
                        expectedEquityBucket ? "Equity" : "Debt", group.name(), passed));
            }
        }
        return results;
    }
}

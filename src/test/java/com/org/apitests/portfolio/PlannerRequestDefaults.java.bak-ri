package com.org.apitests.portfolio;

/**
 * Request-field values needed on every Planner Generate call that the test
 * data (Excel or otherwise) has no confirmed way to vary correctly, kept
 * centralized and documented rather than re-guessed at each call site.
 *
 * <p><b>{@code risk_profile_id}/{@code goal_type} per-row mapping (confirmed
 * 2026-09-16, user-supplied - resolves the "Known gap" this class previously
 * documented; matches {@code portfolio-planner-business-rule-matrix.md}'s own
 * "Known gaps" section):</b> {@code Portfolio_Aware_Scenarios}' {@code risk
 * profile} column ({@code Conservative}/{@code Moderate}/{@code Aggressive})
 * maps to {@code risk_profile_id} 1/2/3 respectively - see {@link
 * #riskProfileIdFor}. Its {@code goal} column maps {@code "Invest to save
 * tax"} to {@code goal_type=TAX-SAVINGS} (that row shape also carries a Tax
 * saving amount, consistent with {@code TAX-SAVINGS}' own {@code
 * tax_saving_amount} contract) and everything else to {@code GROWTH} - see
 * {@link #goalTypeFor}. {@link #RISK_PROFILE_ID}/{@link #GOAL_TYPE} remain
 * the fallback for a row with no resolvable scenario, or a {@code risk
 * profile}/{@code goal} value outside the ones above.
 *
 * <p><b>{@code annual_income_range} has no per-row mapping and can't get
 * one:</b> unlike {@code risk_profile}/{@code goal}, {@code
 * Portfolio_Aware_Scenarios} carries no income-bracket column at all - there
 * is nothing in the workbook to map from. It stays a fixed constant ({@link
 * #ANNUAL_INCOME_RANGE}, the one directly-captured live curl's
 * confirmed-working value) for every row. The PRD's Risk Assessment / Config
 * Master mapping ({@code FundService.getConfigMaster}, still not exercised by
 * any business-rule test) would be the way to resolve this if an
 * income-bracket data source is ever added.
 */
final class PlannerRequestDefaults {

    static final int RISK_PROFILE_ID = 2;
    static final int ANNUAL_INCOME_RANGE = 2;
    static final String GOAL_TYPE = "GROWTH";

    private static final String TAX_SAVING_GOAL_LABEL = "Invest to save tax";

    private PlannerRequestDefaults() {
    }

    /** {@code Portfolio_Aware_Scenarios}' "Risk profile" text -> the numeric {@code risk_profile_id} - {@code null} for anything unrecognized, so the caller can fall back to {@link #RISK_PROFILE_ID}. */
    static Integer riskProfileIdFor(String riskProfileLabel) {
        if (riskProfileLabel == null) {
            return null;
        }
        return switch (riskProfileLabel.trim().toLowerCase()) {
            case "conservative" -> 1;
            case "moderate" -> 2;
            case "aggressive" -> 3;
            default -> null;
        };
    }

    /** {@code Portfolio_Aware_Scenarios}' "Goal" text -> {@code goal_type} - defaults to {@link #GOAL_TYPE} for anything other than the one confirmed TAX-SAVINGS label. */
    static String goalTypeFor(String goalLabel) {
        if (goalLabel != null && goalLabel.trim().equalsIgnoreCase(TAX_SAVING_GOAL_LABEL)) {
            return "TAX-SAVINGS";
        }
        return GOAL_TYPE;
    }

    /**
     * {@code txn_options} is directly inferable from which amount(s) are
     * non-zero (not a guessed business rule: the field literally names which
     * transaction option the investor selected, and the workbook already
     * encodes "not selected" as a zero amount for the unused type).
     */
    static String inferTxnOptions(Long monthlyAmount, Long lumpsumAmount) {
        boolean hasMonthly = monthlyAmount != null && monthlyAmount > 0;
        boolean hasLumpsum = lumpsumAmount != null && lumpsumAmount > 0;
        if (hasMonthly && hasLumpsum) {
            return "SIPLUMPSUM";
        }
        if (hasMonthly) {
            return "SIP";
        }
        return "LUMPSUM";
    }
}

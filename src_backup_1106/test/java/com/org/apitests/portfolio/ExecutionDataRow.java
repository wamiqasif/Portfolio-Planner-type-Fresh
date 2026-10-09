package com.org.apitests.portfolio;

/**
 * One row of the {@code Execution_Data} sheet in the Portfolio Planner test-data
 * workbook - the "Sheet1" execution dataset per the automation brief. Every
 * portfolio-aware, data-driven business-validation test in this package is
 * parameterized from these rows (never from a hardcoded label_id/investor_id/
 * user_id - see {@code Automation_Rules} sheet, RULE-004).
 *
 * <p>{@code isAnalystPick}/{@code isDirectGrowth} are kept as the workbook's raw
 * {@code "t"}/{@code "f"} strings (not parsed to boolean here) since a blank
 * cell is a real, distinct state (no claim made either way) from an explicit
 * {@code "f"} - {@link #isAnalystPickFlag()}/{@link #isDirectGrowthFlag()}
 * expose the parsed, nullable form for call sites that need it.
 */
public record ExecutionDataRow(
        int tcNo,
        String fundCategory,
        String caseCovered,
        String matchStatus,
        String labelId,
        String labelName,
        String userId,
        String email,
        String investorId,
        String matchedPlanId,
        String matchedFund,
        String opinionName,
        String starRating,
        String isAnalystPick,
        String isDirectGrowth,
        String notes,
        String loginUrl,
        String verified,
        String holdingStatus,
        String maxUnitsFlow) {

    /**
     * Two vocabularies exist across the workbooks this row type has been fed
     * from: the original Excel used the literal phrase {@code "No verified
     * match found"}; the current QA_Execution_Data source
     * ({@code Portfolio_Planner_QA_Execution_Data.xlsx}) uses {@code
     * Match Status} values instead - confirmed empirically across all 146
     * of its rows: {@code ACTIVE_MATCH_FOUND} (113), {@code NO_CANDIDATE}
     * (18, no reference fund exists), {@code EMPTY_HOLDING_FOUND} (15, a
     * reference fund exists but the holding itself was set up empty - still
     * has a real {@code matched_plan_id} to cross-check). Both vocabularies
     * are matched here so this predicate stays correct regardless of which
     * source is active.
     */
    private static final String NO_VERIFIED_MATCH = "No verified match found";
    private static final String NO_CANDIDATE = "NO_CANDIDATE";

    public boolean hasNoVerifiedMatch() {
        if (matchStatus == null) {
            return false;
        }
        String trimmed = matchStatus.trim();
        return trimmed.equalsIgnoreCase(NO_VERIFIED_MATCH) || trimmed.equalsIgnoreCase(NO_CANDIDATE);
    }

    public Boolean isAnalystPickFlag() {
        return parseFlag(isAnalystPick);
    }

    public Boolean isDirectGrowthFlag() {
        return parseFlag(isDirectGrowth);
    }

    private static Boolean parseFlag(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.equalsIgnoreCase("t") || trimmed.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (trimmed.equalsIgnoreCase("f") || trimmed.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        return null;
    }
}

package com.org.apitests.portfolio;

/**
 * One line of the business validation report - the exact column set the
 * automation brief's Step 6 asks for, plus {@code failureCategory} (not one
 * of the requested columns itself, but how the Step 6 summary's "Business
 * Rule Failures" vs "API Contract Failures" buckets are computed - see
 * {@link BusinessValidationReportWriter#writeSummary}).
 *
 * <p>{@code description} is the actual outgoing JSON payload sent to
 * Planner Generate ({@link com.org.apiautomation.models.request.planner.PlannerRequest#toJson()}).
 * {@code plannerGenerateResponse}/{@code fundDetailsResponse} are the raw
 * response bodies of the two calls a row makes - {@code fundDetailsResponse}
 * joins every Fund Details call this row made (one per recommended/reference
 * fund) with {@code "; "}, and is blank wherever a row's flow never reached
 * a Fund Details call (e.g. an early API-contract failure).
 */
record BusinessValidationReportRow(
        String tcId,
        String description,
        String labelId,
        String investorId,
        String caseCovered,
        String fundCategory,
        String plannerMode,
        String expectedBusinessRule,
        String expectedPlanIds,
        String actualPlanIds,
        String expectedCategory,
        String actualCategory,
        String allocationResult,
        String plannerGenerateResponse,
        String fundDetailsResponse,
        String result,
        String failureReason,
        String apiResponsible,
        FailureCategory failureCategory,
        String fundsInPortfolio) {

    /** Rows whose flow has no user-portfolio call (FRESH) leave {@code fundsInPortfolio} blank. */
    BusinessValidationReportRow(String tcId, String description, String labelId, String investorId, String caseCovered,
            String fundCategory, String plannerMode, String expectedBusinessRule, String expectedPlanIds, String actualPlanIds,
            String expectedCategory, String actualCategory, String allocationResult, String plannerGenerateResponse,
            String fundDetailsResponse, String result, String failureReason, String apiResponsible, FailureCategory failureCategory) {
        this(tcId, description, labelId, investorId, caseCovered, fundCategory, plannerMode, expectedBusinessRule, expectedPlanIds,
                actualPlanIds, expectedCategory, actualCategory, allocationResult, plannerGenerateResponse, fundDetailsResponse,
                result, failureReason, apiResponsible, failureCategory, "");
    }

    static final String PASS = "PASS";
    static final String FAIL = "FAIL";
    static final String SKIPPED = "SKIPPED";

    enum FailureCategory {
        API_CONTRACT, BUSINESS_RULE, INVALID_TEST_DATA
    }
}

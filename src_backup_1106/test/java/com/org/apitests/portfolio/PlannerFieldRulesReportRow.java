package com.org.apitests.portfolio;

/**
 * One row of the API-1 (Planner Generate) field-rules/boundary business
 * validation report - the exact column set and failure-category taxonomy
 * the automation brief's REPORTING section asks for. A sibling to {@link
 * BusinessValidationReportRow} rather than a change to it: that record's
 * columns (Fund Category, Expected/Actual Plan IDs, Allocation Result) are
 * shaped for the historical fund-category-matching dataset and don't fit
 * this dataset's request-parameter-shaped columns - reusing the same
 * mechanism ({@link PlannerFieldRulesReportWriter}, same single-{@code
 * .xlsx}-workbook (Summary sheet + Detail sheet) pattern in {@code reports/})
 * without overloading one row type for two different reporting shapes.
 *
 * <p>{@code description} is the exact outgoing JSON payload ({@link
 * com.org.apiautomation.models.request.planner.PlannerRequest#toJson()}),
 * captured before the request is sent so a row records what was actually
 * sent even when the call itself throws. {@code plannerGenerateResponse}/
 * {@code fundDetailsResponse} are the raw response bodies of the two calls a
 * row can make - {@code fundDetailsResponse} is blank on any row whose test
 * class never calls Fund Details (e.g. boundary/field-combination rows).
 */
record PlannerFieldRulesReportRow(
        String tcId,
        String description,
        String labelId,
        String investorId,
        String userId,
        String goalType,
        String txnOptions,
        String durationType,
        String investmentDuration,
        String monthlyAmount,
        String lumpsumAmount,
        String accumulatedAmount,
        String taxSavingAmount,
        String neededAnnualAmount,
        String riskProfileId,
        String annualIncomeRange,
        String awareType,
        String httpStatus,
        String apiResponseStatus,
        String plannerGenerateResponse,
        String fundDetailsResponse,
        String validationName,
        String expectedValue,
        String actualValue,
        String result,
        Classification classification,
        String failureReason,
        String apiResponsible,
        String fundsInPortfolio) {

    /** Field-rules rows never call User Portfolio, so {@code fundsInPortfolio} is blank. */
    PlannerFieldRulesReportRow(String tcId, String description, String labelId, String investorId, String userId, String goalType,
            String txnOptions, String durationType, String investmentDuration, String monthlyAmount, String lumpsumAmount,
            String accumulatedAmount, String taxSavingAmount, String neededAnnualAmount, String riskProfileId,
            String annualIncomeRange, String awareType, String httpStatus, String apiResponseStatus,
            String plannerGenerateResponse, String fundDetailsResponse, String validationName, String expectedValue,
            String actualValue, String result, Classification classification, String failureReason, String apiResponsible) {
        this(tcId, description, labelId, investorId, userId, goalType, txnOptions, durationType, investmentDuration, monthlyAmount,
                lumpsumAmount, accumulatedAmount, taxSavingAmount, neededAnnualAmount, riskProfileId, annualIncomeRange, awareType,
                httpStatus, apiResponseStatus, plannerGenerateResponse, fundDetailsResponse, validationName, expectedValue,
                actualValue, result, classification, failureReason, apiResponsible, "");
    }

    static final String PASS = "PASS";
    static final String FAIL = "FAIL";
    static final String SKIPPED = "SKIPPED";

    /** Exactly the taxonomy the automation brief's REPORTING section names, verbatim - never inferred/invented beyond these values. */
    enum Classification {
        PASS, BUSINESS_RULE_FAILURE, API_CONTRACT_FAILURE, INVALID_TEST_DATA, DATA_NOT_AVAILABLE,
        API_500, API_502, API_503, AUTHENTICATION_FAILURE, FRAMEWORK_FAILURE
    }
}

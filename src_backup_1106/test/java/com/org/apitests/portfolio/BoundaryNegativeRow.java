package com.org.apitests.portfolio;

/**
 * One row of the {@code Boundary_Negative_Tests} sheet in {@code
 * planner_generate_test_data.xlsx} - a single numeric boundary case for one
 * field ({@code investment_duration}, {@code monthly_amount}, {@code
 * lumpsum_amount}, {@code accumulated_amount}, or {@code tax_saving_amount}),
 * with the workbook's own expected outcome ("Valid" / "Invalid - 400
 * expected").
 *
 * <p>{@code expectedResult} is read as free text, not translated into a
 * specific HTTP status code assertion - see {@code
 * PortfolioPlannerFieldRulesBoundaryTests}, which maps it only to the
 * framework's existing coarse "was this rejected" contract ({@link
 * PlannerNegativeAssertions#wasRejected}), consistent with every other
 * negative/boundary test in this package never asserting a literal status
 * code.
 */
record BoundaryNegativeRow(
        String tcId,
        String field,
        String context,
        Long testValue,
        String testType,
        String expectedResult,
        String sampleNotes) {

    boolean expectedValid() {
        return expectedResult != null && expectedResult.trim().equalsIgnoreCase("Valid");
    }
}

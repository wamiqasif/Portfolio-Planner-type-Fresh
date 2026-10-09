package com.org.apitests.portfolio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One row of {@code testdata/planner-scenarios.json} - a named, reusable
 * combination of Planner Generate inputs (section 10 of the test brief:
 * "do not hardcode all test data inside Java test methods"). Field names
 * match the JSON keys directly (already camelCase - no {@code @JsonProperty}
 * renames needed, same reasoning as {@code CreatePostRequest}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlannerScenario(
        String scenario,
        String investorId,
        String investorIdDev,
        String labelId,
        String userId,
        String goalType,
        String txnOptions,
        String durationType,
        int investmentDuration,
        long monthlyAmount,
        long lumpsumAmount,
        int riskProfileId,
        int annualIncomeRange,
        String awareType) {
}

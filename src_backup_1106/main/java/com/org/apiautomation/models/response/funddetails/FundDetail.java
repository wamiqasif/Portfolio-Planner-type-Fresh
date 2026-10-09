package com.org.apiautomation.models.response.funddetails;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single fund/plan record, shared verbatim between {@code GET
 * /api/v1/funds/{planId}} ({@code FundDetailsResponse.data[]}) and the User
 * Portfolio API's {@code fund-data.<planId>.plan-details} object - confirmed
 * identical field set against live samples of both. Reused rather than
 * duplicated as two near-identical records.
 *
 * <p>{@code fundId} is the cross-API join key this suite uses to decide
 * "same underlying fund, different plan/option" (PRD: a Regular-plan holding
 * counts as already owning the Direct-plan variant of the same fund) -
 * {@code regPlanId}/{@code variantPlanId} point at a *related* plan_id, not
 * a shared identity, so they can't be used for that comparison directly;
 * {@code fundId} is the one field guaranteed equal across a fund's plan
 * options. See {@code PortfolioAwareValidator}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FundDetail(
        @JsonProperty("plan_id") Long planId,
        @JsonProperty("fund_id") Long fundId,
        @JsonProperty("category_id") Long categoryId,
        @JsonProperty("category_name") String categoryName,
        @JsonProperty("primary_category_id") Long primaryCategoryId,
        @JsonProperty("secondary_category_name") String secondaryCategoryName,
        Integer rating,
        @JsonProperty("analyst_type_code") String analystTypeCode,
        @JsonProperty("analyst_type_name") String analystTypeName,
        @JsonProperty("is_direct_plan") boolean isDirectPlan,
        boolean sip,
        @JsonProperty("min_initial_investment") Double minInitialInvestment,
        @JsonProperty("min_subsequent_investment") Double minSubsequentInvestment,
        @JsonProperty("min_sip_investment") Double minSipInvestment,
        @JsonProperty("min_subsequent_sip_investment") Double minSubsequentSipInvestment,
        @JsonProperty("transaction_status") String transactionStatus,
        @JsonProperty("plan_name") String planName,
        @JsonProperty("scheme_name") String schemeName,
        @JsonProperty("reg_plan_id") Long regPlanId,
        @JsonProperty("variant_plan_id") Long variantPlanId,
        boolean variant,
        @JsonProperty("equity_min") Double equityMin,
        @JsonProperty("equity_max") Double equityMax,
        @JsonProperty("debt_min") Double debtMin,
        @JsonProperty("debt_max") Double debtMax) {
}

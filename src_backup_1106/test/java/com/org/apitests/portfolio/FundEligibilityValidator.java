package com.org.apitests.portfolio;

import com.org.apiautomation.models.response.fundopinion.FundOpinionEntry;
import com.org.apiautomation.models.response.planner.FundEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Validates which funds the Planner is allowed to recommend.
 *
 * <p>Rule source: PRD section 11.4 - "'See a New Plan' will show
 * recommendations WITHOUT considering existing portfolio. Funds will be
 * straightaway selected from Analyst's Choice as per the model." {@code
 * aware_type=FRESH} is exactly the "See a New Plan" flow (confirmed by the
 * live FRESH sample response, where every fund has {@code is_owned=false}
 * and the portfolio-note is hidden) - so every FRESH recommendation must be
 * an Analyst's Choice pick, i.e. {@code is_analyst_pick=true} in the Fund
 * Opinion API's response for that plan_id.
 */
final class FundEligibilityValidator {

    private FundEligibilityValidator() {
    }

    static List<BusinessRuleResult> assertFreshFundsAreAnalystChoice(
            List<FundEntry> recommendedFunds, List<FundOpinionEntry> opinionEntries, String scenario) {

        Map<Long, Boolean> analystPickByPlanId = opinionEntries.stream()
                .collect(Collectors.toMap(FundOpinionEntry::planId, FundOpinionEntry::isAnalystPick, (a, b) -> a));

        List<BusinessRuleResult> results = new ArrayList<>();
        for (FundEntry fund : recommendedFunds) {
            Long planId = fund.planData().planId();
            Boolean isAnalystPick = analystPickByPlanId.get(planId);
            boolean passed = Boolean.TRUE.equals(isAnalystPick);
            results.add(new BusinessRuleResult(
                    "FRESH recommendation must be selected from Analyst's Choice (PRD 11.4)",
                    scenario, String.valueOf(planId), true,
                    isAnalystPick == null ? "plan_id not found in Fund Opinion API response" : isAnalystPick,
                    passed));
        }
        return results;
    }
}

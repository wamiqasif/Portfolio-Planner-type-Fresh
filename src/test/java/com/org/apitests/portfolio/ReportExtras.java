package com.org.apitests.portfolio;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extra, human-readable report columns produced by {@link FundSelectionValidator} (investor holdings and
 * recommended funds' tags / Analyst's Choice priority). Keyed by the planner generate response body, which the
 * report row also carries, so no change to the test class or the report row record is needed.
 */
final class ReportExtras {

    record Extras(String holdings, String recommendedTags) {
    }

    private static final Map<String, Extras> BY_PLANNER_BODY = new ConcurrentHashMap<>();

    private ReportExtras() {
    }

    static void put(String plannerBody, String holdings, String recommendedTags) {
        if (plannerBody != null) {
            BY_PLANNER_BODY.put(plannerBody, new Extras(holdings, recommendedTags));
        }
    }

    static Extras get(String plannerBody) {
        return plannerBody == null ? null : BY_PLANNER_BODY.get(plannerBody);
    }
}

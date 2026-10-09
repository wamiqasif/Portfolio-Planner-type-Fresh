package com.org.apitests.portfolio;

import com.org.apiautomation.models.request.planner.PlannerRequest;
import com.org.apiautomation.models.response.planner.PlannerData;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates that the Planner's response actually reflects the business
 * inputs the request supplied - not just that the call succeeded. Section
 * 3 of the test brief: "extract actual business values, apply business
 * rules... compare expected vs actual", specifically for the request-echo
 * rules explicit in the PRD/API contract (aware_type, investor, and the
 * displayed input summary).
 */
final class InvestmentInputValidator {

    private InvestmentInputValidator() {
    }

    /** When {@code aware_type} is omitted from the request entirely, FRESH is the confirmed default (see {@code PlannerTestData.toRequest}'s Javadoc) - not just "anything goes". */
    static BusinessRuleResult assertAwareTypeEchoed(PlannerRequest request, PlannerData data, String scenario) {
        Object requested = request.get("aware_type");
        String expected = requested == null ? "FRESH" : requested.toString();
        String actual = data.awareType();
        boolean passed = expected.equalsIgnoreCase(actual);
        return BusinessRuleResult.of(passed, "Response aware_type must match the requested aware_type (or default to FRESH when omitted)", scenario, expected, actual);
    }

    static BusinessRuleResult assertInvestorEchoed(PlannerRequest request, PlannerData data, String scenario) {
        Object requested = request.get("investor_id");
        Long actual = data.investor() == null ? null : data.investor().investorId();
        boolean passed = requested != null && actual != null && requested.toString().equals(String.valueOf(actual));
        return BusinessRuleResult.of(passed, "Response investor.investor_id must match the requested investor_id", scenario, requested, actual);
    }

    /**
     * PRD example: {@code "₹50,000 monthly SIP + ₹1,00,00,000 one-time · 15 years"}
     * for {@code monthly_amount=50000, lumpsum_amount=10000000, investment_duration=15, duration_type=y}.
     * Checks each amount actually provided is present, correctly digit-grouped, in the summary text.
     */
    static List<BusinessRuleResult> assertInputSummaryReflectsAmounts(PlannerRequest request, PlannerData data, String scenario) {
        List<BusinessRuleResult> results = new ArrayList<>();
        String summary = data.inputSummaryData() == null ? null : data.inputSummaryData().textFmt();

        results.addAll(checkAmountReflected(request, "monthly_amount", summary, scenario));
        results.addAll(checkAmountReflected(request, "lumpsum_amount", summary, scenario));
        return results;
    }

    private static List<BusinessRuleResult> checkAmountReflected(PlannerRequest request, String field, String summary, String scenario) {
        Object rawAmount = request.get(field);
        if (rawAmount == null) {
            return List.of();
        }
        long amount;
        try {
            amount = Long.parseLong(rawAmount.toString().trim());
        } catch (NumberFormatException notNumeric) {
            return List.of();
        }
        if (amount <= 0) {
            return List.of();
        }

        String expectedFragment = IndianCurrencyFormatter.withRupeeSymbol(amount);
        boolean passed = summary != null && summary.contains(expectedFragment);
        return List.of(BusinessRuleResult.of(passed,
                "input_summary_data.text_fmt must display " + field + " correctly digit-grouped", scenario,
                "contains \"" + expectedFragment + "\"", summary));
    }
}

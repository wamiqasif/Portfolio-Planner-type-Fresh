package com.org.apitests.portfolio;

import com.org.apiautomation.models.request.planner.PlannerRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates the actual outgoing {@link PlannerRequest} JSON body's field
 * presence/absence against the API-1 field-rules contract (see {@code
 * Field_Rules_Reference} sheet in {@code planner_generate_test_data.xlsx}):
 *
 * <ul>
 *   <li>{@code txn_options} is present only for {@code goal_type=GROWTH}.</li>
 *   <li>{@code monthly_amount} is present only for {@code txn_options=SIP}
 *       or {@code SIPLUMPSUM}.</li>
 *   <li>{@code lumpsum_amount} is present only for {@code txn_options=LUMPSUM}
 *       or {@code SIPLUMPSUM}.</li>
 *   <li>{@code accumulated_amount} is present only for {@code
 *       goal_type=REGULAR-INCOME}.</li>
 *   <li>{@code needed_annual_amount} is present only for {@code
 *       goal_type=REGULAR-INCOME} (range 500 - 500,000).</li>
 *   <li>{@code tax_saving_amount} is present only for {@code
 *       goal_type=TAX-SAVINGS}.</li>
 * </ul>
 *
 * <p>"Absent" is checked via {@link PlannerRequest#has(String)} - the field
 * must not be a key in the outgoing body at all, not merely {@code null}/
 * {@code 0}/{@code ""} (per the automation brief: "blank means the field
 * MUST NOT EXIST in the JSON payload").
 *
 * <p>This validates the <em>request</em> this framework built, before it is
 * sent - it is not a response assertion, so it never needs a live API call
 * and runs the same whether the subsequent Planner Generate call succeeds,
 * fails, or times out.
 */
final class PlannerPayloadPresenceValidator {

    private PlannerPayloadPresenceValidator() {
    }

    static List<BusinessRuleResult> assertPresenceRules(PlannerRequest request, String scenario) {
        String goalType = stringOf(request.get("goal_type"));
        String txnOptions = stringOf(request.get("txn_options"));

        boolean isGrowth = "GROWTH".equalsIgnoreCase(goalType);
        boolean isRegularIncome = "REGULAR-INCOME".equalsIgnoreCase(goalType);
        boolean isTaxSaving = "TAX-SAVINGS".equalsIgnoreCase(goalType);

        boolean expectMonthly = isGrowth && ("SIP".equalsIgnoreCase(txnOptions) || "SIPLUMPSUM".equalsIgnoreCase(txnOptions));
        boolean expectLumpsum = isGrowth && ("LUMPSUM".equalsIgnoreCase(txnOptions) || "SIPLUMPSUM".equalsIgnoreCase(txnOptions));

        List<BusinessRuleResult> results = new ArrayList<>();
        results.add(presenceCheck(request, "txn_options", isGrowth, scenario));
        results.add(presenceCheck(request, "monthly_amount", expectMonthly, scenario));
        results.add(presenceCheck(request, "lumpsum_amount", expectLumpsum, scenario));
        results.add(presenceCheck(request, "accumulated_amount", isRegularIncome, scenario));
        results.add(presenceCheck(request, "needed_annual_amount", isRegularIncome, scenario));
        results.add(presenceCheck(request, "tax_saving_amount", isTaxSaving, scenario));
        return results;
    }

    private static BusinessRuleResult presenceCheck(PlannerRequest request, String field, boolean expectedPresent, String scenario) {
        boolean actuallyPresent = request.has(field);
        boolean passed = expectedPresent == actuallyPresent;
        return BusinessRuleResult.of(passed,
                "Outgoing payload field '" + field + "' must be " + (expectedPresent ? "present" : "absent")
                        + " for this goal_type/txn_options combination",
                scenario, expectedPresent ? "present" : "absent", actuallyPresent ? "present" : "absent");
    }

    private static String stringOf(Object value) {
        return value == null ? null : value.toString();
    }
}

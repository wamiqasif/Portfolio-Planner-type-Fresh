package com.org.apitests.portfolio;

/**
 * One business-rule check's outcome, carrying everything section 12 of the
 * test brief asks a business-rule failure to show: which rule, which
 * scenario/plan it was checked against, and expected vs. actual. Every
 * validator in this package returns these instead of asserting directly, so
 * a test method can collect several checks and report all of them together
 * via {@link BusinessRuleReporter#assertAll}.
 */
public record BusinessRuleResult(
        String rule,
        String scenario,
        String planId,
        Object expected,
        Object actual,
        boolean passed) {

    public static BusinessRuleResult pass(String rule, String scenario, String planId, Object expected, Object actual) {
        return new BusinessRuleResult(rule, scenario, planId, expected, actual, true);
    }

    public static BusinessRuleResult fail(String rule, String scenario, String planId, Object expected, Object actual) {
        return new BusinessRuleResult(rule, scenario, planId, expected, actual, false);
    }

    /** Convenience for rules with no specific plan_id (e.g. an allocation-level check). */
    public static BusinessRuleResult of(boolean passed, String rule, String scenario, Object expected, Object actual) {
        return new BusinessRuleResult(rule, scenario, null, expected, actual, passed);
    }
}

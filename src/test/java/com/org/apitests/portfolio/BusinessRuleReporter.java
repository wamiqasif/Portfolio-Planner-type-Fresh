package com.org.apitests.portfolio;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.Assert;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Logs every {@link BusinessRuleResult} in the "BUSINESS RULE FAILURE"/PASS
 * block format from the test brief (section 12), then fails the test with a
 * single combined message listing every failed rule if any did - a manual
 * aggregation step since {@link BusinessRuleResult} isn't a TestNG
 * assertion primitive {@code SoftAssert} understands natively.
 */
final class BusinessRuleReporter {

    private static final Logger LOGGER = LogManager.getLogger(BusinessRuleReporter.class);

    private BusinessRuleReporter() {
    }

    static void assertAll(List<BusinessRuleResult> results) {
        results.forEach(BusinessRuleReporter::log);

        List<BusinessRuleResult> failures = results.stream().filter(r -> !r.passed()).toList();
        if (!failures.isEmpty()) {
            Assert.fail(failures.size() + " business rule(s) failed:\n" +
                    failures.stream().map(BusinessRuleReporter::block).collect(Collectors.joining("\n")));
        }
    }

    private static void log(BusinessRuleResult result) {
        if (result.passed()) {
            LOGGER.info("BUSINESS RULE PASS - {}", block(result));
        } else {
            LOGGER.warn("BUSINESS RULE FAILURE - {}", block(result));
        }
    }

    private static String block(BusinessRuleResult r) {
        return "------------------------------------------\n" +
                (r.scenario() != null ? "Scenario: " + r.scenario() + "\n" : "") +
                "Rule: " + r.rule() + "\n" +
                (r.planId() != null ? "Plan ID: " + r.planId() + "\n" : "") +
                "Expected: " + r.expected() + "\n" +
                "Actual: " + r.actual() + "\n" +
                "Result: " + (r.passed() ? "PASS" : "FAIL") + "\n" +
                "------------------------------------------";
    }
}

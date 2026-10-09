package com.org.apitests.portfolio;

import com.org.apiautomation.models.response.planner.DisclaimerData;

/**
 * PRD section 11.10: "The below line is missing in the Advisor App version
 * of disclaimer but it is there in the Advisor web version. Please add it
 * to the app version as well and make it uniform." - i.e. the Advisor App's
 * Planner response must include this sentence in its disclaimer bullets,
 * confirmed present in both live samples ({@code planner-fresh-response.json},
 * {@code planner-portfolio-aware-response.json}).
 */
final class DisclaimerValidator {

    static final String REQUIRED_SENTENCE =
            "The Operator, its directors, employees, and agents may have holding or position in the securities advised herein.";

    private DisclaimerValidator() {
    }

    static BusinessRuleResult assertRequiredSentencePresent(DisclaimerData disclaimerData, String scenario) {
        boolean present = disclaimerData != null && disclaimerData.data() != null
                && disclaimerData.data().stream().anyMatch(line -> line != null && line.contains(REQUIRED_SENTENCE));

        return BusinessRuleResult.of(present,
                "Disclaimer must include the holding/position sentence, uniform with the web version (PRD 11.10)",
                scenario, REQUIRED_SENTENCE, disclaimerData == null ? null : disclaimerData.data());
    }
}

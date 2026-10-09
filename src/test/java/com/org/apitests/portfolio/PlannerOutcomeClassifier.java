package com.org.apitests.portfolio;

import io.restassured.response.Response;

/**
 * Classifies a Planner Generate {@link Response} into the automation
 * brief's failure taxonomy, shared by {@link PortfolioPlannerFieldRulesDataDrivenTests}
 * and {@link PortfolioPlannerFieldRulesBoundaryTests} so the same HTTP/
 * envelope outcome is never classified two different ways depending on
 * which test class happened to see it.
 *
 * <p>Deliberately does not attempt to distinguish "business-rule failure"
 * here - a non-2xx/{@code status:false} outcome only ever means {@link
 * PlannerFieldRulesReportRow.Classification#API_CONTRACT_FAILURE} at this
 * layer; whether that outcome was actually *expected* (a boundary/negative
 * case) or not (a positive combination) is the caller's business, not
 * this class's.
 */
final class PlannerOutcomeClassifier {

    record Outcome(PlannerFieldRulesReportRow.Classification classification, String reason) {
        boolean isSuccess() {
            return classification == PlannerFieldRulesReportRow.Classification.PASS;
        }
    }

    private PlannerOutcomeClassifier() {
    }

    /** {@code null} means: a genuine HTTP 200 with {@code status:true} - proceed to business-rule validation. Anything else is a terminal outcome for the row. */
    static Outcome classify(Response response) {
        int httpStatus = response.statusCode();
        String rawBody = safeBody(response);

        if (httpStatus == 500) {
            return new Outcome(PlannerFieldRulesReportRow.Classification.API_500, "Planner Generate returned HTTP 500. Body: " + rawBody);
        }
        if (httpStatus == 502) {
            return new Outcome(PlannerFieldRulesReportRow.Classification.API_502, "Planner Generate returned HTTP 502. Body: " + rawBody);
        }
        if (httpStatus == 503) {
            return new Outcome(PlannerFieldRulesReportRow.Classification.API_503, "Planner Generate returned HTTP 503. Body: " + rawBody);
        }
        if (httpStatus == 401 || httpStatus == 403) {
            return new Outcome(PlannerFieldRulesReportRow.Classification.AUTHENTICATION_FAILURE,
                    "Planner Generate returned HTTP " + httpStatus + " even after the framework's automatic auth-retry. Body: " + rawBody);
        }
        if (isLabelOrInvestorNotFound(rawBody)) {
            return new Outcome(PlannerFieldRulesReportRow.Classification.DATA_NOT_AVAILABLE,
                    "label_id/investor_id not resolvable in this environment. Body: " + rawBody);
        }
        if (httpStatus != 200) {
            return new Outcome(PlannerFieldRulesReportRow.Classification.API_CONTRACT_FAILURE,
                    "HTTP " + httpStatus + ". Body: " + rawBody);
        }
        try {
            Boolean status = response.jsonPath().getBoolean("status");
            if (Boolean.FALSE.equals(status)) {
                String message = response.jsonPath().getString("message");
                return new Outcome(PlannerFieldRulesReportRow.Classification.API_CONTRACT_FAILURE,
                        "HTTP 200 with status:false. Message: " + message);
            }
        } catch (RuntimeException notParseable) {
            return new Outcome(PlannerFieldRulesReportRow.Classification.FRAMEWORK_FAILURE,
                    "HTTP 200 body could not be parsed: " + notParseable.getMessage() + ". Body: " + rawBody);
        }
        return new Outcome(PlannerFieldRulesReportRow.Classification.PASS, null);
    }

    private static boolean isLabelOrInvestorNotFound(String rawBody) {
        if (rawBody == null) {
            return false;
        }
        String lower = rawBody.toLowerCase();
        return lower.contains("label(s) not found") || lower.contains("investor not found") || lower.contains("label not found");
    }

    private static String safeBody(Response response) {
        try {
            return response.getBody().asString();
        } catch (RuntimeException unreadable) {
            return "<unreadable body: " + unreadable.getMessage() + ">";
        }
    }
}

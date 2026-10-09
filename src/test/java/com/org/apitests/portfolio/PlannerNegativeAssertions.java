package com.org.apitests.portfolio;

import io.restassured.response.Response;

/** Shared by {@code PortfolioPlannerNegativeTests} and {@code PortfolioPlannerBoundaryTests}: what "the API correctly rejected this input" means for the Planner Generate API. */
final class PlannerNegativeAssertions {

    private PlannerNegativeAssertions() {
    }

    /** Rejected = a non-2xx HTTP status, or a 200 whose body reports {@code status:false} - this backend uses the latter for at least some business-validation failures (never returns a bare empty body on failure, per the Hub API's own precedent), so both must be checked rather than assuming one. */
    static boolean wasRejected(Response response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return true;
        }
        try {
            Boolean status = response.jsonPath().getBoolean("status");
            return Boolean.FALSE.equals(status);
        } catch (Exception notJsonOrNoStatusField) {
            return false;
        }
    }
}

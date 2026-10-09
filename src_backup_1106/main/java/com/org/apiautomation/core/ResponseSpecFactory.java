package com.org.apiautomation.core;

import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.specification.ResponseSpecification;

/**
 * The response-side counterpart to {@link RequestSpecFactory}: a single
 * {@link ResponseSpecification} applied to every response executed through
 * {@link ApiClient}.
 *
 * <p><b>Design decision - carries no status-code, schema, or body
 * expectations.</b> What counts as a "correct" response is a test/business
 * decision (a 404 is exactly what a negative test wants), not something the
 * core HTTP layer can decide for every caller. Baking an expectation in here
 * would make it fail for every consumer that doesn't match it - the core
 * layer overstepping into the assertions layer's job.
 *
 * <p><b>Currently a true no-op, and that's fine.</b> This used to carry
 * {@code .log(LogDetail.ALL)}, which has been removed: it printed straight
 * to {@code System.out} (bypassing Log4j2) and logged headers verbatim,
 * including {@code Authorization} - see {@code RequestSpecFactory}'s
 * Javadoc. Response logging is now {@code listeners.HttpLoggingFilter}'s job
 * (attached as a request-side filter, so it sees the response too, with
 * redaction). What's left here is the extension point for future
 * response-wide expectations that genuinely are framework-level, not
 * business-level - e.g. "every response must include a trace-id header."
 * Applying an empty spec via {@code response.then().spec(...)} costs
 * nothing and lets that be added later without any call site changing.
 */
public final class ResponseSpecFactory {

    private ResponseSpecFactory() {
    }

    public static ResponseSpecification defaultResponseSpec() {
        return new ResponseSpecBuilder().build();
    }
}

package com.org.apiautomation.core;

import com.org.apiautomation.auth.AuthStrategyFactory;
import com.org.apiautomation.auth.DynamicLoginAuthStrategy;
import io.restassured.RestAssured;
import io.restassured.http.Headers;
import io.restassured.http.Method;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;

/**
 * The single place every HTTP call in the framework physically fires from.
 *
 * <p><b>Design decision - stateless, no Singleton.</b> Unlike
 * {@code ConfigManager} (which protects one expensive, shared resource - the
 * loaded properties), {@code ApiClient} holds no fields at all. Every call
 * receives the exact {@link RequestSpecification} it should execute, built
 * by {@link RequestSpecFactory} and customized by the caller with Rest
 * Assured's own fluent methods. With no shared mutable state, there is
 * nothing to protect under parallel execution and nothing a Singleton would
 * add - plain instance methods on a freely-constructible class are simpler
 * and just as safe, and can be mocked or subclassed later if a future layer
 * (retry, auth) needs to wrap it.
 *
 * <p><b>Design decision - one {@code execute()} backs every verb (GET, POST,
 * PUT, PATCH, DELETE, HEAD, OPTIONS).</b> Rest Assured's {@code
 * RequestSpecification.request(Method, String)} takes the HTTP method as
 * data. Near-identical hand-rolled blocks (one per verb) would duplicate the
 * exact same lines for no benefit; routing all of them through one private
 * method keeps the verb methods themselves to a single line each and
 * guarantees they can't drift out of sync with each other (e.g. one verb
 * silently forgetting to apply the response spec).
 *
 * <p><b>Design decision - returns the raw {@link Response}, never an
 * auto-deserialized model.</b> Converting to a typed object here would hide
 * status code, headers, and raw body detail that negative tests specifically
 * need to inspect. The (future) service layer decides if and when to convert
 * to a model; this layer's only job is executing the call.
 *
 * <p>Query params, path params, headers, cookies, and multipart are never
 * mentioned in this class - they're configured by the caller directly on
 * the {@link RequestSpecification} before it's passed in, using Rest
 * Assured's own native methods. See {@link RequestSpecFactory} for why.
 *
 * <p><b>Design decision - the 401/403-triggered auth retry lives here, as
 * two independent top-level requests, not as a Rest Assured {@code Filter}
 * calling {@code FilterContext.next()} twice.</b> Verified directly rather
 * than assumed: a {@code Filter} can only call {@code ctx.next()} once -
 * a second call returns {@code null} - and {@code ctx.send(...)} re-enters
 * the whole filter chain, including the filter itself, causing infinite
 * recursion. Firing two separate, complete requests here avoids both: each
 * independently traverses the full filter chain exactly once, so both
 * attempts get their own {@code HttpLoggingFilter} log entry for free.
 *
 * <p>The retry only applies when the spec already carries an {@code
 * Authorization} header <b>or</b> an {@code auth-token} header - verified
 * empirically that {@code .auth().preemptive().oauth2(...)}/{@code
 * .basic(...)} and {@link DynamicLoginAuthStrategy}'s explicit {@code
 * .header(...)} both manifest synchronously, before the request is ever
 * sent, so this check is reliable for either. This is what stops a
 * deliberately-unauthenticated call (built via {@code
 * RequestSpecFactory.unauthenticatedRequestSpec()} - e.g. a negative test
 * asserting a protected endpoint rejects a request with no credentials)
 * from being "fixed" into a 200 by an unwanted retry: with neither header
 * present, the check is false and the 401/403 is returned as-is, exactly
 * as the test expects.
 *
 * <p>Reapplying the (possibly-invalidated-and-refreshed) strategy onto the
 * same spec is safe to do more than once for every current strategy, but
 * for two different reasons: {@code .auth()}-based strategies (Basic,
 * static Bearer, OAuth2) replace the header rather than duplicating it,
 * verified empirically; {@link DynamicLoginAuthStrategy} uses plain {@code
 * .header(...)} (which *does* duplicate on a second call) but explicitly
 * removes the old value first - see its Javadoc. {@code ApiKeyAuthStrategy}
 * also uses plain {@code .header()} and would duplicate on retry, but never
 * reaches this path in the first place: its {@code invalidate()} is a no-op
 * and it never sets {@code Authorization} or {@code auth-token}.
 */
public final class ApiClient {

    private static final Logger LOGGER = LogManager.getLogger(ApiClient.class);
    private static final Set<Integer> AUTH_FAILURE_STATUS_CODES = Set.of(401, 403);

    public Response get(String endpoint) {
        return get(endpoint, RequestSpecFactory.defaultRequestSpec());
    }

    public Response get(String endpoint, RequestSpecification requestSpec) {
        return execute(Method.GET, endpoint, requestSpec);
    }

    public Response post(String endpoint) {
        return post(endpoint, RequestSpecFactory.defaultRequestSpec());
    }

    public Response post(String endpoint, Object body) {
        return post(endpoint, RequestSpecFactory.defaultRequestSpec(), body);
    }

    public Response post(String endpoint, RequestSpecification requestSpec) {
        return execute(Method.POST, endpoint, requestSpec);
    }

    public Response post(String endpoint, RequestSpecification requestSpec, Object body) {
        return post(endpoint, requestSpec.body(body));
    }

    public Response put(String endpoint, Object body) {
        return put(endpoint, RequestSpecFactory.defaultRequestSpec(), body);
    }

    public Response put(String endpoint, RequestSpecification requestSpec) {
        return execute(Method.PUT, endpoint, requestSpec);
    }

    public Response put(String endpoint, RequestSpecification requestSpec, Object body) {
        return put(endpoint, requestSpec.body(body));
    }

    public Response patch(String endpoint, Object body) {
        return patch(endpoint, RequestSpecFactory.defaultRequestSpec(), body);
    }

    public Response patch(String endpoint, RequestSpecification requestSpec) {
        return execute(Method.PATCH, endpoint, requestSpec);
    }

    public Response patch(String endpoint, RequestSpecification requestSpec, Object body) {
        return patch(endpoint, requestSpec.body(body));
    }

    public Response delete(String endpoint) {
        return delete(endpoint, RequestSpecFactory.defaultRequestSpec());
    }

    public Response delete(String endpoint, RequestSpecification requestSpec) {
        return execute(Method.DELETE, endpoint, requestSpec);
    }

    public Response head(String endpoint, RequestSpecification requestSpec) {
        return execute(Method.HEAD, endpoint, requestSpec);
    }

    public Response options(String endpoint, RequestSpecification requestSpec) {
        return execute(Method.OPTIONS, endpoint, requestSpec);
    }

    private Response execute(Method method, String endpoint, RequestSpecification requestSpec) {
        boolean wasAuthenticated = isAuthenticatedRequest(requestSpec);

        // Deliberately routed through RestAssured.given().spec(...) rather than
        // calling requestSpec.request(method, endpoint) directly. Firing a
        // RequestSpecification straight off RequestSpecBuilder.build() hits a real
        // internal Rest Assured/Groovy defect (NPE: "assertionClosure on null
        // object" in RequestSpecificationImpl) - given().spec(...) is the
        // officially documented idiom and does not carry this defect.
        Response response = RestAssured.given().spec(requestSpec).request(method, endpoint);

        if (wasAuthenticated && AUTH_FAILURE_STATUS_CODES.contains(response.statusCode())) {
            LOGGER.warn("Received {} on an authenticated call - invalidating the cached token and retrying once", response.statusCode());
            AuthStrategyFactory.getStrategy().invalidate();
            AuthStrategyFactory.getStrategy().apply(requestSpec);
            response = RestAssured.given().spec(requestSpec).request(method, endpoint);
        }

        response.then().spec(ResponseSpecFactory.defaultResponseSpec());
        return response;
    }

    private boolean isAuthenticatedRequest(RequestSpecification requestSpec) {
        Headers headers = ((FilterableRequestSpecification) requestSpec).getHeaders();
        return headers.hasHeaderWithName("Authorization") || headers.hasHeaderWithName(DynamicLoginAuthStrategy.AUTH_TOKEN_HEADER);
    }
}

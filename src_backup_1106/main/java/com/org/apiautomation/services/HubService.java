package com.org.apiautomation.services;

import com.org.apiautomation.config.ConfigManager;
import com.org.apiautomation.core.ApiClient;
import com.org.apiautomation.core.RequestSpecFactory;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * Facade over the Hub navigation API (approved test plan:
 * {@code docs/test-plans/hub-api-test-plan.md}). Every {@code Hub*Tests}
 * class calls this - never {@code ApiClient}/{@code RequestSpecFactory}
 * directly - exactly the role {@code services} already plays for {@code
 * PostService}.
 *
 * <p><b>Why every method takes/returns the raw {@code RequestSpecification}/
 * {@code Response}:</b> negative tests (invalid/missing/expired token,
 * wrong method, invalid headers) need to customize or deliberately omit
 * things a "happy path only" method couldn't express, and need the same
 * raw status/header access positive tests get. Consistent with {@code
 * PostService} and {@code core.ApiClient}'s own documented design decision.
 *
 * <p><b>Why there's no {@code BaseService}:</b> same reasoning as {@code
 * PostService} - two services still isn't enough shared logic to justify
 * extracting a base class over.
 *
 * <p><b>Authentication is never handled here.</b> {@link #getHub()} builds
 * its spec via {@code RequestSpecFactory.defaultRequestSpec()}, which
 * already attaches a valid {@code auth-token} automatically (fetched via
 * {@code auth.AuthenticationManager}, transparently, per the framework's
 * existing design). This class never calls {@code AuthenticationManager}
 * itself and never sees a token value - only the negative-auth test helpers
 * in the test classes deliberately build their own unauthenticated spec to
 * inject a bad/missing token, and even those never touch
 * {@code AuthenticationManager} either.
 */
public final class HubService {

    private static final String LEGACY_PATH = "/api/hub/";

    /** The happy path: GET with a valid, framework-managed {@code auth-token}. */
    public Response getHub() {
        return getHub(RequestSpecFactory.defaultRequestSpec());
    }

    /** For negative/edge tests that need a customized spec (bad token, extra headers/params, etc.) - built by the caller, never by this class. */
    public Response getHub(RequestSpecification requestSpec) {
        return new ApiClient().get(hubPath(), requestSpec);
    }

    /** The pre-versioning "Fawkes" endpoint, still live per the API documentation - for backward-compatibility checks (HUB-FUN-08, HUB-BC-01). */
    public Response getHubViaLegacyEndpoint() {
        return new ApiClient().get(LEGACY_PATH, RequestSpecFactory.defaultRequestSpec());
    }

    public Response postToHub() {
        return new ApiClient().post(hubPath(), RequestSpecFactory.defaultRequestSpec());
    }

    /** No body is sent - {@code ApiClient.put(String, RequestSpecification, Object)}'s {@code Object} overload calls {@code requestSpec.body(...)}, which throws on a literal {@code null}. */
    public Response putToHub() {
        return new ApiClient().put(hubPath(), RequestSpecFactory.defaultRequestSpec());
    }

    /** No body is sent - see {@link #putToHub()}'s Javadoc for why the bodyless overload is used deliberately. */
    public Response patchToHub() {
        return new ApiClient().patch(hubPath(), RequestSpecFactory.defaultRequestSpec());
    }

    public Response deleteHub() {
        return new ApiClient().delete(hubPath(), RequestSpecFactory.defaultRequestSpec());
    }

    public Response headHub() {
        return new ApiClient().head(hubPath(), RequestSpecFactory.defaultRequestSpec());
    }

    public Response optionsHub() {
        return new ApiClient().options(hubPath(), RequestSpecFactory.defaultRequestSpec());
    }

    /** Public so negative-auth test helpers can fire a raw, single-shot request against the exact same path this class uses - see {@code HubAuthenticationTests}. */
    public String hubPath() {
        return "/api/" + ConfigManager.getInstance().getApiVersion() + "/hub/";
    }
}

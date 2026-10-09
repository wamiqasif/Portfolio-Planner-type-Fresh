package com.org.apiautomation.services;

import com.org.apiautomation.config.ConfigManager;
import com.org.apiautomation.core.ApiClient;
import com.org.apiautomation.core.RequestSpecFactory;
import com.org.apiautomation.models.request.planner.PlannerRequest;
import com.org.apiautomation.auth.Api1AuthService;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * Facade over the Planner Generate API (Portfolio Planner - see {@code
 * docs/test-plans/Portfolio-Planner-PRD.md} and {@code
 * docs/test-plans/portfolio-planner-apis.md}). Mirrors {@code HubService}'s
 * design: every {@code PortfolioPlanner*Tests} class calls this, never
 * {@code ApiClient}/{@code RequestSpecFactory} directly.
 *
 * <p><b>POST with a JSON body</b> - confirmed against a real, currently-live
 * curl (Content-Type: application/json, every field in the {@code --data}
 * body). An earlier round of live curls in {@code
 * docs/test-plans/portfolio-planner-apis.md} sent the same fields as query
 * params instead with no body; that shape is stale - {@code
 * RequestSpecFactory.defaultRequestSpec(String)} already sets {@code
 * Content-Type: application/json}, so this only needs to attach {@link
 * PlannerRequest#toBody()} as the body via {@code ApiClient}'s existing
 * body-overload, not build query params.
 *
 * <p><b>Host changed 2026-09-08:</b> Planner Generate now lives at {@code
 * /api/v1/planner/funds-buy/generate} on the advisor app API gateway
 * ({@link ConfigManager#getAdvisorApiBaseUrl()} - {@code
 * qaadvappapi.valueresearch.in} in QA), not the main app API host every
 * earlier version of this endpoint used. Same host {@link PortfolioService}
 * already talks to for the User Portfolio API - this is now the second
 * Planner-module service on that gateway, not an outlier.
 */
public final class PlannerService {

    /** The happy path: POST with a valid, framework-managed {@code auth-token} and the given inputs. */
    public Response generate(PlannerRequest request) {
        return generate(request, RequestSpecFactory.defaultRequestSpec(ConfigManager.getInstance().getAdvisorApiBaseUrl()));
    }

    /** For negative/edge tests needing a customized spec (bad token, extra headers, etc.) - built by the caller. */
    public Response generate(PlannerRequest request, RequestSpecification requestSpec) {
        return new ApiClient().post(plannerPath(), requestSpec, request.toBody());
    }

    /**
     * API-1: {@code POST api1.base.url + api1.endpoint} with {@code
     * Authorization: Bearer <access_token>} only (see {@link Api1AuthService}).
     * Deliberately bypasses {@code ApiClient}: its 401/403 retry would re-apply
     * the shared {@code vrlogin} strategy and attach an {@code auth-token}
     * header, which this endpoint must never receive. Retries once itself with
     * a freshly minted access token instead.
     */
    public Response generateApi1(PlannerRequest request) {
        ConfigManager config = ConfigManager.getInstance();
        Response response = postApi1(config, request, Api1AuthService.getInstance().getAccessToken());
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            Api1AuthService.getInstance().invalidate();
            response = postApi1(config, request, Api1AuthService.getInstance().getAccessToken());
        }
        return response;
    }

    private Response postApi1(ConfigManager config, PlannerRequest request, String accessToken) {
        // Body only - the Bearer token is never printed (HttpLoggingFilter redacts Authorization too).
        System.out.println("API-1 planner request payload: " + request.toJson());
        RequestSpecification spec = RequestSpecFactory.unauthenticatedRequestSpec(config.getApi1BaseUrl())
                .header("Authorization", "Bearer " + accessToken);
        return RestAssured.given().spec(spec).body(request.toBody()).post(config.getApi1Endpoint());
    }

    public String plannerPath() {
        return ConfigManager.getInstance().getPlannerEndpoint();
    }
}

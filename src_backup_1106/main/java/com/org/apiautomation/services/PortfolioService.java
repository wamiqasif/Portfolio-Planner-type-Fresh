package com.org.apiautomation.services;

import com.org.apiautomation.config.ConfigManager;
import com.org.apiautomation.core.ApiClient;
import com.org.apiautomation.core.RequestSpecFactory;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * Facade over the User Portfolio API - {@code GET
 * /api/v1/user-portfolio/label-opinion-data?label-ids=...} - used by
 * {@code PORTFOLIO_AWARE} scenarios to fetch an investor's existing holdings
 * for cross-validation against the Planner's recommendation.
 *
 * <p><b>Why this talks to a different host than {@code PlannerService}:</b>
 * confirmed via a live curl - this endpoint is served from the advisor app
 * API gateway ({@link ConfigManager#getAdvisorApiBaseUrl()}), not the main
 * app API host every other service in this module uses. Authentication is
 * still applied automatically ({@code RequestSpecFactory.defaultRequestSpec(String)}
 * attaches it regardless of base URI).
 */
public final class PortfolioService {

    public Response getLabelOpinionData(String labelId) {
        return getLabelOpinionData(labelId, RequestSpecFactory.defaultRequestSpec(ConfigManager.getInstance().getAdvisorApiBaseUrl()));
    }

    public Response getLabelOpinionData(String labelId, RequestSpecification requestSpec) {
        return new ApiClient().get(labelOpinionPath(), requestSpec.queryParam("label-ids", labelId));
    }

    public String labelOpinionPath() {
        return ConfigManager.getInstance().getPortfolioLabelOpinionEndpoint();
    }
}

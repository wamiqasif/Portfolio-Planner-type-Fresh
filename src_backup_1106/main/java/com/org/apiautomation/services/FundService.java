package com.org.apiautomation.services;

import com.org.apiautomation.config.ConfigManager;
import com.org.apiautomation.core.ApiClient;
import com.org.apiautomation.core.RequestSpecFactory;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.List;

/**
 * Facade over every fund-lookup API the Portfolio Planner flow depends on:
 * Fund Opinion, Analyst Pick (same endpoint, different query), Fund
 * Details, Fund Plan Options Master, Fund Filter, and the plan-txn-details
 * GQL component. One class rather than five - all five are thin GETs
 * against the same {@code /api/v1/funds/...} family with no shared state
 * worth a base class, exactly {@code HubService}'s and {@code
 * PostService}'s existing precedent for "one facade per API domain."
 */
public final class FundService {

    private static final String FUND_OPINION_FIELDS = "plan_id,vr_rating,opinion_type_id,is_analyst_pick";
    private static final String ANALYST_PICK_FIELDS = "plan_id,priority_order,tag_name";
    private static final String FUND_PLAN_OPTIONS_FIELDS = "plan_id,sip_investment_details";

    /** Fund Opinion API - {@code GET fund-opinion-data/get-list?plan_id=<csv>&fields=...}. */
    public Response getFundOpinionData(List<String> planIds) {
        return getFundOpinionData(planIds, RequestSpecFactory.defaultRequestSpec());
    }

    /** Same as {@link #getFundOpinionData(List)}, against a caller-supplied spec (e.g. a per-row authenticated session) instead of the framework's shared default. */
    public Response getFundOpinionData(List<String> planIds, RequestSpecification requestSpec) {
        return new ApiClient().get(fundOpinionPath(), requestSpec
                .queryParam("plan_id", String.join(",", planIds))
                .queryParam("fields", FUND_OPINION_FIELDS));
    }

    /** Analyst Pick API - same endpoint as Fund Opinion, called with {@code is_analyst_pick=true} instead of a plan_id filter. */
    public Response getAnalystPicks() {
        return new ApiClient().get(fundOpinionPath(), RequestSpecFactory.defaultRequestSpec()
                .queryParam("is_analyst_pick", true)
                .queryParam("fields", ANALYST_PICK_FIELDS));
    }

    /** Fund Details API - {@code GET /api/v1/funds/{planId}}. */
    public Response getFundDetails(long planId) {
        return getFundDetails(planId, RequestSpecFactory.defaultRequestSpec());
    }

    public Response getFundDetails(long planId, RequestSpecification requestSpec) {
        return new ApiClient().get(ConfigManager.getInstance().getFundDetailsEndpoint() + "/" + planId, requestSpec);
    }

    /** Fund Plan Options Master - {@code GET fund-plan-options-masters/get-list?plan_id=<csv>&fields=...&page=1&limit=3000}. */
    public Response getFundPlanOptions(List<String> planIds) {
        return new ApiClient().get(ConfigManager.getInstance().getFundPlanOptionsEndpoint(), RequestSpecFactory.defaultRequestSpec()
                .queryParam("plan_id", String.join(",", planIds))
                .queryParam("fields", FUND_PLAN_OPTIONS_FIELDS)
                .queryParam("page", 1)
                .queryParam("limit", 3000));
    }

    /**
     * Fund Filter API - {@code GET /api/v1/funds/filter/{encodedFilter}}. The
     * filter itself is an opaque, backend-generated encoded string (see the
     * live curl examples) - this method exists so the framework can execute
     * the call when a test supplies one; no dedicated business-rule test
     * exercises this without a documented meaning for the encoding.
     */
    public Response getFundsByFilter(String encodedFilter) {
        return new ApiClient().get(ConfigManager.getInstance().getFundFilterEndpoint() + "/" + encodedFilter, RequestSpecFactory.defaultRequestSpec());
    }

    /** Plan Transaction Details GQL component - {@code GET /api/v1/gql/component?screen=fund-portfolio-planner-edit&componentName=plan-txn-details-alt&params={"plan_id":...}}. */
    public Response getPlanTxnDetails(long planId) {
        return new ApiClient().get(ConfigManager.getInstance().getGqlComponentEndpoint(), RequestSpecFactory.defaultRequestSpec()
                .queryParam("screen", "fund-portfolio-planner-edit")
                .queryParam("componentName", "plan-txn-details-alt")
                .queryParam("params", "{\"plan_id\":" + planId + "}"));
    }

    /** Config Master API - {@code GET /api/v1/conf/config-masters/get?domain=...&namespace=...&key=...}. */
    public Response getConfigMaster(String domain, String namespace, String key) {
        return new ApiClient().get(ConfigManager.getInstance().getConfigMasterEndpoint(), RequestSpecFactory.defaultRequestSpec()
                .queryParam("domain", domain)
                .queryParam("namespace", namespace)
                .queryParam("key", key));
    }

    public String fundOpinionPath() {
        return ConfigManager.getInstance().getFundOpinionEndpoint();
    }
}

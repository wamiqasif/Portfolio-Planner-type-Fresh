package com.org.apitests.portfolio;

import org.testng.annotations.Test;
import org.testng.Assert;
import com.org.apiautomation.auth.Api1AuthService;
import com.org.apiautomation.models.request.planner.PlannerRequest;
import com.org.apiautomation.services.PlannerService;
import com.org.apitests.base.BaseTest;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Live check of the API-1 flow (login -> token/create -> Bearer planner call).
 * Run ONE test first: {@code -Dtest=Api1PlannerAuthIntegrationTests#api1_singleCall_authFlowWorks}.
 * Prints statuses and YES/NO flags only - never a token value.
 */
public class Api1PlannerAuthIntegrationTests extends BaseTest {

    private final PlannerService plannerService = new PlannerService();

    @Test(groups = {"api1"}, description = "API-1: full login -> token/create -> Bearer planner call, exactly once.")
    public void api1_singleCall_authFlowWorks() {
        Response response = plannerService.generateApi1(PlannerTestData.toRequest(PlannerTestData.scenario("Portfolio Aware - Growth")));

        Api1AuthService.Diagnostics d = Api1AuthService.getInstance().lastDiagnostics();
        System.out.println("login HTTP status            = " + d.loginStatus());
        System.out.println("login token extracted        = " + (d.loginTokenExtracted() ? "YES" : "NO"));
        System.out.println("token-generation HTTP status = " + d.tokenCreateStatus());
        System.out.println("access_token extracted       = " + (d.accessTokenExtracted() ? "YES" : "NO"));
        System.out.println("planner HTTP status          = " + response.statusCode());

        Assert.assertTrue(d.loginTokenExtracted(), "login token not extracted");
        Assert.assertTrue(d.accessTokenExtracted(), "access_token not extracted");
        Assert.assertEquals(response.statusCode(), 200, "planner HTTP status");
    }

    @Test(groups = {"api1"}, description = "API-1: Portfolio Aware - Growth scenario succeeds.")
    public void api1_portfolioAwareGrowth_succeeds() {
        assertSuccess(plannerService.generateApi1(PlannerTestData.toRequest(PlannerTestData.scenario("Portfolio Aware - Growth"))));
    }

    @Test(groups = {"api1"}, description = "API-1: Portfolio Aware - Growth with a 10-year duration succeeds.")
    public void api1_portfolioAwareGrowthTenYears_succeeds() {
        assertSuccess(plannerService.generateApi1(
                PlannerTestData.toRequest(PlannerTestData.scenario("Portfolio Aware - Growth")).investmentDuration(10)));
    }

    @Test(groups = {"api1"}, description = "API-1: REGULAR-INCOME portfolio-aware request (the confirmed live curl body) succeeds.")
    public void api1_regularIncomePortfolioAware_succeeds() {
        PlannerRequest request = PlannerRequest.builder()
                .goalType("REGULAR-INCOME").durationType("m").investmentDuration(5)
                .accumulatedAmount(5000000).labelId(89660240L).userId(123).riskProfileId(2)
                .annualIncomeRange(1).awareType("PORTFOLIO_AWARE").neededAnnualAmount(555).investorId(87683240L);
        assertSuccess(plannerService.generateApi1(request));
    }

    @Test(groups = {"api1"}, description = "API-1: Portfolio Aware - Growth as LUMPSUM succeeds.")
    public void api1_portfolioAwareGrowthLumpsum_succeeds() {
        PlannerRequest request = PlannerTestData.toRequest(PlannerTestData.scenario("Portfolio Aware - Growth"))
                .txnOptions("LUMPSUM").lumpsumAmount(100000).without("monthly_amount");
        assertSuccess(plannerService.generateApi1(request));
    }

    @Test(groups = {"api1"}, description = "API-1: zero monthly and lumpsum amounts are rejected.")
    public void api1_zeroAmounts_isRejected() {
        PlannerRequest request = PlannerTestData.toRequest(PlannerTestData.scenario("Portfolio Aware - Growth"))
                .monthlyAmount(0).lumpsumAmount(0).txnOptions("SIP");
        Response response = plannerService.generateApi1(request);
        Assert.assertEquals(response.statusCode(), 422, "Expected HTTP 422. Body: " + response.getBody().asString());
    }

    private void assertSuccess(Response response) {
        Assert.assertEquals(response.statusCode(), 200, "planner HTTP status, body: " + response.getBody().asString());
    }
}

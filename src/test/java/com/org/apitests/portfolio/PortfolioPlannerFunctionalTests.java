package com.org.apitests.portfolio;

import org.testng.annotations.Test;
import org.testng.Assert;
import com.org.apiautomation.models.response.fundopinion.AnalystPickResponse;
import com.org.apiautomation.models.response.fundopinion.FundOpinionResponse;
import com.org.apiautomation.models.response.funddetails.FundDetailsResponse;
import com.org.apiautomation.models.response.fundplanoptions.FundPlanOptionsResponse;
import com.org.apiautomation.models.response.planner.PlannerResponse;
import com.org.apiautomation.models.response.portfolio.UserPortfolioResponse;
import com.org.apiautomation.services.FundService;
import com.org.apiautomation.services.PlannerService;
import com.org.apiautomation.services.PortfolioService;
import com.org.apitests.base.BaseTest;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * API-contract sanity only (status/HTTP code/data non-null) for every
 * Portfolio Planner API this module calls - deliberately NOT business-rule
 * validation (see {@code PortfolioPlannerFreshTests}/{@code
 * PortfolioPlannerPortfolioAwareTests}/{@code PortfolioPlannerCrossApiTests}
 * for that). Kept separate per section 3 of the test brief: a passing
 * contract check must never be reported as if it proved business
 * correctness.
 */
public class PortfolioPlannerFunctionalTests extends BaseTest {

    private final PlannerService plannerService = new PlannerService();
    private final PortfolioService portfolioService = new PortfolioService();
    private final FundService fundService = new FundService();

    @Test(groups = {"smoke", "sanity", "regression", "portfolio"},
            description = "Planner Generate accepts a valid FRESH request and returns a well-formed recommendation.")
    public void plannerGenerate_freshValidInput_returns200WithRecommendation() {
        Response response = plannerService.generateApi1(PlannerTestData.toRequest(PlannerTestData.scenario("Fresh - Growth")));

        Assert.assertEquals(response.statusCode(), 200);
        PlannerResponse planner = response.as(PlannerResponse.class);
        Assert.assertTrue(planner.status(), "status must be true");
        Assert.assertNotNull(planner.data(), "data must not be null");
        Assert.assertNotNull(planner.data().fundsData(), "funds_data must not be null");
        Assert.assertFalse(planner.data().fundsData().data().isEmpty(), "at least one fund must be recommended");
    }

    @Test(groups = {"smoke", "sanity", "regression", "portfolio"},
            description = "Planner Generate accepts a valid PORTFOLIO_AWARE request and returns a well-formed recommendation.")
    public void plannerGenerate_portfolioAwareValidInput_returns200WithRecommendation() {
        Response response = plannerService.generateApi1(PlannerTestData.toRequest(PlannerTestData.scenario("Portfolio Aware - Growth")));

        Assert.assertEquals(response.statusCode(), 200);
        PlannerResponse planner = response.as(PlannerResponse.class);
        Assert.assertTrue(planner.status(), "status must be true");
        Assert.assertEquals(planner.data().awareType(), "PORTFOLIO_AWARE");
        Assert.assertFalse(planner.data().fundsData().data().isEmpty(), "at least one fund must be recommended");
    }

    @Test(groups = {"sanity", "regression", "portfolio"}, description = "User Portfolio API returns the investor's existing holdings for a valid label.")
    public void userPortfolio_validLabel_returnsHoldings() {
        String labelId = PlannerTestData.scenario("Portfolio Aware - Growth").labelId();
        Response response = portfolioService.getLabelOpinionData(labelId);

        Assert.assertEquals(response.statusCode(), 200);
        UserPortfolioResponse portfolio = response.as(UserPortfolioResponse.class);
        Assert.assertTrue(portfolio.success(), "success must be true");
        Assert.assertNotNull(portfolio.data(), "data must not be null");
        Assert.assertNotNull(portfolio.data().fundData(), "fund-data must not be null");
    }

    @Test(groups = {"sanity", "regression", "portfolio"}, description = "Fund Opinion API returns opinion data for a set of plan_ids.")
    public void fundOpinion_validPlanIds_returnsOpinionData() {
        Response response = fundService.getFundOpinionData(List.of("19701", "17140"));

        Assert.assertEquals(response.statusCode(), 200);
        FundOpinionResponse opinion = response.as(FundOpinionResponse.class);
        Assert.assertTrue(opinion.status(), "status must be true");
        Assert.assertFalse(opinion.data().isEmpty(), "opinion data must not be empty for known plan_ids");
    }

    @Test(groups = {"sanity", "regression", "portfolio"}, description = "Analyst Pick API (is_analyst_pick=true) returns the Analyst's Choice list.")
    public void analystPick_returnsAnalystChoiceList() {
        Response response = fundService.getAnalystPicks();

        Assert.assertEquals(response.statusCode(), 200);
        AnalystPickResponse analystPick = response.as(AnalystPickResponse.class);
        Assert.assertTrue(analystPick.status(), "status must be true");
        Assert.assertFalse(analystPick.data().isEmpty(), "Analyst's Choice list must not be empty");
    }

    @Test(groups = {"sanity", "regression", "portfolio"}, description = "Fund Details API returns the requested plan's details, keyed correctly.")
    public void fundDetails_validPlanId_returnsMatchingPlan() {
        Response response = fundService.getFundDetails(19701L);

        Assert.assertEquals(response.statusCode(), 200);
        FundDetailsResponse fundDetails = response.as(FundDetailsResponse.class);
        Assert.assertTrue(fundDetails.success(), "success must be true");
        Assert.assertFalse(fundDetails.data().isEmpty(), "data must contain the requested plan");
        Assert.assertEquals(fundDetails.data().get(0).planId(), Long.valueOf(19701L));
    }

    @Test(groups = {"sanity", "regression", "portfolio"}, description = "Fund Plan Options Master returns SIP investment details for a set of plan_ids.")
    public void fundPlanOptions_validPlanIds_returnsSipInvestmentDetails() {
        Response response = fundService.getFundPlanOptions(List.of("19701", "15752"));

        Assert.assertEquals(response.statusCode(), 200);
        FundPlanOptionsResponse planOptions = response.as(FundPlanOptionsResponse.class);
        Assert.assertTrue(planOptions.status(), "status must be true");
        Assert.assertFalse(planOptions.data().isEmpty(), "data must not be empty for known plan_ids");
    }
}

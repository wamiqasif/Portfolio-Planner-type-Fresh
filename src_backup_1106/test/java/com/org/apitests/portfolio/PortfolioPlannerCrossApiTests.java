package com.org.apitests.portfolio;

import org.testng.annotations.Test;
import com.org.apiautomation.models.response.funddetails.FundDetail;
import com.org.apiautomation.models.response.funddetails.FundDetailsResponse;
import com.org.apiautomation.models.response.fundopinion.FundOpinionResponse;
import com.org.apiautomation.models.response.planner.FundEntry;
import com.org.apiautomation.models.response.planner.PlannerData;
import com.org.apiautomation.models.response.portfolio.PortfolioFundEntry;
import com.org.apiautomation.models.response.portfolio.UserPortfolioResponse;
import com.org.apiautomation.services.FundService;
import com.org.apiautomation.services.PlannerService;
import com.org.apiautomation.services.PortfolioService;
import com.org.apitests.base.BaseTest;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Multi-API business-rule validation: every check here needs the Planner
 * response PLUS at least one other API (Fund Opinion, Fund Details, or User
 * Portfolio) to compute an expected value - the "Planner -> plan_id -> Fund
 * Opinion/Fund Details -> compare" chain from section 9 of the test brief.
 * Reports which API supplied each value via each {@link BusinessRuleResult}'s
 * rule text.
 */
public class PortfolioPlannerCrossApiTests extends BaseTest {

    private final PlannerService plannerService = new PlannerService();
    private final PortfolioService portfolioService = new PortfolioService();
    private final FundService fundService = new FundService();

    @Test(groups = {"regression", "portfolio"},
            description = "Planner -> Fund Opinion: every FRESH ('See a New Plan') recommendation is an Analyst's Choice pick (PRD 11.4).")
    public void crossApi_freshRecommendationsAreAnalystChoice() {
        String scenario = "Fresh - Growth";
        PlannerData data = PlannerTestData.parseSuccessful(
                plannerService.generateApi1(PlannerTestData.toRequest(PlannerTestData.scenario(scenario))));

        List<FundEntry> recommendedFunds = data.fundsData().data();
        List<String> planIds = recommendedFunds.stream().map(f -> String.valueOf(f.planData().planId())).toList();

        FundOpinionResponse opinion = fundService.getFundOpinionData(planIds).as(FundOpinionResponse.class);

        BusinessRuleReporter.assertAll(FundEligibilityValidator.assertFreshFundsAreAnalystChoice(recommendedFunds, opinion.data(), scenario));
    }

    @Test(groups = {"regression", "portfolio"},
            description = "Planner -> Fund Details: a recommended fund's category (category_id/category_fmt) matches its Fund Details record (category_id/secondary_category_name).")
    public void crossApi_fundCategoryConsistentWithFundDetails() {
        String scenario = "Fresh - Growth";
        PlannerData data = PlannerTestData.parseSuccessful(
                plannerService.generateApi1(PlannerTestData.toRequest(PlannerTestData.scenario(scenario))));

        List<BusinessRuleResult> results = new ArrayList<>();
        for (FundEntry fund : data.fundsData().data()) {
            Long planId = fund.planData().planId();
            FundDetailsResponse fundDetails = fundService.getFundDetails(planId).as(FundDetailsResponse.class);
            if (fundDetails.data().isEmpty()) {
                results.add(BusinessRuleResult.fail("Fund Details API must return the recommended plan_id", scenario, String.valueOf(planId), "1 record", "0 records"));
                continue;
            }
            FundDetail detail = fundDetails.data().get(0);

            boolean categoryIdMatches = fund.categoryData().categoryId() != null && fund.categoryData().categoryId().equals(detail.categoryId());
            results.add(new BusinessRuleResult("Planner category_id must match Fund Details category_id",
                    scenario, String.valueOf(planId), detail.categoryId(), fund.categoryData().categoryId(), categoryIdMatches));

            boolean categoryNameMatches = fund.categoryData().categoryFmt() != null
                    && fund.categoryData().categoryFmt().equalsIgnoreCase(detail.secondaryCategoryName());
            results.add(new BusinessRuleResult("Planner category_fmt must match Fund Details secondary_category_name",
                    scenario, String.valueOf(planId), detail.secondaryCategoryName(), fund.categoryData().categoryFmt(), categoryNameMatches));
        }

        BusinessRuleReporter.assertAll(results);
    }

    @Test(groups = {"regression", "portfolio"},
            description = "Planner -> User Portfolio -> Fund Details: is_owned correctly reflects whether the investor already holds the same underlying fund (matched by fund_id), including under a different plan option (PRD 11.5/11.7).")
    public void crossApi_portfolioAwareOwnershipMatchesActualHoldings() {
        String scenario = "Portfolio Aware - Growth";
        var planScenario = PlannerTestData.scenario(scenario);

        UserPortfolioResponse portfolio = portfolioService.getLabelOpinionData(planScenario.labelId()).as(UserPortfolioResponse.class);
        Set<Long> ownedFundIds = portfolio.data().fundData().values().stream()
                .map(PortfolioFundEntry::planDetails)
                .filter(pd -> pd != null && pd.fundId() != null)
                .map(FundDetail::fundId)
                .collect(Collectors.toSet());

        PlannerData data = PlannerTestData.parseSuccessful(plannerService.generateApi1(PlannerTestData.toRequest(planScenario)));
        List<FundEntry> recommendedFunds = data.fundsData().data();

        Map<Long, FundDetail> recommendedFundDetailsByPlanId = new HashMap<>();
        for (FundEntry fund : recommendedFunds) {
            Long planId = fund.planData().planId();
            FundDetailsResponse fundDetails = fundService.getFundDetails(planId).as(FundDetailsResponse.class);
            if (!fundDetails.data().isEmpty()) {
                recommendedFundDetailsByPlanId.put(planId, fundDetails.data().get(0));
            }
        }

        BusinessRuleReporter.assertAll(PortfolioAwareValidator.assertOwnershipMatchesPortfolio(
                recommendedFunds, recommendedFundDetailsByPlanId, ownedFundIds, scenario));
    }

    @Test(groups = {"regression", "portfolio"},
            description = "Planner -> Fund Details: each fund's allocation bucket (Equity/Debt) is consistent with its category's classification per the PRD's Asset Allocation table.")
    public void crossApi_allocationBucketsMatchCategoryClassification() {
        String scenario = "Portfolio Aware - Growth";
        PlannerData data = PlannerTestData.parseSuccessful(
                plannerService.generateApi1(PlannerTestData.toRequest(PlannerTestData.scenario(scenario))));

        Map<String, FundDetail> fundDetailsByName = new HashMap<>();
        for (FundEntry fund : data.fundsData().data()) {
            FundDetailsResponse fundDetails = fundService.getFundDetails(fund.planData().planId()).as(FundDetailsResponse.class);
            if (!fundDetails.data().isEmpty()) {
                fundDetailsByName.put(fund.planData().name(), fundDetails.data().get(0));
            }
        }

        BusinessRuleReporter.assertAll(AllocationValidator.assertCategoryBucketConsistency(data.breakdownFundsData(), fundDetailsByName, scenario));
    }
}

package com.org.apitests.portfolio;

import org.testng.annotations.Test;
import com.org.apiautomation.models.request.planner.PlannerRequest;
import com.org.apiautomation.models.response.planner.PlannerData;
import com.org.apiautomation.services.PlannerService;
import com.org.apitests.base.BaseTest;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Business-rule validation for the {@code PORTFOLIO_AWARE} ("Includes Your
 * Funds") flow - checks that are self-contained within a single Planner
 * Generate response. See {@code PortfolioPlannerCrossApiTests} for the
 * ownership check that needs the User Portfolio + Fund Details APIs too
 * (confirming {@code is_owned} is actually correct, not just internally
 * consistent with the note text).
 */
public class PortfolioPlannerPortfolioAwareTests extends BaseTest {

    private static final String SCENARIO = "Portfolio Aware - Growth";

    private final PlannerService plannerService = new PlannerService();

    @Test(groups = {"sanity", "regression", "portfolio"},
            description = "PORTFOLIO_AWARE: response echoes the requested aware_type and investor_id (PRD 11.1).")
    public void portfolioAware_inputsAreEchoedCorrectly() {
        PlannerRequest request = PlannerTestData.toRequest(PlannerTestData.scenario(SCENARIO));
        PlannerData data = PlannerTestData.parseSuccessful(plannerService.generateApi1(request));

        List<BusinessRuleResult> results = new ArrayList<>();
        results.add(InvestmentInputValidator.assertAwareTypeEchoed(request, data, SCENARIO));
        results.add(InvestmentInputValidator.assertInvestorEchoed(request, data, SCENARIO));
        results.addAll(InvestmentInputValidator.assertInputSummaryReflectsAmounts(request, data, SCENARIO));

        BusinessRuleReporter.assertAll(results);
    }

    @Test(groups = {"sanity", "regression", "portfolio"},
            description = "PORTFOLIO_AWARE: the 'You already own N fund(s)...' note's count and singular/plural wording match the actual number of owned funds in the response (PRD 11.5).")
    public void portfolioAware_planNoteCountMatchesOwnedFunds() {
        PlannerData data = PlannerTestData.parseSuccessful(
                plannerService.generateApi1(PlannerTestData.toRequest(PlannerTestData.scenario(SCENARIO))));

        BusinessRuleReporter.assertAll(List.of(PortfolioAwareValidator.assertPlanNoteCountConsistency(data, SCENARIO)));
    }

    @Test(groups = {"sanity", "regression", "portfolio"},
            description = "PORTFOLIO_AWARE: allocation buckets sum to 100% and the first-month total equals the sum of each fund's first-month amount (PRD 11.6/11.9).")
    public void portfolioAware_allocationArithmeticIsConsistent() {
        PlannerData data = PlannerTestData.parseSuccessful(
                plannerService.generateApi1(PlannerTestData.toRequest(PlannerTestData.scenario(SCENARIO))));

        List<BusinessRuleResult> results = new ArrayList<>();
        results.add(AllocationValidator.assertBreakdownPercentagesSumTo100(data.breakdownFundsData(), SCENARIO));
        results.add(AllocationValidator.assertFirstMonthTotalMatchesSumOfFunds(data.fundsData(), SCENARIO));

        BusinessRuleReporter.assertAll(results);
    }

    @Test(groups = {"sanity", "regression", "portfolio"},
            description = "PORTFOLIO_AWARE: disclaimer includes the required holding/position sentence, uniform with the web version (PRD 11.10).")
    public void portfolioAware_disclaimerIncludesRequiredSentence() {
        PlannerData data = PlannerTestData.parseSuccessful(
                plannerService.generateApi1(PlannerTestData.toRequest(PlannerTestData.scenario(SCENARIO))));

        BusinessRuleReporter.assertAll(List.of(DisclaimerValidator.assertRequiredSentencePresent(data.disclaimerData(), SCENARIO)));
    }
}

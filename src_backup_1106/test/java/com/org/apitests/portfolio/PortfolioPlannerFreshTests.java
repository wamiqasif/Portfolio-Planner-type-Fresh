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
 * Business-rule validation for the {@code FRESH} ("See a New Plan") flow -
 * checks that are self-contained within a single Planner Generate response
 * (no second API call needed). See {@code PortfolioPlannerCrossApiTests}
 * for the checks that need Fund Opinion/Fund Details data too (e.g. "every
 * FRESH recommendation is an Analyst's Choice pick").
 */
public class PortfolioPlannerFreshTests extends BaseTest {

    private static final String SCENARIO = "Fresh - Growth";

    private final PlannerService plannerService = new PlannerService();

    @Test(groups = {"sanity", "regression", "portfolio"},
            description = "FRESH: response echoes the requested aware_type and investor_id (PRD 11.1).")
    public void fresh_inputsAreEchoedCorrectly() {
        PlannerRequest request = PlannerTestData.toRequest(PlannerTestData.scenario(SCENARIO));
        PlannerData data = PlannerTestData.parseSuccessful(plannerService.generateApi1(request));

        List<BusinessRuleResult> results = new ArrayList<>();
        results.add(InvestmentInputValidator.assertAwareTypeEchoed(request, data, SCENARIO));
        results.add(InvestmentInputValidator.assertInvestorEchoed(request, data, SCENARIO));
        results.addAll(InvestmentInputValidator.assertInputSummaryReflectsAmounts(request, data, SCENARIO));

        BusinessRuleReporter.assertAll(results);
    }

    @Test(groups = {"sanity", "regression", "portfolio"},
            description = "FRESH: no recommended fund is marked owned and the portfolio note is hidden (PRD 11.4/11.5 - 'See a New Plan' ignores the existing portfolio entirely).")
    public void fresh_ownershipNeverAppliedAndNoteHidden() {
        PlannerData data = PlannerTestData.parseSuccessful(
                plannerService.generateApi1(PlannerTestData.toRequest(PlannerTestData.scenario(SCENARIO))));

        BusinessRuleReporter.assertAll(PortfolioAwareValidator.assertNotShownForFresh(data, SCENARIO));
    }

    @Test(groups = {"sanity", "regression", "portfolio"},
            description = "FRESH: allocation buckets sum to 100% and the first-month total equals the sum of each fund's first-month amount (PRD 11.6/11.9).")
    public void fresh_allocationArithmeticIsConsistent() {
        PlannerData data = PlannerTestData.parseSuccessful(
                plannerService.generateApi1(PlannerTestData.toRequest(PlannerTestData.scenario(SCENARIO))));

        List<BusinessRuleResult> results = new ArrayList<>();
        results.add(AllocationValidator.assertBreakdownPercentagesSumTo100(data.breakdownFundsData(), SCENARIO));
        results.add(AllocationValidator.assertFirstMonthTotalMatchesSumOfFunds(data.fundsData(), SCENARIO));

        BusinessRuleReporter.assertAll(results);
    }

    @Test(groups = {"sanity", "regression", "portfolio"},
            description = "FRESH: disclaimer includes the required holding/position sentence, uniform with the web version (PRD 11.10).")
    public void fresh_disclaimerIncludesRequiredSentence() {
        PlannerData data = PlannerTestData.parseSuccessful(
                plannerService.generateApi1(PlannerTestData.toRequest(PlannerTestData.scenario(SCENARIO))));

        BusinessRuleReporter.assertAll(List.of(DisclaimerValidator.assertRequiredSentencePresent(data.disclaimerData(), SCENARIO)));
    }
}

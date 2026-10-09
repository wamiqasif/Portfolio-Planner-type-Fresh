package com.org.apitests.portfolio;

import org.testng.annotations.Test;
import org.testng.Assert;
import com.org.apiautomation.models.request.planner.PlannerRequest;
import com.org.apiautomation.models.response.fundopinion.FundOpinionResponse;
import com.org.apiautomation.models.response.planner.FundEntry;
import com.org.apiautomation.models.response.planner.PlannerData;
import com.org.apiautomation.services.FundService;
import com.org.apiautomation.services.PlannerService;
import com.org.apitests.base.BaseTest;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Data-driven FRESH ("See a New Plan") business validation - one {@code
 * @Test} invocation per {@code Fresh_Test_Data} row (the "Migration to new
 * code" sheet per the automation brief). Reuses the same validators {@code
 * PortfolioPlannerFreshTests} already exercises against a single hardcoded
 * scenario; this class only swaps the data source for the full 42-row
 * combination matrix.
 *
 * <p><b>Identity is fixed, not Excel-driven:</b> {@code Fresh_Test_Data}
 * intentionally carries no {@code label_id}/{@code investor_id}/{@code
 * user_id} - FRESH is portfolio-independent by design (see {@code
 * Automation_Rules} sheet, RULE-002), so every row varies only the financial
 * inputs against the one known-good test investor already used by {@code
 * PortfolioPlannerFreshTests} (loaded from {@code testdata/planner-scenarios.json},
 * not re-hardcoded here).
 */
public class PortfolioPlannerFreshDataDrivenTests extends BaseTest {

    private static final PlannerScenario BASELINE_IDENTITY = PlannerTestData.scenario("Fresh - Growth");

    private final PlannerService plannerService = new PlannerService();
    private final FundService fundService = new FundService();

    @Test(dataProvider = "freshTestData", dataProviderClass = ExecutionDataProvider.class,
            groups = {"regression", "portfolio", "portfolio-business-validation"},
            description = "FRESH business validation for one Fresh_Test_Data row: input echo, allocation arithmetic, disclaimer, ownership never applied, and every recommendation is Analyst's Choice (PRD 11.4).")
    public void freshTestData_businessValidation(FreshTestDataRow row) {
        List<BusinessRuleResult> results = new ArrayList<>();
        String actualPlanIds = "";
        String allocationResult = "N/A";
        String failureReasonOnException = null;
        BusinessValidationReportRow.FailureCategory exceptionCategory = null;
        String description = "";
        String plannerGenerateResponseText = "";

        try {
            long monthlyAmount = row.monthlyAmount() == null ? 0L : row.monthlyAmount();
            long lumpsumAmount = row.lumpsumAmount() == null ? 0L : row.lumpsumAmount();
            int durationMonths = row.investmentDurationMonths() == null ? 12 : row.investmentDurationMonths();

            PlannerRequest request = PlannerRequest.builder()
                    .goalType(PlannerRequestDefaults.GOAL_TYPE)
                    .txnOptions(PlannerRequestDefaults.inferTxnOptions(monthlyAmount, lumpsumAmount))
                    .durationType("m")
                    .investmentDuration(durationMonths)
                    .monthlyAmount(monthlyAmount)
                    .lumpsumAmount(lumpsumAmount)
                    .labelId(Long.parseLong(BASELINE_IDENTITY.labelId()))
                    .userId(Long.parseLong(BASELINE_IDENTITY.userId()))
                    .riskProfileId(PlannerRequestDefaults.RISK_PROFILE_ID)
                    .annualIncomeRange(PlannerRequestDefaults.ANNUAL_INCOME_RANGE)
                    .investorId(Long.parseLong(BASELINE_IDENTITY.investorId()))
                    .awareType("FRESH");
            description = request.toJson();

            io.restassured.response.Response plannerHttpResponse = plannerService.generateApi1(request);
            plannerGenerateResponseText = plannerHttpResponse.getBody().asString();
            PlannerData data = PlannerTestData.parseSuccessful(plannerHttpResponse);
            List<FundEntry> recommendedFunds = data.fundsData().data();
            actualPlanIds = recommendedFunds.stream()
                    .map(f -> String.valueOf(f.planData().planId()))
                    .collect(Collectors.joining(";"));

            results.add(InvestmentInputValidator.assertAwareTypeEchoed(request, data, row.tcId()));
            results.add(InvestmentInputValidator.assertInvestorEchoed(request, data, row.tcId()));
            results.addAll(InvestmentInputValidator.assertInputSummaryReflectsAmounts(request, data, row.tcId()));

            BusinessRuleResult breakdownSums = AllocationValidator.assertBreakdownPercentagesSumTo100(data.breakdownFundsData(), row.tcId());
            BusinessRuleResult firstMonthTotal = AllocationValidator.assertFirstMonthTotalMatchesSumOfFunds(data.fundsData(), row.tcId());
            results.add(breakdownSums);
            results.add(firstMonthTotal);
            allocationResult = (breakdownSums.passed() && firstMonthTotal.passed()) ? "PASS" : "FAIL";

            results.add(DisclaimerValidator.assertRequiredSentencePresent(data.disclaimerData(), row.tcId()));
            results.addAll(PortfolioAwareValidator.assertNotShownForFresh(data, row.tcId()));

            List<String> planIds = recommendedFunds.stream().map(f -> String.valueOf(f.planData().planId())).toList();
            if (!planIds.isEmpty()) {
                io.restassured.response.Response opinionResponse = fundService.getFundOpinionData(planIds);
                if (opinionResponse.statusCode() != 200) {
                    throw new AssertionError("Fund Opinion did not return 200. Status: " + opinionResponse.statusCode()
                            + ", body: " + opinionResponse.getBody().asString());
                }
                FundOpinionResponse opinion = opinionResponse.as(FundOpinionResponse.class);
                results.addAll(FundEligibilityValidator.assertFreshFundsAreAnalystChoice(recommendedFunds, opinion.data(), row.tcId()));
            }
        } catch (AssertionError contractFailure) {
            failureReasonOnException = contractFailure.getMessage();
            exceptionCategory = BusinessValidationReportRow.FailureCategory.API_CONTRACT;
        } catch (RuntimeException unexpected) {
            failureReasonOnException = String.valueOf(unexpected.getMessage());
            exceptionCategory = BusinessValidationReportRow.FailureCategory.API_CONTRACT;
        }

        recordAndAssert(row, description, results, actualPlanIds, allocationResult, plannerGenerateResponseText, failureReasonOnException, exceptionCategory);
    }

    private void recordAndAssert(FreshTestDataRow row, String description, List<BusinessRuleResult> results, String actualPlanIds,
            String allocationResult, String plannerGenerateResponseText, String failureReasonOnException, BusinessValidationReportRow.FailureCategory exceptionCategory) {

        String expectedRule = "FRESH business rules (Analyst's Choice only, no ownership, allocation, disclaimer, input echo - PRD 11.4/11.6/11.9/11.10)";

        if (exceptionCategory != null) {
            BusinessValidationReportWriter.record(new BusinessValidationReportRow(
                    row.tcId(), description, "N/A", BASELINE_IDENTITY.investorId(), row.sourceScenario(), "N/A",
                    "FRESH", expectedRule, "N/A", actualPlanIds, "N/A", "N/A", allocationResult,
                    plannerGenerateResponseText, "",
                    BusinessValidationReportRow.FAIL, failureReasonOnException, "Planner Generate / API contract",
                    exceptionCategory));
            Assert.fail("API contract failure for " + row.tcId() + ": " + failureReasonOnException);
            return;
        }

        List<BusinessRuleResult> failures = results.stream().filter(r -> !r.passed()).toList();
        String result = failures.isEmpty() ? BusinessValidationReportRow.PASS : BusinessValidationReportRow.FAIL;
        String failureReason = failures.isEmpty() ? null : failures.stream()
                .map(r -> r.rule() + " (expected=" + r.expected() + ", actual=" + r.actual() + ")")
                .collect(Collectors.joining("; "));
        BusinessValidationReportRow.FailureCategory category = failures.isEmpty() ? null : BusinessValidationReportRow.FailureCategory.BUSINESS_RULE;

        BusinessValidationReportWriter.record(new BusinessValidationReportRow(
                row.tcId(), description, "N/A", BASELINE_IDENTITY.investorId(), row.sourceScenario(), "N/A",
                "FRESH", expectedRule, "N/A", actualPlanIds, "N/A", "N/A", allocationResult,
                plannerGenerateResponseText, "", result,
                failureReason, failures.isEmpty() ? "" : "Planner Generate / Fund Opinion", category));

        if (!failures.isEmpty()) {
            BusinessRuleReporter.assertAll(results);
        }
    }

    @AfterSuite(alwaysRun = true)
    public void writeBusinessValidationReport() {
        BusinessValidationReportWriter.writeReport();
    }
}

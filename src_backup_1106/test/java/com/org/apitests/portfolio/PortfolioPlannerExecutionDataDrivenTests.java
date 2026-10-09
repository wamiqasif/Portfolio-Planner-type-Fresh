package com.org.apitests.portfolio;

import org.testng.annotations.Test;
import org.testng.Assert;
import com.org.apiautomation.auth.GuidSessionAuthenticator;
import com.org.apiautomation.config.ConfigManager;
import com.org.apiautomation.core.RequestSpecFactory;
import com.org.apiautomation.models.request.planner.PlannerRequest;
import com.org.apiautomation.models.response.funddetails.FundDetail;
import com.org.apiautomation.models.response.funddetails.FundDetailsResponse;
import com.org.apiautomation.models.response.fundopinion.FundOpinionEntry;
import com.org.apiautomation.models.response.fundopinion.FundOpinionResponse;
import com.org.apiautomation.models.response.planner.FundEntry;
import com.org.apiautomation.models.response.planner.PlannerData;
import com.org.apiautomation.models.response.portfolio.PortfolioFundEntry;
import com.org.apiautomation.models.response.portfolio.UserPortfolioResponse;
import com.org.apiautomation.services.FundService;
import com.org.apiautomation.services.PlannerService;
import com.org.apiautomation.services.PortfolioService;
import com.org.apitests.base.BaseTest;
import io.restassured.http.Cookies;
import io.restassured.specification.RequestSpecification;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Data-driven PORTFOLIO_AWARE business validation - one {@code @Test}
 * invocation per {@code Execution_Data} row (146 rows as of the current
 * workbook), fed entirely by {@link ExecutionDataProvider} (RULE-004: never
 * hardcode label_id/investor_id/user_id/matched_plan_id in this class).
 *
 * <p>Reuses every existing self-contained/cross-API validator in this package
 * ({@link InvestmentInputValidator}, {@link AllocationValidator}, {@link
 * DisclaimerValidator}, {@link PortfolioAwareValidator}) rather than
 * duplicating their logic - this class's own job is only: resolve each row's
 * financial inputs (via {@link PortfolioAwareScenarioResolver}), orchestrate
 * the API calls in the right order, add the two Excel-specific integrity
 * checks ({@code matched_plan_id}'s actual category/is_analyst_pick/
 * is_direct_growth vs. what the workbook claims), and emit one {@link
 * BusinessValidationReportRow} per row (Step 6 of the automation brief).
 *
 * <p><b>{@code match_status = "No verified match found"}</b> rows (per the
 * automation brief, a negative/business-validation case - no reference fund
 * exists to cross-check): the reference-fund integrity checks above are
 * skipped for these rows (not failed - there is nothing to check them
 * against), while every self-contained/ownership/allocation check still
 * runs normally, since none of those depend on the workbook having a
 * verified match.
 */
public class PortfolioPlannerExecutionDataDrivenTests extends BaseTest {

    private static final List<PortfolioAwareScenarioRow> SCENARIOS = ExcelTestDataReader.readPortfolioAwareScenarios();

    /**
     * Used only when a row's {@code (fund_category, case_covered)} has no
     * match in {@code Portfolio_Aware_Scenarios} - the row's own business
     * assertions (ownership/category/analyst-pick integrity) don't depend on
     * the exact amount invested, so a small, valid, arbitrary SIP+lumpsum
     * combination is enough to get a real Planner Generate response.
     */
    private static final long FALLBACK_MONTHLY_AMOUNT = 5000L;
    private static final long FALLBACK_LUMPSUM_AMOUNT = 50000L;
    private static final int FALLBACK_DURATION_YEARS = 1;

    private final PlannerService plannerService = new PlannerService();
    private final PortfolioService portfolioService = new PortfolioService();
    private final FundService fundService = new FundService();
    private final GuidSessionAuthenticator guidSessionAuthenticator = new GuidSessionAuthenticator();

    @Test(dataProvider = "executionData", dataProviderClass = ExecutionDataProvider.class,
            groups = {"regression", "portfolio", "portfolio-business-validation"},
            description = "PORTFOLIO_AWARE business validation for one Execution_Data row: ownership, allocation, disclaimer, and (when verified) reference-fund category/analyst-pick/direct-growth integrity.")
    public void executionData_businessValidation(ExecutionDataRow row) {
        List<BusinessRuleResult> results = new ArrayList<>();
        String actualPlanIds = "";
        String actualCategory = "";
        String allocationResult = "N/A";
        String failureReasonOnException = null;
        BusinessValidationReportRow.FailureCategory exceptionCategory = null;
        String[] currentApi = {"Planner Generate"};
        String description = "";
        String plannerGenerateResponseText = "";
        List<String> fundDetailsResponseTexts = new ArrayList<>();
        String fundsInPortfolio = "";

        try {
            currentApi[0] = "Login (GUID)";
            if (row.loginUrl() == null || row.loginUrl().isBlank()) {
                throw new DataNotAvailableException("No Login URL for TC " + row.tcNo() + " (BLOCKED)");
            }
            Cookies rowSession = guidSessionAuthenticator.login(row.loginUrl());

            Optional<PortfolioAwareScenarioRow> scenario = PortfolioAwareScenarioResolver.resolve(row, SCENARIOS);
            long monthlyAmount = scenario.map(PortfolioAwareScenarioRow::monthlyAmount).filter(a -> a != null).orElse(FALLBACK_MONTHLY_AMOUNT);
            long lumpsumAmount = scenario.map(PortfolioAwareScenarioRow::lumpsumAmount).filter(a -> a != null).orElse(FALLBACK_LUMPSUM_AMOUNT);
            int durationYears = scenario.map(PortfolioAwareScenarioRow::timeYears).filter(y -> y != null).orElse(FALLBACK_DURATION_YEARS);
            int riskProfileId = scenario.map(PortfolioAwareScenarioRow::riskProfile)
                    .map(PlannerRequestDefaults::riskProfileIdFor).filter(id -> id != null)
                    .orElse(PlannerRequestDefaults.RISK_PROFILE_ID);
            String goalType = scenario.map(PortfolioAwareScenarioRow::goal)
                    .map(PlannerRequestDefaults::goalTypeFor)
                    .orElse(PlannerRequestDefaults.GOAL_TYPE);

            PlannerRequest request = PlannerRequest.builder()
                    .goalType(goalType)
                    .durationType("y")
                    .investmentDuration(durationYears)
                    .labelId(Long.parseLong(row.labelId()))
                    .userId(Long.parseLong(row.userId()))
                    .riskProfileId(riskProfileId)
                    .annualIncomeRange(PlannerRequestDefaults.ANNUAL_INCOME_RANGE)
                    .investorId(Long.parseLong(row.investorId()))
                    .awareType("PORTFOLIO_AWARE");
            if ("TAX-SAVINGS".equals(goalType)) {
                Long taxSavingAmount = scenario.map(PortfolioAwareScenarioRow::taxSavingAmount).orElse(null);
                if (taxSavingAmount != null) {
                    request.taxSavingAmount(taxSavingAmount);
                }
            } else {
                request.txnOptions(PlannerRequestDefaults.inferTxnOptions(monthlyAmount, lumpsumAmount))
                        .monthlyAmount(monthlyAmount)
                        .lumpsumAmount(lumpsumAmount);
            }
            description = request.toJson();

            currentApi[0] = "User Portfolio";
            io.restassured.response.Response portfolioHttpResponse =
                    portfolioService.getLabelOpinionData(row.labelId(), advisorApiRowSpec(rowSession));
            failIfPortfolioDataMissing(portfolioHttpResponse);
            UserPortfolioResponse portfolio = parseSuccessful(portfolioHttpResponse, UserPortfolioResponse.class, "User Portfolio");
            Set<Long> ownedFundIds = (portfolio.data() == null || portfolio.data().fundData() == null)
                    ? Set.of()
                    : portfolio.data().fundData().values().stream()
                    .map(PortfolioFundEntry::planDetails)
                    .filter(pd -> pd != null && pd.fundId() != null)
                    .map(FundDetail::fundId)
                    .collect(Collectors.toSet());

            fundsInPortfolio = ownedFundIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(";"));

            if (!GuidSessionAuthenticator.hasBearer(rowSession)) {
                currentApi[0] = "Login (GUID) -> token/create";
                throw new AssertionError(GuidSessionAuthenticator.bearerFailureReason(rowSession));
            }
            currentApi[0] = "Planner Generate";
            io.restassured.response.Response plannerHttpResponse = plannerService.generate(request, advisorApiRowSpec(rowSession));
            plannerGenerateResponseText = plannerHttpResponse.getBody().asString();
            PlannerData data = PlannerTestData.parseSuccessful(plannerHttpResponse);
            List<FundEntry> recommendedFunds = data.fundsData().data();
            actualPlanIds = recommendedFunds.stream()
                    .map(f -> String.valueOf(f.planData().planId()))
                    .collect(Collectors.joining(";"));

            results.add(InvestmentInputValidator.assertAwareTypeEchoed(request, data, row.caseCovered()));
            results.add(InvestmentInputValidator.assertInvestorEchoed(request, data, row.caseCovered()));
            results.addAll(InvestmentInputValidator.assertInputSummaryReflectsAmounts(request, data, row.caseCovered()));
            BusinessRuleResult breakdownSums = AllocationValidator.assertBreakdownPercentagesSumTo100(data.breakdownFundsData(), row.caseCovered());
            BusinessRuleResult firstMonthTotal = AllocationValidator.assertFirstMonthTotalMatchesSumOfFunds(data.fundsData(), row.caseCovered());
            results.add(breakdownSums);
            results.add(firstMonthTotal);
            allocationResult = (breakdownSums.passed() && firstMonthTotal.passed()) ? "PASS" : "FAIL";
            results.add(DisclaimerValidator.assertRequiredSentencePresent(data.disclaimerData(), row.caseCovered()));
            results.add(PortfolioAwareValidator.assertPlanNoteCountConsistency(data, row.caseCovered()));

            currentApi[0] = "Fund Details";
            Map<Long, FundDetail> recommendedFundDetailsByPlanId = new HashMap<>();
            for (FundEntry fund : recommendedFunds) {
                Long planId = fund.planData().planId();
                io.restassured.response.Response fundDetailsHttpResponse = fundService.getFundDetails(planId, mainApiRowSpec(rowSession));
                fundDetailsResponseTexts.add(fundDetailsHttpResponse.getBody().asString());
                FundDetailsResponse details = parseSuccessful(fundDetailsHttpResponse, FundDetailsResponse.class, "Fund Details");
                if (!details.data().isEmpty()) {
                    recommendedFundDetailsByPlanId.put(planId, details.data().get(0));
                }
            }
            results.addAll(PortfolioAwareValidator.assertOwnershipMatchesPortfolio(
                    recommendedFunds, recommendedFundDetailsByPlanId, ownedFundIds, row.caseCovered()));

            if (!row.hasNoVerifiedMatch() && row.matchedPlanId() != null) {
                long matchedPlanId = Long.parseLong(row.matchedPlanId());
                currentApi[0] = "Fund Details";
                io.restassured.response.Response matchedFundDetailsHttpResponse = fundService.getFundDetails(matchedPlanId, mainApiRowSpec(rowSession));
                fundDetailsResponseTexts.add(matchedFundDetailsHttpResponse.getBody().asString());
                FundDetailsResponse matchedDetails = parseSuccessful(matchedFundDetailsHttpResponse, FundDetailsResponse.class, "Fund Details");
                if (!matchedDetails.data().isEmpty()) {
                    FundDetail detail = matchedDetails.data().get(0);
                    actualCategory = detail.secondaryCategoryName() != null ? detail.secondaryCategoryName() : detail.categoryName();
                    boolean categoryMatches = CategoryMatcher.matches(row.fundCategory(), detail.categoryName(), detail.secondaryCategoryName());
                    results.add(BusinessRuleResult.of(categoryMatches,
                            "Reference fund (matched_plan_id) category must match the Excel's expected fund_category",
                            row.caseCovered(), row.fundCategory(), actualCategory));

                    Boolean expectedDirectGrowth = row.isDirectGrowthFlag();
                    if (expectedDirectGrowth != null) {
                        results.add(BusinessRuleResult.of(expectedDirectGrowth.equals(detail.isDirectPlan()),
                                "Reference fund's is_direct_plan must match the Excel's expected is_direct_growth",
                                row.caseCovered(), expectedDirectGrowth, detail.isDirectPlan()));
                    }
                }

                Boolean expectedAnalystPick = row.isAnalystPickFlag();
                if (expectedAnalystPick != null) {
                    currentApi[0] = "Fund Opinion";
                    FundOpinionResponse opinion = parseSuccessful(
                            fundService.getFundOpinionData(List.of(row.matchedPlanId()), mainApiRowSpec(rowSession)), FundOpinionResponse.class, "Fund Opinion");
                    Boolean actualAnalystPick = opinion.data().stream()
                            .filter(o -> o.planId() == matchedPlanId)
                            .map(FundOpinionEntry::isAnalystPick)
                            .findFirst().orElse(null);
                    results.add(BusinessRuleResult.of(expectedAnalystPick.equals(actualAnalystPick),
                            "Reference fund's is_analyst_pick must match the Excel's expected is_analyst_pick",
                            row.caseCovered(), expectedAnalystPick, actualAnalystPick));
                }
            }
        } catch (AssertionError contractFailure) {
            failureReasonOnException = contractFailure.getMessage();
            exceptionCategory = BusinessValidationReportRow.FailureCategory.API_CONTRACT;
        } catch (DataNotAvailableException noData) {
            failureReasonOnException = currentApi[0] + ": " + noData.getMessage();
            exceptionCategory = BusinessValidationReportRow.FailureCategory.INVALID_TEST_DATA;
        } catch (RuntimeException unexpected) {
            failureReasonOnException = currentApi[0] + ": " + unexpected.getMessage();
            exceptionCategory = BusinessValidationReportRow.FailureCategory.API_CONTRACT;
        }

        String fundDetailsResponseText = String.join("; ", fundDetailsResponseTexts);
        recordAndAssert(row, description, results, actualPlanIds, actualCategory, allocationResult,
                plannerGenerateResponseText, fundDetailsResponseText, failureReasonOnException, exceptionCategory, currentApi[0], fundsInPortfolio);
    }

    /**
     * A fresh, row-scoped {@link RequestSpecification} against the advisor
     * API gateway (Planner Generate, User Portfolio), authenticated with
     * {@code rowSession}'s cookies instead of the framework's shared {@code
     * vrlogin} session.
     *
     * <p><b>Built fresh on every call, never reused across calls within a
     * row:</b> {@code PortfolioService.getLabelOpinionData} mutates its
     * spec with a {@code label-ids} query param - reusing that same
     * instance for the subsequent Planner Generate call would leak that
     * query param onto a request that never wanted it. A new instance per
     * call is exactly what every other service method's own no-arg
     * overload already does via {@code RequestSpecFactory.defaultRequestSpec()}
     * - this only swaps which credentials are attached, not that ground rule.
     */
    private static RequestSpecification advisorApiRowSpec(Cookies rowSession) {
        return GuidSessionAuthenticator.attach(
                RequestSpecFactory.unauthenticatedRequestSpec(ConfigManager.getInstance().getAdvisorApiBaseUrl()), rowSession);
    }

    /**
     * Same as {@link #advisorApiRowSpec(Cookies)}, against the main app API
     * host instead (Fund Details, Fund Opinion) - confirmed by the backend
     * team to accept the same login-by-guid session cookies.
     */
    private static RequestSpecification mainApiRowSpec(Cookies rowSession) {
        return GuidSessionAuthenticator.attach(
                RequestSpecFactory.unauthenticatedRequestSpec(ConfigManager.getInstance().getBaseUrl()), rowSession);
    }

    /** Row's test data (Login URL, label/investor) is unusable in this environment - not an API defect. */
    private static final class DataNotAvailableException extends RuntimeException {
        DataNotAvailableException(String message) {
            super(message);
        }
    }

    /**
     * The User Portfolio API answers HTTP 200 with {@code success:false,
     * data:[]} and a "Label(s) Not Found" message for unknown labels, which
     * would otherwise crash deserialization (MismatchedInputException).
     */
    private static void failIfPortfolioDataMissing(io.restassured.response.Response response) {
        if (response.statusCode() != 200) {
            return;
        }
        String body = response.getBody().asString();
        String lower = body == null ? "" : body.toLowerCase();
        boolean notFound = lower.contains("label(s) not found") || lower.contains("investor not found");
        boolean emptyFailure = lower.replaceAll("\\s", "").contains("\"success\":false") && lower.replaceAll("\\s", "").contains("\"data\":[]");
        boolean noHoldings = lower.replaceAll("\\s", "").contains("\"fund-data\":[]");
        if (noHoldings) {
            throw new DataNotAvailableException("Label exists but has NO fund holdings (fund-data is empty) - "
                    + "test data not usable for PORTFOLIO_AWARE (BLOCKED). Dev-created holdings may not be visible yet.");
        }
        if (notFound || emptyFailure) {
            String shown = body.length() > 300 ? body.substring(0, 300) + "..." : body;
            throw new DataNotAvailableException("User Portfolio has no data for this label/investor. Body: " + shown);
        }
    }

    /**
     * Guards every {@code .as(...)} call in this class: a raw {@code
     * response.as(type)} on a non-200/non-JSON response (an HTML gateway
     * error page, an empty body) throws an opaque Rest Assured parsing
     * exception instead of a readable assertion - confirmed empirically
     * running this suite against real "no verified match" label_ids, which
     * intermittently return exactly that. Mirrors {@code
     * PlannerTestData.parseSuccessful}'s reasoning for the same problem on
     * the Planner Generate call.
     */
    private <T> T parseSuccessful(io.restassured.response.Response response, Class<T> type, String apiName) {
        if (response.statusCode() != 200) {
            throw new AssertionError(apiName + " did not return 200. Status: " + response.statusCode()
                    + ", Content-Type: " + response.contentType() + ", body: " + response.getBody().asString());
        }
        return response.as(type);
    }

    private void recordAndAssert(ExecutionDataRow row, String description, List<BusinessRuleResult> results, String actualPlanIds,
            String actualCategory, String allocationResult, String plannerGenerateResponseText, String fundDetailsResponseText,
            String failureReasonOnException, BusinessValidationReportRow.FailureCategory exceptionCategory, String failingApi,
            String fundsInPortfolio) {

        String expectedRule = "PORTFOLIO_AWARE business rules (ownership, allocation, disclaimer, input echo, "
                + "reference-fund category/analyst-pick integrity - PRD 11.4-11.10)";
        String expectedPlanIds = row.matchedPlanId() == null ? "N/A" : row.matchedPlanId();
        String expectedCategory = row.fundCategory() == null ? "N/A" : row.fundCategory();

        if (exceptionCategory != null) {
            BusinessValidationReportWriter.record(new BusinessValidationReportRow(
                    String.valueOf(row.tcNo()), description, row.labelId(), row.investorId(), row.caseCovered(), row.fundCategory(),
                    "PORTFOLIO_AWARE", expectedRule, expectedPlanIds, actualPlanIds, expectedCategory,
                    actualCategory.isEmpty() ? "N/A" : actualCategory, allocationResult,
                    plannerGenerateResponseText, fundDetailsResponseText,
                    BusinessValidationReportRow.FAIL, failureReasonOnException, failingApi,
                    exceptionCategory, fundsInPortfolio));
            Assert.fail("API contract failure for TC " + row.tcNo() + ": " + failureReasonOnException);
            return;
        }

        List<BusinessRuleResult> failures = results.stream().filter(r -> !r.passed()).toList();
        String result;
        String failureReason;
        BusinessValidationReportRow.FailureCategory category;

        if (!failures.isEmpty()) {
            result = BusinessValidationReportRow.FAIL;
            failureReason = failures.stream()
                    .map(r -> r.rule() + " (expected=" + r.expected() + ", actual=" + r.actual() + ")")
                    .collect(Collectors.joining("; "));
            category = BusinessValidationReportRow.FailureCategory.BUSINESS_RULE;
        } else if (row.hasNoVerifiedMatch()) {
            result = BusinessValidationReportRow.SKIPPED;
            failureReason = "Reference-fund integrity checks skipped: Excel match_status = 'No verified match found' (no reference fund to cross-check); all self-contained/ownership/allocation checks passed.";
            category = BusinessValidationReportRow.FailureCategory.INVALID_TEST_DATA;
        } else {
            result = BusinessValidationReportRow.PASS;
            failureReason = null;
            category = null;
        }

        BusinessValidationReportWriter.record(new BusinessValidationReportRow(
                String.valueOf(row.tcNo()), description, row.labelId(), row.investorId(), row.caseCovered(), row.fundCategory(),
                "PORTFOLIO_AWARE", expectedRule, expectedPlanIds, actualPlanIds, expectedCategory,
                actualCategory.isEmpty() ? "N/A" : actualCategory, allocationResult,
                plannerGenerateResponseText, fundDetailsResponseText, result, failureReason,
                inferApiResponsible(failures), category, fundsInPortfolio));

        if (!failures.isEmpty()) {
            BusinessRuleReporter.assertAll(results);
        }
    }

    private static String inferApiResponsible(List<BusinessRuleResult> failures) {
        if (failures.isEmpty()) {
            return "";
        }
        return failures.stream().map(r -> {
            String rule = r.rule();
            if (rule.contains("is_owned")) {
                return "Fund Details / User Portfolio";
            }
            if (rule.contains("is_direct_plan") || rule.contains("category")) {
                return "Fund Details";
            }
            if (rule.contains("is_analyst_pick")) {
                return "Fund Opinion";
            }
            return "Planner Generate";
        }).distinct().collect(Collectors.joining(";"));
    }

    @AfterSuite(alwaysRun = true)
    public void writeBusinessValidationReport() {
        BusinessValidationReportWriter.writeReport();
    }
}

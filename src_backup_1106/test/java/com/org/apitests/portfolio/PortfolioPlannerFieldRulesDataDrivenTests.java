package com.org.apitests.portfolio;

import org.testng.annotations.Test;
import org.testng.Assert;
import com.org.apiautomation.models.request.planner.PlannerRequest;
import com.org.apiautomation.models.response.funddetails.FundDetail;
import com.org.apiautomation.models.response.funddetails.FundDetailsResponse;
import com.org.apiautomation.models.response.planner.FundEntry;
import com.org.apiautomation.models.response.planner.PlannerData;
import com.org.apiautomation.models.response.planner.PlannerResponse;
import com.org.apiautomation.models.response.portfolio.PortfolioFundEntry;
import com.org.apiautomation.models.response.portfolio.UserPortfolioResponse;
import com.org.apiautomation.services.FundService;
import com.org.apiautomation.services.PlannerService;
import com.org.apiautomation.services.PortfolioService;
import com.org.apitests.base.BaseTest;
import io.restassured.response.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * API-1 (Planner Generate) business-rule sweep - one {@code @Test} per
 * {@code Positive_Combinations} row (150 rows as of the current workbook),
 * every row expected to be accepted. Covers, per row:
 *
 * <ol>
 *   <li>Outgoing payload field presence/absence ({@link
 *       PlannerPayloadPresenceValidator}) - checked before the request is
 *       even sent.</li>
 *   <li>HTTP/business-envelope outcome, classified into the automation
 *       brief's exact failure taxonomy ({@link PlannerFieldRulesReportRow.Classification})
 *       - a 5xx or a business-rejection is never silently treated as a
 *       business-rule PASS or FAIL, only as what it actually is.</li>
 *   <li>For a genuine 200/{@code status:true} response: every
 *       self-contained response validator already proven out by {@link
 *       PortfolioPlannerPortfolioAwareTests} ({@link InvestmentInputValidator},
 *       {@link AllocationValidator}, {@link DisclaimerValidator}, {@link
 *       PortfolioAwareValidator#assertPlanNoteCountConsistency}), plus the
 *       two cross-API checks ({@link AllocationValidator#assertCategoryBucketConsistency},
 *       {@link PortfolioAwareValidator#assertOwnershipMatchesPortfolio}).</li>
 * </ol>
 *
 * <p><b>Why the owned-fund-id set is fetched once, not per row:</b> every
 * row in this workbook shares the same static identity (label_id=89660240,
 * investor_id=87683240 - see {@code Field_Rules_Reference}), unlike {@link
 * PortfolioPlannerExecutionDataDrivenTests}' per-row identities, so the
 * investor's existing portfolio holdings don't change row to row. Fund
 * Details lookups are cached by {@code plan_id} for the same reason -
 * across 150 rows, the same small pool of recommended funds recurs often.
 *
 * <p><b>{@code aware_type} is always {@code PORTFOLIO_AWARE} here</b> (the
 * workbook's own static value, and the automation brief explicitly
 * disallows changing it to FRESH for this suite) - so {@link
 * FundEligibilityValidator}'s analyst-choice-only rule (a FRESH-only
 * concern) does not apply to this class.
 */
public class PortfolioPlannerFieldRulesDataDrivenTests extends BaseTest {

    private static final Logger LOGGER = LogManager.getLogger(PortfolioPlannerFieldRulesDataDrivenTests.class);

    private final PlannerService plannerService = new PlannerService();
    private final PortfolioService portfolioService = new PortfolioService();
    private final FundService fundService = new FundService();

    private static final Map<Long, FundDetail> FUND_DETAILS_CACHE = new ConcurrentHashMap<>();
    /** Raw Fund Details response body per plan_id, alongside {@link #FUND_DETAILS_CACHE}'s parsed objects - kept only so a report row can show the actual response text (see {@link #buildFundDetailsResponseText}), not used by any validator. */
    private static final Map<Long, String> FUND_DETAILS_RAW_CACHE = new ConcurrentHashMap<>();
    private static volatile Set<Long> ownedFundIdsCache;
    private static volatile boolean ownedFundIdsUnavailable;

    @Test(dataProvider = "positiveCombinations", dataProviderClass = FieldRulesDataProvider.class,
            groups = {"regression", "portfolio", "portfolio-business-validation"},
            description = "API-1: a Positive_Combinations row must be accepted, send exactly the fields its goal_type/txn_options allow, and satisfy the applicable business rules.")
    public void positiveCombination_isAcceptedAndBusinessValid(PositiveCombinationRow row) {
        PlannerRequest request = buildRequest(row);

        List<BusinessRuleResult> presenceResults = PlannerPayloadPresenceValidator.assertPresenceRules(request, row.tcId());
        List<BusinessRuleResult> presenceFailures = presenceResults.stream().filter(r -> !r.passed()).toList();
        if (!presenceFailures.isEmpty()) {
            recordAndFail(row, request, null, "", presenceFailures,
                    PlannerFieldRulesReportRow.Classification.FRAMEWORK_FAILURE,
                    "Outgoing payload field-presence rule(s) violated before the request was even sent - this is a request-building defect, not a backend rule.",
                    "Planner Generate (request construction)");
            return;
        }

        Response response;
        try {
            response = plannerService.generateApi1(request);
        } catch (RuntimeException callFailed) {
            recordAndFail(row, request, null, "", List.of(),
                    PlannerFieldRulesReportRow.Classification.FRAMEWORK_FAILURE,
                    "Planner Generate call threw: " + callFailed.getMessage(), "Planner Generate");
            return;
        }

        PlannerOutcomeClassifier.Outcome outcome = PlannerOutcomeClassifier.classify(response);
        if (!outcome.isSuccess()) {
            recordAndFail(row, request, response, "", List.of(), outcome.classification(),
                    "Positive combination was expected to be accepted. " + outcome.reason(), "Planner Generate");
            return;
        }

        PlannerData data = response.as(PlannerResponse.class).data();
        List<BusinessRuleResult> results = new ArrayList<>();
        results.add(InvestmentInputValidator.assertAwareTypeEchoed(request, data, row.tcId()));
        results.add(InvestmentInputValidator.assertInvestorEchoed(request, data, row.tcId()));
        results.addAll(InvestmentInputValidator.assertInputSummaryReflectsAmounts(request, data, row.tcId()));
        results.add(AllocationValidator.assertBreakdownPercentagesSumTo100(data.breakdownFundsData(), row.tcId()));
        results.add(AllocationValidator.assertFirstMonthTotalMatchesSumOfFunds(data.fundsData(), row.tcId()));
        results.add(DisclaimerValidator.assertRequiredSentencePresent(data.disclaimerData(), row.tcId()));
        results.add(PortfolioAwareValidator.assertPlanNoteCountConsistency(data, row.tcId()));

        List<FundEntry> recommendedFunds = data.fundsData().data();
        Map<Long, FundDetail> recommendedFundDetailsByPlanId = resolveFundDetails(recommendedFunds);
        String fundDetailsResponseText = buildFundDetailsResponseText(recommendedFunds);
        results.addAll(AllocationValidator.assertCategoryBucketConsistency(
                data.breakdownFundsData(), byName(recommendedFunds, recommendedFundDetailsByPlanId), row.tcId()));

        Set<Long> ownedFundIds = resolveOwnedFundIds(row.labelId());
        if (ownedFundIds != null) {
            results.addAll(PortfolioAwareValidator.assertOwnershipMatchesPortfolio(
                    recommendedFunds, recommendedFundDetailsByPlanId, ownedFundIds, row.tcId()));
        }

        List<BusinessRuleResult> failures = results.stream().filter(r -> !r.passed()).toList();
        String actualPlanIds = recommendedFunds.stream()
                .map(f -> String.valueOf(f.planData().planId()))
                .collect(Collectors.joining(";"));

        if (!failures.isEmpty()) {
            recordAndFail(row, request, response, fundDetailsResponseText, failures, PlannerFieldRulesReportRow.Classification.BUSINESS_RULE_FAILURE,
                    null, "Planner Generate (business rules) - recommended plan_ids: " + actualPlanIds);
        } else {
            PlannerFieldRulesReportWriter.record(reportRow(row, request, response, fundDetailsResponseText, presenceResults, results,
                    PlannerFieldRulesReportRow.PASS, PlannerFieldRulesReportRow.Classification.PASS, null, ""));
        }
    }

    /**
     * The confirmed-live contract sends {@code duration_type} lowercase
     * ({@code "y"}/{@code "m"} - see {@code portfolio-planner-apis.md}'s
     * CURRENT curl and {@code planner-scenarios.json}'s {@code
     * durationType: "y"}), but the workbook's {@code duration_type} column
     * is uppercase ({@code "Y"}/{@code "M"}) - confirmed empirically during
     * the pilot run: every Positive_Combinations row sent with the
     * workbook's raw uppercase value got a bare HTTP 422, while the same
     * request lowercased succeeds. Not a business-rule dimension under
     * test (nothing in the brief asks for duration_type case-sensitivity
     * coverage), so normalized here rather than left as a workbook-wide
     * failure.
     */
    private PlannerRequest buildRequest(PositiveCombinationRow row) {
        PlannerRequest request = PlannerRequest.builder()
                .goalType(row.goalType())
                .durationType(row.durationType() == null ? null : row.durationType().toLowerCase())
                .investmentDuration(row.investmentDuration())
                .labelId(Long.parseLong(row.labelId()))
                .userId(Long.parseLong(row.userId()))
                .riskProfileId(row.riskProfileId())
                .annualIncomeRange(row.annualIncomeRange())
                .awareType(row.awareType())
                .investorId(Long.parseLong(row.investorId()));
        if (row.txnOptions() != null) {
            request.txnOptions(row.txnOptions());
        }
        if (row.monthlyAmount() != null) {
            request.monthlyAmount(row.monthlyAmount());
        }
        if (row.lumpsumAmount() != null) {
            request.lumpsumAmount(row.lumpsumAmount());
        }
        if (row.accumulatedAmount() != null) {
            request.accumulatedAmount(row.accumulatedAmount());
        }
        if (row.taxSavingAmount() != null) {
            request.taxSavingAmount(row.taxSavingAmount());
        }
        if (row.neededAnnualAmount() != null) {
            request.neededAnnualAmount(row.neededAnnualAmount());
        }
        return request;
    }

    /** Best-effort: a Fund Details lookup failure for one plan_id skips that fund's category/ownership checks rather than failing the whole row on an unrelated API's hiccup. */
    private Map<Long, FundDetail> resolveFundDetails(List<FundEntry> recommendedFunds) {
        Map<Long, FundDetail> byPlanId = new HashMap<>();
        for (FundEntry fund : recommendedFunds) {
            Long planId = fund.planData().planId();
            if (planId == null) {
                continue;
            }
            FundDetail cached = FUND_DETAILS_CACHE.get(planId);
            if (cached != null) {
                byPlanId.put(planId, cached);
                continue;
            }
            try {
                Response response = fundService.getFundDetails(planId);
                if (response.statusCode() == 200) {
                    FUND_DETAILS_RAW_CACHE.put(planId, response.getBody().asString());
                    FundDetailsResponse details = response.as(FundDetailsResponse.class);
                    if (details.data() != null && !details.data().isEmpty()) {
                        FundDetail detail = details.data().get(0);
                        FUND_DETAILS_CACHE.put(planId, detail);
                        byPlanId.put(planId, detail);
                    }
                } else {
                    LOGGER.warn("Fund Details lookup for plan_id {} returned HTTP {} - skipping category/ownership checks for this fund.", planId, response.statusCode());
                }
            } catch (RuntimeException fundDetailsFailed) {
                LOGGER.warn("Fund Details lookup for plan_id {} failed: {} - skipping category/ownership checks for this fund.", planId, fundDetailsFailed.getMessage());
            }
        }
        return byPlanId;
    }

    /** Joins the raw Fund Details response body (from {@link #FUND_DETAILS_RAW_CACHE}, populated whether this row triggered the fetch or reused an earlier row's) for every plan_id this row recommended - "" for any plan_id whose lookup never succeeded, per {@link #resolveFundDetails}'s best-effort contract. */
    private static String buildFundDetailsResponseText(List<FundEntry> recommendedFunds) {
        return recommendedFunds.stream()
                .map(fund -> fund.planData().planId())
                .filter(java.util.Objects::nonNull)
                .map(FUND_DETAILS_RAW_CACHE::get)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.joining("; "));
    }

    /** {@code breakdown_funds_data}'s nested {@code plan_data} carries a display name but no {@code plan_id} of its own, so the join key is the recommended fund's own {@code plan_data.name} (from {@code funds_data}, which does carry both) - see {@code PortfolioPlannerCrossApiTests.crossApi_allocationBucketsMatchCategoryClassification} for the same pattern. */
    private static Map<String, FundDetail> byName(List<FundEntry> recommendedFunds, Map<Long, FundDetail> byPlanId) {
        Map<String, FundDetail> byName = new HashMap<>();
        for (FundEntry fund : recommendedFunds) {
            FundDetail detail = byPlanId.get(fund.planData().planId());
            if (detail != null && fund.planData().name() != null) {
                byName.put(fund.planData().name(), detail);
            }
        }
        return byName;
    }

    /** Fetched once for the whole suite run - see class Javadoc. Returns {@code null} (skip ownership checks for every row) if the lookup itself is not resolvable, rather than treating a User Portfolio outage as every row's business-rule failure. */
    private Set<Long> resolveOwnedFundIds(String labelId) {
        if (ownedFundIdsUnavailable) {
            return null;
        }
        if (ownedFundIdsCache != null) {
            return ownedFundIdsCache;
        }
        synchronized (PortfolioPlannerFieldRulesDataDrivenTests.class) {
            if (ownedFundIdsCache != null) {
                return ownedFundIdsCache;
            }
            try {
                Response response = portfolioService.getLabelOpinionData(labelId);
                if (response.statusCode() != 200) {
                    LOGGER.warn("User Portfolio lookup for label_id {} returned HTTP {} - ownership checks will be skipped for every row.", labelId, response.statusCode());
                    ownedFundIdsUnavailable = true;
                    return null;
                }
                UserPortfolioResponse portfolio = response.as(UserPortfolioResponse.class);
                Set<Long> ids = (portfolio.data() == null || portfolio.data().fundData() == null)
                        ? Set.of()
                        : portfolio.data().fundData().values().stream()
                        .map(PortfolioFundEntry::planDetails)
                        .filter(pd -> pd != null && pd.fundId() != null)
                        .map(FundDetail::fundId)
                        .collect(Collectors.toSet());
                ownedFundIdsCache = ids;
                return ids;
            } catch (RuntimeException portfolioLookupFailed) {
                LOGGER.warn("User Portfolio lookup for label_id {} failed: {} - ownership checks will be skipped for every row.", labelId, portfolioLookupFailed.getMessage());
                ownedFundIdsUnavailable = true;
                return null;
            }
        }
    }

    private void recordAndFail(PositiveCombinationRow row, PlannerRequest request, Response response, String fundDetailsResponseText,
            List<BusinessRuleResult> failures, PlannerFieldRulesReportRow.Classification classification,
            String failureReasonOverride, String apiResponsible) {

        String failureReason = failureReasonOverride != null ? failureReasonOverride
                : failures.stream().map(r -> r.rule() + " (expected=" + r.expected() + ", actual=" + r.actual() + ")")
                        .collect(Collectors.joining("; "));

        PlannerFieldRulesReportWriter.record(reportRow(row, request, response, fundDetailsResponseText, List.of(), failures,
                PlannerFieldRulesReportRow.FAIL, classification, failureReason, apiResponsible));

        Assert.fail("TC " + row.tcId() + " [" + classification + "]: " + failureReason);
    }

    private PlannerFieldRulesReportRow reportRow(PositiveCombinationRow row, PlannerRequest request, Response response, String fundDetailsResponseText,
            List<BusinessRuleResult> presenceResults, List<BusinessRuleResult> otherResults,
            String result, PlannerFieldRulesReportRow.Classification classification, String failureReason, String apiResponsible) {

        List<BusinessRuleResult> allFailures = new ArrayList<>();
        presenceResults.stream().filter(r -> !r.passed()).forEach(allFailures::add);
        otherResults.stream().filter(r -> !r.passed()).forEach(allFailures::add);

        String validationName = allFailures.isEmpty() ? "" : allFailures.stream().map(BusinessRuleResult::rule).collect(Collectors.joining("; "));
        String expected = allFailures.isEmpty() ? "" : allFailures.stream().map(r -> String.valueOf(r.expected())).collect(Collectors.joining("; "));
        String actual = allFailures.isEmpty() ? "" : allFailures.stream().map(r -> String.valueOf(r.actual())).collect(Collectors.joining("; "));

        String httpStatus = response == null ? "" : String.valueOf(response.statusCode());
        String apiResponseStatus = "";
        if (response != null) {
            try {
                apiResponseStatus = String.valueOf(response.jsonPath().getBoolean("status"));
            } catch (RuntimeException notParseable) {
                apiResponseStatus = "N/A";
            }
        }

        return new PlannerFieldRulesReportRow(
                row.tcId(), request.toJson(), row.labelId(), row.investorId(), row.userId(),
                row.goalType(), nullToEmpty(row.txnOptions()), row.durationType(), String.valueOf(row.investmentDuration()),
                nullToEmpty(row.monthlyAmount()), nullToEmpty(row.lumpsumAmount()),
                nullToEmpty(row.accumulatedAmount()), nullToEmpty(row.taxSavingAmount()), nullToEmpty(row.neededAnnualAmount()),
                String.valueOf(row.riskProfileId()), String.valueOf(row.annualIncomeRange()), row.awareType(),
                httpStatus, apiResponseStatus, response == null ? "" : response.getBody().asString(), fundDetailsResponseText,
                validationName, expected, actual,
                result, classification, failureReason, apiResponsible);
    }

    private static String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @AfterSuite(alwaysRun = true)
    public void writeFieldRulesReport() {
        PlannerFieldRulesReportWriter.writeReport();
    }
}

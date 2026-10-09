package com.org.apitests.portfolio;

import org.testng.annotations.Test;
import org.testng.Assert;
import com.org.apiautomation.models.request.planner.PlannerRequest;
import com.org.apiautomation.services.PlannerService;
import com.org.apitests.base.BaseTest;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.Test;

import java.util.EnumSet;
import java.util.Set;

/**
 * API-1 (Planner Generate) numeric-boundary sweep - one {@code @Test} per
 * {@code Boundary_Negative_Tests} row (49 rows as of the current workbook):
 * {@code investment_duration} (both {@code duration_type}s), {@code
 * monthly_amount}, {@code lumpsum_amount}, {@code accumulated_amount},
 * {@code needed_annual_amount}, {@code tax_saving_amount} -
 * below-minimum/minimum/just-above/mid-range/
 * just-below-maximum/maximum/above-maximum for each.
 *
 * <p><b>Never asserts a literal HTTP status code</b> (the workbook's
 * "Invalid - 400 expected" notes are informational, not asserted
 * verbatim) - reuses {@link PlannerOutcomeClassifier}, the same coarse
 * "was this accepted or rejected" contract every other negative/boundary
 * test in this package already relies on ({@link PlannerNegativeAssertions#wasRejected}),
 * so a 5xx or {@code status:false}-with-a-different-message never gets
 * miscounted as a HTTP-400-shaped rejection.
 *
 * <p>Every other field is held at a known-valid baseline (see {@code
 * Field_Rules_Reference}'s static identity and {@link PlannerRequestDefaults}'
 * confirmed {@code risk_profile_id}/{@code annual_income_range}), so a
 * failure is attributable to the one field actually under test.
 */
public class PortfolioPlannerFieldRulesBoundaryTests extends BaseTest {

    /** Field_Rules_Reference: "label_id ... Static, no change required" / "user_id ... Static" / "investor_id ... Static". */
    private static final long LABEL_ID = 89660240L;
    private static final long USER_ID = 123L;
    private static final long INVESTOR_ID = 87683240L;

    private static final long BASELINE_MONTHLY_AMOUNT = 250_000L;
    private static final int BASELINE_DURATION_YEARS = 15;
    /** Valid, mid-range baseline for the field under test's REGULAR-INCOME sibling, so a boundary sweep on one field is never also (accidentally) a sweep on the other. */
    private static final long BASELINE_ACCUMULATED_AMOUNT = 50_050_000L;
    private static final long BASELINE_NEEDED_ANNUAL_AMOUNT = 250_250L;

    private static final Set<PlannerFieldRulesReportRow.Classification> INFRA_CLASSIFICATIONS = EnumSet.of(
            PlannerFieldRulesReportRow.Classification.API_500, PlannerFieldRulesReportRow.Classification.API_502,
            PlannerFieldRulesReportRow.Classification.API_503, PlannerFieldRulesReportRow.Classification.AUTHENTICATION_FAILURE,
            PlannerFieldRulesReportRow.Classification.DATA_NOT_AVAILABLE, PlannerFieldRulesReportRow.Classification.FRAMEWORK_FAILURE);

    private final PlannerService plannerService = new PlannerService();

    @Test(dataProvider = "boundaryNegativeTests", dataProviderClass = FieldRulesDataProvider.class,
            groups = {"regression", "portfolio"},
            description = "API-1 boundary: a Boundary_Negative_Tests row's field/value must be accepted or rejected exactly as the workbook's Expected Result says.")
    public void boundaryNegative_matchesExpectedOutcome(BoundaryNegativeRow row) {
        PlannerRequest request = buildRequest(row);
        Response response = plannerService.generateApi1(request);

        PlannerOutcomeClassifier.Outcome outcome = PlannerOutcomeClassifier.classify(response);

        if (INFRA_CLASSIFICATIONS.contains(outcome.classification())) {
            record(row, request, response, PlannerFieldRulesReportRow.FAIL, outcome.classification(), outcome.reason());
            Assert.fail("TC " + row.tcId() + " [" + outcome.classification() + "]: " + outcome.reason());
            return;
        }

        boolean accepted = outcome.isSuccess();
        boolean expectedValid = row.expectedValid();

        if (accepted == expectedValid) {
            record(row, request, response, PlannerFieldRulesReportRow.PASS, PlannerFieldRulesReportRow.Classification.PASS, null);
            return;
        }

        String reason = "Expected " + (expectedValid ? "Valid (accepted)" : "Invalid (rejected)") + " for " + row.field()
                + "=" + row.testValue() + " (" + row.testType() + "), but the API " + (accepted ? "accepted" : "rejected") + " it. "
                + outcome.reason();
        record(row, request, response, PlannerFieldRulesReportRow.FAIL, PlannerFieldRulesReportRow.Classification.BUSINESS_RULE_FAILURE, reason);
        Assert.fail("TC " + row.tcId() + ": " + reason);
    }

    private PlannerRequest buildRequest(BoundaryNegativeRow row) {
        PlannerRequest request = PlannerRequest.builder()
                .labelId(LABEL_ID)
                .userId(USER_ID)
                .investorId(INVESTOR_ID)
                .riskProfileId(PlannerRequestDefaults.RISK_PROFILE_ID)
                .annualIncomeRange(PlannerRequestDefaults.ANNUAL_INCOME_RANGE)
                .awareType("PORTFOLIO_AWARE");

        String field = row.field() == null ? "" : row.field();
        if (field.startsWith("investment_duration")) {
            boolean isMonths = field.contains("duration_type=M");
            request.goalType("GROWTH").txnOptions("SIP")
                    .durationType(isMonths ? "m" : "y")
                    .investmentDuration(row.testValue())
                    .monthlyAmount(BASELINE_MONTHLY_AMOUNT);
        } else if ("monthly_amount".equals(field)) {
            request.goalType("GROWTH").txnOptions("SIP")
                    .durationType("y").investmentDuration(BASELINE_DURATION_YEARS)
                    .monthlyAmount(row.testValue());
        } else if ("lumpsum_amount".equals(field)) {
            request.goalType("GROWTH").txnOptions("LUMPSUM")
                    .durationType("y").investmentDuration(BASELINE_DURATION_YEARS)
                    .lumpsumAmount(row.testValue());
        } else if ("accumulated_amount".equals(field)) {
            request.goalType("REGULAR-INCOME")
                    .durationType("y").investmentDuration(BASELINE_DURATION_YEARS)
                    .accumulatedAmount(row.testValue())
                    .neededAnnualAmount(BASELINE_NEEDED_ANNUAL_AMOUNT);
        } else if ("needed_annual_amount".equals(field)) {
            request.goalType("REGULAR-INCOME")
                    .durationType("y").investmentDuration(BASELINE_DURATION_YEARS)
                    .accumulatedAmount(BASELINE_ACCUMULATED_AMOUNT)
                    .neededAnnualAmount(row.testValue());
        } else if ("tax_saving_amount".equals(field)) {
            request.goalType("TAX-SAVINGS")
                    .durationType("y").investmentDuration(BASELINE_DURATION_YEARS)
                    .taxSavingAmount(row.testValue());
        } else {
            throw new IllegalStateException("Unrecognized Boundary_Negative_Tests 'Field' value: " + row.field());
        }
        return request;
    }

    private void record(BoundaryNegativeRow row, PlannerRequest request, Response response, String result,
            PlannerFieldRulesReportRow.Classification classification, String failureReason) {

        String httpStatus = response == null ? "" : String.valueOf(response.statusCode());
        String apiResponseStatus;
        try {
            apiResponseStatus = response == null ? "" : String.valueOf(response.jsonPath().getBoolean("status"));
        } catch (RuntimeException notParseable) {
            apiResponseStatus = "N/A";
        }

        PlannerFieldRulesReportWriter.record(new PlannerFieldRulesReportRow(
                row.tcId(), request.toJson(), String.valueOf(LABEL_ID), String.valueOf(INVESTOR_ID), String.valueOf(USER_ID),
                str(request.get("goal_type")), str(request.get("txn_options")), str(request.get("duration_type")),
                str(request.get("investment_duration")), str(request.get("monthly_amount")), str(request.get("lumpsum_amount")),
                str(request.get("accumulated_amount")), str(request.get("tax_saving_amount")), str(request.get("needed_annual_amount")),
                String.valueOf(PlannerRequestDefaults.RISK_PROFILE_ID), String.valueOf(PlannerRequestDefaults.ANNUAL_INCOME_RANGE),
                "PORTFOLIO_AWARE", httpStatus, apiResponseStatus,
                response == null ? "" : response.getBody().asString(), "",
                row.field() + " " + row.testType(), row.expectedResult(), result.equals(PlannerFieldRulesReportRow.PASS) ? row.expectedResult() : "opposite of " + row.expectedResult(),
                result, classification, failureReason, "Planner Generate"));
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @AfterSuite(alwaysRun = true)
    public void writeFieldRulesReport() {
        PlannerFieldRulesReportWriter.writeReport();
    }
}

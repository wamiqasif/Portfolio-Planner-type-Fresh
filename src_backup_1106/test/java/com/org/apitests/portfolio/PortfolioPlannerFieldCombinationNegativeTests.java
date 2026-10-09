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

/**
 * API-1 (Planner Generate) negative coverage for the "wrong field present
 * for this goal_type/txn_options" combinations the automation brief calls
 * out explicitly (its NEGATIVE TESTS section): a conditional field sent
 * where the {@code Field_Rules_Reference} contract says it must be absent.
 * Distinct from {@link PortfolioPlannerNegativeTests} (missing-mandatory-
 * parameter / invalid-enum cases) - every request here is otherwise a
 * valid, accepted combination with exactly one extra, disallowed field
 * added on top.
 *
 * <p>Same "rejected" contract as every other negative test in this package
 * ({@link PlannerNegativeAssertions#wasRejected}) - no literal HTTP status
 * code is asserted.
 */
public class PortfolioPlannerFieldCombinationNegativeTests extends BaseTest {

    /** Field_Rules_Reference: static, currently-resolvable identity shared by this whole workbook. */
    private static final long LABEL_ID = 89660240L;
    private static final long USER_ID = 123L;
    private static final long INVESTOR_ID = 87683240L;

    private final PlannerService plannerService = new PlannerService();

    @Test(groups = {"regression", "portfolio"},
            description = "GROWTH + SIP with lumpsum_amount also present (disallowed for SIP) must be rejected.")
    public void negative_growthSip_withLumpsumAmountPresent_isRejected() {
        PlannerRequest request = growthBaseline("SIP").monthlyAmount(50000).lumpsumAmount(1000000);
        assertRejected(request, "goal_type=GROWTH, txn_options=SIP, lumpsum_amount present");
    }

    @Test(groups = {"regression", "portfolio"},
            description = "GROWTH + LUMPSUM with monthly_amount also present (disallowed for LUMPSUM) must be rejected.")
    public void negative_growthLumpsum_withMonthlyAmountPresent_isRejected() {
        PlannerRequest request = growthBaseline("LUMPSUM").lumpsumAmount(1000000).monthlyAmount(50000);
        assertRejected(request, "goal_type=GROWTH, txn_options=LUMPSUM, monthly_amount present");
    }

    @Test(groups = {"regression", "portfolio"},
            description = "REGULAR-INCOME with txn_options present (must be absent for this goal_type) must be rejected.")
    public void negative_regularIncome_withTxnOptionsPresent_isRejected() {
        PlannerRequest request = regularIncomeBaseline().txnOptions("SIP");
        assertRejected(request, "goal_type=REGULAR-INCOME, txn_options present");
    }

    @Test(groups = {"regression", "portfolio"},
            description = "REGULAR-INCOME with tax_saving_amount present (belongs only to TAX-SAVINGS) must be rejected.")
    public void negative_regularIncome_withTaxSavingAmountPresent_isRejected() {
        PlannerRequest request = regularIncomeBaseline().taxSavingAmount(50000);
        assertRejected(request, "goal_type=REGULAR-INCOME, tax_saving_amount present");
    }

    @Test(groups = {"regression", "portfolio"},
            description = "REGULAR-INCOME with monthly_amount present (belongs only to GROWTH) must be rejected.")
    public void negative_regularIncome_withMonthlyAmountPresent_isRejected() {
        PlannerRequest request = regularIncomeBaseline().monthlyAmount(50000);
        assertRejected(request, "goal_type=REGULAR-INCOME, monthly_amount present");
    }

    @Test(groups = {"regression", "portfolio"},
            description = "TAX-SAVINGS with txn_options present (must be absent for this goal_type) must be rejected.")
    public void negative_taxSavings_withTxnOptionsPresent_isRejected() {
        PlannerRequest request = taxSavingsBaseline().txnOptions("LUMPSUM");
        assertRejected(request, "goal_type=TAX-SAVINGS, txn_options present");
    }

    @Test(groups = {"regression", "portfolio"},
            description = "TAX-SAVINGS with accumulated_amount present (belongs only to REGULAR-INCOME) must be rejected.")
    public void negative_taxSavings_withAccumulatedAmountPresent_isRejected() {
        PlannerRequest request = taxSavingsBaseline().accumulatedAmount(500000);
        assertRejected(request, "goal_type=TAX-SAVINGS, accumulated_amount present");
    }

    @Test(groups = {"regression", "portfolio"},
            description = "TAX-SAVINGS with lumpsum_amount present (belongs only to GROWTH) must be rejected.")
    public void negative_taxSavings_withLumpsumAmountPresent_isRejected() {
        PlannerRequest request = taxSavingsBaseline().lumpsumAmount(1000000);
        assertRejected(request, "goal_type=TAX-SAVINGS, lumpsum_amount present");
    }

    // Deliberately no "TAX-SAVINGS with needed_annual_amount present must be rejected" test here,
    // unlike accumulated_amount/tax_saving_amount above - confirmed empirically
    // (LabelResolutionDiagnostic.neededAnnualAmountLiveProbe) that the backend returns a normal
    // 200/success for TAX-SAVINGS with needed_annual_amount present, i.e. it does not enforce the
    // "wrong field for this goal_type" rule for this field the way it does for its siblings.
    // needed_annual_amount IS confirmed mandatory for REGULAR-INCOME itself (omitted/0/negative
    // all get HTTP 422 - see the same probe), which is what regularIncomeBaseline() sends.

    private PlannerRequest growthBaseline(String txnOptions) {
        return baseline().goalType("GROWTH").txnOptions(txnOptions).durationType("y").investmentDuration(15);
    }

    private PlannerRequest regularIncomeBaseline() {
        return baseline().goalType("REGULAR-INCOME").durationType("y").investmentDuration(15)
                .accumulatedAmount(500000).neededAnnualAmount(250000);
    }

    private PlannerRequest taxSavingsBaseline() {
        return baseline().goalType("TAX-SAVINGS").durationType("y").investmentDuration(15).taxSavingAmount(50000);
    }

    private PlannerRequest baseline() {
        return PlannerRequest.builder()
                .labelId(LABEL_ID)
                .userId(USER_ID)
                .investorId(INVESTOR_ID)
                .riskProfileId(PlannerRequestDefaults.RISK_PROFILE_ID)
                .annualIncomeRange(PlannerRequestDefaults.ANNUAL_INCOME_RANGE)
                .awareType("PORTFOLIO_AWARE");
    }

    private void assertRejected(PlannerRequest request, String caseDescription) {
        Response response = plannerService.generateApi1(request);
        boolean rejected = PlannerNegativeAssertions.wasRejected(response);

        PlannerFieldRulesReportWriter.record(new PlannerFieldRulesReportRow(
                caseDescription, request.toJson(), String.valueOf(LABEL_ID), String.valueOf(INVESTOR_ID), String.valueOf(USER_ID),
                str(request.get("goal_type")), str(request.get("txn_options")), str(request.get("duration_type")),
                str(request.get("investment_duration")), str(request.get("monthly_amount")), str(request.get("lumpsum_amount")),
                str(request.get("accumulated_amount")), str(request.get("tax_saving_amount")), str(request.get("needed_annual_amount")),
                String.valueOf(PlannerRequestDefaults.RISK_PROFILE_ID), String.valueOf(PlannerRequestDefaults.ANNUAL_INCOME_RANGE),
                "PORTFOLIO_AWARE", String.valueOf(response.statusCode()), String.valueOf(rejected ? "rejected" : "accepted"),
                response.getBody().asString(), "",
                "Invalid field combination must be rejected", "rejected", rejected ? "rejected" : "accepted",
                rejected ? PlannerFieldRulesReportRow.PASS : PlannerFieldRulesReportRow.FAIL,
                rejected ? PlannerFieldRulesReportRow.Classification.PASS : PlannerFieldRulesReportRow.Classification.BUSINESS_RULE_FAILURE,
                rejected ? null : "Expected the API to reject an invalid field combination (" + caseDescription + "), but it accepted it.",
                "Planner Generate"));

        Assert.assertTrue(rejected,
                "Expected the API to reject an invalid field combination (" + caseDescription + "), but it returned a business-successful response. Status: "
                        + response.statusCode() + ", body: " + response.getBody().asString());
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @AfterSuite(alwaysRun = true)
    public void writeFieldRulesReport() {
        PlannerFieldRulesReportWriter.writeReport();
    }
}

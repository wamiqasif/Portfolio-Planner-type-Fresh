package com.org.apitests.portfolio;

import org.testng.annotations.Test;
import org.testng.Assert;
import com.org.apiautomation.models.request.planner.PlannerRequest;
import com.org.apiautomation.services.PlannerService;
import com.org.apitests.base.BaseTest;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Boundary coverage for the Planner Generate API's investment amounts.
 *
 * <p>Boundary values (monthly &gt;= 1,000; one-time/lumpsum &gt;= 5,000) are
 * taken verbatim from the PRD's own validation-message text
 * ("Alert message on Your Investment plan screen": "Monthly amount must be
 * greater than or equal to 1000." / "One-time amount must be greater than
 * or equal to 5000.") - not invented. That section of the PRD documents
 * these thresholds for the Edit Investment Plan flow rather than the
 * initial Planner Generate call; no separate threshold is documented for
 * Generate, and {@code monthly_amount}/{@code lumpsum_amount} are the same
 * business concept in both flows, so these are applied here as the closest
 * verified source of truth available. If Generate is confirmed to use a
 * different threshold, update this class and the business-rule matrix
 * together.
 */
public class PortfolioPlannerBoundaryTests extends BaseTest {

    private static final long MIN_MONTHLY_AMOUNT = 1000;
    private static final long MIN_LUMPSUM_AMOUNT = 5000;

    private final PlannerService plannerService = new PlannerService();

    @Test(groups = {"regression", "portfolio"}, description = "Boundary: monthly_amount just below the PRD's documented minimum (1000) must be rejected.")
    public void boundary_monthlyAmountBelowMinimum_isRejected() {
        PlannerRequest request = baseline().monthlyAmount(MIN_MONTHLY_AMOUNT - 1).without("lumpsum_amount").txnOptions("SIP");
        assertRejected(request, "monthly_amount=" + (MIN_MONTHLY_AMOUNT - 1));
    }

    @Test(groups = {"regression", "portfolio"}, description = "Boundary: monthly_amount at the PRD's documented minimum (1000) must be accepted.")
    public void boundary_monthlyAmountAtMinimum_isAccepted() {
        PlannerRequest request = baseline().monthlyAmount(MIN_MONTHLY_AMOUNT).without("lumpsum_amount").txnOptions("SIP");
        Response response = plannerService.generateApi1(request);
        Assert.assertFalse(PlannerNegativeAssertions.wasRejected(response),
                "monthly_amount at the documented minimum (" + MIN_MONTHLY_AMOUNT + ") should be accepted, but the API rejected it. Status: "
                        + response.statusCode() + ", body: " + response.getBody().asString());
    }

    @Test(groups = {"regression", "portfolio"}, description = "Boundary: lumpsum_amount just below the PRD's documented minimum (5000) must be rejected.")
    public void boundary_lumpsumAmountBelowMinimum_isRejected() {
        PlannerRequest request = baseline().lumpsumAmount(MIN_LUMPSUM_AMOUNT - 1).without("monthly_amount").txnOptions("LUMPSUM");
        assertRejected(request, "lumpsum_amount=" + (MIN_LUMPSUM_AMOUNT - 1));
    }

    @Test(groups = {"regression", "portfolio"}, description = "Boundary: lumpsum_amount at the PRD's documented minimum (5000) must be accepted.")
    public void boundary_lumpsumAmountAtMinimum_isAccepted() {
        PlannerRequest request = baseline().lumpsumAmount(MIN_LUMPSUM_AMOUNT).without("monthly_amount").txnOptions("LUMPSUM");
        Response response = plannerService.generateApi1(request);
        Assert.assertFalse(PlannerNegativeAssertions.wasRejected(response),
                "lumpsum_amount at the documented minimum (" + MIN_LUMPSUM_AMOUNT + ") should be accepted, but the API rejected it. Status: "
                        + response.statusCode() + ", body: " + response.getBody().asString());
    }

    @Test(groups = {"regression", "portfolio"}, description = "Boundary: a zero monthly_amount with SIP selected as the transaction option must be rejected.")
    public void boundary_zeroMonthlyAmountWithSipOption_isRejected() {
        PlannerRequest request = baseline().monthlyAmount(0).without("lumpsum_amount").txnOptions("SIP");
        assertRejectedWith422(request, "monthly_amount=0, txn_options=SIP");
    }

    @Test(groups = {"regression", "portfolio"}, description = "Boundary: a negative monthly_amount must be rejected.")
    public void boundary_negativeMonthlyAmount_isRejected() {
        PlannerRequest request = baseline().monthlyAmount(-1000).without("lumpsum_amount").txnOptions("SIP");
        assertRejectedWith422(request, "monthly_amount=-1000");
    }

    @Test(groups = {"regression", "portfolio"}, description = "Boundary: a negative lumpsum_amount must be rejected.")
    public void boundary_negativeLumpsumAmount_isRejected() {
        PlannerRequest request = baseline().lumpsumAmount(-5000).without("monthly_amount").txnOptions("LUMPSUM");
        assertRejectedWith422(request, "lumpsum_amount=-5000");
    }

    private PlannerRequest baseline() {
        return PlannerTestData.toRequest(PlannerTestData.scenario("Portfolio Aware - Growth"));
    }

    private void assertRejectedWith422(PlannerRequest request, String caseDescription) {
        Response response = plannerService.generateApi1(request);
        Assert.assertEquals(response.statusCode(), 422,
                "Expected HTTP 422 (" + caseDescription + "). Body: " + response.getBody().asString());
    }

    private void assertRejected(PlannerRequest request, String caseDescription) {
        Response response = plannerService.generateApi1(request);
        Assert.assertTrue(PlannerNegativeAssertions.wasRejected(response),
                "Expected the API to reject the request (" + caseDescription + "), but it returned a business-successful response. Status: "
                        + response.statusCode() + ", body: " + response.getBody().asString());
    }
}

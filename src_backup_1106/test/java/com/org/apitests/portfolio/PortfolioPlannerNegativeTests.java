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
 * Negative-path coverage for the Planner Generate API's inputs (section 6
 * of the test brief): missing mandatory parameters and out-of-enum values.
 * "Rejected" per {@link PlannerNegativeAssertions#wasRejected} - see its
 * Javadoc for why both a non-2xx status and a 200/{@code status:false} body
 * both count.
 */
public class PortfolioPlannerNegativeTests extends BaseTest {

    private final PlannerService plannerService = new PlannerService();

    @Test(groups = {"regression", "portfolio"}, description = "Missing mandatory parameter: goal_type omitted must be rejected.")
    public void negative_missingGoalType_isRejected() {
        PlannerRequest request = baseline().without("goal_type");
        assertRejected(request, "goal_type omitted");
    }

    @Test(groups = {"regression", "portfolio"}, description = "Missing mandatory parameter: investor_id omitted must be rejected.")
    public void negative_missingInvestorId_isRejected() {
        PlannerRequest request = baseline().without("investor_id");
        assertRejected(request, "investor_id omitted");
    }

    @Test(groups = {"regression", "portfolio"}, description = "Missing mandatory parameter: risk_profile_id omitted must be rejected.")
    public void negative_missingRiskProfileId_isRejected() {
        PlannerRequest request = baseline().without("risk_profile_id");
        assertRejected(request, "risk_profile_id omitted");
    }

    @Test(groups = {"regression", "portfolio"}, description = "Missing mandatory parameter: txn_options omitted must be rejected.")
    public void negative_missingTxnOptions_isRejected() {
        PlannerRequest request = baseline().without("txn_options");
        assertRejected(request, "txn_options omitted");
    }

    @Test(groups = {"regression", "portfolio"}, description = "Invalid enum: goal_type outside the documented set (GROWTH/monthly-income/tax-saving goals) must be rejected.")
    public void negative_invalidGoalType_isRejected() {
        PlannerRequest request = baseline().goalType("NOT_A_REAL_GOAL_TYPE");
        assertRejected(request, "goal_type=NOT_A_REAL_GOAL_TYPE");
    }

    @Test(groups = {"regression", "portfolio"}, description = "Invalid enum: txn_options outside SIP/LUMPSUM/SIPLUMPSUM must be rejected.")
    public void negative_invalidTxnOptions_isRejected() {
        PlannerRequest request = baseline().txnOptions("NOT_A_REAL_TXN_OPTION");
        assertRejected(request, "txn_options=NOT_A_REAL_TXN_OPTION");
    }

    @Test(groups = {"regression", "portfolio"}, description = "Invalid enum: aware_type outside FRESH/PORTFOLIO_AWARE must be rejected.")
    public void negative_invalidAwareType_isRejected() {
        PlannerRequest request = baseline().awareType("NOT_A_REAL_AWARE_TYPE");
        assertRejected(request, "aware_type=NOT_A_REAL_AWARE_TYPE");
    }

    @Test(groups = {"regression", "portfolio"}, description = "Invalid investor: a non-existent investor_id must be rejected.")
    public void negative_invalidInvestorId_isRejected() {
        PlannerRequest request = baseline().investorId("999999999").investorIdDev("999999999");
        assertRejected(request, "investor_id=999999999 (non-existent)");
    }

    @Test(groups = {"regression", "portfolio"}, description = "Invalid risk profile: an out-of-range risk_profile_id must be rejected.")
    public void negative_invalidRiskProfileId_isRejected() {
        PlannerRequest request = baseline().riskProfileId(-1);
        assertRejected(request, "risk_profile_id=-1");
    }

    private PlannerRequest baseline() {
        return PlannerTestData.toRequest(PlannerTestData.scenario("Portfolio Aware - Growth"));
    }

    private void assertRejected(PlannerRequest request, String caseDescription) {
        Response response = plannerService.generateApi1(request);
        Assert.assertTrue(PlannerNegativeAssertions.wasRejected(response),
                "Expected the API to reject the request (" + caseDescription + "), but it returned a business-successful response. Status: "
                        + response.statusCode() + ", body: " + response.getBody().asString());
    }
}

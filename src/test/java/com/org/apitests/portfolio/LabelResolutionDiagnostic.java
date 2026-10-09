package com.org.apitests.portfolio;

import org.testng.annotations.Test;
import com.org.apiautomation.models.response.portfolio.UserPortfolioResponse;
import com.org.apiautomation.services.PortfolioService;
import com.org.apitests.base.BaseTest;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One-off diagnostic (not part of the business-validation suite): for every
 * unique {@code label_id} in the {@code Execution_Data} sheet, calls the
 * Portfolio API and tallies whether it resolves. Read-only - never calls
 * Planner Generate. Not wired into any suite XML; run explicitly via
 * {@code -Dtest=LabelResolutionDiagnostic}.
 */
public class LabelResolutionDiagnostic extends BaseTest {

    @Test
    public void reportLabelResolutionAcrossExecutionData() {
        List<ExecutionDataRow> rows = ExcelTestDataReader.readExecutionData();
        PortfolioService portfolioService = new PortfolioService();

        Set<String> seenLabelIds = new LinkedHashSet<>();
        Map<String, String> outcomeByLabelId = new LinkedHashMap<>();

        int resolved = 0, notFound = 0, otherError = 0;
        int rowsResolved = 0, rowsNotFound = 0, rowsOtherError = 0;

        for (ExecutionDataRow row : rows) {
            String labelId = row.labelId();
            if (labelId == null || labelId.isBlank()) {
                continue;
            }
            String outcome = outcomeByLabelId.get(labelId);
            if (outcome == null) {
                outcome = classifySafely(portfolioService, labelId);
                outcomeByLabelId.put(labelId, outcome);
                seenLabelIds.add(labelId);
                switch (outcome) {
                    case "RESOLVED" -> resolved++;
                    case "NOT_FOUND" -> notFound++;
                    default -> otherError++;
                }
            }
            switch (outcome) {
                case "RESOLVED" -> rowsResolved++;
                case "NOT_FOUND" -> rowsNotFound++;
                default -> rowsOtherError++;
            }
        }

        StringBuilder report = new StringBuilder();
        report.append("\n=== Label Resolution Diagnostic (Execution_Data) ===\n");
        report.append("Total rows: ").append(rows.size()).append("\n");
        report.append("Unique label_ids: ").append(seenLabelIds.size()).append("\n");
        report.append("-- By unique label_id --\n");
        report.append("  Resolved: ").append(resolved).append("\n");
        report.append("  Not Found: ").append(notFound).append("\n");
        report.append("  Other error: ").append(otherError).append("\n");
        report.append("-- By row (146 total) --\n");
        report.append("  Resolved: ").append(rowsResolved).append("\n");
        report.append("  Not Found: ").append(rowsNotFound).append("\n");
        report.append("  Other error: ").append(rowsOtherError).append("\n");
        report.append("-- Per label_id detail --\n");
        for (Map.Entry<String, String> e : outcomeByLabelId.entrySet()) {
            report.append("  ").append(e.getKey()).append(" -> ").append(e.getValue()).append("\n");
        }
        System.out.println(report);
        org.apache.logging.log4j.LogManager.getLogger(LabelResolutionDiagnostic.class).info(report.toString());
    }

    /**
     * The 39 unique {@code label_id}s that came back as a 502/503 gateway
     * error on the first pass of {@link #reportLabelResolutionAcrossExecutionData()}
     * (run 2026-09-10 11:48, after 8 clean back-to-back calls) - re-checked
     * here one at a time with a delay between calls, since a rapid
     * back-to-back sweep is the likely cause of those gateway errors rather
     * than 39 more genuinely-missing labels.
     */
    private static final List<String> PREVIOUSLY_UNRESOLVED_LABEL_IDS = List.of(
            "89659115", "89659108", "89657436", "89657476", "89661697", "89659110", "89661671",
            "89657995", "89658403", "89658798", "89657254", "89660234", "89660836", "89657287",
            "89657424", "89657240", "89658714", "89661733", "89661734", "89661735", "89661736",
            "89661737", "89661738", "89657430", "89659178", "89658782", "89661739", "89661740",
            "89661741", "89661742", "89661743", "89661744", "89657257", "89657847", "89658362",
            "89657463", "89657912", "89659097", "89659124");

    private static final long RECHECK_DELAY_MS = 5000L;

    @Test
    public void recheckPreviouslyUnresolvedLabelIds() throws InterruptedException {
        PortfolioService portfolioService = new PortfolioService();
        Map<String, String> statusByLabelId = new LinkedHashMap<>();
        Map<String, String> detailByLabelId = new LinkedHashMap<>();

        int resolved = 0, notFound = 0, gatewayError = 0;

        for (int i = 0; i < PREVIOUSLY_UNRESOLVED_LABEL_IDS.size(); i++) {
            String labelId = PREVIOUSLY_UNRESOLVED_LABEL_IDS.get(i);
            if (i > 0) {
                Thread.sleep(RECHECK_DELAY_MS);
            }
            Response response = safeGet(portfolioService, labelId);
            String status;
            String detail;
            if (response == null) {
                status = "GATEWAY_ERROR";
                detail = "network fault after retry (see log)";
            } else if (response.statusCode() == 200) {
                try {
                    UserPortfolioResponse body = response.as(UserPortfolioResponse.class);
                    if (body.success()) {
                        status = "RESOLVED";
                        detail = "success=true";
                    } else if (body.message() != null && body.message().contains("Not Found")) {
                        status = "NOT_FOUND";
                        detail = body.message();
                    } else {
                        status = "OTHER";
                        detail = "success=false, message=" + body.message();
                    }
                } catch (RuntimeException parseFailure) {
                    status = "OTHER";
                    detail = "unparseable 200 body: " + response.getBody().asString();
                }
            } else {
                status = "GATEWAY_ERROR";
                detail = "HTTP " + response.statusCode();
            }
            statusByLabelId.put(labelId, status);
            detailByLabelId.put(labelId, detail);
            switch (status) {
                case "RESOLVED" -> resolved++;
                case "NOT_FOUND" -> notFound++;
                default -> gatewayError++;
            }
        }

        StringBuilder report = new StringBuilder();
        report.append("\n=== Recheck of 39 previously-unresolved label_ids (5s delay between calls) ===\n");
        report.append("Resolved: ").append(resolved).append("\n");
        report.append("Not Found: ").append(notFound).append("\n");
        report.append("Gateway/other error: ").append(gatewayError).append("\n");
        report.append("-- Per label_id detail --\n");
        for (String labelId : PREVIOUSLY_UNRESOLVED_LABEL_IDS) {
            report.append("  ").append(labelId).append(" -> ").append(statusByLabelId.get(labelId))
                    .append(" (").append(detailByLabelId.get(labelId)).append(")\n");
        }
        System.out.println(report);
        org.apache.logging.log4j.LogManager.getLogger(LabelResolutionDiagnostic.class).info(report.toString());
    }

    /**
     * The 37 unique {@code Label ID}s from the "CLI Finder Output" sheet of
     * {@code Portfolio Planner Test CasesQA .xlsx} (~/Downloads) - a
     * distinct ID range (75xxxxxx) from the previously-tested Test Accounts
     * sheet (89xxxxxx), per the user's direction that the old label_ids are
     * no longer available and only this sheet's data should be used. Checked
     * here, one at a time with a delay, purely to classify
     * VALID_FOR_EXECUTION vs DATA_NOT_AVAILABLE before any Planner Generate
     * call is attempted against them.
     */
    private static final List<String> CLI_FINDER_OUTPUT_LABEL_IDS = List.of(
            "75502232", "75498292", "75515138", "75501777", "75524214", "75501321", "75498007",
            "75520107", "75520327", "75517436", "75507224", "75497069", "75517003", "75524161",
            "75501536", "75507353", "75499430", "75515781", "75518073", "75501435", "75498559",
            "75514700", "75524133", "75501050", "75500401", "75495647", "75502530", "75498934",
            "75519496", "75515790", "75498203", "75502517", "75498794", "75497847", "75503428",
            "75502470", "75498272");

    @Test
    public void checkCliFinderOutputCandidateLabelIds() throws InterruptedException {
        PortfolioService portfolioService = new PortfolioService();
        Map<String, String> statusByLabelId = new LinkedHashMap<>();
        Map<String, String> detailByLabelId = new LinkedHashMap<>();

        int resolved = 0, notFound = 0, gatewayError = 0;

        for (int i = 0; i < CLI_FINDER_OUTPUT_LABEL_IDS.size(); i++) {
            String labelId = CLI_FINDER_OUTPUT_LABEL_IDS.get(i);
            if (i > 0) {
                Thread.sleep(RECHECK_DELAY_MS);
            }
            Response response = safeGet(portfolioService, labelId);
            String status;
            String detail;
            if (response == null) {
                status = "GATEWAY_ERROR";
                detail = "network fault after retry (see log)";
            } else if (response.statusCode() == 200) {
                try {
                    UserPortfolioResponse body = response.as(UserPortfolioResponse.class);
                    if (body.success()) {
                        status = "RESOLVED";
                        detail = "success=true";
                    } else if (body.message() != null && body.message().contains("Not Found")) {
                        status = "NOT_FOUND";
                        detail = body.message();
                    } else {
                        status = "OTHER";
                        detail = "success=false, message=" + body.message();
                    }
                } catch (RuntimeException parseFailure) {
                    status = "OTHER";
                    detail = "unparseable 200 body: " + response.getBody().asString();
                }
            } else {
                status = "GATEWAY_ERROR";
                detail = "HTTP " + response.statusCode();
            }
            statusByLabelId.put(labelId, status);
            detailByLabelId.put(labelId, detail);
            switch (status) {
                case "RESOLVED" -> resolved++;
                case "NOT_FOUND" -> notFound++;
                default -> gatewayError++;
            }
        }

        StringBuilder report = new StringBuilder();
        report.append("\n=== CLI Finder Output candidate label_ids (37, 5s delay between calls) ===\n");
        report.append("Resolved: ").append(resolved).append("\n");
        report.append("Not Found: ").append(notFound).append("\n");
        report.append("Gateway/other error: ").append(gatewayError).append("\n");
        report.append("-- Per label_id detail --\n");
        for (String labelId : CLI_FINDER_OUTPUT_LABEL_IDS) {
            report.append("  ").append(labelId).append(" -> ").append(statusByLabelId.get(labelId))
                    .append(" (").append(detailByLabelId.get(labelId)).append(")\n");
        }
        System.out.println(report);
        org.apache.logging.log4j.LogManager.getLogger(LabelResolutionDiagnostic.class).info(report.toString());
    }

    /**
     * The 35 unique {@code Label ID}s from the "Test Accounts" sheet of the
     * same current workbook ({@code Portfolio Planner Test CasesQA .xlsx})
     * - re-checked fresh right now rather than relying on any earlier
     * resolution result from this session, since QA's backing data/gateway
     * state has visibly shifted multiple times already today.
     */
    private static final List<String> TEST_ACCOUNTS_LABEL_IDS = List.of(
            "89657388", "89657289", "89659221", "89660580", "89660592", "89657467", "89659169",
            "89659127", "89659115", "89659108", "89657436", "89657476", "89659142", "89659110",
            "89660448", "89657995", "89658403", "89658798", "89657254", "89660234", "89658714",
            "89657287", "89657424", "89657240", "89660633", "89657430", "89659178", "89657847",
            "89659206", "89660634", "89657257", "89658362", "89657912", "89659097", "89659124");

    @Test
    public void checkTestAccountsLabelIdsFreshNow() throws InterruptedException {
        PortfolioService portfolioService = new PortfolioService();
        Map<String, String> statusByLabelId = new LinkedHashMap<>();
        Map<String, String> detailByLabelId = new LinkedHashMap<>();

        int resolved = 0, notFound = 0, gatewayError = 0;

        for (int i = 0; i < TEST_ACCOUNTS_LABEL_IDS.size(); i++) {
            String labelId = TEST_ACCOUNTS_LABEL_IDS.get(i);
            if (i > 0) {
                Thread.sleep(RECHECK_DELAY_MS);
            }
            Response response = safeGet(portfolioService, labelId);
            String status;
            String detail;
            if (response == null) {
                status = "GATEWAY_ERROR";
                detail = "network fault after retry (see log)";
            } else if (response.statusCode() == 200) {
                try {
                    UserPortfolioResponse body = response.as(UserPortfolioResponse.class);
                    if (body.success()) {
                        status = "RESOLVED";
                        detail = "success=true";
                    } else if (body.message() != null && body.message().contains("Not Found")) {
                        status = "NOT_FOUND";
                        detail = body.message();
                    } else {
                        status = "OTHER";
                        detail = "success=false, message=" + body.message();
                    }
                } catch (RuntimeException parseFailure) {
                    status = "OTHER";
                    detail = "unparseable 200 body: " + response.getBody().asString();
                }
            } else {
                status = "GATEWAY_ERROR";
                detail = "HTTP " + response.statusCode();
            }
            statusByLabelId.put(labelId, status);
            detailByLabelId.put(labelId, detail);
            switch (status) {
                case "RESOLVED" -> resolved++;
                case "NOT_FOUND" -> notFound++;
                default -> gatewayError++;
            }
        }

        StringBuilder report = new StringBuilder();
        report.append("\n=== Test Accounts sheet label_ids (35, fresh check, 5s delay) ===\n");
        report.append("Resolved: ").append(resolved).append("\n");
        report.append("Not Found: ").append(notFound).append("\n");
        report.append("Gateway/other error: ").append(gatewayError).append("\n");
        report.append("-- Per label_id detail --\n");
        for (String labelId : TEST_ACCOUNTS_LABEL_IDS) {
            report.append("  ").append(labelId).append(" -> ").append(statusByLabelId.get(labelId))
                    .append(" (").append(detailByLabelId.get(labelId)).append(")\n");
        }
        System.out.println(report);
        org.apache.logging.log4j.LogManager.getLogger(LabelResolutionDiagnostic.class).info(report.toString());
    }

    /**
     * One-off investigation: does {@code login-by-guid} (the per-row Login
     * URL in the Test Accounts sheet) issue a per-account session/token we
     * could use instead of the framework's single shared auth session? Talks
     * to Rest Assured directly (bypassing {@code core.RequestSpecFactory},
     * same reasoning as {@code auth.LoginService}) since this is a raw,
     * unauthenticated exploratory GET, not a normal service call.
     */
    @Test
    public void investigateLoginByGuidMechanism() {
        String url = "https://qa.valueresearch.in/login-by-guid/?guid=NTcxMThlMTgtMWYyMi0xMWVmLWIzOGQtMDAxNjNlYjNiMDc2";
        Response response = io.restassured.RestAssured.given()
                .redirects().follow(false)
                .get(url);
        StringBuilder report = new StringBuilder();
        report.append("\n=== login-by-guid investigation ===\n");
        report.append("URL: ").append(url).append("\n");
        report.append("Status: ").append(response.statusCode()).append("\n");
        report.append("Headers:\n");
        response.headers().forEach(h -> report.append("  ").append(h.getName()).append(": ").append(h.getValue()).append("\n"));
        report.append("Cookies: ").append(response.getDetailedCookies()).append("\n");
        String body = response.getBody().asString();
        report.append("Body (first 2000 chars): ").append(body.substring(0, Math.min(2000, body.length()))).append("\n");
        System.out.println(report);
        org.apache.logging.log4j.LogManager.getLogger(LabelResolutionDiagnostic.class).info(report.toString());
    }

    /**
     * Follow-up to {@link #investigateLoginByGuidMechanism()}: does the
     * per-account session {@code login-by-guid} establishes actually
     * authenticate the Portfolio API too (label 89657388 is "Not Found"
     * under the framework's normal shared auth-token session)? Tests with
     * the guid-login's cookies attached, no auth-token header at all - the
     * cleanest way to see whether cookies alone grant access.
     */
    @Test
    public void investigateLoginByGuidAgainstPortfolioApi() {
        String guidUrl = "https://qa.valueresearch.in/login-by-guid/?guid=NTcxMThlMTgtMWYyMi0xMWVmLWIzOGQtMDAxNjNlYjNiMDc2";
        Response loginResponse = io.restassured.RestAssured.given()
                .redirects().follow(false)
                .get(guidUrl);

        io.restassured.specification.RequestSpecification withCookies = io.restassured.RestAssured.given()
                .cookies(loginResponse.getDetailedCookies());

        Response viaCookies = withCookies.get("https://qaadvappapi.valueresearch.in/api/v1/user-portfolio/label-opinion-data?label-ids=89657388");

        StringBuilder report = new StringBuilder();
        report.append("\n=== login-by-guid cookies vs Portfolio API (label 89657388) ===\n");
        report.append("Login redirect status: ").append(loginResponse.statusCode()).append("\n");
        report.append("Portfolio API status (cookies only, no auth-token): ").append(viaCookies.statusCode()).append("\n");
        report.append("Portfolio API body: ").append(viaCookies.getBody().asString()).append("\n");
        System.out.println(report);
        org.apache.logging.log4j.LogManager.getLogger(LabelResolutionDiagnostic.class).info(report.toString());
    }

    /**
     * Follow-up to the per-row-authentication implementation: a real run of
     * {@code PortfolioPlannerExecutionDataDrivenTests} with cookies-only
     * (no {@code auth-token}, no browser-identity headers) got a clean 200
     * from User Portfolio but a 500 (empty body, {@code X-Krakend-Completed:
     * false}) from Planner Generate for the same row/session. Isolates
     * whether that 500 is caused by the missing browser-identity headers
     * ({@code json-api-key}/{@code app-platform}/{@code origin}/{@code
     * referer}/{@code user-agent} - the same set {@code auth.vrlogin.headers}
     * already sends on the login call, per the framework's own documented
     * "Known gaps": these are never attached to any authenticated call today,
     * only to the login call itself) rather than being an inherent cookie-auth
     * failure - three variants, same body/cookies, only the header set differs.
     */
    @Test
    public void investigateLoginByGuidAgainstPlannerGenerate() {
        String guidUrl = "https://qa.valueresearch.in/login-by-guid/?guid=NTcxMThlMTgtMWYyMi0xMWVmLWIzOGQtMDAxNjNlYjNiMDc2";
        Response loginResponse = io.restassured.RestAssured.given()
                .redirects().follow(false)
                .get(guidUrl);
        io.restassured.http.Cookies cookies = loginResponse.getDetailedCookies();

        String body = "{\"goal_type\":\"GROWTH\",\"txn_options\":\"SIPLUMPSUM\",\"duration_type\":\"y\","
                + "\"investment_duration\":6,\"monthly_amount\":5000,\"lumpsum_amount\":50000,"
                + "\"label_id\":89657388,\"user_id\":81592990,\"risk_profile_id\":2,"
                + "\"annual_income_range\":2,\"investor_id\":87679622,\"aware_type\":\"PORTFOLIO_AWARE\"}";
        String plannerUrl = "https://qaadvappapi.valueresearch.in/api/v1/planner/funds-buy/generate";

        Response cookiesOnly = io.restassured.RestAssured.given()
                .cookies(cookies)
                .contentType(io.restassured.http.ContentType.JSON)
                .body(body)
                .post(plannerUrl);

        Response cookiesPlusBrowserHeaders = io.restassured.RestAssured.given()
                .cookies(cookies)
                .contentType(io.restassured.http.ContentType.JSON)
                .header("json-api-key", "sw3ihjv52q2awoyw2q3x84vhkhyvepxq")
                .header("app-platform", "web")
                .header("origin", "https://advisorqa.valueresearch.in")
                .header("referer", "https://advisorqa.valueresearch.in/")
                .header("user-agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36")
                .body(body)
                .post(plannerUrl);

        StringBuilder report = new StringBuilder();
        report.append("\n=== login-by-guid cookies vs Planner Generate (label 89657388) ===\n");
        report.append("Login redirect status: ").append(loginResponse.statusCode()).append("\n");
        report.append("Planner Generate status (cookies only): ").append(cookiesOnly.statusCode())
                .append(", X-Krakend-Completed=").append(cookiesOnly.header("X-Krakend-Completed"))
                .append(", body='").append(cookiesOnly.getBody().asString()).append("'\n");
        report.append("Planner Generate status (cookies + browser-identity headers): ").append(cookiesPlusBrowserHeaders.statusCode())
                .append(", X-Krakend-Completed=").append(cookiesPlusBrowserHeaders.header("X-Krakend-Completed"))
                .append(", body='").append(cookiesPlusBrowserHeaders.getBody().asString()).append("'\n");
        System.out.println(report);
        org.apache.logging.log4j.LogManager.getLogger(LabelResolutionDiagnostic.class).info(report.toString());
    }

    /**
     * Control for {@link #investigateLoginByGuidAgainstPlannerGenerate()}:
     * does the exact same Planner Generate body for label 89657388 also
     * 500 under the framework's existing, already-working shared {@code
     * auth-token} session (no cookies at all)? If so, the 500 is not
     * something the per-row cookie-auth change introduced or could fix -
     * it reproduces under the framework's normal, pre-existing auth
     * mechanism too, pointing at the label/account data itself (consistent
     * with this same label_id coming back "Not Found" from User Portfolio
     * under either session) rather than at authentication.
     */
    @Test
    public void investigatePlannerGenerateUnderSharedAuthToken() {
        String body = "{\"goal_type\":\"GROWTH\",\"txn_options\":\"SIPLUMPSUM\",\"duration_type\":\"y\","
                + "\"investment_duration\":6,\"monthly_amount\":5000,\"lumpsum_amount\":50000,"
                + "\"label_id\":89657388,\"user_id\":81592990,\"risk_profile_id\":2,"
                + "\"annual_income_range\":2,\"investor_id\":87679622,\"aware_type\":\"PORTFOLIO_AWARE\"}";
        String plannerUrl = "https://qaadvappapi.valueresearch.in/api/v1/planner/funds-buy/generate";

        Response viaSharedAuth = io.restassured.RestAssured.given()
                .spec(com.org.apiautomation.core.RequestSpecFactory.defaultRequestSpec(
                        com.org.apiautomation.config.ConfigManager.getInstance().getAdvisorApiBaseUrl()))
                .body(body)
                .post(plannerUrl);

        StringBuilder report = new StringBuilder();
        report.append("\n=== Planner Generate under shared auth-token session (label 89657388) ===\n");
        report.append("Status: ").append(viaSharedAuth.statusCode())
                .append(", X-Krakend-Completed=").append(viaSharedAuth.header("X-Krakend-Completed"))
                .append(", body='").append(viaSharedAuth.getBody().asString()).append("'\n");
        System.out.println(report);
        org.apache.logging.log4j.LogManager.getLogger(LabelResolutionDiagnostic.class).info(report.toString());
    }

    /**
     * One-off investigation for the newly-added {@code needed_annual_amount}
     * field (REGULAR-INCOME only, documented UI range 500 - 500,000): does
     * the live backend enforce presence/range on it the same way it enforces
     * {@code accumulated_amount}/{@code tax_saving_amount}, or does it accept
     * it more loosely? Confirmed result (run 2026-09-16): the field IS
     * mandatory for REGULAR-INCOME - omitted, {@code 0}, or negative all
     * return HTTP 422 - but the 500 - 500,000 range is NOT backend-enforced:
     * 499, 500001, and even 999999999999 are all accepted with HTTP 200.
     * See {@code Field_Rules_Reference} in {@code planner_generate_test_data.xlsx}
     * for where this is documented for the workbook.
     */
    @Test
    public void neededAnnualAmountLiveProbe() {
        com.org.apiautomation.services.PlannerService plannerService = new com.org.apiautomation.services.PlannerService();
        StringBuilder report = new StringBuilder("\n=== needed_annual_amount live probe (goal_type=REGULAR-INCOME) ===\n");

        probe(plannerService, report, "below-minimum (499)", 499L);
        probe(plannerService, report, "at-maximum (500000)", 500000L);
        probe(plannerService, report, "above-maximum (500001)", 500001L);
        probe(plannerService, report, "zero (0)", 0L);
        probe(plannerService, report, "negative (-100)", -100L);
        probe(plannerService, report, "far above (999999999999)", 999999999999L);

        Response omitted = plannerService.generate(baselineRegularIncome());
        report.append("omitted entirely -> HTTP ").append(omitted.statusCode())
                .append(", body: ").append(truncate(omitted.getBody().asString())).append("\n");

        System.out.println(report);
        org.apache.logging.log4j.LogManager.getLogger(LabelResolutionDiagnostic.class).info(report.toString());
    }

    private void probe(com.org.apiautomation.services.PlannerService plannerService, StringBuilder report, String label, long value) {
        Response response = plannerService.generate(baselineRegularIncome().neededAnnualAmount(value));
        report.append(label).append(" -> HTTP ").append(response.statusCode())
                .append(", body: ").append(truncate(response.getBody().asString())).append("\n");
    }

    private com.org.apiautomation.models.request.planner.PlannerRequest baselineRegularIncome() {
        return com.org.apiautomation.models.request.planner.PlannerRequest.builder()
                .labelId(89660240L).userId(123L).investorId(87683240L)
                .riskProfileId(2).annualIncomeRange(2).awareType("PORTFOLIO_AWARE")
                .goalType("REGULAR-INCOME").durationType("y").investmentDuration(15)
                .accumulatedAmount(50000000L);
    }

    private static String truncate(String body) {
        return body == null ? "" : body.substring(0, Math.min(400, body.length()));
    }

    private static Response safeGet(PortfolioService portfolioService, String labelId) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return portfolioService.getLabelOpinionData(labelId);
            } catch (Exception networkFault) {
                if (attempt == 2) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * One transient network fault (a dropped chunked-transfer stream, seen
     * empirically on this run) must not abort the whole 146-row scan - retry
     * once, then record the fault as its own outcome rather than letting it
     * propagate. Rest Assured's {@code ResponseParseException} wraps Apache
     * HttpClient's checked {@code IOException} hierarchy and reaches here
     * unchecked only via Groovy's relaxed checked-exception handling - a
     * plain {@code catch (RuntimeException)} does not match it, confirmed
     * empirically when the first version of this retry still let the fault
     * through on both attempts.
     */
    private static String classifySafely(PortfolioService portfolioService, String labelId) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return classify(portfolioService.getLabelOpinionData(labelId));
            } catch (Exception networkFault) {
                if (attempt == 2) {
                    return "OTHER_ERROR (network fault: " + networkFault + ")";
                }
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private static String classify(Response response) {
        if (response.statusCode() != 200) {
            return "OTHER_ERROR (HTTP " + response.statusCode() + ")";
        }
        try {
            UserPortfolioResponse body = response.as(UserPortfolioResponse.class);
            if (body.success()) {
                return "RESOLVED";
            }
            if (body.message() != null && body.message().contains("Not Found")) {
                return "NOT_FOUND";
            }
            return "OTHER_ERROR (success=false: " + body.message() + ")";
        } catch (RuntimeException parseFailure) {
            return "OTHER_ERROR (unparseable body: " + response.getBody().asString() + ")";
        }
    }
}

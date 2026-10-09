package com.org.apitests.portfolio;

import org.testng.annotations.Test;
import com.org.apiautomation.auth.GuidSessionAuthenticator;
import com.org.apiautomation.config.ConfigManager;
import com.org.apiautomation.core.RequestSpecFactory;
import com.org.apiautomation.models.request.planner.PlannerRequest;
import com.org.apiautomation.models.response.funddetails.FundDetail;
import com.org.apiautomation.models.response.funddetails.FundDetailsResponse;
import com.org.apiautomation.models.response.fundopinion.FundOpinionEntry;
import com.org.apiautomation.models.response.fundopinion.FundOpinionResponse;
import com.org.apiautomation.models.response.planner.BreakdownGroup;
import com.org.apiautomation.models.response.planner.FundEntry;
import com.org.apiautomation.models.response.planner.PlannerData;
import com.org.apiautomation.models.response.planner.PlannerResponse;
import com.org.apiautomation.models.response.portfolio.PortfolioFundEntry;
import com.org.apiautomation.models.response.portfolio.UserPortfolioResponse;
import com.org.apiautomation.services.FundService;
import com.org.apiautomation.services.PlannerService;
import com.org.apiautomation.services.PortfolioService;
import com.org.apitests.base.BaseTest;
import io.restassured.http.Cookies;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.org.apitests.portfolio.CompleteCoverageReport.*;

/**
 * Complete Portfolio Planner business-rule coverage run: every line of {@code
 * testdata/generated/complete-coverage-manifest.json} (Portfolio-aware sheet, Migration sheet, API-1
 * boundary/negative workbook) goes INPUT -> actual API-1 request -> response -> PRD/business-rule
 * validation -> PASS/FAIL -> evidence, always with {@code aware_type=PORTFOLIO_AWARE}.
 *
 * <p><b>This test method never throws</b> for a classified outcome: every result (including API 5xx and
 * business-rule failures) is written to the report instead. That is deliberate - a thrown assertion would
 * trigger {@code RetryAnalyzer} (wired onto every {@code @Test}), re-firing a ~20s Planner Generate call
 * and hiding the first outcome; the brief says not to retry. Truth lives in {@code reports/...-coverage.csv}.
 *
 * <p>Optional scoping: {@code -Dcoverage.tcIds=PA-001,PP-A1-020} runs only those TC IDs (pilot);
 * {@code -Dcoverage.report.name=<base>} redirects the report files.
 */
public class PortfolioPlannerCompleteCoverageTests extends BaseTest {

    private static final String PA_KIND = "PORTFOLIO_AWARE_ROW";

    private static final PlannerService PLANNER = new PlannerService();
    private static final PortfolioService PORTFOLIO = new PortfolioService();
    private static final FundService FUNDS = new FundService();
    private static final GuidSessionAuthenticator GUID = new GuidSessionAuthenticator();

    private static final Map<String, Set<Long>> OWNED_FUND_IDS = new ConcurrentHashMap<>();
    private static final Map<Long, FundDetail> FUND_DETAILS = new ConcurrentHashMap<>();
    private static final Map<String, Cookies> SESSIONS = new ConcurrentHashMap<>();

    /** A dependent API (User Portfolio / Fund Details / Fund Opinion / login) answered non-200. */
    private static final class DependentApiFailure extends RuntimeException {
        final String api;
        final int status;

        DependentApiFailure(String api, int status, String body) {
            super(api + " returned HTTP " + status + ": " + abbreviate(body, 200));
            this.api = api;
            this.status = status;
        }
    }

    @DataProvider(name = "coverageScenarios", parallel = true)
    public Object[][] coverageScenarios() {
        String only = System.getProperty("coverage.tcIds");
        Set<String> filter = only == null || only.isBlank() ? null
                : Arrays.stream(only.split(",")).map(String::trim).collect(Collectors.toSet());
        return CompleteCoverageScenario.loadAll().stream()
                .filter(s -> filter == null || filter.contains(s.tcId()) || filter.contains(s.scenarioId()))
                .map(s -> new Object[]{s}).toArray(Object[][]::new);
    }

    @Test(dataProvider = "coverageScenarios", groups = {"portfolio", "portfolio-complete-coverage"},
            description = "Complete Portfolio Planner coverage: one manifest scenario, PORTFOLIO_AWARE, full business-rule validation.")
    public void scenario(CompleteCoverageScenario s) {
        Line line = new Line(s);
        try {
            execute(s, line);
        } catch (DependentApiFailure f) {
            classifyHttp(line, f.status, f.api + ": " + f.getMessage());
            blockRemaining(line, s);
        } catch (DataNotAvailable d) {
            fail(line, DATA_NOT_AVAILABLE, EXECUTED_DATA_FAIL, d.getMessage());
            blockRemaining(line, s);
        } catch (Throwable t) {
            String msg = t.getClass().getSimpleName() + ": " + abbreviate(String.valueOf(t.getMessage()), 300);
            boolean net = t.toString().toLowerCase().matches(".*(timeout|timed out|connect|unknownhost|socket).*");
            fail(line, FRAMEWORK_FAILURE, EXECUTED_API_FAIL, (net ? "TIMEOUT/CONNECTION " : "") + msg);
            blockRemaining(line, s);
        }
        if (line.result.isEmpty()) {
            fail(line, FRAMEWORK_FAILURE, EXECUTED_API_FAIL, "No result determined (framework bug)");
        }
        CompleteCoverageReport.record(line);
    }

    // ================================================================================================
    private void execute(CompleteCoverageScenario s, Line line) {
        if ("NOT_A_TEST_ROW".equals(s.mappingStatus())) {
            line.executionStatus = NOT_A_TEST_ROW;
            line.result = "N/A";
            line.expected = "n/a";
            line.actual = "n/a";
            line.failureReason = s.mappingReason();
            return;
        }
        if (!s.executable()) {
            line.executionStatus = NOT_EXECUTED_MAPPING;
            line.result = "BLOCKED";
            line.failureCategory = DATA_MAPPING_REQUIRED;
            line.failureReason = s.mappingReason();
            line.expected = "executable API-1 payload";
            line.actual = "not executed";
            line.evidence = "Source value could not be mapped to an API field without inventing data.";
            return;
        }

        PlannerRequest request = buildRequest(s.payload());
        line.actualPayload = request.toJson();
        boolean expectValid = Boolean.TRUE.equals(s.expectedValid());
        boolean isPa = PA_KIND.equals(s.kind());
        Map<String, String> acct = s.investorAccount();
        StringBuilder ev = new StringBuilder();
        line.expected = expectValid
                ? "HTTP 200 + status:true and every PORTFOLIO_AWARE business rule satisfied"
                : "Request rejected (invalid per API-1 rules: " + s.expectedInvalidWhy() + ")";

        // ---- outgoing-JSON checks (valid payloads only; negative rows violate presence rules by design)
        if (expectValid) {
            addAll(line, "R-PAYLOAD-PRESENCE", PlannerPayloadPresenceValidator.assertPresenceRules(request, s.tcId()));
            List<BusinessRuleResult> blank = new ArrayList<>();
            for (var e : request.toBody().entrySet()) {
                Object v = e.getValue();
                boolean bad = v == null || (v instanceof String str && str.isBlank()) || (v instanceof Number n && n.doubleValue() == 0);
                if (bad) {
                    blank.add(BusinessRuleResult.fail("Field must be omitted, never null/\"\"/0", s.tcId(), null, "absent", e.getKey() + "=" + v));
                }
            }
            if (blank.isEmpty()) {
                blank.add(BusinessRuleResult.of(true, "No null/\"\"/0 values in outgoing JSON", s.tcId(), "none", "none"));
            }
            addAll(line, "R-PAYLOAD-NO-BLANK", blank);
        }

        // ---- send
        Cookies cookies = null;
        Response response;
        if ("GUID".equals(s.auth())) {
            try {
                cookies = SESSIONS.computeIfAbsent(acct.get("Login URL"), GUID::login);
            } catch (RuntimeException | AssertionError loginProblem) {
                fail(line, LOGIN_FAILURE, EXECUTED_API_FAIL, "GUID login failed: " + abbreviate(String.valueOf(loginProblem.getMessage()), 300));
                line.evidence = "login-by-guid failed before Planner Generate was called";
                blockRemaining(line, s);
                return;
            }
            if (!GuidSessionAuthenticator.hasBearer(cookies)) {
                fail(line, LOGIN_FAILURE, EXECUTED_API_FAIL, GuidSessionAuthenticator.bearerFailureReason(cookies));
                line.evidence = "No Bearer token for the GUID session; Planner Generate was not called";
                blockRemaining(line, s);
                return;
            }
            response = PLANNER.generate(request, advisorSpec(cookies));
        } else {
            response = PLANNER.generateApi1(request);
        }
        final Cookies sessionCookies = cookies;
        line.httpStatus = String.valueOf(response.statusCode());
        String body = safeBody(response);
        ev.append("HTTP ").append(response.statusCode()).append(" in ").append(response.time()).append("ms");

        int http = response.statusCode();
        if (http >= 500 || http == 401 || http == 403 || (http != 200 && expectValid)) {
            classifyHttp(line, http, "Planner Generate returned HTTP " + http + ". Body: " + abbreviate(body, 300));
            line.evidence = ev + "; body=" + abbreviate(body, 300);
            blockRemaining(line, s);
            return;
        }
        if (looksLikeDataNotFound(body)) {
            fail(line, DATA_NOT_AVAILABLE, EXECUTED_DATA_FAIL, "label_id/investor_id not resolvable in this environment. Body: " + abbreviate(body, 300));
            line.evidence = ev.toString();
            blockRemaining(line, s);
            return;
        }

        Boolean statusFlag = null;
        PlannerResponse parsed = null;
        if (http == 200) {
            try {
                parsed = response.as(PlannerResponse.class);
                statusFlag = parsed.status();
            } catch (RuntimeException unparseable) {
                fail(line, FRAMEWORK_FAILURE, EXECUTED_API_FAIL, "HTTP 200 body not parseable as PlannerResponse: " + unparseable.getMessage());
                line.evidence = ev + "; body=" + abbreviate(body, 300);
                blockRemaining(line, s);
                return;
            }
        }
        boolean accepted = http == 200 && Boolean.TRUE.equals(statusFlag) && parsed.data() != null;
        boolean rejected = !accepted;
        String specConflictTag = !expectValid && isPa ? "[SPEC CONFLICT: PRD edit-flow min monthly 1000 vs API-1 workbook min 5000] " : "";

        // ---- R-INPUT-VALIDATION
        boolean validationOk = accepted == expectValid;
        addAll(line, "R-INPUT-VALIDATION", List.of(BusinessRuleResult.of(validationOk,
                "API-1 input validation: " + (expectValid ? "valid input must be accepted" : "invalid input must be rejected"),
                s.tcId(), expectValid ? "accepted (200, status:true)" : "rejected (" + s.expectedInvalidWhy() + ")",
                accepted ? "accepted (200, status:true)" : "rejected (HTTP " + http + (http == 200 ? ", status:false" : "") + ")")));

        if (rejected) {
            ev.append("; rejection body=").append(abbreviate(body, 250));
            line.evidence = ev.toString();
            if (expectValid) {
                if (http == 200) {
                    fail(line, BUSINESS_RULE_FAILURE, EXECUTED_BUSINESS_FAIL,
                            "Valid input rejected: HTTP 200 with status:false. Message: " + abbreviate(body, 250));
                } else {
                    fail(line, API_4XX, EXECUTED_API_FAIL, "Valid input rejected with HTTP " + http + ". Body: " + abbreviate(body, 250));
                }
                blockRemaining(line, s);
            } else {
                line.actual = "Rejected: HTTP " + http + (http == 200 ? " status:false" : "");
                pass(line);
            }
            return;
        }

        // ---- accepted (HTTP 200, status:true): run PORTFOLIO_AWARE business rules
        PlannerData data = parsed.data();
        String scenarioName = s.tcId();
        addAll(line, "R-AWARE-ECHO", List.of(InvestmentInputValidator.assertAwareTypeEchoed(request, data, scenarioName)));
        addAll(line, "R-INVESTOR-ECHO", List.of(InvestmentInputValidator.assertInvestorEchoed(request, data, scenarioName)));
        addAll(line, "R-AMOUNT-ECHO", InvestmentInputValidator.assertInputSummaryReflectsAmounts(request, data, scenarioName));
        addAll(line, "R-DURATION-ECHO", List.of(durationEcho(request, data, scenarioName)));
        BusinessRuleResult sum100 = AllocationValidator.assertBreakdownPercentagesSumTo100(data.breakdownFundsData(), scenarioName);
        addAll(line, "R-ALLOC-100", List.of(sum100));
        addAll(line, "R-FIRST-MONTH-TOTAL", List.of(AllocationValidator.assertFirstMonthTotalMatchesSumOfFunds(data.fundsData(), scenarioName)));
        addAll(line, "R-DISCLAIMER", List.of(DisclaimerValidator.assertRequiredSentencePresent(data.disclaimerData(), scenarioName)));
        addAll(line, "R-NOTE-COUNT", List.of(PortfolioAwareValidator.assertPlanNoteCountConsistency(data, scenarioName)));

        ev.append("; allocation ").append(data.breakdownFundsData().data().stream()
                .map(g -> g.name() + "=" + g.percFmt()).collect(Collectors.joining(" + ")))
                .append(" => total ").append(data.breakdownFundsData().data().stream()
                        .map(BreakdownGroup::percentage).filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue).sum()).append('%');

        // cross-API: User Portfolio + Fund Details for each recommended fund
        List<FundEntry> funds = data.fundsData().data();
        Set<Long> owned = ownedFundIds(request.get("label_id").toString(), cookies);
        Map<Long, FundDetail> detailByPlan = new HashMap<>();
        for (FundEntry f : funds) {
            detailByPlan.put(f.planData().planId(), fundDetail(f.planData().planId(), cookies));
        }
        detailByPlan.values().removeIf(java.util.Objects::isNull);
        addAll(line, "R-OWNERSHIP", PortfolioAwareValidator.assertOwnershipMatchesPortfolio(funds, detailByPlan, owned, scenarioName));

        List<BusinessRuleResult> categoryConsistency = new ArrayList<>();
        for (FundEntry f : funds) {
            FundDetail d = detailByPlan.get(f.planData().planId());
            if (d == null) {
                categoryConsistency.add(BusinessRuleResult.fail("Fund Details must return the recommended plan_id", scenarioName,
                        String.valueOf(f.planData().planId()), "1 record", "0 records"));
                continue;
            }
            categoryConsistency.add(new BusinessRuleResult("Planner category_id must match Fund Details category_id", scenarioName,
                    String.valueOf(f.planData().planId()), d.categoryId(), f.categoryData().categoryId(),
                    f.categoryData().categoryId() != null && f.categoryData().categoryId().equals(d.categoryId())));
            categoryConsistency.add(new BusinessRuleResult("Planner category_fmt must match Fund Details secondary_category_name", scenarioName,
                    String.valueOf(f.planData().planId()), d.secondaryCategoryName(), f.categoryData().categoryFmt(),
                    f.categoryData().categoryFmt() != null && f.categoryData().categoryFmt().equalsIgnoreCase(d.secondaryCategoryName())));
        }
        addAll(line, "R-CATEGORY-CONSISTENCY", categoryConsistency);
        Map<String, FundDetail> byName = new HashMap<>();
        for (FundEntry f : funds) {
            FundDetail d = detailByPlan.get(f.planData().planId());
            if (d != null) {
                byName.put(f.planData().name(), d);
            }
        }
        addAll(line, "R-CATEGORY-BUCKET", AllocationValidator.assertCategoryBucketConsistency(data.breakdownFundsData(), byName, scenarioName));
        ev.append("; owned=").append(funds.stream().filter(FundEntry::isOwned).count()).append('/').append(funds.size())
                .append(" plans=").append(funds.stream().map(f -> String.valueOf(f.planData().planId())).collect(Collectors.joining(",")));

        // ---- account-specific reference-fund + precondition rules (Portfolio-aware sheet rows)
        boolean dataGap = false;
        if (isPa && acct != null) {
            String matchStatus = acct.get("Match Status");
            boolean active = "ACTIVE_MATCH_FOUND".equals(matchStatus);
            dataGap = !active;
            addAll(line, "R-CASE-PRECONDITION", List.of(BusinessRuleResult.of(active,
                    "Account must hold an ACTIVE fund matching the Excel 'Case covered' (Test Accounts match status)", scenarioName,
                    "ACTIVE_MATCH_FOUND", matchStatus + " (units flow " + acct.get("Sum Units Flow") + ")")));
            String planIdText = acct.get("Plan ID");
            if (planIdText != null && !planIdText.isBlank()) {
                long matchedPlanId = Long.parseLong(planIdText);
                FundDetail ref = fundDetail(matchedPlanId, cookies);
                if (ref != null) {
                    String actualCat = ref.secondaryCategoryName() != null ? ref.secondaryCategoryName() : ref.categoryName();
                    boolean catOk = categoryMatchesExcel(s.fundCategory(), ref);
                    boolean accountCatOk = CategoryMatcher.matches(acct.get("Fund Category"), ref.categoryName(), ref.secondaryCategoryName());
                    addAll(line, "R-REF-CATEGORY", List.of(BusinessRuleResult.of(catOk,
                            "Reference fund (matched plan " + matchedPlanId + ") category must equal Excel Fund category",
                            scenarioName, s.fundCategory() + " [alternatives: " + String.join(" | ", categoryAlternatives(s.fundCategory())) + "]",
                            "category_name='" + ref.categoryName() + "', secondary_category_name='" + ref.secondaryCategoryName()
                                    + "' (CategoryMatcher short-form compare; no extra normalization)")));
                    ev.append("; refFund ").append(matchedPlanId).append(" '").append(acct.get("Matched Fund")).append("' cat=").append(actualCat)
                            .append(" (vs Test Accounts label '").append(acct.get("Fund Category")).append("' -> ").append(accountCatOk ? "match" : "mismatch").append(')');
                    String dg = acct.get("Is Direct Growth");
                    if (dg != null && !dg.isBlank()) {
                        boolean expectedDg = dg.equalsIgnoreCase("t") || dg.equalsIgnoreCase("true");
                        addAll(line, "R-REF-DIRECT-GROWTH", List.of(BusinessRuleResult.of(expectedDg == ref.isDirectPlan(),
                                "Reference fund is_direct_plan must equal account is_direct_growth", scenarioName, expectedDg, ref.isDirectPlan())));
                    }
                    String ap = acct.get("Is Analyst Pick");
                    if (ap != null && !ap.isBlank()) {
                        boolean expectedAp = ap.equalsIgnoreCase("t") || ap.equalsIgnoreCase("true");
                        FundOpinionResponse op = parse(call("Fund Opinion", () -> FUNDS.getFundOpinionData(List.of(planIdText), mainSpec(null, sessionCookies))), FundOpinionResponse.class);
                        Boolean actualAp = op.data().stream().filter(o -> o.planId() == matchedPlanId).map(FundOpinionEntry::isAnalystPick).findFirst().orElse(null);
                        addAll(line, "R-REF-ANALYST-PICK", List.of(BusinessRuleResult.of(expectedAp == Boolean.TRUE.equals(actualAp),
                                "Reference fund is_analyst_pick must equal account is_analyst_pick", scenarioName, expectedAp, actualAp)));
                    }
                    boolean recommended = funds.stream().anyMatch(f -> {
                        FundDetail d = detailByPlan.get(f.planData().planId());
                        return d != null && ref.fundId() != null && ref.fundId().equals(d.fundId());
                    });
                    ev.append("; OBSERVATION(not a rule): held reference fund ").append(recommended ? "IS" : "is NOT").append(" in the recommendation");
                }
            }
        }
        line.evidence = ev.toString();

        // ---- verdict
        List<RuleOutcome> failed = line.rules.stream().filter(r -> !r.passed() && !r.blocked()).toList();
        long totalRules = line.rules.stream().filter(r -> !r.blocked()).count();
        line.actual = "HTTP 200 status:true; rules passed " + (totalRules - failed.size()) + "/" + totalRules
                + (failed.isEmpty() ? "" : "; failed: " + failed.stream().map(r -> r.id() + "(expected=" + r.expected() + ", actual=" + r.actual() + ")").collect(Collectors.joining(" | ")));
        boolean onlyLabelMismatch = !failed.isEmpty() && failed.stream().allMatch(r -> r.id().equals("R-REF-CATEGORY"));
        if (failed.isEmpty() && !dataGap) {
            pass(line);
        } else if (failed.isEmpty()) {
            line.result = "DATA_GAP";
            line.failureCategory = DATA_NOT_AVAILABLE;
            line.executionStatus = EXECUTED_DATA_FAIL;
            line.failureReason = "All business rules passed, but the account does not satisfy the Excel case (match status "
                    + acct.get("Match Status") + ", units flow " + acct.get("Sum Units Flow") + "): the case precondition was not actually exercised.";
        } else if (onlyLabelMismatch) {
            line.result = "FAIL";
            line.failureCategory = CATEGORY_LABEL_MISMATCH;
            line.executionStatus = EXECUTED_DATA_FAIL;
            line.failureReason = specConflictTag + "Excel category label does not match Fund Details category under CategoryMatcher; classified (not auto-normalized): "
                    + failed.stream().map(r -> r.expected() + " vs " + r.actual()).collect(Collectors.joining(" | "));
        } else {
            line.result = "FAIL";
            line.failureCategory = BUSINESS_RULE_FAILURE;
            line.executionStatus = EXECUTED_BUSINESS_FAIL;
            line.failureReason = specConflictTag + failed.stream().map(RuleOutcome::id).distinct().collect(Collectors.joining(", "))
                    + (specConflictTag.isEmpty() ? "" : " (monthly_amount below API-1 minimum was accepted)");
        }
    }

    // ================================================================================================
    private static BusinessRuleResult durationEcho(PlannerRequest request, PlannerData data, String scenario) {
        int n = Integer.parseInt(String.valueOf(request.get("investment_duration")));
        boolean years = "y".equalsIgnoreCase(String.valueOf(request.get("duration_type")));
        String text = data.inputSummaryData() == null ? null : data.inputSummaryData().textFmt();
        String t = text == null ? "" : text.toLowerCase();
        boolean ok = years ? t.contains(n + " year") : (t.contains(n + " month") || (n % 12 == 0 && t.contains((n / 12) + " year")));
        return BusinessRuleResult.of(ok, "input_summary_data.text_fmt must reflect the requested duration", scenario,
                n + (years ? " year(s)" : " month(s)"), text);
    }

    /** The Excel group label "Flexicap/Multicap/Large &amp; Mid cap/Focused" lists alternative categories; split on '/' only. */
    private static List<String> categoryAlternatives(String excelCategory) {
        return Arrays.stream(excelCategory.split("/")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    private static boolean categoryMatchesExcel(String excelCategory, FundDetail d) {
        return categoryAlternatives(excelCategory).stream()
                .anyMatch(alt -> CategoryMatcher.matches(alt, d.categoryName(), d.secondaryCategoryName()));
    }

    private static PlannerRequest buildRequest(Map<String, Object> p) {
        PlannerRequest r = PlannerRequest.builder();
        for (var e : p.entrySet()) {
            Object v = e.getValue();
            switch (e.getKey()) {
                case "goal_type" -> r.goalType(v);
                case "txn_options" -> r.txnOptions(v);
                case "duration_type" -> r.durationType(v);
                case "investment_duration" -> r.investmentDuration(v);
                case "monthly_amount" -> r.monthlyAmount(v);
                case "lumpsum_amount" -> r.lumpsumAmount(v);
                case "accumulated_amount" -> r.accumulatedAmount(v);
                case "needed_annual_amount" -> r.neededAnnualAmount(v);
                case "tax_saving_amount" -> r.taxSavingAmount(v);
                case "label_id" -> r.labelId(v);
                case "user_id" -> r.userId(v);
                case "risk_profile_id" -> r.riskProfileId(v);
                case "annual_income_range" -> r.annualIncomeRange(v);
                case "aware_type" -> r.awareType(v);
                case "investor_id" -> r.investorId(v);
                default -> throw new IllegalArgumentException("Unmapped payload field in manifest: " + e.getKey());
            }
        }
        return r;
    }

    private Set<Long> ownedFundIds(String labelId, Cookies cookies) {
        String key = labelId + (cookies == null ? "|vr" : "|guid");
        Set<Long> cached = OWNED_FUND_IDS.get(key);
        if (cached != null) {
            return cached;
        }
        RequestSpecification spec = cookies == null ? null : advisorSpec(cookies);
        Response portfolioResponse = call("User Portfolio",
                () -> spec == null ? PORTFOLIO.getLabelOpinionData(labelId) : PORTFOLIO.getLabelOpinionData(labelId, spec));
        String portfolioBody = safeBody(portfolioResponse);
        String compact = portfolioBody.toLowerCase().replaceAll("\\s", "");
        if (compact.contains("label(s)notfound") || compact.contains("investornotfound")
                || (compact.contains("\"success\":false") && compact.contains("\"data\":[]"))
                || compact.contains("\"fund-data\":[]")) {
            throw new DataNotAvailable("User Portfolio has no data for label " + labelId + ". Body: " + abbreviate(portfolioBody, 300));
        }
        UserPortfolioResponse portfolio = parse(portfolioResponse, UserPortfolioResponse.class);
        Set<Long> ids = (portfolio.data() == null || portfolio.data().fundData() == null) ? Set.of()
                : portfolio.data().fundData().values().stream().map(PortfolioFundEntry::planDetails)
                .filter(pd -> pd != null && pd.fundId() != null).map(FundDetail::fundId).collect(Collectors.toSet());
        OWNED_FUND_IDS.put(key, ids);
        return ids;
    }

    /** User Portfolio holds no data for the label/investor - test data problem, not an API defect. */
    private static final class DataNotAvailable extends RuntimeException {
        DataNotAvailable(String message) {
            super(message);
        }
    }

    private FundDetail fundDetail(long planId, Cookies cookies) {
        FundDetail cached = FUND_DETAILS.get(planId);
        if (cached != null) {
            return cached;
        }
        FundDetailsResponse r = parse(call("Fund Details", () -> cookies == null ? FUNDS.getFundDetails(planId)
                : FUNDS.getFundDetails(planId, mainSpec(null, cookies))), FundDetailsResponse.class);
        if (r.data() == null || r.data().isEmpty()) {
            return null;
        }
        FUND_DETAILS.put(planId, r.data().get(0));
        return r.data().get(0);
    }

    private static Response call(String api, Supplier<Response> request) {
        Response r = request.get();
        if (r.statusCode() != 200) {
            throw new DependentApiFailure(api, r.statusCode(), safeBody(r));
        }
        return r;
    }

    private static <T> T parse(Response r, Class<T> type) {
        return r.as(type);
    }

    private static RequestSpecification advisorSpec(Cookies c) {
        return GuidSessionAuthenticator.attach(RequestSpecFactory.unauthenticatedRequestSpec(ConfigManager.getInstance().getAdvisorApiBaseUrl()), c);
    }

    private static RequestSpecification mainSpec(Object unused, Cookies c) {
        return GuidSessionAuthenticator.attach(RequestSpecFactory.unauthenticatedRequestSpec(ConfigManager.getInstance().getBaseUrl()), c);
    }

    // ---------------------------------------------------------------------------------------------
    private static void addAll(Line line, String ruleId, List<BusinessRuleResult> results) {
        for (BusinessRuleResult r : results) {
            line.rules.add(new RuleOutcome(ruleId, r.passed(), false, String.valueOf(r.expected()), String.valueOf(r.actual())));
        }
    }

    /** Marks every rule that would have applied as BLOCKED (not run because the API call/dependency failed). */
    private static void blockRemaining(Line line, CompleteCoverageScenario s) {
        boolean valid = Boolean.TRUE.equals(s.expectedValid());
        List<String> ids = new ArrayList<>();
        ids.add("R-INPUT-VALIDATION");
        if (valid) {
            ids.addAll(List.of("R-AWARE-ECHO", "R-INVESTOR-ECHO", "R-DURATION-ECHO", "R-ALLOC-100", "R-FIRST-MONTH-TOTAL", "R-DISCLAIMER",
                    "R-NOTE-COUNT", "R-OWNERSHIP", "R-CATEGORY-CONSISTENCY", "R-CATEGORY-BUCKET"));
            if (PA_KIND.equals(s.kind())) {
                ids.addAll(List.of("R-CASE-PRECONDITION", "R-REF-CATEGORY"));
            }
        }
        for (String id : ids) {
            if (line.rules.stream().noneMatch(r -> r.id().equals(id))) {
                line.rules.add(new RuleOutcome(id, false, true, "n/a", "blocked: " + line.failureCategory));
            }
        }
        if (line.actual.isEmpty()) {
            line.actual = line.failureReason;
        }
    }

    private static void classifyHttp(Line line, int status, String reason) {
        switch (status) {
            case 500 -> fail(line, API_500, EXECUTED_API_FAIL, reason);
            case 502 -> fail(line, API_502, EXECUTED_API_FAIL, reason);
            case 503 -> fail(line, API_503, EXECUTED_API_FAIL, reason);
            case 401, 403 -> fail(line, LOGIN_FAILURE, EXECUTED_API_FAIL, reason + " (after the framework's own 401/403 auth-retry for vrlogin sessions)");
            default -> {
                if (status >= 500) {
                    fail(line, API_500, EXECUTED_API_FAIL, reason + " [other 5xx: " + status + "]");
                } else {
                    fail(line, API_4XX, EXECUTED_API_FAIL, reason);
                }
            }
        }
        line.httpStatus = line.httpStatus.isEmpty() ? String.valueOf(status) : line.httpStatus;
    }

    private static void fail(Line line, String category, String execStatus, String reason) {
        line.result = "FAIL";
        line.failureCategory = category;
        line.executionStatus = execStatus;
        line.failureReason = reason;
        if (line.actual.isEmpty()) {
            line.actual = reason;
        }
    }

    private static void pass(Line line) {
        line.result = PASS;
        line.executionStatus = EXECUTED_PASS;
    }

    private static boolean looksLikeDataNotFound(String body) {
        String lower = body == null ? "" : body.toLowerCase();
        return lower.contains("label(s) not found") || lower.contains("investor not found") || lower.contains("label not found");
    }

    private static String safeBody(Response r) {
        try {
            return r.getBody().asString();
        } catch (RuntimeException e) {
            return "<unreadable body: " + e.getMessage() + ">";
        }
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        String flat = s.replaceAll("\\s+", " ");
        return flat.length() <= max ? flat : flat.substring(0, max) + "...";
    }

    @AfterSuite(alwaysRun = true)
    public void writeCoverageReport() {
        CompleteCoverageReport.writeReports();
    }
}

package com.org.apitests.portfolio;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Collects one {@link Line} per executed/blocked manifest scenario and writes the two deliverables:
 * {@code reports/portfolio-planner-complete-scenario-coverage.csv} and {@code ...-summary.txt}.
 * The file base name can be redirected with {@code -Dcoverage.report.name=...} (pilot runs use this so
 * they never overwrite the full-run report).
 */
final class CompleteCoverageReport {

    private static final Logger LOGGER = LogManager.getLogger(CompleteCoverageReport.class);

    // ---- result vocabulary -------------------------------------------------------------------
    static final String PASS = "PASS";
    static final String BUSINESS_RULE_FAILURE = "BUSINESS_RULE_FAILURE";
    static final String CATEGORY_LABEL_MISMATCH = "CATEGORY_LABEL_MISMATCH";
    static final String API_4XX = "API_4XX";
    static final String API_500 = "API_500";
    static final String API_502 = "API_502";
    static final String API_503 = "API_503";
    static final String LOGIN_FAILURE = "LOGIN_FAILURE";
    static final String DATA_NOT_AVAILABLE = "DATA_NOT_AVAILABLE";
    static final String DATA_MAPPING_REQUIRED = "DATA_MAPPING_REQUIRED";
    static final String FRAMEWORK_FAILURE = "FRAMEWORK_FAILURE";

    static final String EXECUTED_PASS = "EXECUTED_PASS";
    static final String EXECUTED_BUSINESS_FAIL = "EXECUTED_BUSINESS_FAIL";
    static final String EXECUTED_API_FAIL = "EXECUTED_API_FAIL";
    static final String EXECUTED_DATA_FAIL = "EXECUTED_DATA_FAIL";
    static final String NOT_EXECUTED_MAPPING = "NOT_EXECUTED_DATA_MAPPING_REQUIRED";
    static final String NOT_A_TEST_ROW = "NOT_A_TEST_ROW";

    /** Every business rule this run can assert, in report order, with its source. */
    static final Map<String, String> RULES = new LinkedHashMap<>();

    static {
        RULES.put("R-INPUT-VALIDATION", "API-1 Business_Rules: valid inputs accepted, invalid/boundary inputs rejected");
        RULES.put("R-PAYLOAD-PRESENCE", "API-1 conditional fields present/absent in the ACTUAL outgoing JSON");
        RULES.put("R-PAYLOAD-NO-BLANK", "Absent field never sent as null/\"\"/0 (outgoing JSON)");
        RULES.put("R-AWARE-ECHO", "Response aware_type == PORTFOLIO_AWARE (PRD 11.1, R9)");
        RULES.put("R-INVESTOR-ECHO", "Response investor.investor_id == requested (R9)");
        RULES.put("R-AMOUNT-ECHO", "input_summary_data shows requested amounts, Indian digit grouping (R9)");
        RULES.put("R-DURATION-ECHO", "input_summary_data reflects requested duration (PRD sample '... 15 years')");
        RULES.put("R-ALLOC-100", "SUM(breakdown_funds_data buckets) == 100% (R6)");
        RULES.put("R-FIRST-MONTH-TOTAL", "total first-month investment == SUM(fund first_month_amount) (R7, PRD 11.6)");
        RULES.put("R-DISCLAIMER", "Required holding/position disclaimer sentence present (R8, PRD 11.10)");
        RULES.put("R-NOTE-COUNT", "'You already own N fund(s)' note count + singular/plural (R3, PRD 11.5)");
        RULES.put("R-OWNERSHIP", "is_owned == fund_id in User Portfolio, incl. plan/option variants (R2, PRD 11.5/11.7)");
        RULES.put("R-CATEGORY-CONSISTENCY", "Planner category_id/category_fmt == Fund Details (R12)");
        RULES.put("R-CATEGORY-BUCKET", "Fund's Equity/Debt bucket matches PRD Asset Allocation classification (R5)");
        RULES.put("R-CASE-PRECONDITION", "Account really satisfies the Excel 'Case covered' (ACTIVE holding present)");
        RULES.put("R-REF-CATEGORY", "Reference fund's Fund Details category == Excel Fund category (CategoryMatcher, no new normalization)");
        RULES.put("R-REF-DIRECT-GROWTH", "Reference fund is_direct_plan == account is_direct_growth");
        RULES.put("R-REF-ANALYST-PICK", "Reference fund is_analyst_pick (Fund Opinion) == account is_analyst_pick");
    }

    /** Rules from the brief that have no computable expectation in the PRD / existing validators - never invented. */
    static final Map<String, String> NOT_ASSERTED = new LinkedHashMap<>();

    static {
        NOT_ASSERTED.put("Fund recommendation eligibility (PORTFOLIO_AWARE)",
                "PRD 11.4 'Analyst's Choice' rule is FRESH-only; no eligibility rule for PORTFOLIO_AWARE is written in the PRD");
        NOT_ASSERTED.put("Risk-profile rules", "No risk-profile -> fund/allocation mapping table in PRD or Config Master evidence");
        NOT_ASSERTED.put("Goal-specific fund selection rules", "PRD gives screen flow, not computable per-goal selection numbers");
        NOT_ASSERTED.put("Case-covered selection priority (which owned fund gets reused)",
                "PRD 11.5/11.7 define the label/count, not a per-case selection order; reported as OBSERVATION in Evidence only");
    }

    record RuleOutcome(String id, boolean passed, boolean blocked, String expected, String actual) {
    }

    /** Mutable result line for one manifest scenario. */
    static final class Line {
        final CompleteCoverageScenario s;
        String actualPayload = "";
        String httpStatus = "";
        String expected = "";
        String actual = "";
        String result = "";
        String failureCategory = "";
        String failureReason = "";
        String evidence = "";
        String executionStatus = "";
        final List<RuleOutcome> rules = new ArrayList<>();

        Line(CompleteCoverageScenario s) {
            this.s = s;
        }

        String rulesTested() {
            return rules.stream().map(RuleOutcome::id).distinct().collect(Collectors.joining("; "));
        }
    }

    private static final List<Line> LINES = Collections.synchronizedList(new ArrayList<>());

    private CompleteCoverageReport() {
    }

    static void record(Line line) {
        LINES.add(line);
    }

    // ---- writers ------------------------------------------------------------------------------
    private static final String[] COLUMNS = {"Source Sheet", "Source Row", "TC ID", "Investor Account", "Fund Category",
            "Case Covered", "Goal", "Risk Profile", "Time", "Lumpsum Amount", "Monthly Amount", "Tax Saving Amount",
            "label_id", "user_id", "investor_id", "aware_type", "txn_options", "duration_type", "investment_duration",
            "Actual Payload", "HTTP Status", "Business Rules Tested", "Expected", "Actual", "Result", "Failure Category",
            "Failure Reason", "Evidence", "Execution Status", "Scenario ID"};

    static void writeReports() {
        List<Line> lines;
        synchronized (LINES) {
            lines = new ArrayList<>(LINES);
        }
        if (lines.isEmpty()) {
            LOGGER.info("Complete coverage report: nothing recorded, skipping.");
            return;
        }
        lines.sort(Comparator.comparing((Line l) -> l.s.scenarioId()));
        String base = System.getProperty("coverage.report.name", "portfolio-planner-complete-scenario");
        Path dir = Path.of("reports");
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(base + "-coverage.csv"), csv(lines), StandardCharsets.UTF_8);
            Files.writeString(dir.resolve(base + "-summary.txt"), summary(lines), StandardCharsets.UTF_8);
            LOGGER.info("Complete coverage report written: reports/{}-coverage.csv and -summary.txt ({} lines)", base, lines.size());
        } catch (IOException e) {
            throw new IllegalStateException("Could not write complete coverage report: " + e.getMessage(), e);
        }
    }

    private static String csv(List<Line> lines) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append(String.join(",", COLUMNS)).append("\r\n");
        for (Line l : lines) {
            CompleteCoverageScenario s = l.s;
            Map<String, Object> p = s.payload();
            Map<String, String> a = s.investorAccount();
            String[] cols = {s.sourceSheet(), s.sourceRow() == 0 ? "n/a" : String.valueOf(s.sourceRow()), s.tcId(),
                    a == null ? "(none in source; see Evidence)" : "Test Accounts case " + a.get("Case ID") + " / label " + a.get("Label ID"),
                    s.fundCategory(), s.caseCovered(), s.goal(), s.riskProfile(), s.timeText(), s.lumpsumText(),
                    s.monthlyText(), s.taxText(),
                    val(p, "label_id"), val(p, "user_id"), val(p, "investor_id"), val(p, "aware_type"), val(p, "txn_options"),
                    val(p, "duration_type"), val(p, "investment_duration"),
                    l.actualPayload, l.httpStatus, l.rulesTested(), l.expected, l.actual, l.result, l.failureCategory,
                    l.failureReason, l.evidence, l.executionStatus, s.scenarioId()};
            for (int i = 0; i < cols.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(quote(cols[i]));
            }
            sb.append("\r\n");
        }
        return sb.toString();
    }

    private static String val(Map<String, Object> p, String k) {
        return p == null || p.get(k) == null ? "" : String.valueOf(p.get(k));
    }

    private static String quote(String v) {
        String s = v == null ? "" : v;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    // ---- summary ------------------------------------------------------------------------------
    private static String summary(List<Line> lines) {
        StringBuilder o = new StringBuilder();
        o.append("PORTFOLIO PLANNER - COMPLETE SCENARIO COVERAGE (aware_type = PORTFOLIO_AWARE for every request)\n");
        o.append("Generated: ").append(java.time.LocalDateTime.now()).append("\n\n");

        // SOURCE COVERAGE: distinct source rows per sheet
        Map<String, Map<Integer, List<Line>>> bySheetRow = new TreeMap<>();
        for (Line l : lines) {
            bySheetRow.computeIfAbsent(l.s.sourceSheet(), k -> new TreeMap<>())
                    .computeIfAbsent(l.s.sourceRow(), k -> new ArrayList<>()).add(l);
        }
        o.append("SOURCE COVERAGE\n---------------\n");
        int totalRows = 0;
        for (var e : bySheetRow.entrySet()) {
            o.append(String.format("%-62s = %d source rows -> %d scenario lines%n", e.getKey(), e.getValue().size(),
                    e.getValue().values().stream().mapToInt(List::size).sum()));
            totalRows += e.getValue().size();
        }
        long srcFromTestCaseWb = bySheetRow.entrySet().stream()
                .filter(e -> e.getKey().equals("Migration to new code") || e.getKey().equals("Portfolio aware recommendations"))
                .mapToLong(e -> e.getValue().size()).sum();
        o.append("TOTAL SOURCE ROWS (test-case workbook: Migration + Portfolio aware) = ").append(srcFromTestCaseWb).append('\n');
        o.append("TOTAL SOURCE ROWS incl. API-1 workbook + generated supplementary = ").append(totalRows).append("\n\n");

        // EXECUTION
        long executed = lines.stream().filter(l -> l.s.executable()).count();
        long mapping = lines.stream().filter(l -> NOT_EXECUTED_MAPPING.equals(l.executionStatus)).count();
        long notRow = lines.stream().filter(l -> NOT_A_TEST_ROW.equals(l.executionStatus)).count();
        o.append("EXECUTION (scenario lines)\n--------------------------\n");
        o.append("Total lines            : ").append(lines.size()).append('\n');
        o.append("Executed (API called)  : ").append(executed).append('\n');
        o.append("Not executed - mapping : ").append(mapping).append("  (NOT_EXECUTED_DATA_MAPPING_REQUIRED)\n");
        o.append("Not a test row         : ").append(notRow).append("  (title / header / blank rows of the Migration sheet)\n\n");

        o.append("SOURCE-ROW STATUS (worst outcome across a row's lines)\n------------------------------------------------------\n");
        for (var e : bySheetRow.entrySet()) {
            Map<String, Long> st = new TreeMap<>();
            long partial = 0;
            for (List<Line> ls : e.getValue().values()) {
                st.merge(rowStatus(ls), 1L, Long::sum);
                if (ls.stream().anyMatch(l -> l.s.executable()) && ls.stream().anyMatch(l -> NOT_EXECUTED_MAPPING.equals(l.executionStatus))) {
                    partial++;
                }
            }
            o.append(e.getKey()).append(": ").append(st).append("  [rows executed but with >=1 blocked cell: ").append(partial).append("]\n");
        }
        o.append('\n');

        // RESULTS
        Map<String, Long> cat = new LinkedHashMap<>();
        for (String c : List.of(PASS, BUSINESS_RULE_FAILURE, CATEGORY_LABEL_MISMATCH, API_4XX, API_500, API_502, API_503,
                LOGIN_FAILURE, DATA_NOT_AVAILABLE, DATA_MAPPING_REQUIRED, FRAMEWORK_FAILURE)) {
            cat.put(c, 0L);
        }
        for (Line l : lines) {
            if (NOT_A_TEST_ROW.equals(l.executionStatus)) {
                continue;
            }
            String c = PASS.equals(l.result) ? PASS : l.failureCategory;
            cat.merge(c, 1L, Long::sum);
        }
        o.append("RESULTS (scenario lines, executed + blocked; non-test rows excluded)\n------------------------------------------------------------------\n");
        cat.forEach((k, v) -> o.append(String.format("%-26s %d%n", k, v)));
        long timeouts = lines.stream().filter(l -> l.failureReason.startsWith("TIMEOUT/CONNECTION")).count();
        long specConflict = lines.stream().filter(l -> l.failureReason.contains("[SPEC CONFLICT")).count();
        o.append("  of which FRAMEWORK_FAILURE that are client timeouts/connection errors (backend-side): ").append(timeouts).append('\n');
        o.append("  of which BUSINESS_RULE_FAILURE tagged [SPEC CONFLICT] (PRD vs API-1 workbook disagree): ").append(specConflict).append('\n');
        o.append("  DATA_GAP lines (account does not satisfy the Excel case; reported as DATA_NOT_AVAILABLE): ")
                .append(lines.stream().filter(l -> "DATA_GAP".equals(l.result)).count()).append("\n\n");

        // BUSINESS RULE COVERAGE
        o.append("BUSINESS RULE COVERAGE (one count per scenario line the rule applied to)\n-----------------------------------------------------------------------\n");
        o.append(String.format("%-24s %6s %6s %6s %8s  %s%n", "RULE", "TESTS", "PASS", "FAIL", "BLOCKED", "DESCRIPTION"));
        for (var r : RULES.entrySet()) {
            long t = 0, p = 0, f = 0, b = 0;
            for (Line l : lines) {
                for (RuleOutcome ro : l.rules) {
                    if (!ro.id().equals(r.getKey())) {
                        continue;
                    }
                    t++;
                    if (ro.blocked()) {
                        b++;
                    } else if (ro.passed()) {
                        p++;
                    } else {
                        f++;
                    }
                }
            }
            o.append(String.format("%-24s %6d %6d %6d %8d  %s%n", r.getKey(), t, p, f, b, r.getValue()));
        }
        o.append("\nRULES FROM THE BRIEF NOT ASSERTED (no computable PRD rule - not invented):\n");
        NOT_ASSERTED.forEach((k, v) -> o.append(" - ").append(k).append(": ").append(v).append('\n'));
        o.append('\n');

        // SCENARIO COVERAGE
        o.append("SCENARIO COVERAGE (executed lines: total / PASS)\n------------------------------------------------\n");
        coverage(o, "goal_type", lines, p -> str(p, "goal_type"), List.of("GROWTH", "REGULAR-INCOME", "TAX-SAVINGS"));
        coverage(o, "txn_options", lines, p -> str(p, "txn_options"), List.of("SIP", "SIPLUMPSUM", "LUMPSUM"));
        coverage(o, "duration_type", lines, p -> str(p, "duration_type"), List.of("y", "m"));
        coverage(o, "risk_profile_id", lines, p -> str(p, "risk_profile_id"), List.of("1", "2", "3"));
        coverage(o, "annual_income_range", lines, p -> str(p, "annual_income_range"), List.of("1", "2", "3", "4", "5"));
        o.append("Fund category (Portfolio aware recommendations sheet, exact Excel value):\n");
        Map<String, long[]> byCat = new TreeMap<>();
        for (Line l : lines) {
            if (!"Portfolio aware recommendations".equals(l.s.sourceSheet())) {
                continue;
            }
            long[] c = byCat.computeIfAbsent(l.s.fundCategory(), k -> new long[3]);
            c[0]++;
            if (l.s.executable()) {
                c[1]++;
            }
            if (PASS.equals(l.result)) {
                c[2]++;
            }
        }
        byCat.forEach((k, v) -> o.append(String.format("  %-48s rows=%3d executed=%3d pass=%3d%n", k, v[0], v[1], v[2])));
        return o.toString();
    }

    private static String rowStatus(List<Line> ls) {
        boolean anyExec = ls.stream().anyMatch(l -> l.s.executable());
        if (!anyExec) {
            return ls.stream().allMatch(l -> NOT_A_TEST_ROW.equals(l.executionStatus)) ? NOT_A_TEST_ROW : NOT_EXECUTED_MAPPING;
        }
        for (String s : List.of(EXECUTED_BUSINESS_FAIL, EXECUTED_API_FAIL, EXECUTED_DATA_FAIL)) {
            if (ls.stream().anyMatch(l -> s.equals(l.executionStatus))) {
                return s;
            }
        }
        return EXECUTED_PASS;
    }

    private static String str(Map<String, Object> p, String k) {
        return p == null || p.get(k) == null ? "(absent)" : String.valueOf(p.get(k));
    }

    private static void coverage(StringBuilder o, String name, List<Line> lines,
            java.util.function.Function<Map<String, Object>, String> key, List<String> expected) {
        Map<String, long[]> m = new TreeMap<>();
        for (String e : expected) {
            m.put(e, new long[2]);
        }
        for (Line l : lines) {
            if (!l.s.executable()) {
                continue;
            }
            long[] c = m.computeIfAbsent(key.apply(l.s.payload()), k -> new long[2]);
            c[0]++;
            if (PASS.equals(l.result)) {
                c[1]++;
            }
        }
        o.append(name).append(": ");
        m.forEach((k, v) -> o.append(k).append(" = ").append(v[0]).append('/').append(v[1]).append(v[0] == 0 ? " (NOT COVERED)" : "").append("   "));
        o.append('\n');
    }
}

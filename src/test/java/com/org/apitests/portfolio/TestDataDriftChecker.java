package com.org.apitests.portfolio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects TEST DATA DRIFT: the test sheet's opinion / star rating / Analyst's Choice values for the
 * reference fund (matched_plan_id) no longer match what QA returns today.
 *
 * <p>Why: opinions and ratings are refreshed every month (see {@code as_on_date} in
 * fund-opinion-data). When they change, an account may stop covering the scenario it was chosen for
 * (e.g. Tata Liquid Reg-G moved from 3 star to 4 star on 30-Sep-2026, so the "Good 1, 2 or 3 star"
 * case no longer applies). Such rows must be reported as a test data problem, not as an app bug.
 *
 * <p>The checker classifies the reference fund from LIVE data into the same six cases the test sheet
 * uses, and compares with the row's "Case covered":
 * <ul>
 *   <li>Case changed &rarr; rule "[TEST DATA DRIFT] ..." FAILS (default) so the row is visibly marked;
 *       run with {@code -Dtestdata.drift=warn} to log it as a warning instead.</li>
 *   <li>Case unchanged but a value changed (e.g. 4 &rarr; 5 star) &rarr; [WARN] only.</li>
 * </ul>
 * The fund-selection checks in {@link FundSelectionValidator} always use live data, so they stay
 * valid even when the sheet is out of date.
 */
final class TestDataDriftChecker {

    static final String CASE_AC_DG = "One or more Analyst's Choice funds (Direct-Growth)";
    static final String CASE_AC_NOT_DG = "One or more Analyst's Choice funds (NOT Direct-Growth)";
    static final String CASE_GOOD45_DG = "One or more 'Good' funds (4 or 5 star; Direct-Growth), but NONE are Analyst's Choice";
    static final String CASE_GOOD45_REG = "One or more 'Good' funds (4 or 5 star; Regular or Dividend), but NONE are Analyst's Choice";
    static final String CASE_GOOD123 = "One or more 'Good' funds (1, 2 or 3 star), but NONE are Analyst's Choice";
    static final String CASE_NEITHER = "One or more funds which are neither 'Good' nor Analyst's Choice";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String WARN = "[WARN] ";

    private TestDataDriftChecker() {
    }

    /**
     * @param caseCovered  row's "Case covered" text from the test sheet
     * @param sheetOpinion row's opinion name (e.g. "Good", "Steady")
     * @param sheetStar    row's star rating text (e.g. "3")
     * @param sheetAc      row's Analyst's Choice flag (may be null)
     * @param fundName     row's matched fund name (for messages)
     * @param planId       matched_plan_id
     * @param liveDirect   is_direct_plan of the matched plan from fund details (may be null)
     * @param opinionBody  body of fund-opinion-data/get-list?plan_id={planId} (no 'fields' filter)
     */
    static List<BusinessRuleResult> check(String caseCovered, String sheetOpinion, String sheetStar, Boolean sheetAc,
                                          String fundName, long planId, Boolean liveDirect, String opinionBody) {
        List<BusinessRuleResult> out = new ArrayList<>();
        JsonNode row = null;
        String asOn = "?";
        try {
            for (JsonNode o : MAPPER.readTree(opinionBody == null || opinionBody.isBlank() ? "{}" : opinionBody).path("data")) {
                if (o.path("plan_id").asLong(0) == planId) {
                    row = o;
                    break;
                }
            }
        } catch (Exception e) {
            out.add(BusinessRuleResult.of(true, WARN + "Test data drift not checked - opinion API body unreadable",
                    caseCovered, "plan " + planId, e.getMessage()));
            return out;
        }
        if (row == null) {
            out.add(BusinessRuleResult.of(true, WARN + "Test data drift not checked - no live opinion data for reference fund",
                    caseCovered, "plan " + planId, fundName));
            return out;
        }
        asOn = row.path("as_on_date").asText("?");
        boolean liveAc = row.path("is_analyst_pick").asBoolean(false);
        Integer liveOpinionType = row.path("opinion_type_id").isNull() || row.path("opinion_type_id").isMissingNode()
                ? null : row.path("opinion_type_id").asInt();
        String liveOpinion = row.path("opinion_name").isNull() ? null : row.path("opinion_name").asText(null);
        Integer liveStar = row.path("vr_rating").isNull() || row.path("vr_rating").isMissingNode() ? null : row.path("vr_rating").asInt();

        String liveCase = classify(liveAc, liveDirect, liveOpinionType, liveOpinion, liveStar);
        String sheetCase = normalise(caseCovered);

        String sheetValues = "opinion=" + sheetOpinion + ", star=" + sheetStar + ", analyst_choice=" + sheetAc;
        String liveValues = "opinion=" + liveOpinion + ", star=" + liveStar + ", analyst_choice=" + liveAc + " (as on " + asOn + ")";
        boolean valuesDiffer = !same(sheetOpinion, liveOpinion) || !same(sheetStar, liveStar == null ? null : String.valueOf(liveStar))
                || (sheetAc != null && sheetAc != liveAc);

        if (liveCase != null && sheetCase != null && !liveCase.equals(sheetCase)) {
            boolean warnOnly = "warn".equalsIgnoreCase(System.getProperty("testdata.drift", "fail"));
            out.add(BusinessRuleResult.of(warnOnly,
                    (warnOnly ? WARN : "") + "[TEST DATA DRIFT] Account no longer covers this scenario - refresh test data sheet",
                    caseCovered,
                    caseCovered + " [sheet: " + sheetValues + "]",
                    "now covers: " + liveCase + " [QA: " + liveValues + "] - " + fundName + " (plan " + planId + ")"));
        } else if (valuesDiffer) {
            out.add(BusinessRuleResult.of(true, WARN + "Test data values changed but scenario still covered", caseCovered,
                    sheetValues, liveValues + " - " + fundName));
        } else {
            out.add(BusinessRuleResult.of(true, "Test data matches live QA data (as on " + asOn + ")", caseCovered,
                    sheetValues, liveValues));
        }
        return out;
    }

    /** Same six-way classification the test sheet uses, from live data. */
    static String classify(boolean ac, Boolean direct, Integer opinionTypeId, String opinionName, Integer star) {
        if (ac) {
            if (direct == null) {
                return null;
            }
            return direct ? CASE_AC_DG : CASE_AC_NOT_DG;
        }
        boolean good = (opinionTypeId != null && opinionTypeId == FundSelectionValidator.GOOD_OPINION_TYPE_ID)
                || "good".equalsIgnoreCase(opinionName);
        if (!good) {
            return CASE_NEITHER;
        }
        if (star == null) {
            return null; // unrated Good fund - no sheet case for it
        }
        if (star <= 3) {
            return CASE_GOOD123;
        }
        if (direct == null) {
            return null;
        }
        return direct ? CASE_GOOD45_DG : CASE_GOOD45_REG;
    }

    /** "Three or more Analyst's Choice funds" is an Analyst's Choice Direct-Growth case for the reference fund. */
    static String normalise(String caseCovered) {
        if (caseCovered == null) {
            return null;
        }
        String c = caseCovered.trim();
        if (c.startsWith("Three or more Analyst's Choice")) {
            return CASE_AC_DG;
        }
        for (String known : List.of(CASE_AC_DG, CASE_AC_NOT_DG, CASE_GOOD45_DG, CASE_GOOD45_REG, CASE_GOOD123, CASE_NEITHER)) {
            if (known.equalsIgnoreCase(c)) {
                return known;
            }
        }
        return null;
    }

    private static boolean same(String a, String b) {
        if (a == null || a.isBlank() || "nan".equalsIgnoreCase(a)) {
            return true; // sheet value missing: nothing to compare
        }
        return a.trim().equalsIgnoreCase(b == null ? "" : b.trim());
    }
}

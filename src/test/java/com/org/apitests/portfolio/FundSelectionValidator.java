package com.org.apitests.portfolio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.Deflater;

/**
 * Validates WHICH funds the Planner picked in PORTFOLIO_AWARE mode, using the fund-assignment
 * logic doc ("STEP 5: Assigning specific funds"): exclusion filters (5.2A), variant normalisation
 * (5.2B), queue precedence A > B > C (5.3/5.5), and fund-house diversification (5.6B/5.7).
 *
 * <p><b>Scope / honesty about what can and cannot be checked:</b>
 * <ul>
 *   <li>The model's slots (categories and amounts, Steps 1-4) are not documented, so slots are
 *       taken FROM the actual response; this validator checks the fund chosen for each slot,
 *       not whether the slot itself is right.</li>
 *   <li>Queue B sort metrics (1-yr alpha etc.), investment restrictions (min/max SIP) and tag
 *       preferences are not available here. Where they could legitimately explain a different
 *       pick, the result is reported as {@code [WARN]} (logged, not failed).</li>
 *   <li>PM decision (15-Oct-2026): an active fund at or above 15% of the total portfolio is excluded
 *       from the portfolio pool AND does not enter Queue C (Analyst's Choice list) - it must not appear
 *       in the plan at all.</li>
 * </ul>
 *
 * <p>Results use {@link BusinessRuleResult}: FAIL = rule broken; PASS whose rule text starts with
 * {@code [WARN]} = needs a human look, does not fail the row.
 */
final class FundSelectionValidator {

    /** Data access, implemented by the calling test with its row-scoped (authenticated) specs. */
    interface Fetcher {
        /** Body of fund-opinion-data/get-list?plan_id=...&fields=plan_id,vr_rating,opinion_type_id,is_analyst_pick */
        String fundOpinions(List<Long> planIds);

        /** Body of fund-opinion-data/get-list?is_analyst_pick=true&fields=plan_id,priority_order,tag_name (all pages) */
        String analystPicks();

        /** Body of /api/v1/funds/{planId} */
        String fundDetails(long planId);

        /** Body of /api/v1/funds/filter/{encodedFilter} */
        String fundsByFilter(String encodedFilter);

        /** Body of gql component plan-txn-details (the source the planner uses); null = limits not checked. */
        default String planTxnDetails(List<Long> planIds) {
            return null;
        }

        /**
         * No longer called (plan-txn-details-alt usage was reverted by dev, 14-Oct-2026). Kept only so that
         * test classes that still override it keep compiling.
         */
        @Deprecated
        default String planTxnDetailsAlt(List<Long> planIds) {
            return null;
        }
    }

    static final double CONCENTRATION_LIMIT_PERCENT = 15.0;
    static final int GOOD_OPINION_TYPE_ID = 1;
    static final String LARGE_CAP_INDEX_SLOT = "large cap index";
    static final String GROWTH_SLOT = "growth";
    /** PM 16-Oct-2026: these categories may fall back to a 'Good' fund when no Analyst's Choice fund is available. */
    static final Set<String> GOOD_FALLBACK_SLOTS = Set.of("conservative hybrid", "dynamic asset allocation");
    private static final String WARN = "[WARN] ";

    /** Logic doc 5.4.2: Flexicap / Value / Large & Midcap / Multicap / Focused form one 'Growth' slot. */
    private static final Set<String> GROWTH_CATEGORIES = Set.of(
            "flexi cap", "flexicap", "large & midcap", "large & mid cap", "large and mid cap",
            "multi cap", "multicap", "value oriented", "value", "focused");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern OWN_COUNT = Pattern.compile("own\\s*(?:<b>)?\\s*(\\d+)\\s*fund", Pattern.CASE_INSENSITIVE);

    /** Analyst's Choice list is global, so it is fetched once per run. */
    private static volatile Map<Long, AcInfo> acListCache;

    private FundSelectionValidator() {
    }

    record AcInfo(Integer priority, List<String> tags) {
        boolean watchlisted() {
            return tags.stream().anyMatch(t -> t.toLowerCase().contains("watch"));
        }
    }

    /** One fund as seen by the logic: a held plan, a normalised Direct-Growth variant, or a recommended plan. */
    static final class Fund {
        long planId;
        long fundId;
        String name;
        String category;
        boolean index;
        boolean etf;
        boolean fof;
        boolean closedEnd;
        boolean solutionOriented;
        boolean directGrowth;
        Long amcId;
        Integer rating;
        Integer opinionTypeId;
        boolean ac;
        /** opinion-data is_provisional ("Good Start": fund under 3 years old, no star rating). */
        boolean provisional;
        /** opinion-data tag_name + Analyst's Choice list tags (e.g. "Diversified", "Value", "International"). */
        List<String> tags = new ArrayList<>();
        Integer acPriority;
        boolean watchlisted;
        double units;
        String heldAs;
        Integer heldPlanRating;
        Integer heldPlanOpinionTypeId;
        String classification;
        String heldPlanClassification;
        JsonNode raw;
        /** Row of fund-opinion-data/get-list for this plan (Queue B sort metrics live here). */
        JsonNode opinionRaw;
        int primaryCategoryId;
        boolean heldPlanAc;
        String exclusion;

        boolean good() {
            return opinionTypeId != null && opinionTypeId == GOOD_OPINION_TYPE_ID;
        }

        String slot() {
            String c = category == null ? "" : category.toLowerCase().trim();
            if (index && c.equals("large cap")) {
                return LARGE_CAP_INDEX_SLOT;
            }
            if (GROWTH_CATEGORIES.contains(c)) {
                return GROWTH_SLOT;
            }
            return c;
        }

        String label() {
            return name + " (plan " + planId + (heldAs != null ? ", held as " + heldAs : "") + ")";
        }
    }

    record Recommended(Fund fund, boolean isOwned, double firstMonthAmount, List<InvestmentLimits.Leg> legs) {
    }

    // =============================================================================================
    static List<BusinessRuleResult> validate(String portfolioBody, String plannerBody, Fetcher fetcher, String scenario) {
        List<BusinessRuleResult> out = new ArrayList<>();
        JsonNode portfolio = read(portfolioBody);
        JsonNode planner = read(plannerBody);
        // Monthly income plan (REGULAR-INCOME): response carries regular_income_funds_data
        JsonNode rif = planner.path("data").path("regular_income_funds_data");
        final boolean incomePlan = !rif.isMissingNode() && !rif.isNull() && rif.size() > 0;
        Map<Long, AcInfo> acList = acList(fetcher);

        // ---- held funds (only units > 0)
        List<Fund> held = new ArrayList<>();
        Map<Long, Double> weightByFund = new HashMap<>();
        Map<Long, String> skippedHolding = new HashMap<>();
        Map<Long, Double> weightByPlan = new HashMap<>();
        List<String> redeemedHoldings = new ArrayList<>();
        JsonNode fundData = portfolio.path("data").path("fund-data");
        if (fundData.isObject()) {
            for (JsonNode entry : fundData) {
                Fund f = parseFund(entry.path("plan-details"));
                f.units = entry.path("analysis-data").path("total_units").asDouble(0);
                weightByPlan.merge(f.planId, entry.path("analysis-data").path("weightage").asDouble(0), Double::sum);
                if (f.units <= 0) {
                    redeemedHoldings.add(f.name + " (plan " + f.planId + ") | units 0 - fully redeemed");
                }
                if (f.units <= 0 && !Boolean.parseBoolean(System.getProperty("selection.redeemedCountsAsHeld", "false"))) {
                    skippedHolding.putIfAbsent(f.fundId, "held with 0 units (fully redeemed): " + f.name);
                    continue;
                }
                held.add(f);
                weightByFund.merge(f.fundId, entry.path("analysis-data").path("weightage").asDouble(0), Double::sum);
            }
        }

        // held plans' own opinion/rating (opinion API), used for the held-plan rating check
        enrich(held, acList, fetcher);

        // ---- 5.2B variant normalisation: every held fund is represented by its Direct-Growth plan
        Map<Long, Fund> candidateByFund = new LinkedHashMap<>();
        for (Fund h : held) {
            if (candidateByFund.containsKey(h.fundId)) {
                continue;
            }
            if (h.etf) {
                continue; // ETFs are always excluded (5.2A) and have no Direct-Growth plan
            }
            Fund dg = h.directGrowth ? h : directGrowthOf(h, fetcher);
            if (dg == null) {
                skippedHolding.put(h.fundId, "Direct-Growth plan not resolvable for " + h.name);
                out.add(BusinessRuleResult.of(true, WARN + "Could not resolve Direct-Growth variant (5.2B) - fund not validated",
                        scenario, "Direct-Growth plan of fund_id " + h.fundId, h.label()));
                continue;
            }
            if (dg != h) {
                dg.heldAs = h.name;
                dg.heldPlanRating = h.rating;
                dg.heldPlanOpinionTypeId = h.opinionTypeId;
                dg.heldPlanClassification = h.classification;
                dg.heldPlanAc = h.ac;
            }
            candidateByFund.put(h.fundId, dg);
            skippedHolding.remove(h.fundId);
        }

        // ---- opinions + AC info for candidates
        enrich(new ArrayList<>(candidateByFund.values()), acList, fetcher);

        // ---- recommended funds
        List<Recommended> recommended = recommendedFunds(planner, fetcher, acList);
        if (recommended.isEmpty()) {
            out.add(BusinessRuleResult.of(true, WARN + "No recommended funds found in response - selection not validated", scenario, "funds", "none"));
            ReportExtras.put(plannerBody, holdingsText(held, weightByPlan, redeemedHoldings, candidateByFund), "");
            return out;
        }
        Set<String> slotsInPlan = recommended.stream().map(r -> r.fund().slot()).collect(Collectors.toSet());

        // ---- 5.6A investment limits (plan-txn-details) for recommended funds and held candidates
        List<Long> limitIds = new ArrayList<>();
        recommended.forEach(r -> limitIds.add(r.fund().planId));
        candidateByFund.values().forEach(c -> limitIds.add(c.planId));
        String limitsBody = null;
        try {
            limitsBody = fetcher.planTxnDetails(limitIds.stream().distinct().toList());
        } catch (RuntimeException e) {
            out.add(BusinessRuleResult.of(true, WARN + "Investment limits not loaded: " + e.getMessage(), scenario, "-", "-"));
        }
        final Map<Long, InvestmentLimits.Limits> limits = InvestmentLimits.parse(limitsBody);
        if (incomePlan) {
            for (Recommended r : recommended) {
                Fund f = r.fund();
                if ("growth".equals(f.slot()) && f.opinionRaw != null) {
                    // WARN only for now: tag data comes from opinion-data / Analyst's Choice list and may be incomplete
                    out.add(BusinessRuleResult.of(true, (hasTag(f, "diversified") ? "" : WARN)
                            + "Monthly income plan: growth fund should have the 'Diversified' tag (PM, 15-Oct-2026)", scenario,
                            "tag Diversified", f.label() + " tags=" + f.tags));
                }
            }
        }
        for (Recommended r : recommended) {
            InvestmentLimits.Limits l = limits.get(r.fund().planId);
            if (l == null) {
                continue;
            }
            String broken = InvestmentLimits.violation(l, r.legs());
            out.add(BusinessRuleResult.of(broken == null,
                    "Recommended fund must meet its own investment limits (5.6A, plan-txn-details)", scenario,
                    "all limits met", broken == null ? r.fund().label() : r.fund().label() + ": " + broken));
        }

        // ---- 5.2A exclusion filters
        for (Fund c : candidateByFund.values()) {
            c.exclusion = exclusionReason(c, weightByFund.getOrDefault(c.fundId, 0.0), slotsInPlan);
        }

        // ---- slot by slot, in descending order of amount (5.5)
        List<Recommended> ordered = new ArrayList<>(recommended);
        ordered.sort(Comparator.comparingDouble(Recommended::firstMonthAmount).reversed());
        Set<Long> used = new HashSet<>();
        int ownedExpected = 0;
        int redeemedShownAsOwned = 0;

        for (Recommended r : ordered) {
            Fund rf = r.fund();
            String slot = rf.slot();
            Fund heldCandidate = candidateByFund.get(rf.fundId);
            boolean heldEligible = heldCandidate != null && heldCandidate.exclusion == null;

            // (1) an excluded held fund must not be recommended
            if (heldCandidate != null && heldCandidate.exclusion != null) {
                boolean concentration = heldCandidate.exclusion.contains("% of total portfolio");
                if (rf.ac && !concentration) {
                    // On hold (QA, 15-Oct-2026): whether a held fund excluded for reasons other than the 15% rule
                    // (e.g. held plan rated 1-3 star) may come back through the Analyst's Choice list is not
                    // decided yet (09-Oct decision set aside). Reported as WARN only - never fails the row.
                    // PM decision (15-Oct-2026): a fund above 15% does NOT enter Queue C (handled below).
                    if (r.isOwned()) {
                        ownedExpected++;
                    }
                    out.add(BusinessRuleResult.of(true, WARN + "Excluded held fund came back via Analyst's Choice list"
                                    + " - rule on hold (not checked)", scenario,
                            rf.label() + " [held as " + (heldCandidate.heldAs != null ? heldCandidate.heldAs : rf.name)
                                    + "; excluded from portfolio pool: " + heldCandidate.exclusion + "]",
                            "is_owned=" + r.isOwned()));
                } else {
                    // above 15% (PM 15-Oct-2026: not in Queue C either) or not Analyst's Choice: must not be in the plan
                    out.add(BusinessRuleResult.of(false, "Excluded portfolio fund must not be recommended (5.2A)", scenario,
                            "not in plan (" + heldCandidate.exclusion + ")", rf.label()));
                    used.add(rf.fundId);
                    continue;
                }
            }

            // (2) is_owned must follow 5.2B / PRD 11.7 (variants count as owned)
            if (heldEligible) {
                ownedExpected++;
                out.add(BusinessRuleResult.of(r.isOwned(),
                        "is_owned must be true for a fund picked from the portfolio, incl. Regular/IDCW variants (5.2B, PRD 11.7)",
                        scenario, "is_owned=true for " + rf.label() + (heldCandidate.heldAs != null ? " [held as " + heldCandidate.heldAs + "]" : ""),
                        "is_owned=" + r.isOwned()));
            } else if (heldCandidate == null && r.isOwned()) {
                String why = skippedHolding.get(rf.fundId);
                if (why != null && why.startsWith("held with 0 units")) {
                    out.add(BusinessRuleResult.of(false,
                            "Fully redeemed fund (0 units) must not be shown as 'Already yours' (PM confirmed)", scenario,
                            "is_owned=false for " + rf.label(), "is_owned=true; " + why));
                    redeemedShownAsOwned++;
                } else if (why != null) {
                    out.add(BusinessRuleResult.of(true, WARN + "Ownership not verifiable: " + why, scenario, "-", rf.label()));
                } else {
                    out.add(BusinessRuleResult.of(false, "is_owned must be false for a fund the investor does not hold", scenario,
                            "is_owned=false for " + rf.label(), "is_owned=true"));
                }
            }

            // (3) queue precedence A > B > C; held funds that cannot take this slot's legs are validly skipped
            List<Fund> queueA = new ArrayList<>(queue(candidateByFund.values(), slot, used, true));
            List<Fund> queueB = new ArrayList<>(queue(candidateByFund.values(), slot, used, false));
            for (List<Fund> q : List.of(queueA, queueB)) {
                q.removeIf(c -> {
                    if (c.fundId == rf.fundId) {
                        return false;
                    }
                    String why = InvestmentLimits.violation(limits.get(c.planId), r.legs());
                    if (why == null) {
                        why = fundHouseBlock(c, rf, recommended);
                    }
                    // (v21) Removed the "Value funds are not used in the monthly income growth slot" rule: it came from a
                    // developer statement, not from the logic doc. Only the doc rule (5.4.3 prefer Diversified) is kept below.
                    if (why == null && incomePlan && "growth".equals(slot) && !hasTag(c, "diversified")) {
                        why = "monthly income plan: growth slot needs the 'Diversified' tag (PM, 15-Oct-2026); tags="
                                + c.tags;
                    }
                    if (why != null) {
                        out.add(BusinessRuleResult.of(true, WARN + "Held fund validly skipped for slot '" + slot
                                + "' - investment limit / fund-house / tag rule", scenario, c.label(), why));
                        return true;
                    }
                    return false;
                });
            }
            if (heldEligible && heldCandidate.ac) {
                Fund better = queueA.stream().filter(a -> a.fundId != rf.fundId)
                        .filter(a -> priority(a) < priority(heldCandidate)).findFirst().orElse(null);
                out.add(better == null
                        ? BusinessRuleResult.of(true, "Queue A pick for slot '" + slot + "'", scenario, "held AC fund", rf.label())
                        : BusinessRuleResult.of(true, WARN + "Higher-priority held AC fund exists for slot '" + slot
                                + "' (tag preference / restriction / fund-house rule may explain)", scenario, better.label(), rf.label()));
            } else if (heldEligible) {
                List<Fund> aAlternatives = queueA.stream().filter(a -> a.fundId != rf.fundId).toList();
                out.add(BusinessRuleResult.of(aAlternatives.isEmpty(),
                        "Queue A (held AC) must win over Queue B (held Good) for slot '" + slot + "' (5.3/5.5)", scenario,
                        aAlternatives.isEmpty() ? "Queue B pick allowed" : names(aAlternatives) + " (unless fund-house rule blocks it)",
                        rf.label()));
                if (aAlternatives.isEmpty() && queueB.size() > 1) {
                    out.add(QueueBSort.check(queueB, heldCandidate, slot, scenario));
                }
            } else {
                List<Fund> skipped = new ArrayList<>(queueA);
                skipped.addAll(queueB);
                out.add(BusinessRuleResult.of(skipped.isEmpty(),
                        "Held eligible funds must be used before Analyst's Choice master list for slot '" + slot + "' (5.5)", scenario,
                        skipped.isEmpty() ? "Queue C pick allowed" : names(skipped) + " (unless fund-house rule blocks it)",
                        rf.label()));
                // Planner (funds-buy/generate): funds outside the portfolio come from the Analyst's Choice list (Queue C).
                // PM exception (16-Oct-2026): for Conservative Hybrid and Dynamic Asset Allocation, if no Analyst's
                // Choice fund is available, a 'Good' opinion fund may be picked (portfolio-aware and fresh mode).
                boolean goodFallbackCategory = GOOD_FALLBACK_SLOTS.contains(slot);
                if (!rf.ac && goodFallbackCategory) {
                    out.add(BusinessRuleResult.of(rf.good() && !rf.watchlisted && !rf.etf,
                            "Conservative Hybrid / Dynamic Asset Allocation: non-Analyst's Choice fund must be 'Good' (PM exception, 16-Oct-2026)",
                            scenario, "opinion Good, not ETF", "opinion_type_id=" + rf.opinionTypeId + ", ETF=" + rf.etf + " - " + rf.label()));
                } else {
                    out.add(BusinessRuleResult.of(rf.ac && !rf.watchlisted && !rf.etf,
                            "Fund not in portfolio must be a non-watchlisted, non-ETF Analyst's Choice fund (Queue C, 5.3)", scenario,
                            "AC=true, watchlisted=false, ETF=false",
                            "AC=" + rf.ac + ", watchlisted=" + rf.watchlisted + ", ETF=" + rf.etf + " - " + rf.label()));
                }
            }
            used.add(rf.fundId);
        }

        // (4) count sentence (PRD 11.5)
        out.add(countNote(planner, ownedExpected, scenario));

        // (5) fund-house diversification (5.6B / 5.7)
        out.addAll(fundHouseRules(recommended, scenario));
        ReportExtras.put(plannerBody, holdingsText(held, weightByPlan, redeemedHoldings, candidateByFund),
                recommendedTagsText(recommended));
        return out;
    }

    // =============================================================================================
    static String exclusionReason(Fund c, double combinedWeight, Set<String> slotsInPlan) {
        if (!c.ac && !c.good()) {
            return "opinion is neither Analyst's Choice nor Good (opinion_type_id=" + c.opinionTypeId + ")";
        }
        if (c.etf) {
            return "ETF";
        }
        if (c.closedEnd) {
            return "closed-end fund";
        }
        if (c.solutionOriented) {
            return "solution-oriented (children/retirement) fund";
        }
        if (c.index && !(LARGE_CAP_INDEX_SLOT.equals(c.slot()) && slotsInPlan.contains(LARGE_CAP_INDEX_SLOT))) {
            return "index fund and the plan has no matching index slot";
        }
        // QA/PM rule (15-Oct-2026): a held "Good Start" (provisional) fund is used only if it is Analyst's Choice
        if (c.provisional && !c.ac) {
            return "provisional opinion (Good Start) and not Analyst's Choice";
        }
        if (c.good() && !c.ac && c.rating != null && c.rating >= 1 && c.rating <= 3) {
            return "Good but not AC and rated " + c.rating + " star";
        }
        // pseudocode applies exclusions to the HELD plan before adding its Direct-Growth variant.
        // v23: 5.2A rule 4 excludes a fund that is 'Good' AND NOT Analyst's Choice AND rated 1-3 star. The opinion
        // (incl. Analyst's Choice) belongs to the FUND, so if the fund's Direct-Growth plan is Analyst's Choice the
        // held Regular/IDCW plan's own rating does not exclude it (dev fix + analyst check, UAT 26-Oct-2026).
        boolean heldPlanGood = c.heldPlanOpinionTypeId != null
                ? c.heldPlanOpinionTypeId == GOOD_OPINION_TYPE_ID
                : "good".equalsIgnoreCase(c.heldPlanClassification); // opinion API often has no row for Regular/IDCW plans
        if (c.heldAs != null && heldPlanGood && !c.ac
                && !c.heldPlanAc && c.heldPlanRating != null && c.heldPlanRating >= 1 && c.heldPlanRating <= 3) {
            return "held plan (" + c.heldAs + ") is Good but not AC and rated " + c.heldPlanRating + " star";
        }
        if (c.ac && c.watchlisted) {
            return "Analyst's Choice but WATCHLISTED";
        }
        if (!c.index && !c.etf && !c.fof && combinedWeight >= CONCENTRATION_LIMIT_PERCENT) {
            return String.format("active fund is %.2f%% of total portfolio (>= %.0f%%)", combinedWeight, CONCENTRATION_LIMIT_PERCENT);
        }
        return null;
    }

    private static List<Fund> queue(Iterable<Fund> candidates, String slot, Set<Long> used, boolean analystsChoice) {
        List<Fund> q = new ArrayList<>();
        for (Fund c : candidates) {
            if (c.exclusion == null && !used.contains(c.fundId) && slot.equals(c.slot())
                    && (analystsChoice ? c.ac : (!c.ac && c.good()))) {
                q.add(c);
            }
        }
        q.sort(Comparator.comparingInt(FundSelectionValidator::priority));
        return q;
    }

    /**
     * Would putting held candidate {@code c} in place of {@code picked} break a fund-house rule (5.6B)?
     * Returns the reason, or null. Index funds are exempt. Used so a valid skip (e.g. SBI Contra skipped
     * because SBI Focused already fills the same growth slot) is not reported as a bug.
     */
    static boolean isValueCategory(Fund f) {
        String c = f.category == null ? "" : f.category.toLowerCase();
        return c.contains("value") || c.contains("contra");
    }

    // ---- report extras (v22): readable holdings and tags for the report ------------------------------------------

    private static String opinionText(Integer opinionTypeId) {
        if (opinionTypeId == null) {
            return "no opinion";
        }
        return opinionTypeId == GOOD_OPINION_TYPE_ID ? "Good" : "not Good (opinion type " + opinionTypeId + ")";
    }

    static String holdingsText(List<Fund> held, Map<Long, Double> weightByPlan, List<String> redeemed, Map<Long, Fund> candidateByFund) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (Fund h : held) {
            sb.append(i++).append(". ").append(h.name).append(" (plan ").append(h.planId).append(")")
              .append(" | units ").append(String.format("%.3f", h.units))
              .append(" | ").append(String.format("%.2f", weightByPlan.getOrDefault(h.planId, 0.0))).append("% of portfolio")
              .append(" | ").append(opinionText(h.opinionTypeId))
              .append(" | ").append(h.rating == null ? "unrated" : h.rating + " star")
              .append(" | Analyst's Choice: ").append(h.ac ? "yes" : "no");
            Fund dg = candidateByFund.get(h.fundId);
            if (dg != null && dg != h) {
                sb.append(" | Direct-G: ").append(dg.name).append(" (plan ").append(dg.planId).append("), AC ").append(dg.ac ? "yes" : "no")
                  .append(", ").append(opinionText(dg.opinionTypeId));
            }
            if (dg != null && dg.exclusion != null) {
                sb.append(" | EXCLUDED: ").append(dg.exclusion);
            }
            sb.append('\n');
        }
        for (String r : redeemed) {
            sb.append(i++).append(". ").append(r).append('\n');
        }
        return sb.length() == 0 ? "no fund holdings" : sb.toString().trim();
    }

    static String recommendedTagsText(List<Recommended> recommended) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (Recommended r : recommended) {
            Fund f = r.fund();
            sb.append(i++).append(". ").append(f.name).append(" (plan ").append(f.planId).append(")")
              .append(" | tags ").append(f.tags.isEmpty() ? "-" : String.join(", ", f.tags))
              .append(" | Analyst's Choice: ").append(f.ac ? "yes, priority " + (f.acPriority == null ? "-" : f.acPriority) : "no")
              .append(r.isOwned() ? " | Already yours" : "")
              .append('\n');
        }
        return sb.toString().trim();
    }

    static boolean hasTag(Fund f, String tag) {
        return f.tags.stream().anyMatch(t -> t.toLowerCase().contains(tag));
    }

    static String fundHouseBlock(Fund c, Fund picked, List<Recommended> plan) {
        if (c.index || c.amcId == null) {
            return null;
        }
        List<Fund> others = plan.stream().map(Recommended::fund)
                .filter(f -> f.planId != picked.planId && f.fundId != c.fundId && !f.index && f.amcId != null).toList();
        for (Fund o : others) {
            if (o.amcId.equals(c.amcId) && o.slot().equals(picked.slot())) {
                return "fund house already used in the same '" + picked.slot() + "' slot by " + o.name + " (5.6B rule 3)";
            }
        }
        boolean amcPresent = others.stream().anyMatch(o -> o.amcId.equals(c.amcId));
        if (!amcPresent) {
            return null;
        }
        if (plan.size() <= 4) {
            return "plan has <=4 funds and fund house already used (5.6B)";
        }
        // 5.6B rule 1: max 2 funds from the same fund house. Categories are filled in descending order of the
        // TOTAL amount to be invested (PM, 22-Oct-2026), so funds already holding the two places stay.
        long sameAmc = others.stream().filter(o -> o.amcId.equals(c.amcId)).count();
        if (sameAmc >= 2) {
            return "fund house already has 2 funds in the plan (5.6B rule 1; categories filled by total amount, PM 22-Oct-2026)";
        }
        Map<Long, Long> counts = others.stream().collect(Collectors.groupingBy(o -> o.amcId, Collectors.counting()));
        boolean otherDuplicate = counts.entrySet().stream().anyMatch(e -> e.getValue() > 1 && !e.getKey().equals(c.amcId));
        return otherDuplicate ? "another fund house is already duplicated in this plan (5.6B rule 1)" : null;
    }

    private static int priority(Fund f) {
        return f.acPriority == null ? Integer.MAX_VALUE : f.acPriority;
    }

    private static BusinessRuleResult countNote(JsonNode planner, int ownedExpected, String scenario) {
        JsonNode note = planner.path("data").path("plan_note_data");
        String text = note.path("text_fmt").isNull() ? "" : note.path("text_fmt").asText("");
        Matcher m = OWN_COUNT.matcher(text);
        Integer shown = m.find() ? Integer.valueOf(m.group(1)) : null;
        if (ownedExpected == 0) {
            return BusinessRuleResult.of(shown == null, "'You already own N funds' must not be shown when no portfolio fund is picked (PRD 11.5)",
                    scenario, "no 'already own' count", text.isEmpty() ? "<none>" : text);
        }
        return BusinessRuleResult.of(shown != null && shown == ownedExpected,
                "'You already own N fund(s)' must equal the number of funds picked from the portfolio (PRD 11.5)",
                scenario, ownedExpected, shown == null ? "<no count shown>" : shown);
    }

    private static List<BusinessRuleResult> fundHouseRules(List<Recommended> recs, String scenario) {
        List<BusinessRuleResult> out = new ArrayList<>();
        int n = recs.size();
        Map<Long, List<Recommended>> byAmc = new HashMap<>();
        for (Recommended r : recs) {
            if (!r.fund().index && r.fund().amcId != null) {
                byAmc.computeIfAbsent(r.fund().amcId, k -> new ArrayList<>()).add(r);
            }
        }
        List<Long> duplicated = byAmc.entrySet().stream().filter(e -> e.getValue().size() > 1).map(Map.Entry::getKey).toList();
        if (n <= 4) {
            out.add(BusinessRuleResult.of(duplicated.isEmpty(), "Plan with <=4 funds must not repeat a fund house, index funds exempt (5.6B)",
                    scenario, "no repeated AMC", duplicated.isEmpty() ? "none" : amcText(duplicated, byAmc)));
        } else {
            out.add(BusinessRuleResult.of(duplicated.size() <= 1, "Plan with >=5 funds may duplicate at most one fund house (5.6B rule 1)",
                    scenario, "<=1 duplicated AMC", duplicated.isEmpty() ? "none" : amcText(duplicated, byAmc)));
            double total = recs.stream().mapToDouble(Recommended::firstMonthAmount).sum();
            for (Long amc : duplicated) {
                double share = byAmc.get(amc).stream().mapToDouble(Recommended::firstMonthAmount).sum();
                out.add(BusinessRuleResult.of(total <= 0 || share / total <= 0.5,
                        "One fund house must not exceed 50% of first-month total (5.6B rule 2)", scenario,
                        "<= 50%", String.format("AMC %d = %.1f%%", amc, total <= 0 ? 0 : share * 100 / total)));
            }
        }
        for (Long amc : duplicated) {
            Set<String> slots = new HashSet<>();
            boolean sameSlot = false;
            for (Recommended r : byAmc.get(amc)) {
                sameSlot |= !slots.add(r.fund().slot());
            }
            out.add(BusinessRuleResult.of(!sameSlot, "No fund-house repetition within the same category slot (5.6B rule 3)",
                    scenario, "different AMCs within a slot", amcText(List.of(amc), byAmc)));
        }
        return out;
    }

    private static String amcText(List<Long> amcs, Map<Long, List<Recommended>> byAmc) {
        return amcs.stream().map(a -> "AMC " + a + ": " + byAmc.get(a).stream().map(r -> r.fund().name).collect(Collectors.joining(", ")))
                .collect(Collectors.joining("; "));
    }

    private static String names(List<Fund> funds) {
        return funds.stream().map(Fund::label).collect(Collectors.joining(", "));
    }

    // =============================================================================================
    private static List<Recommended> recommendedFunds(JsonNode planner, Fetcher fetcher, Map<Long, AcInfo> acList) {
        List<JsonNode> nodes = new ArrayList<>();
        collectFundNodes(planner.path("data").path("funds_data"), nodes);
        List<Recommended> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        List<Fund> funds = new ArrayList<>();
        for (JsonNode n : nodes) {
            long planId = n.path("plan_data").path("plan_id").asLong(0);
            if (planId == 0 || !seen.add(planId)) {
                continue;
            }
            JsonNode detail = read(fetcher.fundDetails(planId)).path("data").path(0);
            Fund f = detail.isMissingNode() ? new Fund() : parseFund(detail);
            f.planId = planId;
            if (f.name == null) {
                f.name = n.path("plan_data").path("name").asText("?");
            }
            funds.add(f);
            out.add(new Recommended(f, n.path("is_owned").asBoolean(false), n.path("first_month_amount").asDouble(0),
                    InvestmentLimits.legs(n)));
        }
        enrich(funds, acList, fetcher);
        return out;
    }

    /** Finds every object under funds_data that carries plan_data + is_owned (handles income/growth sections). */
    private static void collectFundNodes(JsonNode node, List<JsonNode> out) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isObject() && node.has("plan_data") && node.has("is_owned")) {
            out.add(node);
            return;
        }
        if (node.isObject() || node.isArray()) {
            for (JsonNode child : node) {
                collectFundNodes(child, out);
            }
        }
    }

    private static void enrich(List<Fund> funds, Map<Long, AcInfo> acList, Fetcher fetcher) {
        if (funds.isEmpty()) {
            return;
        }
        List<Long> ids = funds.stream().map(f -> f.planId).distinct().toList();
        Map<Long, JsonNode> opinions = new HashMap<>();
        for (JsonNode o : read(fetcher.fundOpinions(ids)).path("data")) {
            opinions.put(o.path("plan_id").asLong(), o);
        }
        for (Fund f : funds) {
            JsonNode o = opinions.get(f.planId);
            if (o != null) {
                if (!o.path("opinion_type_id").isNull() && !o.path("opinion_type_id").isMissingNode()) {
                    f.opinionTypeId = o.path("opinion_type_id").asInt();
                }
                if (!o.path("vr_rating").isNull() && !o.path("vr_rating").isMissingNode()) {
                    f.rating = o.path("vr_rating").asInt();
                }
                f.ac = o.path("is_analyst_pick").asBoolean(false);
                f.provisional = o.path("is_provisional").asBoolean(false);
                for (JsonNode t : o.path("tag_name")) {
                    if (!f.tags.contains(t.asText())) {
                        f.tags.add(t.asText());
                    }
                }
                f.opinionRaw = o;
            }
            AcInfo ac = acList.get(f.planId);
            if (ac != null) {
                f.ac = true;
                f.acPriority = ac.priority();
                f.watchlisted = ac.watchlisted();
                for (String t : ac.tags()) {
                    if (!f.tags.contains(t)) {
                        f.tags.add(t);
                    }
                }
            }
        }
    }

    private static Fund directGrowthOf(Fund held, Fetcher fetcher) {
        for (String filter : List.of(
                "{\"filters\":[{\"fund_id\":{\"=\":[" + held.fundId + "]}}]}",
                "{\"filters\":[{\"reg_plan_id\":{\"=\":[" + held.planId + "]}}]}")) {
            try {
                for (JsonNode p : read(fetcher.fundsByFilter(encodeFilter(filter))).path("data")) {
                    Fund f = parseFund(p);
                    if (f.fundId == held.fundId && f.directGrowth) {
                        return f;
                    }
                }
            } catch (RuntimeException ignored) {
                // try the next filter shape
            }
        }
        return null;
    }

    static Fund parseFund(JsonNode d) {
        Fund f = new Fund();
        f.planId = d.path("plan_id").asLong(0);
        f.fundId = d.path("fund_id").asLong(0);
        f.name = text(d, "short_name", text(d, "composite_short_name", text(d, "scheme_name", null)));
        String cat = text(d, "secondary_category_name", null);
        if (cat == null) {
            String full = text(d, "category_name", "");
            cat = full.contains(":") ? full.substring(full.indexOf(':') + 1).trim() : full;
        }
        f.category = cat;
        f.index = d.path("is_index_fund").asBoolean(false);
        f.etf = d.path("is_etf_plan").asBoolean(false);
        f.fof = d.path("is_fof").asBoolean(false);
        String maturity = text(d, "maturity_date", null);
        f.closedEnd = maturity != null && !maturity.isBlank();
        String fullCat = text(d, "category_name", "").toLowerCase();
        f.solutionOriented = fullCat.contains("solution") || fullCat.contains("children") || fullCat.contains("retirement");
        boolean growthOption = "growth".equalsIgnoreCase(text(d, "plan_name", "")) || d.path("cumulative").asBoolean(false);
        f.directGrowth = d.path("is_direct_plan").asBoolean(false) && growthOption;
        f.amcId = d.path("amc_id").isMissingNode() || d.path("amc_id").isNull() ? null : d.path("amc_id").asLong();
        f.rating = d.path("rating").isMissingNode() || d.path("rating").isNull() ? null : d.path("rating").asInt();
        f.classification = text(d, "fund_classification_name", null);
        f.raw = d;
        f.primaryCategoryId = d.path("primary_category_id").asInt(0);
        return f;
    }

    private static Map<Long, AcInfo> acList(Fetcher fetcher) {
        Map<Long, AcInfo> cached = acListCache;
        if (cached != null) {
            return cached;
        }
        Map<Long, AcInfo> map = new HashMap<>();
        for (JsonNode e : read(fetcher.analystPicks()).path("data")) {
            List<String> tags = new ArrayList<>();
            for (JsonNode t : e.path("tag_name")) {
                tags.add(t.asText());
            }
            Integer prio = e.path("priority_order").isNull() || e.path("priority_order").isMissingNode() ? null : e.path("priority_order").asInt();
            map.put(e.path("plan_id").asLong(), new AcInfo(prio, tags));
        }
        acListCache = map;
        return map;
    }

    /** Same encoding as the backend's filter URLs: zlib-deflate, then URL-safe Base64 without padding. */
    static String encodeFilter(String json) {
        Deflater deflater = new Deflater();
        deflater.setInput(json.getBytes(StandardCharsets.UTF_8));
        deflater.finish();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[512];
        while (!deflater.finished()) {
            int n = deflater.deflate(buffer);
            bytes.write(buffer, 0, n);
        }
        deflater.end();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
    }

    private static String text(JsonNode d, String field, String fallback) {
        JsonNode v = d.path(field);
        return v.isMissingNode() || v.isNull() ? fallback : v.asText();
    }

    private static JsonNode read(String body) {
        try {
            String json = body == null ? "{}" : (body.startsWith("\uFEFF") ? body.substring(1) : body);
            return MAPPER.readTree(json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            throw new IllegalStateException("Fund selection validator: unparseable JSON - " + e.getMessage(), e);
        }
    }
}

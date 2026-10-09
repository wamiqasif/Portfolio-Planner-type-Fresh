package com.org.apitests.portfolio;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Queue B ('Good' funds in existing portfolio) sort order from the fund-assignment logic doc,
 * section 5.3, table "Sort by the following fields, in this exact sequence for different fund types".
 * Each next priority is only a tie-breaker for the previous one.
 *
 * <pre>
 * Fund type                                   Priority 1                     Priority 2                      Priority 3
 * Active equity/hybrid, active FoFs, ELSS     1-yr alpha (higher)            Consistency score (higher)      3-yr alpha (higher)
 *   (except arbitrage and international)
 * International equity                        Nasdaq 100 first               Median 3-yr alpha (higher)      Median 3m TER (lower)
 * Hybrid arbitrage                            Composite VR rating (higher)   Median 3m TER (lower)           -
 * Equity/hybrid/commodity index, passive FoF  1-yr tracking diff (higher)    Median 3m TER (lower)           1-yr tracking error (lower)
 * Active debt, active debt FoFs               Composite VR rating (higher)   Return consistency (higher)     Median 3m TER (lower)
 * Debt index, TMFs, passive debt FoFs         Avg net YTM 3m (higher)        Median 3m TER (lower)           Avg AUM 6m (higher)
 * </pre>
 *
 * <p>Field names are not documented, so each metric is looked up in the fund JSON (user-portfolio
 * plan-details or /funds response) by name pattern. A known field name can be forced with a system
 * property, e.g. {@code -Dqueueb.field.alpha1y=one_year_alpha}. When a metric is missing the check is
 * reported as {@code [WARN]} with the field it could not find, never as FAIL.
 */
final class QueueBSort {

    private static final String WARN = "[WARN] ";

    enum Metric {
        ALPHA_1Y("alpha1y", List.of("one_year_alpha"), "(?=.*alpha)(?=.*(1y|1_y|1yr|1_yr|one_year|1_year|year_1|alpha_1(?!\\d)))(?!.*(3y|3_y|3yr|three|3_year|5y)).*"),
        ALPHA_3Y("alpha3y", List.of("three_year_alpha"), "(?=.*alpha)(?=.*(3y|3_y|3yr|3_yr|three_year|3_year|year_3|alpha_3(?!\\d))).*"),
        MEDIAN_ALPHA_3Y("medianalpha3y", List.of("median_3y_alpha"), ".*median.*3y.*alpha.*"),
        CONSISTENCY("consistency", List.of("consistency_score"), "consistency_score"),
        RETURN_CONSISTENCY("returnconsistency", List.of("return_consistency_vs_median_1y"), ".*return.*consistency.*|.*consistency.*return.*"),
        TER("ter", List.of("median_ter_3m", "avg_ter_3m"), ".*(median.*ter|ter_3m|3m_ter|total_expense_ratio|expense_ratio).*"),
        TRACKING_DIFFERENCE("trackingdiff", List.of("tracking_difference_6m_avg"), ".*tracking.*diff.*"),
        TRACKING_ERROR("trackingerror", List.of("tracking_error_1y"), ".*tracking.*error.*"),
        COMPOSITE_RATING("composite", List.of("composite_vr_score"), ".*composite.*(rating|score).*"),
        NET_YTM("ytm", List.of("avg_net_ytm_3m"), ".*ytm.*"),
        AUM("aum", List.of("avg_aum_6m"), ".*aum.*");

        final String key;
        /** Exact field names in fund-opinion-data/get-list (confirmed on QA, Oct 2026), tried first. */
        final List<String> fields;
        final Pattern pattern;

        Metric(String key, List<String> fields, String regex) {
            this.key = key;
            this.fields = fields;
            this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        }
    }

    /** One sort level: how to read the value, and whether higher is better. */
    record Level(String label, Function<FundSelectionValidator.Fund, Double> value, boolean higherFirst, Metric metric) {
    }

    private QueueBSort() {
    }

    static String fundType(FundSelectionValidator.Fund f) {
        String c = f.category == null ? "" : f.category.toLowerCase(Locale.ROOT);
        boolean debt = f.primaryCategoryId == 2;
        if (f.etf) {
            return "ETF";
        }
        if (f.index || (f.fof && !c.contains("international"))) {
            return debt ? "Debt index / passive debt FoF" : "Index fund / passive FoF";
        }
        if (c.contains("international") || c.contains("global")) {
            return "International equity";
        }
        if (c.contains("arbitrage")) {
            return "Hybrid arbitrage";
        }
        if (debt) {
            return "Active debt";
        }
        return "Active equity / hybrid / ELSS";
    }

    static List<Level> levels(String type) {
        List<Level> l = new ArrayList<>();
        switch (type) {
            case "International equity" -> {
                l.add(new Level("Underlying index Nasdaq 100 first", QueueBSort::nasdaqFlag, true, null));
                l.add(metric("Median 3-yr alpha", Metric.MEDIAN_ALPHA_3Y, true));
                l.add(metric("Median 3-month expense ratio", Metric.TER, false));
            }
            case "Hybrid arbitrage" -> {
                l.add(metric("Composite VR rating score", Metric.COMPOSITE_RATING, true));
                l.add(metric("Median 3-month expense ratio", Metric.TER, false));
            }
            case "Index fund / passive FoF" -> {
                l.add(metric("1-yr tracking difference", Metric.TRACKING_DIFFERENCE, true));
                l.add(metric("Median 3-month expense ratio", Metric.TER, false));
                l.add(metric("1-yr tracking error", Metric.TRACKING_ERROR, false));
            }
            case "Active debt" -> {
                l.add(metric("Composite VR rating score", Metric.COMPOSITE_RATING, true));
                l.add(metric("Return consistency", Metric.RETURN_CONSISTENCY, true));
                l.add(metric("Median 3-month expense ratio", Metric.TER, false));
            }
            case "Debt index / passive debt FoF" -> {
                l.add(metric("Average net YTM (3 months)", Metric.NET_YTM, true));
                l.add(metric("Median 3-month expense ratio", Metric.TER, false));
                l.add(metric("Average AUM (6 months)", Metric.AUM, true));
            }
            default -> {
                l.add(metric("1-yr alpha", Metric.ALPHA_1Y, true));
                l.add(metric("Consistency score", Metric.CONSISTENCY, true));
                l.add(metric("3-yr alpha", Metric.ALPHA_3Y, true));
            }
        }
        return l;
    }

    private static Level metric(String label, Metric m, boolean higherFirst) {
        return new Level(label, f -> {
            Double v = value(f.opinionRaw, m);
            return v != null ? v : value(f.raw, m);
        }, higherFirst, m);
    }

    /**
     * Expected winner among the held 'Good' candidates of one slot, or a WARN when a needed metric is missing.
     * {@code picked} is the fund the planner actually chose (one of the candidates).
     */
    static BusinessRuleResult check(List<FundSelectionValidator.Fund> candidates, FundSelectionValidator.Fund picked,
                                    String slot, String scenario) {
        String type = fundType(picked);
        String rule = "Queue B sort for slot '" + slot + "' (" + type + ", logic 5.3)";
        List<FundSelectionValidator.Fund> remaining = new ArrayList<>(candidates);
        List<String> trail = new ArrayList<>();
        for (Level level : levels(type)) {
            Map<FundSelectionValidator.Fund, Double> values = new java.util.LinkedHashMap<>();
            List<String> missing = new ArrayList<>();
            for (FundSelectionValidator.Fund f : remaining) {
                Double v = level.value().apply(f);
                if (v == null) {
                    missing.add(f.name);
                } else {
                    values.put(f, v);
                }
            }
            if (!missing.isEmpty()) {
                String field = level.metric() == null ? "" : " - field not found (pass -Dqueueb.field." + level.metric().key + "=<name>)";
                return BusinessRuleResult.of(true, WARN + rule + " not verifiable: '" + level.label() + "' missing for "
                        + String.join(", ", missing) + field, scenario, describe(candidates, trail), picked.label());
            }
            double best = level.higherFirst()
                    ? values.values().stream().mapToDouble(Double::doubleValue).max().orElse(0)
                    : values.values().stream().mapToDouble(Double::doubleValue).min().orElse(0);
            trail.add(level.label() + ": " + values.entrySet().stream()
                    .map(e -> e.getKey().name + "=" + fmt(e.getValue())).collect(Collectors.joining(", ")));
            remaining = values.entrySet().stream().filter(e -> Math.abs(e.getValue() - best) < 1e-9)
                    .map(Map.Entry::getKey).collect(Collectors.toList());
            if (remaining.size() == 1) {
                FundSelectionValidator.Fund winner = remaining.get(0);
                return BusinessRuleResult.of(winner.fundId == picked.fundId, rule, scenario,
                        winner.label() + " [" + String.join(" | ", trail) + "]", picked.label());
            }
        }
        boolean pickedAmongBest = remaining.stream().anyMatch(f -> f.fundId == picked.fundId);
        return BusinessRuleResult.of(pickedAmongBest, rule + " - full tie after all priorities", scenario,
                "any of " + remaining.stream().map(f -> f.name).collect(Collectors.joining(", ")) + " [" + String.join(" | ", trail) + "]",
                picked.label());
    }

    private static String describe(List<FundSelectionValidator.Fund> c, List<String> trail) {
        return c.stream().map(f -> f.name).collect(Collectors.joining(", ")) + (trail.isEmpty() ? "" : " [" + String.join(" | ", trail) + "]");
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.4f", v);
    }

    /** 1 when the fund's name or benchmark mentions Nasdaq 100, else 0. */
    private static Double nasdaqFlag(FundSelectionValidator.Fund f) {
        String text = (f.name + " " + textOf(f.raw, "scheme_name") + " " + textOf(f.raw, "benchmark_name")
                + " " + textOf(f.raw, "stated_index_name")).toLowerCase(Locale.ROOT);
        return text.contains("nasdaq") ? 1.0 : 0.0;
    }

    /** Finds a numeric metric in the fund JSON: explicit field override first, then the first key matching the pattern. */
    static Double value(JsonNode raw, Metric m) {
        if (raw == null) {
            return null;
        }
        String forced = System.getProperty("queueb.field." + m.key);
        if (forced != null && !forced.isBlank()) {
            return number(raw.path(forced));
        }
        for (String field : m.fields) {
            Double v = number(raw.path(field));
            if (v != null) {
                return v;
            }
        }
        Iterator<Map.Entry<String, JsonNode>> it = raw.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            if (m.pattern.matcher(e.getKey()).matches() && !e.getKey().toLowerCase(Locale.ROOT).endsWith("_as_on")) {
                Double v = number(e.getValue());
                if (v != null) {
                    return v;
                }
            }
        }
        return null;
    }

    private static Double number(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return null;
        }
        if (n.isNumber()) {
            return n.asDouble();
        }
        try {
            return Double.parseDouble(n.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String textOf(JsonNode raw, String field) {
        return raw == null || raw.path(field).isMissingNode() || raw.path(field).isNull() ? "" : raw.path(field).asText();
    }
}

package com.org.apitests.portfolio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Investment limits of a plan from {@code /api/v1/gql/component?screen=fund-portfolio-planner-edit
 * &componentName=plan-txn-details-alt} and the check of the planner's legs against them (logic doc 5.6A).
 *
 * <p>Why: the planner skips a fund when the slot's investment breaks the fund's limits, e.g. a lumpsum
 * spread over 6 months for a fund that needs at least 12 SIP instalments (Navi Nifty 50 Index, #39178),
 * or a 6-month tax-saving SIP for a fund that needs 36 (Nippon India ELSS, #39206). Without this check
 * the validator reports such valid skips as bugs, and cannot catch a recommended fund that breaks its
 * own limits (SBI Nifty Index: min 12 instalments, given a 6-month spread).
 */
final class InvestmentLimits {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private InvestmentLimits() {
    }

    /** One investment leg of a recommended fund, from funds_data[].legs[]. */
    record Leg(String type, double amount, Integer months) {
        boolean sip() {
            return "sip".equalsIgnoreCase(type);
        }
    }

    record Limits(long planId, String name, Boolean allowSip, Boolean allowLumpsum,
                  Integer minSipInstalments, Integer maxSipInstalments,
                  Double minSipAmount, Double maxSipAmount,
                  Double minLumpsumAmount, Double maxLumpsumAmount) {
    }

    /** Parses the plan-txn-details body into planId -> limits. Empty map when body is null/unreadable. */
    static Map<Long, Limits> parse(String body) {
        Map<Long, Limits> out = new HashMap<>();
        if (body == null || body.isBlank()) {
            return out;
        }
        try {
            for (JsonNode d : MAPPER.readTree(body).path("data")) {
                long id = d.path("plan_id").asLong(0);
                if (id == 0) {
                    continue;
                }
                out.put(id, new Limits(id, d.path("name").asText(""),
                        bool(d, "is_allow_sip"), bool(d, "is_allow_lumpsum"),
                        integer(d, "min_sip_installment"), integer(d, "max_sip_installment"),
                        number(d, "min_sip_investment"), number(d, "max_sip_investment"),
                        number(d, "min_lumpsum_investment"), number(d, "max_lumpsum_investment")));
            }
        } catch (Exception e) {
            return out;
        }
        return out;
    }

    /** Legs of one funds_data entry. */
    static List<Leg> legs(JsonNode fundNode) {
        List<Leg> out = new ArrayList<>();
        for (JsonNode l : fundNode.path("legs")) {
            JsonNode m = l.path("duration_months");
            out.add(new Leg(l.path("type").asText(""), l.path("amount").asDouble(0),
                    m.isMissingNode() || m.isNull() ? null : m.asInt()));
        }
        return out;
    }

    /** First broken limit for these legs, or null when every limit is met (or limits unknown). */
    static String violation(Limits l, List<Leg> legs) {
        if (l == null || legs == null || legs.isEmpty()) {
            return null;
        }
        double monthlySip = 0;
        for (Leg leg : legs) {
            if (leg.sip()) {
                monthlySip += leg.amount();
                if (Boolean.FALSE.equals(l.allowSip())) {
                    return "SIP not allowed";
                }
                if (leg.months() != null && l.minSipInstalments() != null && leg.months() < l.minSipInstalments()) {
                    return "needs min " + l.minSipInstalments() + " SIP instalments, plan gives " + leg.months()
                            + " (Rs " + fmt(leg.amount()) + " x " + leg.months() + ")";
                }
                if (leg.months() != null && l.maxSipInstalments() != null && leg.months() > l.maxSipInstalments()) {
                    return "allows max " + l.maxSipInstalments() + " SIP instalments, plan gives " + leg.months();
                }
                if (l.minSipAmount() != null && leg.amount() < l.minSipAmount()) {
                    return "min SIP Rs " + fmt(l.minSipAmount()) + ", plan gives Rs " + fmt(leg.amount());
                }
            } else {
                if (Boolean.FALSE.equals(l.allowLumpsum())) {
                    return "lumpsum not allowed";
                }
                if (l.minLumpsumAmount() != null && leg.amount() < l.minLumpsumAmount()) {
                    return "min lumpsum Rs " + fmt(l.minLumpsumAmount()) + ", plan gives Rs " + fmt(leg.amount());
                }
                if (l.maxLumpsumAmount() != null && leg.amount() > l.maxLumpsumAmount()) {
                    return "max lumpsum Rs " + fmt(l.maxLumpsumAmount()) + ", plan gives Rs " + fmt(leg.amount());
                }
            }
        }
        if (l.maxSipAmount() != null && monthlySip > l.maxSipAmount()) {
            return "combined monthly SIP Rs " + fmt(monthlySip) + " above max Rs " + fmt(l.maxSipAmount());
        }
        return null;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static Boolean bool(JsonNode d, String f) {
        JsonNode v = d.path(f);
        return v.isMissingNode() || v.isNull() ? null : v.asBoolean(false);
    }

    private static Integer integer(JsonNode d, String f) {
        JsonNode v = d.path(f);
        return v.isMissingNode() || v.isNull() ? null : v.asInt();
    }

    private static Double number(JsonNode d, String f) {
        JsonNode v = d.path(f);
        return v.isMissingNode() || v.isNull() ? null : v.asDouble(0);
    }
}

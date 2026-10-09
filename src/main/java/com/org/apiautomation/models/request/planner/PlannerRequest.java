package com.org.apiautomation.models.request.planner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON request body for {@code POST /api/v1/planner/generate}.
 *
 * <p><b>Contract history:</b> {@code docs/test-plans/portfolio-planner-apis.md}'s
 * originally-captured curls sent every field as a query string param on the
 * {@code POST} with no body. A later, directly-confirmed real curl (Content-Type:
 * application/json, all fields in a JSON {@code --data} body, no
 * {@code investor_id_dev}) shows the current contract is a JSON body instead
 * - {@code PlannerService.generate} sends {@link #toBody()}'s map as the
 * request body, not as query params. Kept as a generic string-keyed map
 * either way, so this class didn't need to change shape - only how {@code
 * PlannerService} uses it did.
 *
 * <p>A mutable builder rather than a record: boundary/negative tests need to
 * override a single field, or remove one entirely to simulate a missing
 * mandatory parameter ({@link #without(String)}) - a record's
 * all-or-nothing constructor can't express "everything from this baseline
 * except one field is now absent" without a full copy-constructor per
 * field. Values are kept as {@code Object} (not typed fields) specifically
 * so a negative test can put a deliberately invalid value of any shape
 * (e.g. a negative number, an out-of-enum string) into any param.
 */
public final class PlannerRequest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Map<String, Object> params = new LinkedHashMap<>();

    private PlannerRequest() {
    }

    public static PlannerRequest builder() {
        return new PlannerRequest();
    }

    /** Shallow copy - lets a test start from a known-good baseline and override/remove just what it's testing. */
    public PlannerRequest copy() {
        PlannerRequest copy = new PlannerRequest();
        copy.params.putAll(this.params);
        return copy;
    }

    public PlannerRequest investorIdDev(Object value) {
        return set("investor_id_dev", value);
    }

    public PlannerRequest goalType(Object value) {
        return set("goal_type", value);
    }

    public PlannerRequest txnOptions(Object value) {
        return set("txn_options", value);
    }

    public PlannerRequest durationType(Object value) {
        return set("duration_type", value);
    }

    public PlannerRequest investmentDuration(Object value) {
        return set("investment_duration", value);
    }

    public PlannerRequest monthlyAmount(Object value) {
        return set("monthly_amount", value);
    }

    public PlannerRequest lumpsumAmount(Object value) {
        return set("lumpsum_amount", value);
    }

    public PlannerRequest labelId(Object value) {
        return set("label_id", value);
    }

    public PlannerRequest userId(Object value) {
        return set("user_id", value);
    }

    public PlannerRequest riskProfileId(Object value) {
        return set("risk_profile_id", value);
    }

    public PlannerRequest annualIncomeRange(Object value) {
        return set("annual_income_range", value);
    }

    public PlannerRequest investorId(Object value) {
        return set("investor_id", value);
    }

    public PlannerRequest awareType(Object value) {
        return set("aware_type", value);
    }

    /** Applicable only when {@code goal_type=REGULAR-INCOME} - omit entirely (never set to {@code null}/{@code 0}) for every other goal type. */
    public PlannerRequest accumulatedAmount(Object value) {
        return set("accumulated_amount", value);
    }

    /** Applicable only when {@code goal_type=TAX-SAVINGS} - omit entirely (never set to {@code null}/{@code 0}) for every other goal type. */
    public PlannerRequest taxSavingAmount(Object value) {
        return set("tax_saving_amount", value);
    }

    /**
     * Applicable only when {@code goal_type=REGULAR-INCOME} - omit entirely (never set to
     * {@code null}/{@code 0}) for every other goal type. Mandatory there: a positive number,
     * or the backend returns HTTP 422. Documented UI range 500 - 500,000, but NOT
     * backend-enforced (confirmed empirically - see {@code LabelResolutionDiagnostic
     * .neededAnnualAmountLiveProbe}: 499, 500001, and values far past 500,000 are all accepted).
     */
    public PlannerRequest neededAnnualAmount(Object value) {
        return set("needed_annual_amount", value);
    }

    public Object get(String key) {
        return params.get(key);
    }

    /** True iff the outgoing body will carry this key at all - distinct from {@code get(key) != null}, since a key is never set to a literal {@code null} in this class (see {@link #without(String)} for "not present"). */
    public boolean has(String key) {
        return params.containsKey(key);
    }

    /** Removes a param entirely - for "missing mandatory parameter" negative tests, distinct from setting it to null/blank. */
    public PlannerRequest without(String key) {
        params.remove(key);
        return this;
    }

    public Map<String, Object> toBody() {
        return new LinkedHashMap<>(params);
    }

    /** The exact outgoing JSON body as a string - for report/diagnostic use (e.g. a "Description" report column); not used on the actual send path, which sends {@link #toBody()}'s map directly. */
    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize PlannerRequest to JSON", e);
        }
    }

    private PlannerRequest set(String key, Object value) {
        params.put(key, value);
        return this;
    }
}

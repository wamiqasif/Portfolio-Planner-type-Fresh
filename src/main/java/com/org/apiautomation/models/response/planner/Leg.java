package com.org.apiautomation.models.response.planner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One SIP or one-time investment instruction within a fund's recommendation.
 * {@code type} is {@code "sip"} or {@code "one_time"} - used throughout the
 * validators instead of {@code typeFmt} (a display string, not a stable
 * contract value).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Leg(
        String type,
        @JsonProperty("type_fmt") String typeFmt,
        Double amount,
        @JsonProperty("amount_fmt") String amountFmt,
        @JsonProperty("duration_months") Integer durationMonths,
        @JsonProperty("duration_fmt") String durationFmt,
        @JsonProperty("is_recurring") boolean isRecurring,
        @JsonProperty("frequency_fmt") String frequencyFmt) {

    public boolean isSip() {
        return "sip".equals(type);
    }

    public boolean isOneTime() {
        return "one_time".equals(type);
    }
}

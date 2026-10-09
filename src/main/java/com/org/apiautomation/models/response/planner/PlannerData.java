package com.org.apiautomation.models.response.planner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The {@code data} object of a Planner Generate response.
 *
 * <p><b>Deliberately not mapped here, per the agreed exclusion list:</b>
 * {@code port_builder_id}, {@code parent_port_builder_id},
 * {@code recommend_reason_data}, {@code recommend_reason} - none of these
 * fields exist as record components, so Jackson never even attempts to read
 * them (harmless either way thanks to {@code @JsonIgnoreProperties}, but
 * leaving them out entirely makes the exclusion visible in the model itself
 * rather than just in test logic).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlannerData(
        @JsonProperty("aware_type") String awareType,
        @JsonProperty("inpage_title") String inpageTitle,
        InvestorInfo investor,
        @JsonProperty("input_summary_data") InputSummaryData inputSummaryData,
        @JsonProperty("funds_data") FundsData fundsData,
        @JsonProperty("breakdown_funds_data") BreakdownFundsData breakdownFundsData,
        @JsonProperty("plan_note_data") PlanNoteData planNoteData,
        @JsonProperty("disclaimer_data") DisclaimerData disclaimerData) {
}

package com.org.apiautomation.models.response.planner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The {@code plan_data} object attached to a recommended fund. Deliberately
 * missing here: {@code port_builder_id} territory - out of scope for this
 * automation per the exclusion list agreed with the team; this record only
 * carries the fields business-rule validation actually needs.
 *
 * <p>{@code planId}/{@code fullUrl} are null on the nested {@code plan_data}
 * inside {@code breakdown_funds_data} (that shape only carries {@code name}/
 * {@code is_show}) - both shapes map onto this one record since Jackson
 * simply leaves absent fields null.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlanRef(
        @JsonProperty("plan_id") Long planId,
        String name,
        @JsonProperty("full_url") String fullUrl,
        @JsonProperty("is_show") boolean isShow) {
}

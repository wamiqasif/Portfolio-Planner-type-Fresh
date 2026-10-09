package com.org.apiautomation.models.response.hub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The "Add Investor" call-to-action block shown on the Hub. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AddInvestor(
        @JsonProperty("is_show") boolean isShow,
        @JsonProperty("is_active") boolean isActive,
        String title,
        String url,
        @JsonProperty("component_code") String componentCode) {
}

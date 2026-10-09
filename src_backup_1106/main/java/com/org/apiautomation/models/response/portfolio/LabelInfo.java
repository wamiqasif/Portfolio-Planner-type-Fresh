package com.org.apiautomation.models.response.portfolio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LabelInfo(
        @JsonProperty("label_id") long labelId,
        @JsonProperty("investor_id") long investorId,
        @JsonProperty("is_verified") boolean isVerified,
        @JsonProperty("label_name") String labelName) {
}

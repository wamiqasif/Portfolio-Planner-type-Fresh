package com.org.apiautomation.models.response.planner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InvestorInfo(
        @JsonProperty("investor_id") Long investorId,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("investor_name") String investorName,
        String pan) {
}

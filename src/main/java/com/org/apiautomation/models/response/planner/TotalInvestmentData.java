package com.org.apiautomation.models.response.planner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TotalInvestmentData(
        Double amount,
        @JsonProperty("amount_fmt") String amountFmt,
        String label) {
}

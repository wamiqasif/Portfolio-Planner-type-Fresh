package com.org.apiautomation.models.response.planner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FundsData(
        String title,
        List<FundEntry> data,
        @JsonProperty("total_investment_data") TotalInvestmentData totalInvestmentData) {
}

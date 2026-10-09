package com.org.apiautomation.models.response.planner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BreakdownFundsData(
        String title,
        List<BreakdownGroup> data,
        @JsonProperty("totals_data") TotalsData totalsData) {
}

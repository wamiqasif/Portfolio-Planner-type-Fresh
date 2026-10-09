package com.org.apiautomation.models.response.planner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BreakdownFundEntry(
        @JsonProperty("plan_data") PlanRef planData,
        List<Leg> legs) {
}

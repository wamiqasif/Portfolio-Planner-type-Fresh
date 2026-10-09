package com.org.apiautomation.models.response.portfolio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/** {@code data} object of the User Portfolio API - {@code "fund-data"} is a map keyed by plan_id (as a string), not an array. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortfolioData(@JsonProperty("fund-data") Map<String, PortfolioFundEntry> fundData) {
}

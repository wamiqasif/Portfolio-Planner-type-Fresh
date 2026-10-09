package com.org.apiautomation.models.response.fundplanoptions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Top-level wrapper for {@code GET /api/v1/funds/fund-plan-options-masters/get-list}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FundPlanOptionsResponse(boolean status, String message, List<FundPlanOptionEntry> data) {
}

package com.org.apiautomation.models.response.funddetails;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Top-level wrapper for {@code GET /api/v1/funds/{planId}}.
 *
 * <p><b>Note the field name: {@code success}, not {@code status}.</b>
 * Confirmed against the live sample ({@code src/test/resources/testdata/fund-details.json}:
 * {@code {"data": [...], "message": "Records found", "success": true}}) -
 * every other Portfolio Planner API in this module uses {@code status}
 * instead. Genuinely inconsistent across this backend's own endpoints, not
 * a typo to "fix" here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FundDetailsResponse(boolean success, String message, List<FundDetail> data) {
}

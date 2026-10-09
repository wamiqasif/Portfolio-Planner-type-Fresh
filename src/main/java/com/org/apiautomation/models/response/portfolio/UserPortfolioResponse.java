package com.org.apiautomation.models.response.portfolio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Top-level wrapper for {@code GET /api/v1/user-portfolio/label-opinion-data?label-ids=...}.
 *
 * <p><b>Note the field name: {@code success}, not {@code status}.</b>
 * Confirmed against the live sample ({@code src/test/resources/testdata/user-portfolio-response.json}:
 * {@code {"data": {...}, "message": "success", "success": true}}) and
 * against a live call during development - same inconsistency as {@code
 * FundDetailsResponse}, a different endpoint on this backend using a
 * different envelope field than the {@code status} convention every other
 * Portfolio Planner API in this module uses.
 *
 * <p>{@code data} needs {@link PortfolioDataDeserializer}: for an investor
 * with no existing portfolio at all, this backend sends {@code data: []}
 * (an empty array) instead of {@code data: {"fund-data": {}}} - see that
 * class's Javadoc.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserPortfolioResponse(
        boolean success,
        String message,
        @JsonDeserialize(using = PortfolioDataDeserializer.class) PortfolioData data) {
}

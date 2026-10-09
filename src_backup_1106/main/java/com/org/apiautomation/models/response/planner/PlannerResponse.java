package com.org.apiautomation.models.response.planner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Top-level wrapper for {@code POST /api/v1/planner/generate}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlannerResponse(boolean status, String message, PlannerData data) {
}

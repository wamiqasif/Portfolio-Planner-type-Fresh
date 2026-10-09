package com.org.apiautomation.models.response.fundopinion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Top-level wrapper for {@code GET /api/v1/funds/fund-opinion-data/get-list} (Fund Opinion API - fields=plan_id,vr_rating,opinion_type_id,is_analyst_pick). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FundOpinionResponse(boolean status, String message, List<FundOpinionEntry> data) {
}

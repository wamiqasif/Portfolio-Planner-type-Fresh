package com.org.apiautomation.models.response.fundopinion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FundOpinionEntry(
        @JsonProperty("plan_id") long planId,
        @JsonProperty("vr_rating") Integer vrRating,
        @JsonProperty("opinion_type_id") Integer opinionTypeId,
        @JsonProperty("is_analyst_pick") boolean isAnalystPick) {
}

package com.org.apiautomation.models.response.fundopinion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Top-level wrapper for the same endpoint as {@link FundOpinionResponse}, called instead with {@code is_analyst_pick=true} and {@code fields=plan_id,priority_order,tag_name} (Analyst Pick API). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalystPickResponse(boolean status, String message, List<AnalystPickEntry> data) {
}

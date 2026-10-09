package com.org.apiautomation.models.response.fundopinion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalystPickEntry(
        @JsonProperty("plan_id") long planId,
        @JsonProperty("priority_order") Integer priorityOrder,
        @JsonProperty("tag_name") List<String> tagName) {
}

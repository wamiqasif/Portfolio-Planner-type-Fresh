package com.org.apiautomation.models.response.planner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CategoryData(
        @JsonProperty("category_id") Long categoryId,
        @JsonProperty("category_fmt") String categoryFmt,
        @JsonProperty("is_show") boolean isShow) {
}

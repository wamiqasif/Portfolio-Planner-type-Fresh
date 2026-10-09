package com.org.apiautomation.models.response.planner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** One recommended fund in {@code funds_data.data[]}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FundEntry(
        @JsonProperty("plan_data") PlanRef planData,
        @JsonProperty("category_data") CategoryData categoryData,
        @JsonProperty("first_month_amount") Double firstMonthAmount,
        @JsonProperty("is_owned") boolean isOwned,
        List<Leg> legs,
        @JsonProperty("mode_label_fmt") String modeLabelFmt) {
}

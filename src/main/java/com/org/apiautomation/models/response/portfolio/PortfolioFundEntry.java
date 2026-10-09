package com.org.apiautomation.models.response.portfolio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.org.apiautomation.models.response.funddetails.FundDetail;

import java.util.List;

/** One entry of {@code data["fund-data"]}, keyed by plan_id (see {@link PortfolioData}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortfolioFundEntry(
        @JsonProperty("plan-details") FundDetail planDetails,
        @JsonProperty("label-data") List<LabelInfo> labelData) {
}

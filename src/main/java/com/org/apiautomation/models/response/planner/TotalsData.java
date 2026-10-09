package com.org.apiautomation.models.response.planner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TotalsData(
        @JsonProperty("first_month_total_fmt") String firstMonthTotalFmt,
        @JsonProperty("lumpsum_spread_fmt") String lumpsumSpreadFmt,
        @JsonProperty("one_time_fmt") String oneTimeFmt,
        @JsonProperty("sip_total_fmt") String sipTotalFmt) {
}

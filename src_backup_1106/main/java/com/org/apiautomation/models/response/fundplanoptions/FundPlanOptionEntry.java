package com.org.apiautomation.models.response.fundplanoptions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FundPlanOptionEntry(
        @JsonProperty("plan_id") long planId,
        @JsonProperty("sip_investment_details") List<SipInvestmentDetail> sipInvestmentDetails) {
}

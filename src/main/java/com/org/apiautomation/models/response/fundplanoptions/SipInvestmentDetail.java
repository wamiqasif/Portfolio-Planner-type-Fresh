package com.org.apiautomation.models.response.fundplanoptions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SipInvestmentDetail(
        @JsonProperty("amount_from") Double amountFrom,
        @JsonProperty("amount_to") Double amountTo,
        @JsonProperty("amount_in_multiples_of") Double amountInMultiplesOf,
        @JsonProperty("min_amount") Double minAmount,
        @JsonProperty("max_amount") Double maxAmount,
        @JsonProperty("min_agg_amount") Double minAggAmount,
        Integer installments,
        @JsonProperty("frequency_desc") String frequencyDesc,
        @JsonProperty("frequency_code_name") String frequencyCodeName,
        @JsonProperty("frequency_id") Integer frequencyId) {
}

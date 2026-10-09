package com.org.apiautomation.models.response.hub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The well-behaved, structurally stable part of the Hub response's {@code
 * data} object. {@code disclosures}, {@code disclosures_data}, {@code
 * page_info}, and {@code tracking_info} are deliberately NOT modeled here -
 * they contain confirmed polymorphic/unstable shapes (a field that is an
 * array in one real response and an object in another; a details object
 * that is sometimes {@code {list}} and sometimes {@code {desc}}). Forcing
 * those into a single Jackson type would fight the exact instability the
 * test suite exists to catch - the handful of tests that need them read
 * them via {@code response.jsonPath()} directly instead.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HubData(
        @JsonProperty("inpage_title") String inpageTitle,
        List<HubGroup> hub,
        @JsonProperty("add_investor") AddInvestor addInvestor,
        @JsonProperty("is_add_investor_allowed") boolean isAddInvestorAllowed) {
}

package com.org.apiautomation.models.response.hub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Top-level wrapper for the Hub API response. See {@link HubData} for what is and isn't strongly typed, and why. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HubResponse(boolean status, HubData data) {
}

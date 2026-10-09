package com.org.apiautomation.models.response.hub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A single navigation entry in the Hub. {@code downloadUrl}, {@code
 * componentCode}, and {@code products} are legitimately nullable by design
 * (BR-1 in the Hub API test plan) - {@code id}, {@code title}, {@code
 * icon}, {@code url}, {@code actionHint} are not.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Menu(
        String id,
        String title,
        String icon,
        String url,
        @JsonProperty("download_url") String downloadUrl,
        @JsonProperty("is_ext_url") boolean isExtUrl,
        @JsonProperty("component_code") String componentCode,
        @JsonProperty("action_hint") String actionHint,
        List<Product> products) {
}

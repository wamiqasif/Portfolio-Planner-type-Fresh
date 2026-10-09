package com.org.apiautomation.models.response.hub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The per-product variant of a {@link Menu} (e.g. a Fund Advisor URL vs. a
 * Stock Advisor URL for the same conceptual destination). Deliberately a
 * separate, narrower type from {@code Menu} - real sample data confirms it
 * has no {@code id}, {@code icon}, or {@code action_hint}. Modeling it as
 * the same type as {@code Menu} would silently accept invalid data that a
 * schema check should reject (HUB-SCH-02).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Product(
        String title,
        String url,
        @JsonProperty("download_url") String downloadUrl,
        @JsonProperty("is_ext_url") boolean isExtUrl,
        @JsonProperty("component_code") String componentCode) {
}

package com.org.apiautomation.models.response.planner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** PRD section 11.5: the "You already own N funds..." sentence, shown only for {@code PORTFOLIO_AWARE} and only when at least one fund was picked from the existing portfolio. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlanNoteData(
        @JsonProperty("is_show") boolean isShow,
        String style,
        @JsonProperty("text_fmt") String textFmt) {
}

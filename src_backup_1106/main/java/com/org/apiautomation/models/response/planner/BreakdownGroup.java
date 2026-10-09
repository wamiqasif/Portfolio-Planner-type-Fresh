package com.org.apiautomation.models.response.planner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** One asset-class bucket ({@code "Equity"}/{@code "Debt"}/...) inside {@code breakdown_funds_data.data[]}, with its declared allocation percentage. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BreakdownGroup(
        String name,
        @JsonProperty("perc_fmt") String percFmt,
        List<BreakdownFundEntry> data) {

    /** Parses {@code "75%"} -> {@code 75.0}. Returns {@code null} if unparsable rather than throwing - a formatting change here is a contract concern for the caller to assert on, not this record's job. */
    public Double percentage() {
        if (percFmt == null) {
            return null;
        }
        try {
            return Double.parseDouble(percFmt.replace("%", "").trim());
        } catch (NumberFormatException notNumeric) {
            return null;
        }
    }
}

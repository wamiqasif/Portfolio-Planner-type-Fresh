package com.org.apiautomation.models.response.planner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DisclaimerData(List<String> data, String title) {
}

package com.org.apiautomation.models.response.hub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** One top-level section of the Hub (e.g. "Profile", "Stocks"), containing its own list of {@link Menu} entries. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HubGroup(String id, String title, List<Menu> menus) {
}

package com.org.apiautomation.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response model for the sample CRUD resource (see {@code
 * services.PostService}). {@code @JsonIgnoreProperties(ignoreUnknown = true)}:
 * real APIs add fields over time; without this, deserialization fails the
 * moment the server returns a property this record doesn't declare, for a
 * field no test ever asked about.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PostResponse(int id, int userId, String title, String body) {
}

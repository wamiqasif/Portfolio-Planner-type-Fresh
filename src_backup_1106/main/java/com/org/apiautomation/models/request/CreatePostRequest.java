package com.org.apiautomation.models.request;

/**
 * Request model for the sample CRUD resource used to demonstrate the
 * framework end to end (see {@code services.PostService}). No {@code
 * @JsonProperty} renames needed here - this API's JSON keys already match
 * Java camelCase field names directly, which is itself worth showing: not
 * every API needs the renaming machinery a snake_case API would.
 */
public record CreatePostRequest(String title, String body, int userId) {
}

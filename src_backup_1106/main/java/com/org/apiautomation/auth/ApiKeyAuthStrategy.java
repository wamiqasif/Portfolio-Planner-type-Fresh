package com.org.apiautomation.auth;

import com.org.apiautomation.config.ConfigManager;
import io.restassured.specification.RequestSpecification;

/**
 * Attaches a static API key as a header - many APIs authenticate this way
 * instead of a token exchange or Basic auth. Both the header name (e.g.
 * {@code X-API-Key}, {@code Api-Key}, or something vendor-specific) and its
 * value are configurable via {@link ConfigManager}, since different APIs
 * name the header differently; only the value is a secret.
 */
public final class ApiKeyAuthStrategy implements AuthStrategy {

    @Override
    public RequestSpecification apply(RequestSpecification requestSpec) {
        ConfigManager config = ConfigManager.getInstance();
        return requestSpec.header(config.getAuthApiKeyHeaderName(), config.getAuthApiKeyValue());
    }
}

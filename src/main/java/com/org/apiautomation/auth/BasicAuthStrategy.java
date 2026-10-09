package com.org.apiautomation.auth;

import com.org.apiautomation.config.ConfigManager;
import io.restassured.specification.RequestSpecification;

/**
 * HTTP Basic authentication (RFC 7617). Username/password come from
 * {@link ConfigManager} (never hardcoded here), and the actual
 * base64-encoding of the {@code Authorization} header is Rest Assured's own
 * job via {@code .auth().basic(...)} - there is no header-building logic to
 * write or duplicate.
 *
 * <p>Applied preemptively: the credentials are sent on the first request,
 * rather than waiting for the server to challenge with a 401 first (the
 * "non-preemptive" Basic auth flow). A test probing an API's actual
 * behavior has no use for an extra unauthenticated round trip just to
 * trigger that challenge.
 */
public final class BasicAuthStrategy implements AuthStrategy {

    @Override
    public RequestSpecification apply(RequestSpecification requestSpec) {
        ConfigManager config = ConfigManager.getInstance();
        return requestSpec.auth().preemptive().basic(config.getAuthUsername(), config.getAuthPassword());
    }
}

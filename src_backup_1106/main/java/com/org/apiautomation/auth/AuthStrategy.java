package com.org.apiautomation.auth;

import io.restassured.specification.RequestSpecification;

/**
 * A pluggable mechanism for attaching credentials to an outgoing request.
 * Every mechanism the framework supports (Basic, Bearer, API Key, OAuth2,
 * VR dynamic login) implements this same contract, so {@code
 * RequestSpecFactory} (core layer) can apply whichever one is configured
 * for the active environment without knowing which one it is. Adding a new
 * mechanism later means adding one class that implements this interface and
 * one line in {@link AuthStrategyFactory} - never touching an existing
 * strategy.
 */
public interface AuthStrategy {

    /**
     * Attaches this mechanism's credentials to {@code requestSpec} and
     * returns it (Rest Assured's {@code RequestSpecification} methods mutate
     * and return the same instance, so implementations simply pass that
     * return value through).
     */
    RequestSpecification apply(RequestSpecification requestSpec);

    /**
     * Forces the next {@link #apply} call to obtain fresh credentials
     * rather than reuse a cached one - called by {@link AuthRetryFilter}
     * after a 401/403.
     *
     * <p>Default no-op: a static mechanism (Basic, a fixed Bearer token,
     * API key) has nothing to invalidate - reapplying it sends the exact
     * same credential, which is the correct behavior if it's genuinely
     * wrong (retrying won't fix a bad password). Only a mechanism that
     * caches a *fetched* credential ({@link OAuth2AuthStrategy}, {@link
     * DynamicLoginAuthStrategy}) needs to override this.
     */
    default void invalidate() {
    }
}

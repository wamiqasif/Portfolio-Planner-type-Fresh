package com.org.apiautomation.exceptions;

/**
 * Thrown when a configured authentication mechanism cannot produce valid
 * credentials - e.g. an OAuth2 token endpoint is unreachable, returns a
 * non-2xx status, or its response has no {@code access_token}; or a bearer
 * token is required but blank. Kept distinct from {@link ConfigurationException}
 * because the cause here is a runtime authentication failure (a dead auth
 * server, expired/revoked credentials), not necessarily a setup mistake.
 */
public class AuthenticationException extends RuntimeException {

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.org.apiautomation.exceptions;

/**
 * Thrown when the login API responds HTTP 200 but its own JSON body reports
 * {@code "status": false} - a business-level login rejection (e.g. a bad
 * password), not a transport failure. Kept distinct from the more general
 * {@link AuthenticationException} so the server's own reported reason
 * (from {@code session-alerts[].message}) surfaces as-is, instead of being
 * discarded in favor of a generic "no token was found" message from code
 * that went on to look for a token that a failed login never has.
 */
public class LoginFailedException extends AuthenticationException {

    public LoginFailedException(String message) {
        super(message);
    }

    public LoginFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}

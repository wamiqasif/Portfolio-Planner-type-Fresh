package com.org.apiautomation.auth;

import com.org.apiautomation.config.ConfigManager;
import com.org.apiautomation.exceptions.AuthenticationException;
import io.restassured.specification.RequestSpecification;

/**
 * Attaches a bearer token to the {@code Authorization} header via Rest
 * Assured's built-in {@code .auth().oauth2(token)}, which sets exactly
 * {@code Authorization: Bearer <token>} - not hand-rolled string
 * concatenation.
 *
 * <p><b>Why this class is reused, not duplicated, by {@link OAuth2AuthStrategy}:</b>
 * once OAuth2 has obtained an access token, "attach it as a bearer token" is
 * the exact same operation this class already performs. {@code
 * OAuth2AuthStrategy} delegates to an instance of this class instead of
 * calling {@code .auth().oauth2(...)} a second time - the only place that
 * line of code exists is here.
 *
 * <p>Two constructors reflect two different sources of the token:
 * <ul>
 *   <li>{@link #BearerTokenAuthStrategy()} - reads a statically configured,
 *       already-issued token from {@link ConfigManager} (e.g. a long-lived
 *       service token supplied via {@code -Dauth.bearer.token=...} from a CI
 *       secret store). No network call is made to obtain it.</li>
 *   <li>{@link #BearerTokenAuthStrategy(String)} - accepts a token obtained
 *       some other way. This is exactly what {@code OAuth2AuthStrategy} uses
 *       after fetching one from the token endpoint.</li>
 * </ul>
 */
public final class BearerTokenAuthStrategy implements AuthStrategy {

    private final String token;

    public BearerTokenAuthStrategy() {
        this(ConfigManager.getInstance().getAuthBearerToken());
    }

    public BearerTokenAuthStrategy(String token) {
        if (token == null || token.isBlank()) {
            throw new AuthenticationException(
                    "Bearer token must not be blank. Set auth.bearer.token (or -Dauth.bearer.token=...) "
                            + "when auth.type=bearer.");
        }
        this.token = token;
    }

    @Override
    public RequestSpecification apply(RequestSpecification requestSpec) {
        return requestSpec.auth().preemptive().oauth2(token);
    }
}

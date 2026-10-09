package com.org.apiautomation.auth;

import com.org.apiautomation.config.ConfigManager;
import com.org.apiautomation.exceptions.AuthenticationException;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.time.Instant;

/**
 * OAuth2 client-credentials grant: exchanges a client id/secret for an
 * access token at the environment's token endpoint, then attaches it as a
 * bearer token by delegating to {@link BearerTokenAuthStrategy} - reused,
 * not reimplemented, since attaching a bearer token is exactly what that
 * class already does.
 *
 * <p><b>Why this deliberately does not use {@code core.RequestSpecFactory} /
 * {@code core.ApiClient} for the token-fetch call:</b> {@code
 * RequestSpecFactory} applies whatever {@link AuthStrategy} is configured to
 * every spec it builds - that's how auth ends up attached to real API calls
 * automatically. If this class asked {@code RequestSpecFactory} for a spec,
 * fetching a token would try to attach OAuth2 authentication to the very
 * request being used to obtain that authentication - a circular
 * dependency, not just a redundant call. It also needs a content type
 * ({@code application/x-www-form-urlencoded}, per RFC 6749 for the
 * client-credentials grant) that no other request in the framework uses, so
 * there is nothing generic to reuse here regardless. Talking to Rest
 * Assured directly for this one bootstrapping call keeps the dependency
 * direction clean: {@code core} depends on {@code auth}, never the reverse.
 *
 * <p>Delegates caching to {@link TokenManager} (under its own cache key, so
 * it never shares a slot with {@link DynamicLoginAuthStrategy}'s token) so
 * a token is fetched once and reused until it's close to expiring, not
 * before every API call.
 */
public final class OAuth2AuthStrategy implements AuthStrategy {

    private static final String CACHE_KEY = "oauth2";

    @Override
    public RequestSpecification apply(RequestSpecification requestSpec) {
        String accessToken = TokenManager.getInstance().getToken(CACHE_KEY, this::fetchToken);
        return new BearerTokenAuthStrategy(accessToken).apply(requestSpec);
    }

    @Override
    public void invalidate() {
        TokenManager.getInstance().invalidate(CACHE_KEY);
    }

    private TokenManager.CachedToken fetchToken() {
        ConfigManager config = ConfigManager.getInstance();
        Response response;

        try {
            RequestSpecification tokenRequestSpec = RestAssured.given()
                    .baseUri(config.getAuthTokenUrl())
                    .contentType(ContentType.URLENC)
                    .formParam("grant_type", config.getOAuth2GrantType())
                    .formParam("client_id", config.getOAuth2ClientId())
                    .formParam("client_secret", config.getOAuth2ClientSecret())
                    .formParam("scope", config.getOAuth2Scope());

            // Same non-prod-only relaxation as RequestSpecFactory, for the same
            // reason: non-prod auth servers commonly run behind internal/
            // self-signed certificates; prod never gets this relaxed.
            if (!"prod".equals(config.getEnvironment())) {
                tokenRequestSpec.relaxedHTTPSValidation();
            }

            response = tokenRequestSpec.post();
        } catch (Exception e) {
            throw new AuthenticationException(
                    "Failed to reach OAuth2 token endpoint: " + config.getAuthTokenUrl(), e);
        }

        if (response.statusCode() != 200) {
            throw new AuthenticationException(
                    "OAuth2 token endpoint returned status " + response.statusCode() + ": " + response.asString());
        }

        String accessToken = response.jsonPath().getString("access_token");
        int expiresInSeconds = response.jsonPath().getInt("expires_in");

        if (accessToken == null || accessToken.isBlank()) {
            throw new AuthenticationException(
                    "OAuth2 token endpoint response had no access_token: " + response.asString());
        }

        return new TokenManager.CachedToken(accessToken, Instant.now().plusSeconds(expiresInSeconds));
    }
}

package com.org.apiautomation.auth;

import com.org.apiautomation.config.ConfigManager;
import com.org.apiautomation.exceptions.AuthenticationException;
import com.org.apiautomation.exceptions.LoginFailedException;
import com.org.apiautomation.utils.SensitiveDataMasker;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;

/**
 * The single place "how do we get a currently-valid VR login token" is
 * decided. Uses {@link LoginService} to perform the actual HTTP call (per
 * requirement #15 - login mechanics live there, not here), extracts the
 * token from whatever shape the response turns out to be, and delegates
 * caching/expiry/thread-safety to the already-existing {@link TokenManager}
 * rather than re-implementing it.
 *
 * <p>Deliberately holds no state of its own and is not a singleton -
 * {@link TokenManager} is already the real, shared, thread-safe cache;
 * adding a second singleton on top of it here would be ceremony with
 * nothing to protect. {@link DynamicLoginAuthStrategy} constructs this
 * fresh, exactly like every other {@code AuthStrategy} constructs its own
 * stateless collaborators.
 *
 * <p><b>Why token extraction tries a JSON field first, then a cookie:</b>
 * this framework has no confirmed sample of what the login response
 * actually looks like - only the request shape was provided. A PHP/session
 * based backend (which the login request's own header set suggests this
 * is) plausibly returns the token as a JSON field *or* sets it as a
 * cookie. Both the JSON field name ({@code auth.vrlogin.token.jsonpath},
 * default {@code "token"}) and the cookie name ({@code
 * auth.vrlogin.token.cookie.name}, default blank/disabled) are
 * configurable specifically so this assumption can be corrected with a
 * config change, not a code change, once the real response is confirmed.
 */
public final class AuthenticationManager {

    private static final Logger LOGGER = LogManager.getLogger(AuthenticationManager.class);
    private static final String CACHE_KEY = "vrlogin";

    private final LoginService loginService = new LoginService();

    /** Returns a currently-valid token, logging in only if there isn't a cached one that's still fresh (requirement #10). */
    public String getValidToken() {
        return TokenManager.getInstance().getToken(CACHE_KEY, this::login);
    }

    /** Forces the next {@link #getValidToken} call to log in again rather than reuse the cache - called after a 401/403. */
    public void invalidateToken() {
        TokenManager.getInstance().invalidate(CACHE_KEY);
    }

    private TokenManager.CachedToken login() {
        Response response = loginService.login();

        if (response.statusCode() != 200) {
            throw new AuthenticationException("Login failed with status " + response.statusCode());
        }

        failFastOnExplicitStatusFalse(response);

        String token = extractToken(response);
        if (token == null || token.isBlank()) {
            throw new AuthenticationException(
                    "Login succeeded (status 200) but no token was found - checked JSON field '"
                            + ConfigManager.getInstance().getVrLoginTokenJsonPath() + "' and cookie '"
                            + ConfigManager.getInstance().getVrLoginTokenCookieName() + "'. "
                            + "Adjust auth.vrlogin.token.jsonpath / auth.vrlogin.token.cookie.name once the real response shape is confirmed.");
        }

        LOGGER.info("Login successful - token acquired: {}", SensitiveDataMasker.mask(token));

        Instant expiresAt = Instant.now().plusSeconds(ConfigManager.getInstance().getVrLoginTokenExpirySeconds());
        return new TokenManager.CachedToken(token, expiresAt);
    }

    /**
     * The login API returns HTTP 200 even on a business-level login
     * failure (e.g. a rejected password) - the real outcome is the JSON
     * body's own {@code "status"} field, with {@code session-alerts[].message}
     * carrying the server's reason. Without this check, a {@code
     * "status": false} response (which never has a token) fell straight
     * into {@link #extractToken}, which then failed the generic, misleading
     * way: "no token was found," with the server's actual, specific reason
     * discarded. Only fires on an explicit {@code false} - a missing or
     * unparseable {@code "status"} field falls through to token extraction
     * unchanged, same as before this check existed.
     */
    private void failFastOnExplicitStatusFalse(Response response) {
        JsonPath jsonPath;
        Boolean bodyStatus;
        try {
            jsonPath = new JsonPath(stripLeadingBom(response.getBody().asString()));
            bodyStatus = jsonPath.getBoolean("status");
        } catch (Exception jsonParsingFailed) {
            return;
        }

        if (!Boolean.FALSE.equals(bodyStatus)) {
            return;
        }

        List<String> alertMessages = jsonPath.getList("session-alerts.message", String.class);
        String serverMessage = alertMessages.isEmpty()
                ? "status=false with no session-alerts message in the response"
                : String.join("; ", alertMessages);
        throw new LoginFailedException("Login API rejected the request: " + serverMessage);
    }

    private String extractToken(Response response) {
        ConfigManager config = ConfigManager.getInstance();

        String rawBody = response.getBody().asString();
        LOGGER.debug("Raw login response body (pre-parse): {}", rawBody);

        String token = null;
        try {
            token = new JsonPath(stripLeadingBom(rawBody)).getString(config.getVrLoginTokenJsonPath());
        } catch (Exception jsonParsingFailed) {
            // Response body isn't JSON at all (e.g. an HTML error page, or a
            // plain-text token) - fall through to the cookie check below
            // rather than failing here. Logged (not swallowed silently) so a
            // genuine parsing problem - as opposed to "field legitimately
            // absent" - is diagnosable instead of surfacing only as "no
            // token was found" with no indication why.
            LOGGER.debug("Login response body could not be parsed as JSON at path '{}'",
                    config.getVrLoginTokenJsonPath(), jsonParsingFailed);
        }

        if ((token == null || token.isBlank())) {
            String cookieName = config.getVrLoginTokenCookieName();
            if (cookieName != null && !cookieName.isBlank()) {
                token = response.getCookie(cookieName);
            }
        }

        LOGGER.debug("Extracted token: {}", SensitiveDataMasker.mask(token));
        return token;
    }

    /**
     * Strips a leading UTF-8 byte-order-mark character if present. A PHP
     * backend emitting a stray BOM before its JSON output (e.g. from a
     * BOM-saved included file evaluated before the response body is
     * written) is a real, observed cause of a root-level JSON field like
     * {@code "token"} failing to parse even though it's plainly present in
     * the body - {@code JsonPath} treats a leading {@code U+FEFF} as an
     * invalid token, not insignificant whitespace, and rejects the whole
     * document.
     */
    private static String stripLeadingBom(String body) {
        return (body != null && !body.isEmpty() && body.charAt(0) == '\uFEFF') ? body.substring(1) : body;
    }
}

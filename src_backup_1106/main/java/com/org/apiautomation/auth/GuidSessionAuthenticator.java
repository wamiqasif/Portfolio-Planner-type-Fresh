package com.org.apiautomation.auth;

import com.org.apiautomation.config.ConfigManager;
import com.org.apiautomation.exceptions.AuthenticationException;
import io.restassured.RestAssured;
import io.restassured.http.Cookie;
import io.restassured.http.Cookies;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Establishes a per-account session via the {@code login-by-guid} endpoint -
 * the Excel workbook's own "Login URL" column, one distinct value per
 * Execution_Data row - and returns the resulting session cookies. Confirmed
 * by the backend team to be sufficient credentials for Planner Generate,
 * User Portfolio, Fund Details, and Fund Opinion (across both the advisor
 * API gateway and the main app API host).
 *
 * <p><b>Talks to Rest Assured directly instead of going through {@code
 * core.RequestSpecFactory}</b> - the same reason {@link LoginService} and
 * {@code OAuth2AuthStrategy} do: this call is inherently unauthenticated
 * (there is nothing to attach yet), and {@code
 * RequestSpecFactory.defaultRequestSpec} would apply the framework's
 * unrelated shared {@code vrlogin} strategy on top of it for no reason.
 *
 * <p><b>Deliberately not an {@link AuthStrategy}.</b> Every existing
 * strategy is stateless and resolved once per environment via {@link
 * AuthStrategyFactory} from static config - one mechanism, shared by every
 * call for the life of the suite. This is the opposite: a fresh session per
 * Excel row, keyed by that row's own GUID, never cached and never selected
 * via {@code auth.type}. Forcing it into the {@code AuthStrategy} shape
 * would mean threading a row-specific GUID through a contract designed for
 * zero-argument, environment-wide credentials. Instead, a caller obtains a
 * {@link Cookies} instance here and attaches it to a row-scoped {@code
 * RequestSpecification} directly - exactly the escape hatch {@code
 * RequestSpecFactory}'s own Javadoc already documents for a caller-supplied
 * cookie (e.g. {@code RequestSpecFactory.unauthenticatedRequestSpec(baseUri)
 * .cookies(cookies)}).
 */
public final class GuidSessionAuthenticator {

    private static final Logger LOGGER = LogManager.getLogger(GuidSessionAuthenticator.class);

    /** {@code shared} (default), {@code per-account}, or {@code per-account-then-shared}. */
    static final String BEARER_MODE_PROPERTY = "guid.bearer.mode";
    private static final Pattern SESSION_ID = Pattern.compile("^[0-9a-fA-F]{32}$");

    /**
     * Planner Generate sits behind the KrakenD gateway, which needs {@code
     * Authorization: Bearer} on top of the login-by-guid cookies. Keyed by
     * {@link Cookies} identity: each {@link #login} returns a fresh instance
     * that its caller holds for the row's lifetime.
     */
    private static final Map<Cookies, String> BEARERS = Collections.synchronizedMap(new IdentityHashMap<>());
    /** Reason no bearer was obtained - cookie NAMES only, never values. */
    private static final Map<Cookies, String> BEARER_FAILURES = Collections.synchronizedMap(new IdentityHashMap<>());
    /** Sessions using the shared API-1 token - refreshed automatically when the JWT is about to expire. */
    private static final java.util.Set<Cookies> SHARED_SESSIONS =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    /** Refresh the shared JWT when fewer than this many seconds remain (a run of 146 rows outlived the token). */
    private static final long REFRESH_MARGIN_SECONDS = 120;

    /**
     * Calls {@code loginUrl} (a full URL, exactly as given in the Excel
     * "Login URL" column) and returns the session cookies it establishes.
     *
     * <p>Redirects are not followed - confirmed via a real call that the
     * redirect target is just the frontend homepage, never anything this
     * framework needs, and the session cookies are already set on this
     * first response regardless of whether the redirect is followed.
     */
    public Cookies login(String loginUrl) {
        if (loginUrl == null || loginUrl.isBlank()) {
            throw new AuthenticationException("No Login URL/GUID provided for this row - cannot establish a per-row session");
        }

        RequestSpecification spec = RestAssured.given().redirects().follow(false);
        if (!"prod".equals(ConfigManager.getInstance().getEnvironment())) {
            spec = spec.relaxedHTTPSValidation();
        }

        Response response = spec.get(loginUrl);
        LOGGER.info("login-by-guid responded with status {}", response.statusCode());

        Cookies cookies = response.getDetailedCookies();
        if (!cookies.exist()) {
            throw new AuthenticationException(
                    "login-by-guid returned status " + response.statusCode() + " but set no session cookies - "
                            + "cannot establish a per-row session");
        }
        attachBearerToken(cookies);
        return cookies;
    }

    /** Adds the session cookies and, when one was obtained for them, {@code Authorization: Bearer}. Never logs either. */
    public static RequestSpecification attach(RequestSpecification spec, Cookies cookies) {
        RequestSpecification withCookies = spec.cookies(cookies);
        String bearer = BEARERS.get(cookies);
        if (bearer != null && SHARED_SESSIONS.contains(cookies)) {
            bearer = freshSharedToken(bearer);
            BEARERS.put(cookies, bearer);
        }
        return bearer == null ? withCookies : withCookies.header("Authorization", "Bearer " + bearer);
    }

    public static boolean hasBearer(Cookies cookies) {
        return BEARERS.containsKey(cookies);
    }

    /** Why no bearer is attached to {@code cookies} (names/statuses only); empty if one is. */
    public static String bearerFailureReason(Cookies cookies) {
        String reason = BEARER_FAILURES.get(cookies);
        return reason == null ? "" : reason;
    }

    private void attachBearerToken(Cookies cookies) {
        String mode = System.getProperty(BEARER_MODE_PROPERTY, "shared").trim().toLowerCase();
        List<String> names = new ArrayList<>();
        cookies.asList().forEach(c -> names.add(c.getName()));
        List<String> problems = new ArrayList<>();
        String token = null;
        String strategy = null;

        if (mode.startsWith("per-account")) {
            token = perAccountToken(cookies, problems);
            strategy = "per-account";
        }
        if (token == null && (mode.equals("shared") || mode.equals("per-account-then-shared") || !mode.startsWith("per-account"))) {
            try {
                token = Api1AuthService.getInstance().getAccessToken();
                strategy = "shared";
            } catch (RuntimeException e) {
                problems.add("shared API-1 token failed: " + e.getMessage());
            }
        }

        if (token == null) {
            String reason = "No Bearer token obtained (mode=" + mode + ", cookie names=" + names + "): " + String.join("; ", problems);
            BEARER_FAILURES.put(cookies, reason);
            LOGGER.warn(reason);
        } else {
            BEARERS.put(cookies, token);
            if ("shared".equals(strategy)) {
                SHARED_SESSIONS.add(cookies);
            }
            LOGGER.info("GUID session bearer attached: mode={}, strategy={}, cookie names={}", mode, strategy, names);
        }
    }

    /** Cookies-only first, then {@code Auth-Token} = each 32-hex cookie value. */
    private String perAccountToken(Cookies cookies, List<String> problems) {
        List<String> authTokenCandidates = new ArrayList<>();
        authTokenCandidates.add(null);
        for (Cookie c : cookies.asList()) {
            if (c.getValue() != null && SESSION_ID.matcher(c.getValue()).matches()) {
                authTokenCandidates.add(c.getValue());
            }
        }
        ConfigManager config = ConfigManager.getInstance();
        for (String candidate : authTokenCandidates) {
            String label = candidate == null ? "cookies only" : "Auth-Token from a 32-hex cookie";
            try {
                RequestSpecification spec = RestAssured.given()
                        .baseUri(config.getApi1AuthBaseUrl())
                        .header("app-platform", config.getApi1AppPlatform())
                        .cookies(cookies);
                if (!"prod".equals(config.getEnvironment())) {
                    spec = spec.relaxedHTTPSValidation();
                }
                if (candidate != null) {
                    spec = spec.header("Auth-Token", candidate);
                }
                Response r = spec.post(config.getApi1TokenUrl());
                String access = r.statusCode() == 200 ? Api1AuthService.extractField(r.asString(), "access_token") : null;
                if (access != null) {
                    return access;
                }
                problems.add("per-account token/create (" + label + ") HTTP " + r.statusCode() + ", no access_token");
            } catch (RuntimeException e) {
                problems.add("per-account token/create (" + label + ") error: " + e.getClass().getSimpleName());
            }
        }
        return null;
    }

    /**
     * Returns {@code current} unless its JWT {@code exp} is within {@link #REFRESH_MARGIN_SECONDS};
     * then re-authenticates via {@link Api1AuthService}. Fix for the 08-Oct-2026 run where the last
     * rows got 401 -> vrlogin retry -> "Login failed with status 307" because the token had expired.
     */
    private static synchronized String freshSharedToken(String current) {
        Long exp = jwtExpiry(current);
        long now = System.currentTimeMillis() / 1000;
        if (exp != null && exp - now > REFRESH_MARGIN_SECONDS) {
            return current;
        }
        String latest = Api1AuthService.getInstance().getAccessToken();
        if (latest.equals(current)) {
            Api1AuthService.getInstance().invalidate();
            latest = Api1AuthService.getInstance().getAccessToken();
            LOGGER.info("Shared API-1 Bearer token refreshed (old one expired or expiring)");
        }
        return latest;
    }

    /** Reads the {@code exp} claim of a JWT without verifying it; null if not a readable JWT. */
    private static Long jwtExpiry(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            String json = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
            java.util.regex.Matcher m = Pattern.compile("\"exp\"\\s*:\\s*(\\d+)").matcher(json);
            return m.find() ? Long.parseLong(m.group(1)) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}

package com.org.apiautomation.core;

import com.org.apiautomation.auth.AuthStrategyFactory;
import com.org.apiautomation.config.ConfigManager;
import com.org.apiautomation.listeners.HttpLoggingFilter;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/**
 * Builds a fully-configured Rest Assured {@link RequestSpecification}: base
 * URI, JSON content type, connect/read timeouts, request/response logging,
 * and authentication - the handful of things every single API call needs,
 * resolved once from {@link ConfigManager} instead of repeated in every
 * future service class.
 *
 * <p><b>Design decision - logging goes through {@code HttpLoggingFilter}, not
 * Rest Assured's own {@code .log(LogDetail.ALL)}.</b> An earlier version of
 * this method used {@code .log(LogDetail.ALL)}, which registers Rest
 * Assured's built-in logging filter - it prints straight to {@code
 * System.out}, bypassing Log4j2 entirely (no file output, no level control),
 * and it logs every header verbatim, including {@code Authorization} - a
 * real credential leak into console/CI logs given the auth layer this framework
 * has. {@code HttpLoggingFilter} (attached below via {@code .addFilter(...)})
 * replaces it: same visibility into every request/response, routed through
 * Log4j2 like everything else, with known-sensitive headers redacted before
 * they're ever written anywhere.
 *
 * <p><b>Design decision - authentication is applied here, automatically.</b>
 * {@link #defaultRequestSpec(String)} attaches whichever {@link
 * com.org.apiautomation.auth.AuthStrategy} is configured for the active
 * environment ({@code auth.type}) via {@link AuthStrategyFactory}. This is
 * the one intentional dependency this layer has on the {@code auth} package,
 * and it only goes in this direction: {@code core -> auth -> config}, never
 * back. Every future service built on {@link ApiClient} gets authenticated
 * requests for free, without knowing or caring which of the four supported
 * mechanisms is active - that's the entire point of the auth layer being a
 * Strategy behind a Factory rather than an if/else a caller has to write.
 *
 * <p><b>Design decision - no query/path param/header/cookie/multipart methods
 * here.</b> Those are already first-class, well-tested methods on Rest
 * Assured's own {@code RequestSpecification} ({@code .queryParam()},
 * {@code .pathParam()}, {@code .header()}, {@code .cookie()},
 * {@code .multiPart()}). Wrapping each in a same-named pass-through method
 * would add a method call that does nothing but forward its arguments - the
 * exact "wrapper that provides no value" this layer is meant to avoid.
 * Callers configure those directly on the spec this factory returns, e.g.:
 *
 * <pre>{@code
 * RequestSpecification spec = RequestSpecFactory.defaultRequestSpec()
 *         .pathParam("id", 42)
 *         .queryParam("expand", "profile")
 *         .header("X-Correlation-Id", correlationId)
 *         .cookie("session", token);
 * }</pre>
 *
 * <p><b>Design decision - base URI management lives here, not in a separate
 * class.</b> A dedicated {@code BaseUriManager} would just hold one string
 * and forward it to {@link ConfigManager} - again, no behavior of its own.
 * Instead, base URI resolution is a parameter of this factory: default from
 * config, or an explicit override for the (real but uncommon) case of a test
 * needing to call a second host - a dedicated auth server, a third-party
 * dependency - without changing the environment's main {@code base.url}.
 *
 * <p><b>Design decision - always builds a new instance.</b> Nothing is
 * cached or shared across calls. If this returned one shared spec, two
 * TestNG threads running in parallel and each adding their own query params
 * to "the" spec would corrupt each other's requests. A fresh object per call
 * makes that race structurally impossible rather than something to remember.
 */
public final class RequestSpecFactory {

    private RequestSpecFactory() {
    }

    /**
     * Builds a request spec using the base URI from the active environment's
     * configuration ({@link ConfigManager#getBaseUrl()}).
     */
    public static RequestSpecification defaultRequestSpec() {
        return defaultRequestSpec(ConfigManager.getInstance().getBaseUrl());
    }

    /**
     * Builds a request spec against an explicit base URI, overriding
     * configuration for this one spec, with authentication already applied.
     *
     * <p>Once auth is applied, this carries an {@code Authorization} header
     * - which is exactly the signal {@code ApiClient} uses to decide
     * whether a 401/403 on this call should trigger its automatic
     * invalidate-and-retry-once behavior. {@link #unauthenticatedRequestSpec}
     * never has that header, so a deliberately-unauthenticated call (e.g. a
     * negative test expecting a 401) is correctly left alone - see {@code
     * ApiClient}'s Javadoc for the full reasoning and what was verified
     * before landing on this approach.
     */
    public static RequestSpecification defaultRequestSpec(String baseUri) {
        RequestSpecification spec = unauthenticatedRequestSpec(baseUri);
        return AuthStrategyFactory.getStrategy().apply(spec);
    }

    /**
     * Builds a request spec exactly like {@link #defaultRequestSpec(String)},
     * but with no authentication applied. This is the extraction that lets
     * {@code defaultRequestSpec} be "build the base spec, then apply auth"
     * instead of duplicating the base-spec construction in two places.
     *
     * <p>Also a legitimate escape hatch on its own for a test that
     * deliberately needs an unauthenticated call - e.g. a health-check
     * endpoint, or a negative test asserting that a protected endpoint
     * really does reject a request with no credentials.
     *
     * <p><b>Not used for OAuth2's or the VR login flow's own token-fetch
     * call.</b> {@code auth.OAuth2AuthStrategy} and {@code
     * auth.LoginService} talk to Rest Assured directly for that instead of
     * calling this method - see their Javadoc for why: obtaining a token
     * through the {@code auth} package while depending back on {@code core}
     * would be a circular package dependency, and both need a different
     * content type than any other request anyway.
     */
    public static RequestSpecification unauthenticatedRequestSpec(String baseUri) {
        ConfigManager config = ConfigManager.getInstance();

        RequestSpecification spec = new RequestSpecBuilder()
                .setBaseUri(baseUri)
                .setContentType(ContentType.JSON)
                .setConfig(timeoutConfig(config))
                .addFilter(new HttpLoggingFilter())
                .build();

        // Non-prod test environments commonly run behind self-signed or
        // internal CAs. Deliberately never relaxed for prod - a real
        // certificate problem there must fail loudly, not be swallowed.
        if (!"prod".equals(config.getEnvironment())) {
            spec.relaxedHTTPSValidation();
        }

        return spec;
    }

    /**
     * Builds a request spec suitable for a multipart request, using the
     * configured base URI.
     *
     * <p><b>Why this exists as a separate method, and isn't a "value-less
     * wrapper":</b> {@link #defaultRequestSpec()} sets {@code Content-Type:
     * application/json}. Rest Assured does not override an explicitly-set
     * content type when a part is added - calling {@code .multiPart(...)} on
     * that spec throws {@code IllegalArgumentException("Content-Type
     * application/json is not valid when using multiparts...")}. Every
     * caller building a multipart request would hit that exact exception
     * unless they knew to call {@code .noContentType()} first - a genuinely
     * non-obvious Rest Assured quirk, not something a test author should
     * have to discover. This method encodes that fix once.
     */
    public static RequestSpecification multipartRequestSpec() {
        return multipartRequestSpec(ConfigManager.getInstance().getBaseUrl());
    }

    public static RequestSpecification multipartRequestSpec(String baseUri) {
        return defaultRequestSpec(baseUri).noContentType();
    }

    private static RestAssuredConfig timeoutConfig(ConfigManager config) {
        return RestAssuredConfig.config().httpClient(
                HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", config.getConnectTimeoutMs())
                        .setParam("http.socket.timeout", config.getReadTimeoutMs()));
    }
}

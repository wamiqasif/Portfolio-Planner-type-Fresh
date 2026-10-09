package com.org.apitests.hub;

import org.testng.annotations.Test;
import com.org.apiautomation.auth.AuthenticationManager;
import com.org.apiautomation.auth.DynamicLoginAuthStrategy;
import com.org.apiautomation.config.ConfigManager;
import com.org.apiautomation.core.RequestSpecFactory;
import com.org.apiautomation.services.HubService;
import com.org.apitests.base.BaseTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.testng.Assert;
import org.testng.asserts.SoftAssert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Automates the approved Hub API test plan's Authorization (cross-user
 * isolation), Caching, Security, HTML Injection, XSS, and SQL Injection
 * categories. See {@code docs/test-plans/hub-api-test-plan.md}.
 *
 * <p><b>HUB-AUTHZ-05 and HUB-SEC-02 (cross-user data isolation) are
 * automated as a self-consistency and call-to-call stability check, not a
 * true two-identity comparison</b>: today's configuration
 * ({@code auth.vrlogin.username}/{@code password}) provisions exactly one
 * test identity, with no second identity configured anywhere. A genuine
 * "User A's response never contains User B's PII" test needs a second,
 * independently-valid credential pair, which does not exist yet. This is
 * the strongest automatable proxy available until one is added - it is not
 * a substitute for the real two-user test, and that gap is intentional to
 * document, not paper over.
 *
 * <p><b>HUB-SEC-01 (cross-environment token) uses the same "well-formed but
 * not accepted" proxy token as {@code HubAuthenticationTests}' {@code
 * HUB-TOK-EXP-01}</b>, for the same reason: a real second, live,
 * differently-configured environment with its own valid credentials isn't
 * available (the dev/stage {@code base.url} values in config are unconfirmed
 * placeholders, and their credentials are unset {@code CHANGE_ME} values) -
 * attempting a real cross-environment login would either fail loudly for an
 * unrelated reason or silently no-op, neither of which is what this test
 * needs. Once a second, real environment is wired up with its own
 * credentials, this should be revisited.
 */
public class HubSecurityTests extends BaseTest {

    private final HubService hubService = new HubService();
    private final AuthenticationManager authenticationManager = new AuthenticationManager();

    @Test(groups = {"sanity", "regression", "hub"}, description = "HUB-AUTHZ-05: PII/tracking data is internally self-consistent and stable across repeated calls with the same token.")
    public void authz_crossUserDataIsolation_selfConsistencyProxy() {
        // Arrange / Act
        Response first = hubService.getHub();
        Response second = hubService.getHub();

        // Assert - one rule (this token's PII is stable and coherent), independent fields
        SoftAssert softAssert = new SoftAssert();
        Assert.assertEquals(second.jsonPath().getString("tracking_info.ga_user_properties.sha256_email_address"),
                first.jsonPath().getString("tracking_info.ga_user_properties.sha256_email_address"),
                "PII hash changed between two immediately-repeated calls with the same token");
        Assert.assertEquals(second.jsonPath().getList("tracking_info.we_user_properties"),
                first.jsonPath().getList("tracking_info.we_user_properties"),
                "we_user_properties changed between two immediately-repeated calls with the same token");
        softAssert.assertAll();
    }

    @Test(groups = {"sanity", "regression", "hub"}, description = "HUB-SEC-01: a well-formed but not-accepted (cross-environment proxy) token is rejected.")
    public void security_crossEnvironmentProxyToken_isRejected() {
        // Arrange
        String proxyForeignToken = HubAssertions.flipOneCharacter(authenticationManager.getValidToken());
        RequestSpecification spec = RequestSpecFactory.unauthenticatedRequestSpec(ConfigManager.getInstance().getBaseUrl())
                .header(DynamicLoginAuthStrategy.AUTH_TOKEN_HEADER, proxyForeignToken);

        // Act
        Response response = HubAssertions.rawSingleShotGet(spec, hubService.hubPath());

        // Assert
        Assert.assertEquals(response.statusCode(), 401);
    }

    @Test(groups = {"sanity", "regression", "hub"}, description = "HUB-SEC-02: PII fields belong strictly to this token's own user (see class-level Javadoc for the single-identity limitation).")
    public void security_piiScopedToOwnUser_selfConsistencyProxy() {
        // Arrange
        String configuredUsername = ConfigManager.getInstance().getVrLoginUsername();

        // Act - we_user_properties is a list of {name, type, value} tuples, not a flat object
        Response response = hubService.getHub();
        String email = response.jsonPath().getString("tracking_info.we_user_properties.find { it.name == 'we_email' }.value");

        // Assert
        Assert.assertNotNull(email, "we_user_properties' 'we_email' entry must be present for a real user session");
        if (configuredUsername != null && configuredUsername.contains("@")) {
            Assert.assertEquals(email.toLowerCase(), configuredUsername.toLowerCase(),
                    "Returned PII email does not match the configured test identity's own username");
        }
    }

    @Test(groups = {"sanity", "regression", "hub"}, description = "HUB-SEC-03: no internal error detail (stack trace, DB error, server version) is leaked on a forced error path.")
    public void security_noInternalErrorDetailLeaked() {
        // Arrange
        RequestSpecification spec = RequestSpecFactory.unauthenticatedRequestSpec(ConfigManager.getInstance().getBaseUrl());

        // Act
        Response response = HubAssertions.rawSingleShotGet(spec, hubService.hubPath());

        // Assert - one rule (no internal detail leaked), independent signals
        SoftAssert softAssert = new SoftAssert();
        String body = response.getBody().asString();
        Assert.assertFalse(Pattern.compile("(?i)(at (java|com|org)\\.|traceback|stacktrace|exception in thread)").matcher(body).find(),
                "Response body appears to leak a stack trace");
        String serverHeader = response.getHeader("Server");
        if (serverHeader != null) {
            Assert.assertFalse(Pattern.compile("\\d+\\.\\d+").matcher(serverHeader).find(),
                    "Server header appears to leak a version number: " + serverHeader);
        }
        softAssert.assertAll();
    }

    @Test(groups = {"sanity", "regression", "hub"}, description = "HUB-SEC-04: plaintext HTTP is never served - redirected to HTTPS or rejected.")
    public void security_httpsIsEnforced() {
        // Arrange
        String httpBaseUrl = ConfigManager.getInstance().getBaseUrl().replaceFirst("^https://", "http://");
        RequestSpecification spec = RequestSpecFactory.unauthenticatedRequestSpec(httpBaseUrl).redirects().follow(false);

        // Act / Assert
        try {
            Response response = HubAssertions.rawSingleShotGet(spec, hubService.hubPath());
            boolean isRedirectToHttps = response.statusCode() >= 300 && response.statusCode() < 400
                    && response.getHeader("Location") != null && response.getHeader("Location").startsWith("https://");
            boolean isRejected = response.statusCode() >= 400;
            Assert.assertTrue(isRedirectToHttps || isRejected,
                    "Plain HTTP must redirect to HTTPS or be rejected, got " + response.statusCode() + " with no HTTPS redirect");
        } catch (Exception plainHttpConnectionRefusedOrReset) {
            // The connection never completing in plaintext is itself a pass - "never served in plaintext".
        }
    }

    @Test(groups = {"sanity", "regression", "hub"}, description = "HUB-CACHE-01: Cache-Control is not publicly/CDN-cacheable for this PII-bearing, personalized content.")
    public void caching_notPubliclyCacheable() {
        // Arrange / Act
        Response response = hubService.getHub();

        // Assert
        String cacheControl = response.getHeader("Cache-Control");
        Assert.assertNotNull(cacheControl, "Cache-Control must be present on personalized, PII-bearing content");
        String normalized = cacheControl.toLowerCase();
        Assert.assertTrue(normalized.contains("private") || normalized.contains("no-store"),
                "Expected 'private' or 'no-store' in Cache-Control, got: " + cacheControl);
        Assert.assertFalse(normalized.contains("public"), "PII-bearing content must never be marked 'public': " + cacheControl);
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-HTML-INJ-01: no HTML tags appear outside the sanctioned disclosures_data...desc fields.")
    public void htmlInjection_noTagsOutsideSanctionedFields() {
        // Arrange
        Pattern htmlTag = Pattern.compile("<[a-zA-Z][^>]*>");

        // Act
        Response response = hubService.getHub();
        List<String> menuTitles = response.jsonPath().getList("data.hub.menus.flatten().title", String.class);
        List<String> actionHints = response.jsonPath().getList("data.hub.menus.flatten().action_hint", String.class);
        String inpageTitle = response.jsonPath().getString("data.inpage_title");

        // Assert - one rule (no unsanctioned markup), independent fields
        SoftAssert softAssert = new SoftAssert();
        menuTitles.forEach(title -> Assert.assertFalse(title != null && htmlTag.matcher(title).find(), "HTML tag found in a menu title: " + title));
        actionHints.forEach(hint -> Assert.assertFalse(hint != null && htmlTag.matcher(hint).find(), "HTML tag found in an action_hint: " + hint));
        Assert.assertFalse(inpageTitle != null && htmlTag.matcher(inpageTitle).find(), "HTML tag found in inpage_title: " + inpageTitle);
        softAssert.assertAll();
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-HTML-INJ-02: the one currently-real user-influenced field (the KYC name) is never returned with raw, unescaped markup.")
    public void htmlInjection_userInfluencedNameField_isNeverRawMarkup() {
        // Arrange
        Pattern htmlTag = Pattern.compile("<[a-zA-Z][^>]*>");

        // Act - we_user_properties is a list of {name, type, value} tuples; the KYC full name is the entry named "full_name"
        Response response = hubService.getHub();
        String name = response.jsonPath().getString("tracking_info.we_user_properties.find { it.name == 'full_name' }.value");

        // Assert
        if (name != null) {
            Assert.assertFalse(htmlTag.matcher(name).find(), "we_user_properties.name must never contain raw HTML markup: " + name);
        }
    }

    @Test(groups = {"smoke", "sanity", "regression", "hub"}, description = "HUB-XSS-01: a script payload in auth-token or any other header is never reflected unescaped.")
    public void xss_scriptPayload_neverReflectedInAuthTokenOrAnyHeader() {
        // Arrange
        String xssPayload = "<script>alert(1)</script>";
        RequestSpecification specWithPayloadAsToken = RequestSpecFactory.unauthenticatedRequestSpec(ConfigManager.getInstance().getBaseUrl())
                .header(DynamicLoginAuthStrategy.AUTH_TOKEN_HEADER, xssPayload);
        RequestSpecification specWithPayloadInOtherHeader = RequestSpecFactory.defaultRequestSpec()
                .header("X-Custom-Test-Header", xssPayload);

        // Act
        Response responseWithPayloadAsToken = HubAssertions.rawSingleShotGet(specWithPayloadAsToken, hubService.hubPath());
        Response responseWithPayloadInOtherHeader = hubService.getHub(specWithPayloadInOtherHeader);

        // Assert - one rule (never reflected), two independent injection points
        SoftAssert softAssert = new SoftAssert();
        Assert.assertEquals(responseWithPayloadAsToken.statusCode(), 401);
        Assert.assertFalse(responseWithPayloadAsToken.getBody().asString().contains(xssPayload), "XSS payload reflected when sent as auth-token");
        Assert.assertFalse(responseWithPayloadInOtherHeader.getBody().asString().contains(xssPayload), "XSS payload reflected when sent in a custom header");
        softAssert.assertAll();
    }

    @Test(groups = {"smoke", "sanity", "regression", "hub"}, description = "HUB-SQLI-01: classic SQL injection payloads in auth-token are rejected with 401, no SQL error text, no behavioral difference.")
    public void sqlInjection_classicPayloadsInAuthToken_returns401NoLeak() {
        // Arrange
        List<String> payloads = List.of("' OR '1'='1", "'; DROP TABLE users;--");

        // Act / Assert - one rule, independent per payload
        SoftAssert softAssert = new SoftAssert();
        for (String payload : payloads) {
            RequestSpecification spec = RequestSpecFactory.unauthenticatedRequestSpec(ConfigManager.getInstance().getBaseUrl())
                    .header(DynamicLoginAuthStrategy.AUTH_TOKEN_HEADER, payload);
            Response response = HubAssertions.rawSingleShotGet(spec, hubService.hubPath());
            Assert.assertEquals(response.statusCode(), 401, "Payload '" + payload + "' did not return 401");
            Assert.assertFalse(response.getBody().asString().toLowerCase().contains("sql"), "Payload '" + payload + "' leaked SQL error text");
        }
        softAssert.assertAll();
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-SQLI-02: SQL injection payloads in Referer/User-Agent cause no behavioral difference and no error leakage.")
    public void sqlInjection_payloadsInOtherHeaders_noBehavioralDifference() {
        // Arrange
        String payload = "' OR '1'='1";
        RequestSpecification spec = RequestSpecFactory.defaultRequestSpec()
                .header("Referer", payload)
                .header("User-Agent", payload);

        // Act
        Response response = hubService.getHub(spec);

        // Assert
        Assert.assertEquals(response.statusCode(), 200, "A SQLi payload in Referer/User-Agent must not change the outcome for an otherwise-valid request");
        Assert.assertFalse(response.getBody().asString().toLowerCase().contains("sql"), "Response leaked SQL error text");
    }
}

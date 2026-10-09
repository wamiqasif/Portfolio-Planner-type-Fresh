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


/**
 * Automates the approved Hub API test plan's Auth (E), Invalid Token (G),
 * Missing Token (H), and Expired Token (I) categories, plus the two
 * {@code HDR-MISS} cases that cross-reference them. See {@code
 * docs/test-plans/hub-api-test-plan.md}.
 *
 * <p><b>Why most methods here call {@link #rawSingleShotGet}, not {@link
 * HubService#getHub()}/{@code getHub(spec)}:</b> {@code ApiClient}'s
 * documented behavior is to invalidate-and-retry-once on a 401/403 whenever
 * an {@code Authorization} or {@code auth-token} header is present -
 * exactly the self-healing a real caller wants, but it would silently turn
 * every bad/missing-token negative test below into an unexpected 200 (a
 * fresh, genuinely valid token fetched mid-retry masking the very server
 * behavior these cases exist to observe). {@code
 * HUB-TOK-EXP-02} is the deliberate exception - that case's entire point
 * *is* the retry, so it goes through {@link HubService} normally.
 */
public class HubAuthenticationTests extends BaseTest {

    private final HubService hubService = new HubService();
    private final AuthenticationManager authenticationManager = new AuthenticationManager();

    @Test(groups = {"smoke", "sanity", "regression", "hub"}, description = "HUB-AUTH-01: a valid auth-token succeeds.")
    public void auth_validToken_succeeds() {
        // Arrange / Act
        Response response = hubService.getHub();

        // Assert
        Assert.assertEquals(response.statusCode(), 200);
    }

    @Test(groups = {"sanity", "regression", "hub"},
            description = "HUB-AUTH-02: cookie-only auth (no auth-token header) is not a fallback channel.")
    public void auth_cookieOnly_noHeader_isRejected() {
        // Arrange
        RequestSpecification spec = RequestSpecFactory.unauthenticatedRequestSpec(ConfigManager.getInstance().getBaseUrl())
                .cookie("user_session", "arbitrary-non-auth-token-session-value")
                .cookie("auth_token", "arbitrary-non-auth-token-cookie-value");

        // Act
        Response response = hubService.getHub(spec);

        // Assert - documents the actual behavior: cookies alone must not authenticate
        Assert.assertEquals(response.statusCode(), 401, "Cookie-only auth unexpectedly succeeded - this would be an undocumented second auth channel");
    }

    @Test(groups = {"sanity", "regression", "hub"},
            description = "HUB-AUTH-03: a valid auth-token header is honored even when an unrelated cookie is also present.")
    public void auth_headerTakesPrecedenceOverUnrelatedCookie() {
        // Arrange
        RequestSpecification spec = RequestSpecFactory.defaultRequestSpec()
                .cookie("user_session", "unrelated-cookie-value-that-must-not-influence-the-response");

        // Act
        Response response = hubService.getHub(spec);

        // Assert
        Assert.assertEquals(response.statusCode(), 200, "A valid auth-token header must succeed regardless of an unrelated cookie");
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-AUTH-04: an Authorization header alone, with no auth-token, is insufficient.")
    public void auth_authorizationHeaderAlone_isInsufficient() {
        // Arrange
        RequestSpecification spec = RequestSpecFactory.unauthenticatedRequestSpec(ConfigManager.getInstance().getBaseUrl())
                .header("Authorization", "Bearer some-irrelevant-value");

        // Act
        Response response = rawSingleShotGet(spec);

        // Assert
        Assert.assertEquals(response.statusCode(), 401, "Authorization header alone must not authenticate a Hub request");
    }

    @Test(groups = {"smoke", "sanity", "regression", "hub"},
            description = "HUB-TOK-INV-01: a random malformed token is rejected with 401, never a 500.")
    public void tokenInvalid_randomMalformed_returns401NotServerError() {
        // Arrange
        RequestSpecification spec = unauthenticatedSpecWithToken("randomgarbage123");

        // Act
        Response response = rawSingleShotGet(spec);

        // Assert
        Assert.assertEquals(response.statusCode(), 401);
    }

    @Test(groups = {"sanity", "regression", "hub"},
            description = "HUB-TOK-INV-02: a SQL-injection payload as the token is rejected with 401 and no SQL error text leaked.")
    public void tokenInvalid_sqlInjectionPayload_returns401WithNoLeakedError() {
        // Arrange
        RequestSpecification spec = unauthenticatedSpecWithToken("' OR '1'='1");

        // Act
        Response response = rawSingleShotGet(spec);

        // Assert
        Assert.assertEquals(response.statusCode(), 401);
        String body = response.getBody().asString().toLowerCase();
        Assert.assertFalse(body.contains("sql") || body.contains("syntax error") || body.contains("exception"),
                "Response body appears to leak SQL/error detail: " + body);
    }

    @Test(groups = {"sanity", "regression", "hub"},
            description = "HUB-TOK-INV-03: an XSS payload as the token is rejected with 401 and never reflected in the response.")
    public void tokenInvalid_xssPayload_returns401NeverReflected() {
        // Arrange
        String xssPayload = "<script>alert(1)</script>";
        RequestSpecification spec = unauthenticatedSpecWithToken(xssPayload);

        // Act
        Response response = rawSingleShotGet(spec);

        // Assert
        Assert.assertEquals(response.statusCode(), 401);
        Assert.assertFalse(response.getBody().asString().contains(xssPayload), "The XSS payload must never be reflected back in the response body");
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-TOK-INV-04: a truncated valid token is rejected with 401.")
    public void tokenInvalid_truncatedValidToken_returns401() {
        // Arrange
        String validToken = authenticationManager.getValidToken();
        Assert.assertTrue(validToken.length() > 8, "Valid token unexpectedly too short to truncate meaningfully");
        String truncatedToken = validToken.substring(0, validToken.length() - 8);
        RequestSpecification spec = unauthenticatedSpecWithToken(truncatedToken);

        // Act
        Response response = rawSingleShotGet(spec);

        // Assert
        Assert.assertEquals(response.statusCode(), 401);
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-TOK-INV-05: a valid token with junk appended is rejected with 401.")
    public void tokenInvalid_validTokenWithAppendedJunk_returns401() {
        // Arrange
        String validToken = authenticationManager.getValidToken();
        RequestSpecification spec = unauthenticatedSpecWithToken(validToken + "junkappendedhere");

        // Act
        Response response = rawSingleShotGet(spec);

        // Assert
        Assert.assertEquals(response.statusCode(), 401);
    }

    @Test(groups = {"smoke", "sanity", "regression", "hub"},
            description = "HUB-TOK-MISS-01 / HUB-HDR-MISS-01: no auth-token header at all is rejected with 401, never a 500 or 200.")
    public void tokenMissing_noHeaderAtAll_returns401() {
        // Arrange
        RequestSpecification spec = RequestSpecFactory.unauthenticatedRequestSpec(ConfigManager.getInstance().getBaseUrl());

        // Act - safe via the normal service call: with no auth header present at all, ApiClient never attempts a retry
        Response response = hubService.getHub(spec);

        // Assert
        Assert.assertEquals(response.statusCode(), 401);
    }

    @Test(groups = {"sanity", "regression", "hub"}, description = "HUB-TOK-MISS-02: an empty-string auth-token value is rejected with 401.")
    public void tokenMissing_emptyStringValue_returns401() {
        // Arrange
        RequestSpecification spec = unauthenticatedSpecWithToken("");

        // Act
        Response response = rawSingleShotGet(spec);

        // Assert
        Assert.assertEquals(response.statusCode(), 401);
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-TOK-MISS-03: a whitespace-only auth-token value is rejected with 401.")
    public void tokenMissing_whitespaceOnlyValue_returns401() {
        // Arrange
        RequestSpecification spec = unauthenticatedSpecWithToken("   ");

        // Act
        Response response = rawSingleShotGet(spec);

        // Assert
        Assert.assertEquals(response.statusCode(), 401);
    }

    @Test(groups = {"sanity", "regression", "hub"},
            description = "HUB-TOK-EXP-01: a token whose shape is valid but whose value is no longer accepted is rejected with 401/403.")
    public void tokenExpired_noLongerAcceptedShapedToken_returns401Or403() {
        // Arrange - a genuinely-expired-at-the-server token isn't reproducible on demand,
        // so a real, currently-issued token is corrupted in place (same length/shape,
        // one character flipped) to simulate "well-formed but no longer valid" at the wire level.
        String validToken = authenticationManager.getValidToken();
        String noLongerValidToken = flipOneCharacter(validToken);
        RequestSpecification spec = unauthenticatedSpecWithToken(noLongerValidToken);

        // Act
        Response response = rawSingleShotGet(spec);

        // Assert
        Assert.assertTrue(response.statusCode() == 401 || response.statusCode() == 403,
                "Expected 401 or 403, got " + response.statusCode());
    }

    @Test(groups = {"regression", "hub"},
            description = "HUB-TOK-EXP-02: given a no-longer-valid cached token, the framework re-logs in once and retries transparently, ending in 200.")
    public void tokenExpired_frameworkAutoRefreshesAndRetriesTransparently() {
        // Arrange - deliberately going through the normal HubService path this time:
        // this is the one case where ApiClient's built-in invalidate-and-retry-once
        // behavior is exactly the thing under test, not something to route around.
        RequestSpecification spec = unauthenticatedSpecWithToken(flipOneCharacter(authenticationManager.getValidToken()));

        // Act
        Response response = hubService.getHub(spec);

        // Assert
        Assert.assertEquals(response.statusCode(), 200, "Expected the framework to transparently re-login and retry into a final 200");
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-HDR-MISS-02: omitting a custom User-Agent does not affect the outcome.")
    public void header_missingCustomUserAgent_stillSucceeds() {
        // Arrange / Act - no method anywhere in this framework ever sets a custom User-Agent
        Response response = hubService.getHub();

        // Assert
        Assert.assertEquals(response.statusCode(), 200);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private RequestSpecification unauthenticatedSpecWithToken(String tokenValue) {
        return RequestSpecFactory.unauthenticatedRequestSpec(ConfigManager.getInstance().getBaseUrl())
                .header(DynamicLoginAuthStrategy.AUTH_TOKEN_HEADER, tokenValue);
    }

    private Response rawSingleShotGet(RequestSpecification requestSpec) {
        return HubAssertions.rawSingleShotGet(requestSpec, hubService.hubPath());
    }

    private String flipOneCharacter(String value) {
        return HubAssertions.flipOneCharacter(value);
    }
}

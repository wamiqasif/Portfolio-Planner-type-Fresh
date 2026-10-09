package com.org.apitests.hub;

import org.testng.annotations.Test;
import com.org.apiautomation.config.ConfigManager;
import com.org.apiautomation.core.RequestSpecFactory;
import com.org.apiautomation.services.HubService;
import com.org.apitests.base.BaseTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.testng.Assert;
import org.testng.asserts.SoftAssert;
import org.testng.annotations.Test;

/**
 * Automates the approved Hub API test plan's Headers (D), HTTP Method (F),
 * Invalid Headers (K), Accept Header (L), and Content-Type (M) categories.
 * See {@code docs/test-plans/hub-api-test-plan.md}.
 */
public class HubHeaderAndMethodTests extends BaseTest {

    private final HubService hubService = new HubService();

    @Test(groups = {"regression", "hub"}, description = "HUB-HDR-01: mandatory response headers are present.")
    public void headers_mandatoryResponseHeaders_present() {
        // Arrange / Act
        Response response = hubService.getHub();

        // Assert
        String contentType = response.contentType();
        Assert.assertNotNull(contentType, "Content-Type header must be present");
        Assert.assertTrue(contentType.contains("application/json"), "Expected application/json, got: " + contentType);
    }

    @Test(groups = {"smoke", "sanity", "regression", "hub"}, description = "HUB-HDR-02: the auth-token credential is never echoed back in any response header.")
    public void headers_credentialNeverEchoedBack() {
        // Arrange / Act
        Response response = hubService.getHub();

        // Assert
        response.getHeaders().asList().forEach(header ->
                Assert.assertFalse(header.getName().equalsIgnoreCase("auth-token"),
                        "auth-token must never be echoed back as a response header"));
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-HDR-03: CORS headers are sane for a cross-origin webview request.")
    public void headers_corsHeaders_areSaneForCrossOrigin() {
        // Arrange
        RequestSpecification spec = RequestSpecFactory.defaultRequestSpec()
                .header("Origin", "https://qa.valueresearch.in");

        // Act
        Response response = hubService.getHub(spec);

        // Assert - one rule (CORS is safely configured), independent facets
        SoftAssert softAssert = new SoftAssert();
        String allowOrigin = response.getHeader("Access-Control-Allow-Origin");
        String allowCredentials = response.getHeader("Access-Control-Allow-Credentials");
        if (allowOrigin != null) {
            boolean wildcardWithCredentials = "*".equals(allowOrigin) && "true".equalsIgnoreCase(allowCredentials);
            Assert.assertFalse(wildcardWithCredentials, "Access-Control-Allow-Origin: * must never be combined with credentialed requests");
        }
        softAssert.assertAll();
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-HDR-04: baseline security headers are present.")
    public void headers_baselineSecurityHeaders_present() {
        // Arrange / Act
        Response response = hubService.getHub();

        // Assert
        String nosniff = response.getHeader("X-Content-Type-Options");
        Assert.assertEquals(nosniff, "nosniff", "Expected X-Content-Type-Options: nosniff per the platform security baseline");
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-METH-01: POST is rejected with 405.")
    public void method_post_isRejected() {
        // Arrange / Act
        Response response = hubService.postToHub();

        // Assert
        Assert.assertEquals(response.statusCode(), 405);
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-METH-02: PUT is rejected with 405.")
    public void method_put_isRejected() {
        // Arrange / Act
        Response response = hubService.putToHub();

        // Assert
        Assert.assertEquals(response.statusCode(), 405);
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-METH-03: DELETE is rejected with 405.")
    public void method_delete_isRejected() {
        // Arrange / Act
        Response response = hubService.deleteHub();

        // Assert
        Assert.assertEquals(response.statusCode(), 405);
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-METH-04: PATCH is rejected with 405.")
    public void method_patch_isRejected() {
        // Arrange / Act
        Response response = hubService.patchToHub();

        // Assert
        Assert.assertEquals(response.statusCode(), 405);
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-METH-05: HEAD behaves consistently - either 200 with no body, or 405.")
    public void method_head_isConsistent() {
        // Arrange / Act
        Response response = hubService.headHub();

        // Assert
        Assert.assertTrue(response.statusCode() == 200 || response.statusCode() == 405,
                "Expected 200 (headers-only) or 405, got " + response.statusCode());
        if (response.statusCode() == 200) {
            Assert.assertTrue(response.getBody().asByteArray().length == 0, "HEAD must never return a body");
        }
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-METH-06: OPTIONS/CORS preflight never returns a server error.")
    public void method_options_neverServerErrors() {
        // Arrange / Act
        Response response = hubService.optionsHub();

        // Assert
        Assert.assertTrue(response.statusCode() < 500, "OPTIONS must never return a 5xx, got " + response.statusCode());
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-HDR-INV-01: an unrecognized custom header is ignored.")
    public void headerInvalid_unrecognizedCustomHeader_isIgnored() {
        // Arrange
        RequestSpecification spec = RequestSpecFactory.defaultRequestSpec()
                .header("X-Totally-Made-Up-Header", "some-value");

        // Act
        Response response = hubService.getHub(spec);

        // Assert
        Assert.assertEquals(response.statusCode(), 200);
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-HDR-INV-02: an oversized header value fails gracefully, never a server error.")
    public void headerInvalid_oversizedHeaderValue_failsGracefully() {
        // Arrange
        RequestSpecification spec = RequestSpecFactory.defaultRequestSpec()
                .header("X-Oversized-Header", "a".repeat(16 * 1024));

        // Act
        Response response = hubService.getHub(spec);

        // Assert
        Assert.assertTrue(response.statusCode() == 400 || response.statusCode() == 431,
                "Expected a graceful 400/431 for an oversized header, got " + response.statusCode());
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-HDR-ACC-01: a non-JSON Accept header is handled consistently.")
    public void headerAccept_nonJson_isHandledConsistently() {
        // Arrange
        RequestSpecification spec = RequestSpecFactory.defaultRequestSpec().header("Accept", "text/xml");

        // Act
        Response response = hubService.getHub(spec);

        // Assert - either lenient (JSON served regardless) or strict (406) is acceptable, as long as it's not a server error
        Assert.assertTrue(response.statusCode() == 200 || response.statusCode() == 406,
                "Expected 200 (lenient) or 406 (strict), got " + response.statusCode());
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-HDR-ACC-02: a missing Accept header defaults to JSON.")
    public void headerAccept_missingEntirely_defaultsToJson() {
        // Arrange / Act - RequestSpecFactory never sets Accept explicitly
        Response response = hubService.getHub();

        // Assert
        Assert.assertEquals(response.statusCode(), 200);
        Assert.assertTrue(response.contentType() != null && response.contentType().contains("application/json"));
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-HDR-CT-01: a meaningless Content-Type on a bodyless GET is ignored.")
    public void headerContentType_meaninglessOnGet_isIgnored() {
        // Arrange
        RequestSpecification spec = RequestSpecFactory.defaultRequestSpec().contentType("application/xml");

        // Act
        Response response = hubService.getHub(spec);

        // Assert
        Assert.assertEquals(response.statusCode(), 200);
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-HDR-CT-02: a multipart Content-Type on a bodyless GET is ignored.")
    public void headerContentType_multipartOnGet_isIgnored() {
        // Arrange
        RequestSpecification spec = RequestSpecFactory.defaultRequestSpec(ConfigManager.getInstance().getBaseUrl())
                .noContentType().contentType("multipart/form-data");

        // Act
        Response response = hubService.getHub(spec);

        // Assert
        Assert.assertEquals(response.statusCode(), 200);
    }
}

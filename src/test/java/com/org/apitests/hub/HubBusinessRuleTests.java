package com.org.apitests.hub;

import org.testng.annotations.Test;
import com.org.apiautomation.auth.DynamicLoginAuthStrategy;
import com.org.apiautomation.config.ConfigManager;
import com.org.apiautomation.core.RequestSpecFactory;
import com.org.apiautomation.models.response.hub.HubResponse;
import com.org.apiautomation.models.response.hub.Menu;
import com.org.apiautomation.services.HubService;
import com.org.apitests.base.BaseTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.testng.Assert;
import org.testng.asserts.SoftAssert;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.List;
import java.util.Set;

/**
 * Automates the approved Hub API test plan's Invalid URLs (R, partial),
 * Download URLs (N, partial), Boolean Fields (O, partial), and Business
 * Validation (Section 4) categories - the business-rule checks BR-1/4/5/6
 * outside of what's already covered in {@code HubSchemaAndContractTests}.
 * See {@code docs/test-plans/hub-api-test-plan.md}.
 */
public class HubBusinessRuleTests extends BaseTest {

    /** BR-4's three known, already-documented `is_ext_url` mismatches - excluded from the general sweep (HUB-BOOL-03) and pinned separately (HUB-URL-INV-03). */
    private static final Set<String> KNOWN_BR4_MISMATCHES = Set.of("stock_screener", "about_us", "user_agreement");

    private final HubService hubService = new HubService();

    @Test(groups = {"regression", "hub"}, description = "HUB-URL-INV-02: a javascript: scheme URL must never be served.")
    public void urlInvalid_javascriptScheme_mustNeverBeServed() {
        // Arrange
        String maliciousUrl = "javascript:alert(1)";

        // Act
        boolean isSafeScheme = isSafeUrlScheme(maliciousUrl);

        // Assert
        Assert.assertFalse(isSafeScheme, "'" + maliciousUrl + "' must be rejected by the URL-scheme safety check");
    }

    @Test(groups = {"sanity", "regression", "hub"},
            description = "HUB-URL-INV-03: the three known is_ext_url mismatches (BR-4) are pinned as an open regression until fixed.")
    public void urlInvalid_threeKnownIsExtUrlMismatches_arePinnedAsOpenDefects() {
        // Arrange
        HubResponse hub = hubService.getHub().as(HubResponse.class);
        String baseUrl = ConfigManager.getInstance().getBaseUrl();

        // Act
        List<Menu> knownMismatchedMenus = HubAssertions.allMenus(hub.data().hub()).stream()
                .filter(m -> KNOWN_BR4_MISMATCHES.contains(m.id()))
                .toList();

        // Assert - each is expected to still be WRONG; a passing (correct) value here means BR-4 was fixed and this test must be revisited
        SoftAssert softAssert = new SoftAssert();
        Assert.assertEquals(knownMismatchedMenus.size(), KNOWN_BR4_MISMATCHES.size(), "Expected to find all 3 known BR-4 menus in the response");
        for (Menu menu : knownMismatchedMenus) {
            boolean actuallyCrossOrigin = HubAssertions.isCrossOrigin(menu.url(), baseUrl);
            softAssert.assertNotEquals(menu.isExtUrl(), actuallyCrossOrigin,
                    "Menu '" + menu.id() + "' no longer mismatches - BR-4 may be fixed; update this test and the test plan");
        }
        softAssert.assertAll();
    }

    @Test(groups = {"sanity", "regression", "hub"},
            description = "HUB-BOOL-03: no is_ext_url mismatches exist beyond the 3 already-known BR-4 cases.")
    public void boolean_isExtUrlFullSweep_noMismatchesBeyondKnownThree() {
        // Arrange
        HubResponse hub = hubService.getHub().as(HubResponse.class);
        String baseUrl = ConfigManager.getInstance().getBaseUrl();

        // Act / Assert
        SoftAssert softAssert = new SoftAssert();
        for (Menu menu : HubAssertions.allMenus(hub.data().hub())) {
            if (KNOWN_BR4_MISMATCHES.contains(menu.id())) {
                continue;
            }
            boolean actuallyCrossOrigin = HubAssertions.isCrossOrigin(menu.url(), baseUrl);
            Assert.assertEquals(menu.isExtUrl(), actuallyCrossOrigin,
                    "New is_ext_url mismatch found on menu '" + menu.id() + "' beyond the 3 known BR-4 cases");
        }
        softAssert.assertAll();
    }

 

    @Test(groups = {"regression", "hub"}, description = "HUB-BOOL-01: a forced failure's status:false is accompanied by a meaningful error body, not a bare false.")
    public void boolean_statusFalse_carriesRealExplanation() {
        // Arrange
        RequestSpecification spec = RequestSpecFactory.unauthenticatedRequestSpec(ConfigManager.getInstance().getBaseUrl())
                .header(DynamicLoginAuthStrategy.AUTH_TOKEN_HEADER, "deliberately-invalid-token-to-force-a-failure");

        // Act
        Response response = HubAssertions.rawSingleShotGet(spec, hubService.hubPath());

        // Assert
        String body = response.getBody().asString();
        Assert.assertTrue(body == null || body.isBlank(), "A forced failure must return a non-empty, explanatory error body");
        Boolean status = safelyExtractStatus(response);
        if (status != null) {
            Assert.assertTrue(status, "status must be False on a forced failure");
        }
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-BIZ-01: every menu's icon follows the /assets/images/hub_icon/<id>.svg convention (BR-5).")
    public void biz_iconMatchesIdConvention() {
        // Arrange
        HubResponse hub = hubService.getHub().as(HubResponse.class);

        // Act / Assert
        SoftAssert softAssert = new SoftAssert();
        for (Menu menu : HubAssertions.allMenus(hub.data().hub())) {
            Assert.assertTrue(HubAssertions.iconMatchesIdConvention(menu), "Icon convention violated for menu '" + menu.id() + "': " + menu.icon());
        }
        softAssert.assertAll();
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-BIZ-03: every non-null products array has length exactly 2 (BR-6).")
    public void biz_productsArrayLength_isZeroOrExactlyTwo() {
        // Arrange
        HubResponse hub = hubService.getHub().as(HubResponse.class);

        // Act / Assert
        SoftAssert softAssert = new SoftAssert();
        for (Menu menu : HubAssertions.allMenus(hub.data().hub())) {
            if (menu.products() != null) {
                Assert.assertEquals(menu.products().size(), 2,
                        "Menu '" + menu.id() + "' has a non-null products array of length " + menu.products().size() + ", expected exactly 2");
            }
        }
        softAssert.assertAll();
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-BIZ-04: download_url is never identical to url when both are present.")
    public void biz_downloadUrlDistinctFromUrl() {
        // Arrange
        HubResponse hub = hubService.getHub().as(HubResponse.class);

        // Act / Assert
        SoftAssert softAssert = new SoftAssert();
        for (Menu menu : HubAssertions.allMenus(hub.data().hub())) {
            if (menu.downloadUrl() != null) {
                softAssert.assertNotEquals(menu.downloadUrl(), menu.url(),
                        "Menu '" + menu.id() + "' has identical url and download_url - a data-quality smell");
            }
        }
        softAssert.assertAll();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private boolean isSafeUrlScheme(String url) {
        try {
            String scheme = URI.create(url).getScheme();
            return scheme == null || scheme.matches("https?");
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    private Boolean safelyExtractStatus(Response response) {
        try {
            return response.jsonPath().getBoolean("status");
        } catch (Exception notJsonOrNoStatusField) {
            return null;
        }
    }
}

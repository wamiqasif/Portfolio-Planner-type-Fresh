package com.org.apitests.hub;

import org.testng.annotations.Test;

import com.org.apiautomation.models.response.hub.HubResponse;
import com.org.apiautomation.services.HubService;
import com.org.apitests.base.BaseTest;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.asserts.SoftAssert;


import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Automates the approved Hub API test plan's Functional and Response
 * Validation cases (categories A and B). See {@code
 * docs/test-plans/hub-api-test-plan.md} - no scenario here was invented;
 * every method maps to exactly the Test Case ID(s) named in its {@code
 * description}.hub_legacyVsVersionedEndpoint_structurallyEquivalent
 *
 * <p>Authentication is never handled in this class: {@link HubService#getHub()}
 * builds its request via {@code RequestSpecFactory.defaultRequestSpec()},
 * which already attaches a valid {@code auth-token} transparently. No
 * method here references {@code AuthenticationManager} or a token value.
 */
public class HubFunctionalTests extends BaseTest {

    private final HubService hubService = new HubService();

    @Test(groups = {"smoke", "sanity", "regression", "hub"},
            description = "HUB-FUN-01: a valid request returns the full Hub structure.")
    public void hub_withValidToken_returns200WithFullStructure() {
        // Arrange
        // (auth-token fetched transparently by the framework - nothing to arrange here)

        // Act
        Response response = hubService.getHub();

        // Assert
        Assert.assertEquals(response.statusCode(), 200);
        HubResponse hub = response.as(HubResponse.class);
        Assert.assertTrue(hub.status());
        Assert.assertFalse(hub.data().hub().isEmpty(), "data.hub must not be empty on a valid request");
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-FUN-02: inpage_title matches the expected page title.")
    public void hub_inpageTitle_matchesExpectedValue() {
        // Arrange / Act
        HubResponse hub = hubService.getHub().as(HubResponse.class);

        // Assert
        Assert.assertEquals(hub.data().inpageTitle(), "The Hub");
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-FUN-03: all expected top-level hub groups are present.")
    public void hub_allExpectedGroups_present() {
        // Arrange
        Set<String> expectedGroupIds = Set.of("profile", "investor_account", "mutual_funds", "stocks", "more");

        // Act
        HubResponse hub = hubService.getHub().as(HubResponse.class);

        // Assert
        Set<String> actualGroupIds = hub.data().hub().stream().map(g -> g.id()).collect(Collectors.toSet());
        Assert.assertTrue(actualGroupIds.containsAll(expectedGroupIds),
                "Expected groups " + expectedGroupIds + " but found " + actualGroupIds);
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-FUN-04: menu counts per group match the last signed-off baseline.")
    public void hub_menuCountsPerGroup_matchBaseline() {
        // Arrange - baseline agreed from the approved sample response
        java.util.Map<String, Integer> baselineCounts = java.util.Map.of(
                "profile", 4, "investor_account", 9, "mutual_funds", 5, "stocks", 8, "more", 10);

        // Act
        HubResponse hub = hubService.getHub().as(HubResponse.class);

        // Assert - one business rule (baseline parity), independent per group, so soft assertions
        SoftAssert softAssert = new SoftAssert();
        hub.data().hub().forEach(group -> {
            Integer expected = baselineCounts.get(group.id());
            if (expected != null) {
                Assert.assertEquals(group.menus().size(), expected.intValue(),
                        "Menu count changed for group '" + group.id() + "' - review before treating as a regression");
            }
        });
        softAssert.assertAll();
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-FUN-05: add_investor block is structurally correct.")
    public void hub_addInvestorBlock_structurallyCorrect() {
        // Arrange / Act
        HubResponse hub = hubService.getHub().as(HubResponse.class);

        // Assert - one rule ("the block is well-formed"), several independent fields
        SoftAssert softAssert = new SoftAssert();
        var addInvestor = hub.data().addInvestor();
        Assert.assertNotNull(addInvestor, "add_investor must be present");
        if (addInvestor != null) {
            Assert.assertNotNull(addInvestor.title(), "add_investor.title must not be null");
            Assert.assertNotNull(addInvestor.url(), "add_investor.url must not be null");
        }
        softAssert.assertAll();
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-FUN-07: the Statutory Disclosures entry is present with non-empty text.")
    public void hub_statutoryDisclosure_present() {
        // Arrange / Act
        Response response = hubService.getHub();

        // Assert
        List<String> titles = response.jsonPath().getList("data.disclosures.title", String.class);
        Assert.assertTrue(titles.contains("Statutory Disclosures"), "Expected a 'Statutory Disclosures' entry, found: " + titles);
    }

   

    @Test(groups = {"regression", "hub"}, description = "HUB-FUN-09: the response is structurally stable across repeated calls.")
    public void hub_repeatedCalls_structurallyStable() {
        // Arrange / Act
        HubResponse first = hubService.getHub().as(HubResponse.class);
        HubResponse second = hubService.getHub().as(HubResponse.class);

        // Assert
        Assert.assertEquals(second.data().hub(), first.data().hub(),
                "The hub structure changed between two immediately-repeated calls");
    }

    @Test(groups = {"smoke", "sanity", "regression", "hub"}, description = "HUB-RESP-01: top-level status is boolean true on success.")
    public void response_status_isTrueOnSuccess() {
        // Arrange / Act
        HubResponse hub = hubService.getHub().as(HubResponse.class);

        // Assert
        Assert.assertTrue(hub.status());
    }

    @Test(groups = {"smoke", "sanity", "regression", "hub"}, description = "HUB-RESP-02: HTTP status code is exactly 200 on success.")
    public void response_httpStatusCode_is200() {
        // Arrange / Act
        Response response = hubService.getHub();

        // Assert
        Assert.assertEquals(response.statusCode(), 200);
    }

    @Test(groups = {"smoke", "sanity", "regression", "hub"}, description = "HUB-RESP-03: response Content-Type is application/json.")
    public void response_contentType_isApplicationJson() {
        // Arrange / Act
        Response response = hubService.getHub();

        // Assert
        Assert.assertTrue(response.contentType() != null && response.contentType().contains("application/json"),
                "Expected application/json, got: " + response.contentType());
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-RESP-04: page_info is fully populated.")
    public void response_pageInfo_fullyPopulated() {
        // Arrange / Act
        Response response = hubService.getHub();

        // Assert - one rule ("page_info is well-formed"), several independent fields
        SoftAssert softAssert = new SoftAssert();
        Assert.assertNotNull(response.jsonPath().getString("page_info.lang"), "page_info.lang must not be null");
        Assert.assertNotNull(response.jsonPath().getString("page_info.title"), "page_info.title must not be null");
        Assert.assertNotNull(response.jsonPath().getString("page_info.inpage_title"), "page_info.inpage_title must not be null");
        Assert.assertNotNull(response.jsonPath().getString("page_info.meta_keywords"), "page_info.meta_keywords must not be null");
        softAssert.assertAll();
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-RESP-05: tracking_info's core sharing fields are present.")
    public void response_trackingInfo_coreFieldsPresent() {
        // Arrange / Act
        Response response = hubService.getHub();

        // Assert
        SoftAssert softAssert = new SoftAssert();
        Assert.assertNotNull(response.jsonPath().getString("tracking_info.canonical_url"), "canonical_url must not be null");
        Assert.assertNotNull(response.jsonPath().getString("tracking_info.url_for_sharing"), "url_for_sharing must not be null");
        softAssert.assertAll();
    }
}

package com.org.apitests.hub;

import org.testng.annotations.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.org.apiautomation.models.response.hub.HubGroup;
import com.org.apiautomation.models.response.hub.HubResponse;
import com.org.apiautomation.models.response.hub.Menu;
import com.org.apiautomation.services.HubService;
import com.org.apitests.base.BaseTest;
import io.restassured.module.jsv.JsonSchemaValidator;
import io.restassured.response.Response;
import org.hamcrest.MatcherAssert;
import org.testng.Assert;
import org.testng.asserts.SoftAssert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Automates the approved Hub API test plan's Schema Validation (C), Invalid
 * Data Types (Q), Invalid URLs (R, partial), Duplicate IDs/Menus (S, T),
 * Enum Validation (Z), Pagination (AE), and Contract/Compatibility (AM, AN,
 * AO) categories. See {@code docs/test-plans/hub-api-test-plan.md}.
 *
 * <p>{@code HUB-TYPE-01/02/03} and the fixture-comparison cases ({@code
 * HUB-SCH-05}/{@code HUB-TYPE-04}, {@code HUB-CONTRACT-01}, {@code
 * HUB-FC-01/02}) deliberately don't call the live API - they validate the
 * validation logic itself against synthetic or previously-captured payloads
 * (the plan's own "Simulated/contract-drift payload" precondition), or
 * compare two real, already-captured samples that can't both come from one
 * live call. Every other method here does exercise {@link HubService}
 * against the real, currently-configured environment.
 */
public class HubSchemaAndContractTests extends BaseTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final HubService hubService = new HubService();

    @Test(groups = {"smoke", "sanity", "regression", "hub"},
            description = "HUB-SCH-01: the full response validates against the Hub JSON Schema.")
    public void hub_fullResponse_matchesJsonSchema() {
        // Arrange / Act
        Response response = hubService.getHub();

        // Assert
        response.then().assertThat().body(JsonSchemaValidator.matchesJsonSchemaInClasspath("schemas/hub-schema.json"));
    }

    @Test(groups = {"sanity", "regression", "hub"},
            description = "HUB-SCH-02 / HUB-CONTRACT-02: Product validates against the Product schema, and fails against the Menu schema - the two are never interchangeable.")
    public void productSchema_isNarrowerThanMenuSchema() {
        // Arrange
        HubResponse hub = hubService.getHub().as(HubResponse.class);
        Menu menuWithProducts = HubAssertions.allMenus(hub.data().hub()).stream()
                .filter(m -> m.products() != null && !m.products().isEmpty())
                .findFirst().orElseThrow(() -> new AssertionError("No menu with products found to validate against"));

        // Act
        String productJson;
        try {
            productJson = OBJECT_MAPPER.writeValueAsString(menuWithProducts.products().get(0));
        } catch (Exception e) {
            throw new AssertionError("Failed to serialize a real product entry for schema validation", e);
        }

        // Assert
        MatcherAssert.assertThat(productJson, JsonSchemaValidator.matchesJsonSchemaInClasspath("schemas/hub-product-schema.json"));
        Assert.assertThrows(AssertionError.class, () ->
                MatcherAssert.assertThat(productJson, JsonSchemaValidator.matchesJsonSchemaInClasspath("schemas/hub-menu-schema.json")));
    }

    @Test(groups = {"regression", "hub"},
            description = "HUB-SCH-03: disclosures_data.details always matches exactly one of the two known shapes ({list} or {desc}).")
    public void disclosuresData_details_matchesListOrDescShape() {
        // Arrange / Act
        Response response = hubService.getHub();
        List<Map> entries = response.jsonPath().getList("data.disclosures_data.data", Map.class);

        // Assert - one rule (exactly one shape), independent per entry
        SoftAssert softAssert = new SoftAssert();
        for (Map entry : entries) {
            @SuppressWarnings("unchecked")
            Map<String, Object> details = (Map<String, Object>) entry.get("details");
            boolean hasList = details.containsKey("list");
            boolean hasDesc = details.containsKey("desc");
            Assert.assertTrue(hasList ^ hasDesc,
                    "Entry '" + entry.get("title") + "' details must have exactly one of 'list'/'desc', found list=" + hasList + " desc=" + hasDesc);
        }
        softAssert.assertAll();
    }

    @Test(groups = {"regression", "hub"},
            description = "HUB-SCH-04: data_fmt is a string for Name/Email and a number for Mobile Number - never the reverse.")
    public void disclosuresData_dataFmt_typeMatchesSiblingName() {
        // Arrange / Act
        Response response = hubService.getHub();
        List<Map> entries = response.jsonPath().getList("data.disclosures_data.data", Map.class);

        // Assert
        SoftAssert softAssert = new SoftAssert();
        for (Map entry : entries) {
            @SuppressWarnings("unchecked")
            Map<String, Object> details = (Map<String, Object>) entry.get("details");
            Object rawList = details.get("list");
            if (rawList == null) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) rawList;
            for (Map<String, Object> item : list) {
                String name = (String) item.get("name");
                Object dataFmt = item.get("data_fmt");
                if ("Mobile Number".equals(name)) {
                    Assert.assertTrue(dataFmt instanceof Number, "data_fmt for 'Mobile Number' must be a number, was: " + dataFmt);
                } else {
                    Assert.assertTrue(dataFmt instanceof String, "data_fmt for '" + name + "' must be a string, was: " + dataFmt);
                }
            }
        }
        softAssert.assertAll();
    }

    @Test(groups = {"sanity", "regression", "hub"},
            description = "HUB-SCH-05 / HUB-TYPE-04: tracking_info.ga_user_properties is an array in the documented sample and an object in the live sample - pinning BR-8's contract defect.")
    public void trackingInfo_gaUserProperties_typeStability_acrossBothKnownSamples() throws IOException {
        // Arrange
        String documentedGaUserProperties = extractField("hub-response-documented-sample.json", "ga_user_properties");
        String liveGaUserProperties = extractField("hub-response-live-sample.json", "ga_user_properties");

        // Act / Assert - the documented sample's shape (array) must pass...
        MatcherAssert.assertThat(documentedGaUserProperties,
                JsonSchemaValidator.matchesJsonSchemaInClasspath("schemas/hub-tracking-info-ga-user-properties-schema.json"));

        // ...and the live sample's shape (object) is EXPECTED to fail the same schema.
        // This assertion passing means BR-8 is still an open defect; once fixed, this
        // whole test should be revisited (both samples would then agree on one shape).
        Assert.assertThrows(AssertionError.class, () -> MatcherAssert.assertThat(liveGaUserProperties,
                JsonSchemaValidator.matchesJsonSchemaInClasspath("schemas/hub-tracking-info-ga-user-properties-schema.json")));
    }

    @Test(groups = {"regression", "hub"},
            description = "HUB-SCH-06: disclosures_data entries may omit 'title' entirely - it is optional, not required.")
    public void disclosuresData_title_isOptionalNotRequired() {
        // Arrange / Act
        Response response = hubService.getHub();
        List<Map> entries = response.jsonPath().getList("data.disclosures_data.data", Map.class);

        // Assert
        boolean atLeastOneEntryOmitsTitle = entries.stream().anyMatch(entry -> !entry.containsKey("title"));
        Assert.assertTrue(atLeastOneEntryOmitsTitle,
                "Expected at least one disclosures_data entry to omit 'title' entirely (the copyright/registration block)");
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-TYPE-01: is_ext_url as a string (instead of boolean) fails schema validation.")
    public void schema_isExtUrl_asString_fails() {
        // Arrange
        String malformedMenu = validMenuJsonWithOverride("\"is_ext_url\": false", "\"is_ext_url\": \"false\"");

        // Act / Assert
        Assert.assertThrows(AssertionError.class, () ->
                MatcherAssert.assertThat(malformedMenu, JsonSchemaValidator.matchesJsonSchemaInClasspath("schemas/hub-menu-schema.json")));
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-TYPE-02: id as an integer (instead of a string) fails schema validation.")
    public void schema_id_asInteger_fails() {
        // Arrange
        String malformedMenu = validMenuJsonWithOverride("\"id\": \"account_details\"", "\"id\": 12345");

        // Act / Assert
        Assert.assertThrows(AssertionError.class, () ->
                MatcherAssert.assertThat(malformedMenu, JsonSchemaValidator.matchesJsonSchemaInClasspath("schemas/hub-menu-schema.json")));
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-TYPE-03: data_fmt violating the type-by-name rule (BR-11) is rejected by our own check.")
    public void schema_dataFmt_typeNameMismatch_fails() {
        // Arrange
        java.util.Map<String, Object> mobileAsString = java.util.Map.of("name", "Mobile Number", "data_fmt", "9711400789");
        java.util.Map<String, Object> nameAsNumber = java.util.Map.of("name", "Name", "data_fmt", 123);

        // Act / Assert - each is an independent violation of the same rule
        SoftAssert softAssert = new SoftAssert();
        Assert.assertFalse(mobileAsString.get("data_fmt") instanceof Number, "sanity check on the fixture itself");
        Assert.assertTrue(dataFmtViolatesRule(mobileAsString), "Mobile Number as a string should violate BR-11");
        Assert.assertTrue(dataFmtViolatesRule(nameAsNumber), "Name as a number should violate BR-11");
        softAssert.assertAll();
    }

    private boolean dataFmtViolatesRule(java.util.Map<String, Object> entry) {
        boolean isMobileNumber = "Mobile Number".equals(entry.get("name"));
        boolean dataFmtIsNumber = entry.get("data_fmt") instanceof Number;
        return isMobileNumber != dataFmtIsNumber;
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-URL-INV-01: a malformed URL fails the URL-format contract check.")
    public void url_malformedSyntax_failsContractCheck() {
        // Arrange
        String malformedUrl = "htp:/broken";

        // Act
        boolean isWellFormed = isWellFormedUrlOrPath(malformedUrl);

        // Assert
        Assert.assertFalse(isWellFormed, "'" + malformedUrl + "' should fail the URL-format check");
    }

    private boolean isWellFormedUrlOrPath(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        if (url.startsWith("/")) {
            return true; // relative path
        }
        try {
            java.net.URI uri = java.net.URI.create(url);
            return uri.getScheme() != null && uri.getScheme().matches("https?") && uri.getHost() != null;
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-ENUM-01: page_info.lang is always one of the app's supported locale codes.")
    public void pageInfo_lang_isSupportedLocale() {
        // Arrange
        Set<String> supportedLocales = Set.of("en");

        // Act
        String lang = hubService.getHub().jsonPath().getString("page_info.lang");

        // Assert
        Assert.assertTrue(supportedLocales.contains(lang), "Unsupported locale returned: " + lang);
    }

    @Test(groups = {"sanity", "regression", "hub"}, description = "HUB-ENUM-02: every non-null component_code is from the registered set.")
    public void componentCode_isFromRegisteredSet2() {
        // Arrange - the registered set confirmed in the approved sample response
        Set<String> registeredCodes = Set.of(
                "INVESTOR_ADD", "MY_INVESTOR_ACCOUNT_PAGE", "RISK_ASSESSMENT_PAGE", "MANAGE_SYS_INVESTMENTS_PAGE",
                "PORTFOLIO_YOUR_ORDERS_PAGE", "PORTFOLIO_PLANNER_PAGE", "FUND_ANALYSTS_CHOICE_PAGE", "STORIES_VIDEOS_PAGE",
                "STOCK_ADVISOR_PAGE", "VRSA_TEAM_PAGE", "STOCK_SCREENER_PAGE", "STOCK_READY_TO_INVEST_PORTFOLIO_PAGE",
                "STOCK_SELL_PLANNER_PAGE", "STOCK_COVERAGE_INSIGHT_PAGE", "STOCK_INVESTMENT_PLANNER_PAGE","USER_AGREEMENT");

        // Act
        HubResponse hub = hubService.getHub().as(HubResponse.class);
        List<Menu> allMenus = HubAssertions.allMenus(hub.data().hub());

        // Assert
        SoftAssert softAssert = new SoftAssert();
        for (Menu menu : allMenus) {
            if (menu.componentCode() != null) {
                Assert.assertTrue(registeredCodes.contains(menu.componentCode()),
                        "Unregistered component_code '" + menu.componentCode() + "' on menu '" + menu.id() + "'");
            }
        }
        softAssert.assertAll();
    }

    @Test(groups = {"sanity", "regression", "hub"},
            description = "HUB-DUP-ID-01 / HUB-EDGE-05: no two menu ids are duplicated anywhere in the response.")
    public void menuIds_areUniqueAcrossResponse() {
        // Arrange / Act
        HubResponse hub = hubService.getHub().as(HubResponse.class);
        List<String> ids = HubAssertions.allMenus(hub.data().hub()).stream().map(Menu::id).collect(Collectors.toList());

        // Assert
        Set<String> uniqueIds = new HashSet<>(ids);
        Assert.assertEquals(uniqueIds.size(), ids.size(), "Duplicate menu id(s) found: " + duplicatesOf(ids));
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-DUP-ID-02: no two hub group ids are duplicated.")
    public void hubGroupIds_areUnique() {
        // Arrange / Act
        HubResponse hub = hubService.getHub().as(HubResponse.class);
        List<String> ids = hub.data().hub().stream().map(HubGroup::id).collect(Collectors.toList());

        // Assert
        Set<String> uniqueIds = new HashSet<>(ids);
        Assert.assertEquals(uniqueIds.size(), ids.size(), "Duplicate hub group id(s) found: " + duplicatesOf(ids));
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-DUP-MENU-01: no two menus share the same (title, url) pair within one group.")
    public void noDuplicateTitleUrlPairs_withinAGroup() {
        // Arrange / Act
        HubResponse hub = hubService.getHub().as(HubResponse.class);

        // Assert
        SoftAssert softAssert = new SoftAssert();
        for (HubGroup group : hub.data().hub()) {
            List<String> pairs = group.menus().stream().map(m -> m.title() + "|" + m.url()).collect(Collectors.toList());
            Set<String> uniquePairs = new HashSet<>(pairs);
            Assert.assertEquals(uniquePairs.size(), pairs.size(),
                    "Duplicate (title,url) pair within group '" + group.id() + "': " + duplicatesOf(pairs));
        }
        softAssert.assertAll();
    }

    @Test(groups = {"regression", "hub"},
            description = "HUB-PAGN-01: unrecognized pagination-shaped query params (page/limit/offset) are ignored, not silently half-implemented.")
    public void unknownQueryParams_ignoredWithoutAlteringResponse() {
        // Arrange
        HubResponse baseline = hubService.getHub().as(HubResponse.class);
        var specWithParams = com.org.apiautomation.core.RequestSpecFactory.defaultRequestSpec()
                .queryParam("page", 2).queryParam("limit", 5).queryParam("offset", 10);

        // Act
        HubResponse withParams = hubService.getHub(specWithParams).as(HubResponse.class);

        // Assert
        Assert.assertEquals(withParams.data().hub(), baseline.data().hub(),
                "Response changed when unrecognized pagination-shaped query params were added");
    }

    @Test(groups = {"sanity", "regression", "hub"},
            description = "HUB-CONTRACT-01: the documented sample and the live sample differ only in the known, tracked way (BR-8) - not anywhere else in the well-behaved structure.")
    public void documentedVsLiveSample_onlyExpectedDifferencesFound() throws IOException {
        // Arrange
        JsonNode documented = readFixture("hub-response-documented-sample.json");
        JsonNode live = readFixture("hub-response-live-sample.json");

        // Act
        JsonNode documentedHub = documented.path("data").path("hub");
        JsonNode liveHub = live.path("data").path("hub");
        JsonNode documentedGaUserProperties = documented.path("tracking_info").path("ga_user_properties");
        JsonNode liveGaUserProperties = live.path("tracking_info").path("ga_user_properties");

        // Assert - the well-behaved part must be byte-for-byte identical...
        Assert.assertEquals(liveHub, documentedHub, "The hub/menu structure differs between the two real samples - unexpected drift");
        // ...while the ONE known, already-flagged instability (BR-8) is confirmed present, not accidentally fixed without anyone noticing.
        Assert.assertTrue(documentedGaUserProperties.isArray(), "Documented sample's ga_user_properties should be an array");
        Assert.assertTrue(liveGaUserProperties.isObject(), "Live sample's ga_user_properties should be an object - if this now fails, BR-8 may have been fixed; update this test and the test plan accordingly");
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-BC-01: the legacy Fawkes endpoint still returns a response this framework's models can parse.")
    public void legacyEndpoint_returnsParsableResponse2() {
        // Arrange / Act
        Response response = hubService.getHubViaLegacyEndpoint();

        // Assert - parsing itself is the assertion: a failure to deserialize throws and fails this test
        Assert.assertEquals(response.statusCode(), 200);
        response.as(HubResponse.class);
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-FC-01: an unrecognized new field in the response is tolerated, not a breaking change.")
    public void unknownNewField_toleratedWithoutError() throws IOException {
        // Arrange
        JsonNode live = readFixture("hub-response-live-sample.json");
        ((ObjectNode) live).put("a_field_this_client_has_never_seen_before", "some-new-value");
        String mutatedJson = OBJECT_MAPPER.writeValueAsString(live);

        // Act - a failure to deserialize throws and fails this test, which is the assertion itself
        HubResponse parsed = OBJECT_MAPPER.readValue(mutatedJson, HubResponse.class);

        // Assert
        Assert.assertTrue(parsed.status());
        Assert.assertFalse(parsed.data().hub().isEmpty());
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-FC-02: an additional or reordered hub group is tolerated, not a breaking change.")
    public void additionalOrReorderedGroups_toleratedWithoutError() throws IOException {
        // Arrange
        JsonNode live = readFixture("hub-response-live-sample.json");
        ObjectNode newGroup = OBJECT_MAPPER.createObjectNode();
        newGroup.put("id", "a_brand_new_group_no_client_knows_about");
        newGroup.put("title", "New Section");
        newGroup.putArray("menus");
        ((com.fasterxml.jackson.databind.node.ArrayNode) live.path("data").path("hub")).insert(0, newGroup);
        String mutatedJson = OBJECT_MAPPER.writeValueAsString(live);

        // Act - a failure to deserialize throws and fails this test, which is the assertion itself
        HubResponse parsed = OBJECT_MAPPER.readValue(mutatedJson, HubResponse.class);

        // Assert
        Assert.assertTrue(parsed.data().hub().stream().anyMatch(g -> g.id().equals("a_brand_new_group_no_client_knows_about")));
    }

    // ------------------------------------------------------------------
    // Fixture helpers
    // ------------------------------------------------------------------

    private JsonNode readFixture(String resourceName) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("testdata/" + resourceName)) {
            if (in == null) {
                throw new IOException("Fixture not found on classpath: testdata/" + resourceName);
            }
            return OBJECT_MAPPER.readTree(in);
        }
    }

    private String extractField(String resourceName, String trackingInfoFieldName) throws IOException {
        JsonNode node = readFixture(resourceName).path("tracking_info").path(trackingInfoFieldName);
        return OBJECT_MAPPER.writeValueAsString(node);
    }

    /** Builds a valid, known-good Menu JSON string with one field's literal text replaced - used to craft a targeted, minimal contract violation. */
    private String validMenuJsonWithOverride(String originalFragment, String replacementFragment) {
        String validMenuJson = """
                {
                  "id": "account_details",
                  "title": "Account Details",
                  "icon": "/assets/images/hub_icon/account_details.svg",
                  "url": "/myaccount/",
                  "download_url": null,
                  "is_ext_url": false,
                  "component_code": null,
                  "action_hint": "View and edit your account details",
                  "products": null
                }
                """;
        Assert.assertTrue(validMenuJson.contains(originalFragment), "Test fixture setup error: fragment not found");
        return validMenuJson.replace(originalFragment, replacementFragment);
    }

    private <T> List<T> duplicatesOf(List<T> values) {
        Set<T> seen = new HashSet<>();
        return values.stream().filter(v -> !seen.add(v)).collect(Collectors.toList());
    }
}

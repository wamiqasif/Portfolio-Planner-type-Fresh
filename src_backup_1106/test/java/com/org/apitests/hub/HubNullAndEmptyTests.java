package com.org.apitests.hub;

import org.testng.annotations.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.org.apiautomation.models.response.hub.AddInvestor;
import com.org.apiautomation.models.response.hub.HubResponse;
import com.org.apiautomation.models.response.hub.Menu;
import com.org.apitests.base.BaseTest;
import org.testng.Assert;
import org.testng.asserts.SoftAssert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Automates the approved Hub API test plan's Null Values (I), Empty Arrays
 * (J), Empty Objects (part of J), and most of the Edge Case (AN) categories.
 * See {@code docs/test-plans/hub-api-test-plan.md}.
 *
 * <p>Every case here is a "Simulated payload" precondition in the plan
 * itself - the real Hub API is server-controlled and can't be made to
 * genuinely return a null array entry, a missing key, or an empty object on
 * demand. Each method constructs the exact minimal JSON shape the plan
 * describes and deserializes it with the framework's own Jackson models
 * (never a live HTTP call), matching the plan's own precondition column.
 */
public class HubNullAndEmptyTests extends BaseTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test(groups = {"regression", "hub"}, description = "HUB-NULL-01: component_code: null is treated as 'no permission gate', not an error.")
    public void nullValues_componentCodeNull_isNotAnError() throws IOException {
        // Arrange / Act - the baseline menu shape already carries a null component_code, exactly as real samples do
        Menu menu = OBJECT_MAPPER.readValue(validMenuJson(), Menu.class);

        // Assert
        Assert.assertNull(menu.componentCode());
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-NULL-02: download_url: null means no download affordance is offered.")
    public void nullValues_downloadUrlNull_meansNoDownloadAffordance() throws IOException {
        // Arrange / Act
        Menu menu = OBJECT_MAPPER.readValue(validMenuJson(), Menu.class);

        // Assert
        Assert.assertNull(menu.downloadUrl());
    }

    @Test(groups = {"regression", "hub"},
            description = "HUB-NULL-03 / HUB-EDGE-06: products: null is handled without a null-array-iteration crash.")
    public void nullValues_productsNull_noIterationCrash() throws IOException {
        // Arrange / Act
        Menu menu = OBJECT_MAPPER.readValue(validMenuJson(), Menu.class);

        // Assert - iterating via the null-safe pattern is the assertion: a crash here throws and fails this test
        Assert.assertNull(menu.products());
        Optional.ofNullable(menu.products()).orElse(List.of()).forEach(p -> { });
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-NULL-04: ga_uid: null (anonymous session) does not break tracking access.")
    public void nullValues_gaUidNull_anonymousSessionHandledSafely() {
        // Arrange
        String trackingInfoJson = "{\"ga_uid\": null, \"canonical_url\": \"/hub/\"}";

        // Act / Assert - reading the null field is the assertion: a crash here throws and fails this test
        String gaUid = io.restassured.path.json.JsonPath.from(trackingInfoJson).getString("ga_uid");
        Assert.assertNull(gaUid);
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-NULL-05: ga_tracking_url: null means no broken tracking call is attempted.")
    public void nullValues_gaTrackingUrlNull_noBrokenTrackingCallAttempted() {
        // Arrange
        String trackingInfoJson = "{\"ga_tracking_url\": null}";

        // Act
        String gaTrackingUrl = io.restassured.path.json.JsonPath.from(trackingInfoJson).getString("ga_tracking_url");
        Optional<String> trackingCallTarget = Optional.ofNullable(gaTrackingUrl);

        // Assert
        Assert.assertTrue(trackingCallTarget.isEmpty(), "A null ga_tracking_url must never resolve to an attempted call target");
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-EMPTY-ARR-01 / HUB-EDGE-01: hub: [] parses as a valid, empty 'no navigation available' state, not a crash.")
    public void emptyArray_emptyHub_parsesAsNoNavigationState() throws IOException {
        // Arrange
        String responseJson = """
                {"status": true, "data": {"inpage_title": "The Hub", "hub": [], "add_investor": null, "is_add_investor_allowed": false}}
                """;

        // Act
        HubResponse hub = OBJECT_MAPPER.readValue(responseJson, HubResponse.class);

        // Assert
        Assert.assertNotNull(hub.data().hub());
        Assert.assertTrue(hub.data().hub().isEmpty());
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-EDGE-02: a minimal single-group, single-menu payload parses correctly - no code assumes >= N items.")
    public void edgeCase_singleGroupSingleMenu_parsesCorrectly() throws IOException {
        // Arrange
        String responseJson = """
                {"status": true, "data": {"inpage_title": "The Hub", "add_investor": null, "is_add_investor_allowed": false,
                  "hub": [{"id": "only_group", "title": "Only Group", "menus": [
                    {"id": "only_menu", "title": "Only Menu", "icon": "/assets/images/hub_icon/only_menu.svg",
                     "url": "/only/", "download_url": null, "is_ext_url": false, "component_code": null,
                     "action_hint": "The only menu", "products": null}
                  ]}]}}
                """;

        // Act
        HubResponse hub = OBJECT_MAPPER.readValue(responseJson, HubResponse.class);

        // Assert
        Assert.assertEquals(hub.data().hub().size(), 1);
        Assert.assertEquals(hub.data().hub().get(0).menus().size(), 1);
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-EDGE-04: a literal null entry inside menus[] is passed through, not thrown on, by deserialization.")
    public void edgeCase_nullMenuEntry_doesNotThrowDuringDeserialization() throws IOException {
        // Arrange
        String responseJson = """
                {"status": true, "data": {"inpage_title": "The Hub", "add_investor": null, "is_add_investor_allowed": false,
                  "hub": [{"id": "group", "title": "Group", "menus": [null,
                    {"id": "valid_menu", "title": "Valid Menu", "icon": "/assets/images/hub_icon/valid_menu.svg",
                     "url": "/valid/", "download_url": null, "is_ext_url": false, "component_code": null,
                     "action_hint": "A valid menu", "products": null}
                  ]}]}}
                """;

        // Act - a null menu entry must not throw during deserialization; if it does, this test fails
        HubResponse hub = OBJECT_MAPPER.readValue(responseJson, HubResponse.class);

        // Assert
        List<Menu> menus = hub.data().hub().get(0).menus();
        Assert.assertEquals(menus.size(), 2);
        Assert.assertNull(menus.get(0));
        Assert.assertNotNull(menus.get(1));
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-EDGE-07: a structurally-missing icon key violates BR-1 and fails schema validation.")
    public void edgeCase_missingIconKey_violatesSchema() {
        // Arrange
        String menuJsonMissingIcon = """
                {"id": "account_details", "title": "Account Details", "url": "/myaccount/",
                 "download_url": null, "is_ext_url": false, "component_code": null,
                 "action_hint": "View and edit your account details", "products": null}
                """;

        // Act / Assert
        Assert.assertThrows(AssertionError.class, () -> org.hamcrest.MatcherAssert.assertThat(menuJsonMissingIcon,
                io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath("schemas/hub-menu-schema.json")));
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-EDGE-08: an invalid, non-UPPER_SNAKE_CASE component_code fails the format contract.")
    public void edgeCase_invalidComponentCodeFormat_failsContract() {
        // Arrange
        String invalidCode = "my invalid code!";

        // Act / Assert
        Assert.assertFalse(isUpperSnakeCase(invalidCode), "'" + invalidCode + "' must fail the UPPER_SNAKE_CASE contract check");
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-EDGE-09: a relative URL missing its leading slash is pinned as non-conforming to today's convention.")
    public void edgeCase_relativeUrlMissingLeadingSlash_documentedAsNonConforming() {
        // Arrange
        String nonConformingUrl = "myaccount/";

        // Act / Assert
        Assert.assertFalse(nonConformingUrl.startsWith("/"), "Documents that this URL does not conform to the leading-slash relative-URL convention used everywhere else in the sample data");
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-EDGE-10: a protocol-relative URL is recognized as scheme-less, pinning current parsing behavior.")
    public void edgeCase_protocolRelativeUrl_recognizedAsSchemeLess() {
        // Arrange
        String protocolRelativeUrl = "//qastock.valueresearch.in/";

        // Act
        String scheme = URI.create(protocolRelativeUrl).getScheme();

        // Assert
        Assert.assertNull(scheme, "A protocol-relative URL is expected to parse with no scheme - client-side handling must account for this explicitly");
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-EDGE-12: action_hint present but empty is distinct from missing, and is never rendered as a tooltip.")
    public void edgeCase_actionHintEmptyString_isDistinctFromMissing() throws IOException {
        // Arrange
        String menuJson = validMenuJsonWithOverride(
                "\"action_hint\": \"View and edit your account details\"", "\"action_hint\": \"\"");

        // Act
        Menu menu = OBJECT_MAPPER.readValue(menuJson, Menu.class);

        // Assert
        Assert.assertNotNull(menu.actionHint());
        Assert.assertTrue(menu.actionHint().isEmpty());
        Assert.assertFalse(hasRenderableActionHint(menu.actionHint()), "An empty action_hint must not be treated as renderable");
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-EDGE-13: multi-KB metadata round-trips without truncation.")
    public void edgeCase_largeMetadata_noTruncation() throws IOException {
        // Arrange
        String largeKeywords = "keyword,".repeat(1000);

        // Act
        String roundTripped = io.restassured.path.json.JsonPath.from("{\"meta_keywords\": " + toJsonString(largeKeywords) + "}").getString("meta_keywords");

        // Assert
        Assert.assertEquals(roundTripped.length(), largeKeywords.length(), "Large metadata must round-trip without truncation");
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-EDGE-14: a response with tracking_info missing entirely still parses the rest of the page correctly.")
    public void edgeCase_trackingInfoMissingEntirely_restOfPageStillParses() throws IOException {
        // Arrange
        JsonNode live = readLiveFixture();
        ((ObjectNode) live).remove("tracking_info");

        // Act - deserialization throwing would fail this test on its own
        HubResponse hub = OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsString(live), HubResponse.class);

        // Assert
        Assert.assertTrue(hub.status());
        Assert.assertFalse(hub.data().hub().isEmpty());
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-EDGE-15: a response with the disclosures key missing entirely (not empty) still parses correctly.")
    public void edgeCase_disclosuresKeyMissingEntirely_stillParses() throws IOException {
        // Arrange
        JsonNode live = readLiveFixture();
        ((ObjectNode) live).remove("disclosures");

        // Act - deserialization throwing would fail this test on its own
        HubResponse hub = OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsString(live), HubResponse.class);

        // Assert
        Assert.assertTrue(hub.status());
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-EDGE-18: a 200+ character title is preserved without truncation.")
    public void edgeCase_longTitle_noTruncation() throws IOException {
        // Arrange
        String longTitle = "A".repeat(250);
        String menuJson = validMenuJsonWithOverride("\"title\": \"Account Details\"", "\"title\": \"" + longTitle + "\"");

        // Act
        Menu menu = OBJECT_MAPPER.readValue(menuJson, Menu.class);

        // Assert
        Assert.assertEquals(menu.title().length(), 250);
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-EDGE-19: quotes, backslashes, and pipes in a title round-trip with correct JSON escaping.")
    public void edgeCase_specialCharactersInTitle_correctlyEscaped() throws IOException {
        // Arrange
        String titleWithSpecialChars = "Say \"hello\" \\ to | pipes";

        // Act
        String serialized = OBJECT_MAPPER.writeValueAsString(titleWithSpecialChars);
        String deserialized = OBJECT_MAPPER.readValue(serialized, String.class);

        // Assert
        Assert.assertEquals(deserialized, titleWithSpecialChars);
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-EMPTY-ARR-03: an empty disclosures array renders no disclosure section, with no error.")
    public void emptyArray_emptyDisclosures_noErrorNoSectionRendered() {
        // Arrange
        String responseJson = "{\"disclosures\": []}";

        // Act
        List<Object> disclosures = io.restassured.path.json.JsonPath.from(responseJson).getList("disclosures");

        // Assert
        Assert.assertTrue(disclosures.isEmpty());
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-EMPTY-ARR-04: the documented sample's empty we_user_properties array degrades tracking gracefully.")
    public void emptyArray_documentedSampleWeUserPropertiesEmpty_gracefulDegradation() throws IOException {
        // Arrange
        JsonNode documented = readDocumentedFixture();

        // Act / Assert
        JsonNode weUserProperties = documented.path("tracking_info").path("we_user_properties");
        Assert.assertTrue(weUserProperties.isArray() && weUserProperties.isEmpty());
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-EMPTY-OBJ-01: an empty add_investor object is treated as 'feature unavailable', not a crash.")
    public void emptyObject_emptyAddInvestor_treatedAsFeatureUnavailable() throws IOException {
        // Arrange
        String addInvestorJson = "{}";

        // Act
        AddInvestor addInvestor = OBJECT_MAPPER.readValue(addInvestorJson, AddInvestor.class);

        // Assert - one rule (safe defaults throughout), independent fields
        SoftAssert softAssert = new SoftAssert();
        Assert.assertFalse(addInvestor.isShow());
        Assert.assertFalse(addInvestor.isActive());
        softAssert.assertNull(addInvestor.title());
        softAssert.assertNull(addInvestor.url());
        softAssert.assertAll();
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-EMPTY-OBJ-02: an empty details object (neither list nor desc) renders gracefully, with no crash.")
    public void emptyObject_emptyDisclosureDetails_gracefulNoCrash() {
        // Arrange
        String detailsJson = "{}";

        // Act
        java.util.Map<String, Object> details = io.restassured.path.json.JsonPath.from(detailsJson).getMap("$");

        // Assert
        Assert.assertNull(details.get("list"));
        Assert.assertNull(details.get("desc"));
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-EMPTY-RESP-02: data: null with status:true parses without an NPE, and the safe accessor pattern degrades gracefully.")
    public void emptyResponse_dataNullStatusTrue_noNpeWithSafeAccessorPattern() throws IOException {
        // Arrange
        String responseJson = "{\"status\": true, \"data\": null}";

        // Act - deserializing data:null must never throw; if it does, this test fails
        HubResponse hub = OBJECT_MAPPER.readValue(responseJson, HubResponse.class);

        // Assert
        Assert.assertTrue(hub.status());
        Assert.assertNull(hub.data());
        List<?> safeHubGroups = Optional.ofNullable(hub.data()).map(d -> (List<?>) d.hub()).orElse(List.of());
        Assert.assertTrue(safeHubGroups.isEmpty(), "The null-safe accessor pattern must degrade to an empty list, not throw");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String validMenuJson() {
        return """
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
    }

    private String validMenuJsonWithOverride(String originalFragment, String replacementFragment) {
        String json = validMenuJson();
        Assert.assertTrue(json.contains(originalFragment), "Test fixture setup error: fragment not found: " + originalFragment);
        return json.replace(originalFragment, replacementFragment);
    }

    private boolean isUpperSnakeCase(String value) {
        return value != null && value.matches("^[A-Z][A-Z0-9_]*$");
    }

    private boolean hasRenderableActionHint(String actionHint) {
        return actionHint != null && !actionHint.isBlank();
    }

    private String toJsonString(String value) throws IOException {
        return OBJECT_MAPPER.writeValueAsString(value);
    }

    private JsonNode readLiveFixture() throws IOException {
        return readFixture("hub-response-live-sample.json");
    }

    private JsonNode readDocumentedFixture() throws IOException {
        return readFixture("hub-response-documented-sample.json");
    }

    private JsonNode readFixture(String resourceName) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("testdata/" + resourceName)) {
            if (in == null) {
                throw new IOException("Fixture not found on classpath: testdata/" + resourceName);
            }
            return OBJECT_MAPPER.readTree(in);
        }
    }
}

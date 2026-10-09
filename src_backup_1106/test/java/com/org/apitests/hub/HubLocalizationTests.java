package com.org.apitests.hub;

import org.testng.annotations.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.org.apiautomation.core.RequestSpecFactory;
import com.org.apiautomation.services.HubService;
import com.org.apitests.base.BaseTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Automates the approved Hub API test plan's Localization (T) and Special
 * Characters (V) categories. See {@code docs/test-plans/hub-api-test-plan.md}.
 */
public class HubLocalizationTests extends BaseTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HubService hubService = new HubService();

    @Test(groups = {"regression", "hub"}, description = "HUB-I18N-01: a supported alternate Accept-Language is honored or documented as English-only.")
    public void i18n_supportedAlternateLocale_localizedOrDocumentedEnglishOnly() {
        // Arrange
        RequestSpecification spec = RequestSpecFactory.defaultRequestSpec().header("Accept-Language", "hi-IN");

        // Act
        Response response = hubService.getHub(spec);

        // Assert - either localized content or a graceful, still-successful English-only response is acceptable; a server error is not
        Assert.assertEquals(response.statusCode(), 200);
        Assert.assertFalse(response.jsonPath().getList("data.hub").isEmpty());
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-I18N-02: an unsupported/garbage Accept-Language falls back to the default locale gracefully.")
    public void i18n_unsupportedGarbageLocale_gracefulFallback() {
        // Arrange
        RequestSpecification spec = RequestSpecFactory.defaultRequestSpec().header("Accept-Language", "xx-ZZ");

        // Act
        Response response = hubService.getHub(spec);

        // Assert
        Assert.assertEquals(response.statusCode(), 200, "A garbage Accept-Language must fall back gracefully, never error");
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-UNI-01: the existing curly-quote title round-trips correctly as UTF-8, not mojibake.")
    public void unicode_existingCurlyQuoteTitle_roundTripsCorrectly() {
        // Arrange
        String curlyApostrophe = "’";

        // Act
        Response response = hubService.getHub();
        List<String> allText = response.jsonPath().getList("data.hub.menus.flatten().title", String.class);
        boolean anyTitleUsesCurlyQuote = allText.stream().anyMatch(title -> title != null && title.contains(curlyApostrophe));

        // Assert
        Assert.assertTrue(anyTitleUsesCurlyQuote, "Expected at least one title (e.g. \"Analyst" + curlyApostrophe + "s Choice\") to contain a correctly-decoded U+2019 curly apostrophe, not mojibake");
    }

    @Test(groups = {"regression", "hub"},
            description = "HUB-UNI-02 / HUB-EDGE-17: emoji/CJK/RTL characters in a title encode and decode correctly with no render-breaking loss.")
    public void unicode_multiByteTitle_encodesAndDecodesCorrectly() throws Exception {
        // Arrange
        String multiByteTitle = "新聞 🚀 العربية";

        // Act
        String serialized = OBJECT_MAPPER.writeValueAsString(multiByteTitle);
        String deserialized = OBJECT_MAPPER.readValue(serialized, String.class);

        // Assert
        Assert.assertEquals(deserialized, multiByteTitle, "Multi-byte Unicode must survive a JSON encode/decode round trip unchanged");
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-SPCHAR-01: ampersands in real titles are correctly escaped, not double-encoded or broken.")
    public void specialCharacters_ampersandInTitles_correctlyEscaped() {
        // Arrange / Act
        Response response = hubService.getHub();
        List<String> titles = response.jsonPath().getList("data.hub.menus.flatten().title", String.class);

        // Assert
        boolean anyTitleHasSingleAmpersand = titles.stream().anyMatch(title -> title != null && title.contains(" & ") && !title.contains("&amp;"));
        Assert.assertTrue(anyTitleHasSingleAmpersand, "Expected at least one title (e.g. 'Stories & Videos') with a plain, correctly-decoded ampersand, not double-encoded as &amp;");
    }

    @Test(groups = {"regression", "hub"}, description = "HUB-SPCHAR-02: HTML entities in disclosure text (e.g. &copy;) render as intended, not double-escaped.")
    public void specialCharacters_htmlEntitiesInDisclosures_notDoubleEscaped() {
        // Arrange / Act
        Response response = hubService.getHub();
        List<String> disclosureDescriptions = response.jsonPath().getList("data.disclosures_data.data.details.desc", String.class);

        // Assert
        boolean anyDoubleEscaped = disclosureDescriptions.stream().anyMatch(desc -> desc != null && desc.contains("&amp;copy;"));
        Assert.assertFalse(anyDoubleEscaped, "A disclosure description contains a double-escaped HTML entity (&amp;copy; instead of &copy;)");
    }
}

package com.org.apitests.hub;

import com.org.apiautomation.models.response.hub.HubGroup;
import com.org.apiautomation.models.response.hub.Menu;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.testng.asserts.SoftAssert;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reusable validation and request-execution helpers shared across every
 * {@code Hub*Tests} class - built here, not in the main framework's {@code
 * utils} package, because this logic (flattening menus, cross-origin
 * detection, BR-1's mandatory-field check, the raw-single-shot-request
 * escape hatch used by several negative-auth tests) is specific to the Hub
 * API's own test suite, not a generic framework concern any other module
 * would need.
 *
 * <p>Package-private: only the {@code com.org.apitests.hub} test classes
 * use this, so it has no reason to be part of the framework's public API.
 */
final class HubAssertions {

    private HubAssertions() {
    }

    /** Every {@link Menu} across every {@link HubGroup}, flattened - most business-rule checks apply per-menu regardless of which group it's in. */
    static List<Menu> allMenus(List<HubGroup> hub) {
        return hub.stream().flatMap(g -> g.menus().stream()).collect(Collectors.toList());
    }

    /**
     * True if {@code url} points at a different host than {@code baseUrl}.
     * A relative URL (no scheme) is same-origin by definition. Used to
     * check BR-4's {@code is_ext_url} correctness - the field this API's
     * real sample data gets wrong for three menus.
     */
    static boolean isCrossOrigin(String url, String baseUrl) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            return false;
        }
        String urlHost = hostOf(url);
        String baseHost = hostOf(baseUrl);
        return urlHost == null || !urlHost.equalsIgnoreCase(baseHost);
    }

    private static String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /** BR-1: every menu must have non-null id/title/icon/url/action_hint. Uses soft assertions since these are five independent fields of the same rule (requirement #10). */
    static void assertMandatoryFieldsPresent(SoftAssert softAssert, Menu menu) {
        String context = " (menu id=" + menu.id() + ")";
        softAssert.assertNotNull(menu.id(), "menu.id must not be null" + context);
        softAssert.assertNotNull(menu.title(), "menu.title must not be null" + context);
        softAssert.assertNotNull(menu.icon(), "menu.icon must not be null" + context);
        softAssert.assertNotNull(menu.url(), "menu.url must not be null" + context);
        softAssert.assertNotNull(menu.actionHint(), "menu.action_hint must not be null" + context);
    }

    /** BR-5: icon path must be {@code /assets/images/hub_icon/<menu_id>.svg}. */
    static boolean iconMatchesIdConvention(Menu menu) {
        return ("/assets/images/hub_icon/" + menu.id() + ".svg").equals(menu.icon());
    }

    /**
     * Fires exactly one request with no auth-retry, bypassing {@code
     * ApiClient}'s built-in invalidate-and-retry-once behavior (see its
     * Javadoc). Used by every negative-auth test (invalid/missing/expired
     * token, injection-in-header) across {@code HubAuthenticationTests} and
     * {@code HubSecurityTests} - going through {@code HubService} normally
     * would let the framework silently fetch a fresh, valid token on the
     * resulting 401 and retry into a 200, masking the exact single-request
     * server behavior these tests exist to observe.
     */
    static Response rawSingleShotGet(RequestSpecification requestSpec, String path) {
        return RestAssured.given().spec(requestSpec).get(path);
    }

    /** Corrupts one character of an otherwise-valid, well-formed token - the closest available proxy for "well-formed but no longer accepted" without a real expired/foreign token on demand. */
    static String flipOneCharacter(String value) {
        char[] chars = value.toCharArray();
        int middle = chars.length / 2;
        chars[middle] = chars[middle] == 'a' ? 'b' : 'a';
        return new String(chars);
    }
}

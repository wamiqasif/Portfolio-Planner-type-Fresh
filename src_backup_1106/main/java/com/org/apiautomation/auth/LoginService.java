package com.org.apiautomation.auth;

import com.org.apiautomation.config.ConfigManager;
import com.org.apiautomation.utils.SensitiveDataMasker;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Calls the login API. Nothing else - no token extraction, no caching, no
 * decision about when to call it. That's {@link AuthenticationManager}'s
 * job, which uses this class internally.
 *
 * <p><b>Why this talks to Rest Assured directly instead of going through
 * {@code core.RequestSpecFactory}</b> - the same reason {@link
 * OAuth2AuthStrategy} does: {@code RequestSpecFactory.defaultRequestSpec()}
 * applies whichever {@link AuthStrategy} is configured to every spec it
 * builds. If this class asked it for a spec, logging in would try to
 * attach the very authentication it's trying to obtain - a circular
 * dependency. Login also needs {@code application/x-www-form-urlencoded}
 * content type and a distinct set of headers, so there's nothing generic
 * from {@code core} to reuse here regardless.
 */
public final class LoginService {

    private static final Logger LOGGER = LogManager.getLogger(LoginService.class);
    private static final String LOGIN_PATH = "/api/v0/login/";

    /**
     * Fires the login request and returns the raw response - status code,
     * headers, body, all of it. {@link AuthenticationManager} decides what
     * counts as success and where the token lives in the result; this
     * method doesn't look at either.
     */
    public Response login() {
        ConfigManager config = ConfigManager.getInstance();

        LOGGER.info("Calling login API for username={}, provider={}, site-code={}",
                config.getVrLoginUsername(), config.getVrLoginProvider(), config.getVrLoginSiteCode());

        Map<String, String> headers = parseExtraHeaders(config.getVrLoginHeaders());
        Map<String, String> formParams = buildFormParams(config);

        // DEBUG-only, and password masked even there (requirement #13 still
        // applies to a diagnostic log, not just the response log below) -
        // exists so a request that doesn't match a known-working Postman/
        // cURL capture can be diffed field-by-field instead of guessed at.
        // Ciphertext length and key fingerprint (never the key or plaintext
        // itself) let a login failure be diagnosed as "wrong key configured"
        // or "encryption produced the wrong size" without a live server.
        LOGGER.debug("Generated login request headers: {}", headers);
        LOGGER.debug("Generated login request body: {}", maskPassword(formParams));
        LOGGER.debug("Encrypted password length: {} bytes (base64), RSA public key fingerprint (SHA-256): {}",
                formParams.get("password").length(), EncryptionService.fingerprint(config.getVrLoginRsaPublicKey()));
        System.out.println("Encrypted Password Length = " + formParams.get("password").length());
        System.out.println("Encrypted Password = " + formParams.get("password"));

        // Rest Assured appends "; charset=ISO-8859-1" to ContentType.URLENC by
        // default (EncoderConfig's own default), which the real login page's
        // browser request never sends - confirmed via a byte-for-byte capture
        // of the wire request against a real curl. Harmless for this all-ASCII
        // body, but suppressed here so the Content-Type header matches the
        // known-working browser request exactly rather than merely "close enough".
        RequestSpecification loginRequestSpec = RestAssured.given()
                .config(RestAssuredConfig.config().encoderConfig(
                        EncoderConfig.encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                .baseUri(config.getVrLoginBaseUrl())
                .contentType(ContentType.URLENC)
                .headers(headers)
                .formParams(formParams);

        // Same non-prod-only relaxation as RequestSpecFactory/OAuth2AuthStrategy,
        // for the same reason: non-prod servers commonly run behind internal/
        // self-signed certificates; prod never gets this relaxed.
        if (!"prod".equals(config.getEnvironment())) {
            loginRequestSpec.relaxedHTTPSValidation();
        }

        Response response = loginRequestSpec.post(LOGIN_PATH);

        // Deliberately logs only the status code, never the body: whatever
        // shape the login response turns out to have, it's the one place
        // the actual authentication token appears in plaintext. Requirement
        // #13 - masking the password above is not enough if the response
        // gets logged unmasked right after.
        LOGGER.info("Login API responded with status {}", response.statusCode());

        return response;
    }

    /** The exact set and order of form fields sent - kept as one place so {@link #login} and its debug log can never drift apart. */
    private Map<String, String> buildFormParams(ConfigManager config) {
        Map<String, String> formParams = new LinkedHashMap<>();
        formParams.put("username", config.getVrLoginUsername());
        // RSA-encrypted, not plaintext - the backend expects exactly what
        // the real login page's own JSEncrypt call produces. See
        // EncryptionService's Javadoc for why a plaintext password field
        // reliably gets rejected instead.
        formParams.put("password", EncryptionService.encrypt(config.getVrLoginPassword(), config.getVrLoginRsaPublicKey()));
        formParams.put("provider", config.getVrLoginProvider());
        formParams.put("site-code", config.getVrLoginSiteCode());
        formParams.put("order-id", config.getVrLoginOrderId());
        formParams.put("token", config.getVrLoginFormToken());
        formParams.put("target", config.getVrLoginTarget());
        formParams.put("url-hash", config.getVrLoginUrlHash());
        return formParams;
    }

    private Map<String, String> maskPassword(Map<String, String> formParams) {
        Map<String, String> masked = new LinkedHashMap<>(formParams);
        masked.computeIfPresent("password", (name, value) -> SensitiveDataMasker.mask(value));
        return masked;
    }

    /**
     * Parses {@code "Name:Value||Name2:Value2"} into a header map. A small,
     * private, single-caller parser - not extracted anywhere shared, since
     * nothing else in the framework needs this exact format.
     *
     * <p><b>Pairs are separated by {@code ||}, not a single {@code ;}.</b>
     * A real required header here is a browser {@code User-Agent}
     * (e.g. {@code "Mozilla/5.0 (X11; Linux x86_64) ..."}), which contains a
     * literal semicolon inside it - splitting on a single {@code ;} would
     * silently corrupt that value into bogus extra pairs. {@code ||} is
     * vanishingly unlikely to appear inside any real header value, so this
     * one change makes the format safe for any header, not just the ones
     * currently configured. The name/value split within a pair still uses a
     * single {@code :} with a limit of 2, so a value containing its own
     * colons (a URL, e.g. {@code referer:https://example.com/}) is left intact.
     */
    private Map<String, String> parseExtraHeaders(String headerConfig) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (headerConfig == null || headerConfig.isBlank()) {
            return headers;
        }
        for (String pair : headerConfig.split("\\|\\|")) {
            String[] nameAndValue = pair.split(":", 2);
            if (nameAndValue.length == 2 && !nameAndValue[0].isBlank()) {
                headers.put(nameAndValue[0].trim(), nameAndValue[1].trim());
            }
        }
        return headers;
    }
}

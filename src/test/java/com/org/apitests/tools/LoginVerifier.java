package com.org.apitests.tools;

import com.org.apiautomation.auth.EncryptionService;
import com.org.apiautomation.config.ConfigManager;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Standalone, manually-run verifier for the RSA-encrypted login request -
 * deliberately NOT a TestNG test (no {@code @Test}, not referenced by any
 * suite XML, so Surefire never picks it up). Exists to answer one question
 * in isolation: does the exact request shape the login flow now builds get
 * accepted by the real login API? Builds the request from scratch (does
 * not call into {@code LoginService}) so it still proves something on the
 * day {@code LoginService} itself is the thing under suspicion. Calls
 * {@code POST /api/v0/login/} directly and prints the raw response -
 * bypassing {@code AuthenticationManager}'s caching and exception
 * translation entirely, so a rejection's real reason is visible immediately.
 *
 * <p>Run with real credentials via system properties, e.g.:
 * {@code java -Denv=qa -Dauth.vrlogin.username=... -Dauth.vrlogin.password=...
 * -cp <test-classpath> com.org.apitests.tools.LoginVerifier}
 */
public final class LoginVerifier {

    private LoginVerifier() {
    }

    public static void main(String[] args) {
        ConfigManager config = ConfigManager.getInstance();

        String encryptedPassword = EncryptionService.encrypt(config.getVrLoginPassword(), config.getVrLoginRsaPublicKey());
        System.out.println("Encrypted password length (base64): " + encryptedPassword.length() + " chars");
        System.out.println("RSA public key fingerprint (SHA-256): " + EncryptionService.fingerprint(config.getVrLoginRsaPublicKey()));

        Map<String, String> formParams = new LinkedHashMap<>();
        formParams.put("username", config.getVrLoginUsername());
        formParams.put("password", encryptedPassword);
        formParams.put("provider", config.getVrLoginProvider());
        formParams.put("site-code", config.getVrLoginSiteCode());
        formParams.put("order-id", config.getVrLoginOrderId());
        formParams.put("token", config.getVrLoginFormToken());
        formParams.put("target", config.getVrLoginTarget());
        formParams.put("url-hash", config.getVrLoginUrlHash());

        Map<String, String> maskedFormParams = new LinkedHashMap<>(formParams);
        maskedFormParams.put("password", "***REDACTED*** (" + encryptedPassword.length() + " chars)");
        System.out.println("Generated form body: " + maskedFormParams);

        Map<String, String> headers = parseExtraHeaders(config.getVrLoginHeaders());
        System.out.println("Generated headers: " + headers);

        RequestSpecification spec = RestAssured.given()
                .baseUri(config.getVrLoginBaseUrl())
                .contentType(ContentType.URLENC)
                .headers(headers)
                .formParams(formParams);

        if (!"prod".equals(config.getEnvironment())) {
            spec.relaxedHTTPSValidation();
        }

        Response response = spec.post("/api/v0/login/");

        System.out.println("HTTP status: " + response.statusCode());
        System.out.println("Response body:\n" + response.getBody().asPrettyString());
    }

    // Duplicated from LoginService's own parseExtraHeaders rather than
    // reused, deliberately - see class Javadoc on why this verifier must
    // not depend on LoginService.
    private static Map<String, String> parseExtraHeaders(String headerConfig) {
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

package com.org.apiautomation.auth;

import com.org.apiautomation.config.ConfigManager;
import com.org.apiautomation.exceptions.AuthenticationException;
import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * API-1 bearer flow: {@code login -> response.token -> Auth-Token header ->
 * /auth/token/create/ -> response.access_token -> Authorization: Bearer}.
 *
 * <p>Only {@code token} from the login response and {@code access_token} from
 * the token-create response are ever used - never {@code redirect_url}'s
 * token, {@code aa}, or {@code refresh_token}. The Step-1 token is sent only
 * as {@code Auth-Token} to token-create; the planner call only ever gets the
 * Bearer access token (see {@code PlannerService.generateApi1}).
 *
 * <p>Like {@link LoginService}, talks to Rest Assured directly rather than
 * {@code core.RequestSpecFactory} (circular-dependency reason documented
 * there). Token values are never logged; {@link #lastDiagnostics()} exposes
 * only statuses and extracted-yes/no flags.
 */
public final class Api1AuthService {

    private static final Logger LOGGER = LogManager.getLogger(Api1AuthService.class);
    private static final Api1AuthService INSTANCE = new Api1AuthService();

    /** Status codes and extracted-or-not flags only - never a value. */
    public record Diagnostics(int loginStatus, boolean loginTokenExtracted,
                              int tokenCreateStatus, boolean accessTokenExtracted) {
    }

    private String accessToken;
    private Diagnostics diagnostics = new Diagnostics(-1, false, -1, false);

    public static Api1AuthService getInstance() {
        return INSTANCE;
    }

    /** Cached, thread-safe (suite runs classes in parallel): one login per JVM until {@link #invalidate()}. */
    public synchronized String getAccessToken() {
        if (accessToken == null) {
            accessToken = authenticate();
        }
        return accessToken;
    }

    public synchronized void invalidate() {
        accessToken = null;
    }

    public synchronized Diagnostics lastDiagnostics() {
        return diagnostics;
    }

    private String authenticate() {
        ConfigManager config = ConfigManager.getInstance();

        Response login = loginRequest(config).post(config.getApi1LoginUrl());
        String loginToken = extractField(login.asString(), "token");
        // Same backend quirk as AuthenticationManager: HTTP 200 is not success, body "status" is.
        boolean statusFalse = "false".equals(extractField(login.asString(), "status"));
        diagnostics = new Diagnostics(login.statusCode(), loginToken != null, -1, false);
        LOGGER.info("API-1 login: HTTP {}, token extracted = {}", login.statusCode(), yesNo(loginToken != null));
        if (login.statusCode() != 200 || statusFalse || loginToken == null) {
            throw new AuthenticationException("API-1 login failed: HTTP " + login.statusCode()
                    + ", token extracted = " + yesNo(loginToken != null) + ", body status=false: " + statusFalse);
        }

        Response tokenCreate = baseRequest(config)
                .header("Auth-Token", loginToken)
                .post(config.getApi1TokenUrl());
        String access = extractField(tokenCreate.asString(), "access_token");
        diagnostics = new Diagnostics(login.statusCode(), true, tokenCreate.statusCode(), access != null);
        LOGGER.info("API-1 token/create: HTTP {}, access_token extracted = {}",
                tokenCreate.statusCode(), yesNo(access != null));
        if (tokenCreate.statusCode() != 200 || access == null) {
            throw new AuthenticationException("API-1 token/create failed: HTTP " + tokenCreate.statusCode()
                    + ", access_token extracted = " + yesNo(access != null));
        }
        return access;
    }

    private RequestSpecification loginRequest(ConfigManager config) {
        String encrypted = !config.getApi1Password().isBlank()
                ? EncryptionService.encrypt(config.getApi1Password(), config.getVrLoginRsaPublicKey())
                : config.getApi1PasswordEncrypted();
        if (encrypted.isBlank()) {
            throw new AuthenticationException("Neither api1.password nor api1.password.encrypted is configured");
        }
        // multipart/form-data, matching the working curl's --form fields.
        return baseRequest(config)
                .header("Authorization", config.getApi1LoginBasicAuth())
                .header("json-api-key", config.getApi1JsonApiKey())
                .header("app-type", config.getApi1AppType())
                .header("app-version", config.getApi1AppVersion())
                .header("site-code", config.getApi1SiteCode())
                .multiPart("username", config.getApi1Username())
                .multiPart("password", encrypted)
                .multiPart("device", config.getApi1Device())
                .multiPart("platform", config.getApi1Platform());
    }

    private RequestSpecification baseRequest(ConfigManager config) {
        RequestSpecification spec = RestAssured.given()
                .baseUri(config.getApi1AuthBaseUrl())
                .header("app-platform", config.getApi1AppPlatform());
        if (!"prod".equals(config.getEnvironment())) {
            spec.relaxedHTTPSValidation();
        }
        return spec;
    }

    /** Top-level string field of a JSON body, tolerating a leading UTF-8 BOM; {@code null} if absent/blank/unparseable. */
    public static String extractField(String body, String field) {
        if (body == null) {
            return null;
        }
        String json = body.startsWith("﻿") ? body.substring(1) : body;
        try {
            Object value = JsonPath.from(json).get(field);
            return value == null || value.toString().isBlank() ? null : value.toString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String yesNo(boolean b) {
        return b ? "YES" : "NO";
    }
}

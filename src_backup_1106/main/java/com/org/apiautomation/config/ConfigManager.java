package com.org.apiautomation.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Single, authoritative entry point for configuration. Every other package
 * asks {@code ConfigManager.getInstance()} for a value - nothing else in the
 * framework reads a properties file or resolves the environment directly.
 *
 * <p>Resolves the active environment via {@link EnvironmentLoader} and loads
 * its properties via {@link PropertyReader} exactly once per JVM, using the
 * initialization-on-demand holder idiom: thread-safe lazy initialization
 * without any explicit {@code synchronized} block, guaranteed by the JLS
 * class-loading contract.
 */
public final class ConfigManager {

    private static final Logger LOGGER = LogManager.getLogger(ConfigManager.class);

    private static final String LOCAL_OVERRIDES_FILE = "config-local.properties";

    private final String environment;
    private final Properties properties;

    private ConfigManager() {
        this.environment = EnvironmentLoader.resolveEnvironment();
        this.properties = PropertyReader.load(environment);
        applyLocalOverrides(this.properties);
        LOGGER.info("ConfigManager initialized for environment '{}'", environment);
    }

    /**
     * Overlays {@code config-local.properties} (working directory, gitignored,
     * optional) onto the env file - where real secrets live on a developer
     * machine. A {@code -D} system property still wins over it (see {@link
     * PropertyReader}).
     */
    private static void applyLocalOverrides(Properties target) {
        Path local = Path.of(LOCAL_OVERRIDES_FILE);
        if (!Files.isRegularFile(local)) {
            return;
        }
        try (InputStream in = Files.newInputStream(local)) {
            Properties overrides = new Properties();
            overrides.load(in);
            target.putAll(overrides);
            LOGGER.info("Applied {} local override key(s) from '{}'", overrides.size(), LOCAL_OVERRIDES_FILE);
        } catch (IOException e) {
            throw new com.org.apiautomation.exceptions.ConfigurationException("Failed to read " + LOCAL_OVERRIDES_FILE, e);
        }
    }

    private static final class Holder {
        private static final ConfigManager INSTANCE = new ConfigManager();
    }

    public static ConfigManager getInstance() {
        return Holder.INSTANCE;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getBaseUrl() {
        return PropertyReader.get(properties, ConfigKeys.BASE_URL);
    }

    /** Shared version segment (e.g. {@code v0}) confirmed real for login, contact-us, and Hub - all three live on the same host/version today. Defaulted, not required, since older/legacy endpoints (e.g. the Hub API's Fawkes path) don't use it at all. */
    public String getApiVersion() {
        return PropertyReader.get(properties, ConfigKeys.API_VERSION, "v0");
    }

    public int getConnectTimeoutMs() {
        return PropertyReader.getInt(properties, ConfigKeys.CONNECT_TIMEOUT_MS);
    }

    public int getReadTimeoutMs() {
        return PropertyReader.getInt(properties, ConfigKeys.READ_TIMEOUT_MS);
    }

    public String getAuthType() {
        return PropertyReader.get(properties, ConfigKeys.AUTH_TYPE);
    }

    /**
     * Required, not defaulted - and correctly so, on reflection. Only
     * {@link com.org.apiautomation.auth.OAuth2AuthStrategy} ever calls this,
     * and only when {@code auth.type=oauth2} is active - exactly the case
     * where a missing value should fail immediately with a clear
     * {@link com.org.apiautomation.exceptions.ConfigurationException},
     * not silently fall back to an empty base URI that would fail later
     * with a far less diagnostic error.
     */
    public String getAuthTokenUrl() {
        return PropertyReader.get(properties, ConfigKeys.AUTH_TOKEN_URL);
    }

    public String getAuthUsername() {
        return PropertyReader.get(properties, ConfigKeys.AUTH_USERNAME, "");
    }

    public String getAuthPassword() {
        return PropertyReader.get(properties, ConfigKeys.AUTH_PASSWORD, "");
    }

    public String getAuthBearerToken() {
        return PropertyReader.get(properties, ConfigKeys.AUTH_BEARER_TOKEN, "");
    }

    public String getAuthApiKeyHeaderName() {
        return PropertyReader.get(properties, ConfigKeys.AUTH_APIKEY_HEADER_NAME, "X-API-Key");
    }

    public String getAuthApiKeyValue() {
        return PropertyReader.get(properties, ConfigKeys.AUTH_APIKEY_VALUE, "");
    }

    public String getOAuth2ClientId() {
        return PropertyReader.get(properties, ConfigKeys.AUTH_OAUTH2_CLIENT_ID);
    }

    public String getOAuth2ClientSecret() {
        return PropertyReader.get(properties, ConfigKeys.AUTH_OAUTH2_CLIENT_SECRET);
    }

    public String getOAuth2Scope() {
        return PropertyReader.get(properties, ConfigKeys.AUTH_OAUTH2_SCOPE, "");
    }

    public String getOAuth2GrantType() {
        return PropertyReader.get(properties, ConfigKeys.AUTH_OAUTH2_GRANT_TYPE, "client_credentials");
    }

    /** Only {@code LoginService} ever calls this, and only when {@code auth.type=vrlogin} - required for the same reason as {@link #getAuthTokenUrl()}. */
    public String getVrLoginBaseUrl() {
        return PropertyReader.get(properties, ConfigKeys.AUTH_VRLOGIN_BASE_URL);
    }

    public String getVrLoginUsername() {
        return PropertyReader.get(properties, ConfigKeys.AUTH_VRLOGIN_USERNAME);
    }

    public String getVrLoginPassword() {
        return PropertyReader.get(properties, ConfigKeys.AUTH_VRLOGIN_PASSWORD);
    }

    // provider/site-code/order-id/form-token/target/url-hash: defaulted to
    // "" rather than required. Verified against the real login request that
    // order-id, token, and url-hash are legitimately sent blank - "missing"
    // and "intentionally blank" aren't distinguishable for these fields, so
    // failing fast on blank would be wrong.
    public String getVrLoginProvider() {
        return PropertyReader.get(properties, ConfigKeys.AUTH_VRLOGIN_PROVIDER, "");
    }

    public String getVrLoginSiteCode() {
        return PropertyReader.get(properties, ConfigKeys.AUTH_VRLOGIN_SITE_CODE, "");
    }

    public String getVrLoginOrderId() {
        return PropertyReader.get(properties, ConfigKeys.AUTH_VRLOGIN_ORDER_ID, "");
    }

    /** The login form's own field literally named "token" - unrelated to the authentication token this flow produces. Named distinctly here to avoid confusing the two. */
    public String getVrLoginFormToken() {
        return PropertyReader.get(properties, ConfigKeys.AUTH_VRLOGIN_FORM_TOKEN, "");
    }

    public String getVrLoginTarget() {
        return PropertyReader.get(properties, ConfigKeys.AUTH_VRLOGIN_TARGET, "");
    }

    public String getVrLoginUrlHash() {
        return PropertyReader.get(properties, ConfigKeys.AUTH_VRLOGIN_URL_HASH, "");
    }

    /**
     * Extra headers the login endpoint needs beyond Content-Type, as
     * {@code "Name:Value;Name2:Value2"} - configurable per requirement #3
     * rather than hardcoded, since which headers a given deployment's WAF/
     * backend actually requires isn't something this framework can know in
     * advance. Parsed by {@code LoginService} - see its Javadoc.
     */
    public String getVrLoginHeaders() {
        return PropertyReader.get(properties, ConfigKeys.AUTH_VRLOGIN_HEADERS, "X-Requested-With:XMLHttpRequest");
    }

    /**
     * The login backend's RSA public key (base64 DER, X.509
     * SubjectPublicKeyInfo - i.e. a PEM {@code PUBLIC KEY} block with the
     * header/footer and line breaks stripped), confirmed against the real
     * frontend's own {@code new JSEncrypt().setPublicKey(...)} call. {@code
     * LoginService} uses it to RSA-encrypt the password before sending -
     * the backend never accepts a plaintext password field.
     */
    public String getVrLoginRsaPublicKey() {
        return PropertyReader.get(properties, ConfigKeys.AUTH_VRLOGIN_RSA_PUBLIC_KEY);
    }

    /** JSON field name to read the token from in the login response - see {@code AuthenticationManager} for why this is configurable rather than assumed. */
    public String getVrLoginTokenJsonPath() {
        return PropertyReader.get(properties, ConfigKeys.AUTH_VRLOGIN_TOKEN_JSON_PATH, "token");
    }

    /** Cookie name to fall back to if the JSON field is absent. Blank (the default) disables the cookie fallback entirely. */
    public String getVrLoginTokenCookieName() {
        return PropertyReader.get(properties, ConfigKeys.AUTH_VRLOGIN_TOKEN_COOKIE_NAME, "");
    }

    /** How long to treat a freshly-obtained token as valid before proactively refreshing. A wrong guess here is not a correctness bug - see {@code AuthRetryFilter}, which refreshes reactively on a real 401/403 regardless of this value. */
    public int getVrLoginTokenExpirySeconds() {
        return PropertyReader.getInt(properties, ConfigKeys.AUTH_VRLOGIN_TOKEN_EXPIRY_SECONDS, 1500);
    }

    public int getRetryMaxAttempts() {
        return PropertyReader.getInt(properties, ConfigKeys.RETRY_MAX_ATTEMPTS);
    }

    public int getRetryDelayMs() {
        return PropertyReader.getInt(properties, ConfigKeys.RETRY_DELAY_MS);
    }

    // ------------------------------------------------------------------
    // Portfolio Planner APIs (docs/test-plans/portfolio-planner-apis.md).
    // Endpoint paths are defaulted (not required) so dev/stage/prod, which
    // don't define them explicitly today, still resolve the same paths -
    // only qa.properties documents them inline since qa is the only
    // actively-exercised environment for this module right now.
    // ------------------------------------------------------------------

    /** The User Portfolio and (since 2026-09-08) Planner Generate APIs' host - confirmed via live curls to differ from {@link #getBaseUrl()}. Falls back to {@code getBaseUrl()} for any environment that hasn't defined a distinct advisor host. */
    public String getAdvisorApiBaseUrl() {
        return PropertyReader.get(properties, ConfigKeys.ADVISOR_API_BASE_URL, getBaseUrl());
    }

    /** Path only - see {@code services.PlannerService} for the host ({@link #getAdvisorApiBaseUrl()}, not {@link #getBaseUrl()}). Default updated 2026-09-08 to match qa.properties' confirmed-current path; only qa.properties has been directly confirmed against a live curl so far - if dev/stage/prod turn out to still be on the old path, override there explicitly rather than relying on this default. */
    public String getPlannerEndpoint() {
        return PropertyReader.get(properties, ConfigKeys.PLANNER_ENDPOINT, "/api/v1/planner/funds-buy/generate");
    }

    public String getPortfolioLabelOpinionEndpoint() {
        return PropertyReader.get(properties, ConfigKeys.PORTFOLIO_LABEL_OPINION_ENDPOINT, "/api/v1/user-portfolio/label-opinion-data");
    }

    public String getFundOpinionEndpoint() {
        return PropertyReader.get(properties, ConfigKeys.FUND_OPINION_ENDPOINT, "/api/v1/funds/fund-opinion-data/get-list");
    }

    /** Base path only - callers append {@code /{planId}}, see {@code services.FundService.getFundDetails}. */
    public String getFundDetailsEndpoint() {
        return PropertyReader.get(properties, ConfigKeys.FUND_DETAILS_ENDPOINT, "/api/v1/funds");
    }

    public String getFundPlanOptionsEndpoint() {
        return PropertyReader.get(properties, ConfigKeys.FUND_PLAN_OPTIONS_ENDPOINT, "/api/v1/funds/fund-plan-options-masters/get-list");
    }

    /** Base path only - callers append {@code /{encodedFilter}}, see {@code services.FundService.getFundsByFilter}. */
    public String getFundFilterEndpoint() {
        return PropertyReader.get(properties, ConfigKeys.FUND_FILTER_ENDPOINT, "/api/v1/funds/filter");
    }

    public String getGqlComponentEndpoint() {
        return PropertyReader.get(properties, ConfigKeys.GQL_COMPONENT_ENDPOINT, "/api/v1/gql/component");
    }

    public String getConfigMasterEndpoint() {
        return PropertyReader.get(properties, ConfigKeys.CONFIG_MASTER_ENDPOINT, "/api/v1/conf/config-masters/get");
    }

    // ------------------------------------------------------------------
    // API-1 bearer flow. Non-secret values are defaulted to the confirmed
    // QA shape; secrets are required and resolve -D > config-local.properties
    // > env file > env var API1_<KEY> (e.g. API1_PASSWORD). Never logged.
    // ------------------------------------------------------------------

    /** Host serving the planner call (advisor API gateway). */
    public String getApi1BaseUrl() {
        return PropertyReader.get(properties, ConfigKeys.API1_BASE_URL, "https://qaadvappapi.valueresearch.in");
    }

    /** Host serving login and token/create - a different host from the planner's. */
    public String getApi1AuthBaseUrl() {
        return PropertyReader.get(properties, ConfigKeys.API1_AUTH_BASE_URL, "https://qaappapi.valueresearch.in");
    }

    public String getApi1Endpoint() {
        return PropertyReader.get(properties, ConfigKeys.API1_ENDPOINT, "/api/v1/planner/funds-buy/generate");
    }

    public String getApi1TokenUrl() {
        return PropertyReader.get(properties, ConfigKeys.API1_TOKEN_URL, "/api/v0/auth/token/create/");
    }

    public String getApi1LoginUrl() {
        return PropertyReader.get(properties, ConfigKeys.API1_LOGIN_URL, "/api/v0/login/");
    }

    public String getApi1AppPlatform() {
        return PropertyReader.get(properties, ConfigKeys.API1_APP_PLATFORM, "android");
    }

    public String getApi1AppType() {
        return PropertyReader.get(properties, ConfigKeys.API1_APP_TYPE, "ADV_APP");
    }

    public String getApi1AppVersion() {
        return PropertyReader.get(properties, ConfigKeys.API1_APP_VERSION, "1.0.33");
    }

    public String getApi1SiteCode() {
        return PropertyReader.get(properties, ConfigKeys.API1_SITE_CODE, "VROADV");
    }

    public String getApi1Device() {
        return PropertyReader.get(properties, ConfigKeys.API1_DEVICE, "android");
    }

    public String getApi1Platform() {
        return PropertyReader.get(properties, ConfigKeys.API1_PLATFORM, "VROAPP");
    }

    /** Full header value, e.g. {@code Basic xxxx}. */
    public String getApi1LoginBasicAuth() {
        return api1Secret(ConfigKeys.API1_LOGIN_BASIC_AUTH);
    }

    public String getApi1JsonApiKey() {
        return api1Secret(ConfigKeys.API1_JSON_API_KEY);
    }

    public String getApi1Username() {
        return api1Secret(ConfigKeys.API1_USERNAME);
    }

    /** Plaintext password, RSA-encrypted at call time. Blank if only {@link #getApi1PasswordEncrypted()} is set. */
    public String getApi1Password() {
        return api1Optional(ConfigKeys.API1_PASSWORD);
    }

    /** Optional pre-encrypted password (base64 RSA ciphertext) - used only when no plaintext password is configured. */
    public String getApi1PasswordEncrypted() {
        return api1Optional(ConfigKeys.API1_PASSWORD_ENCRYPTED);
    }

    private String api1Optional(String key) {
        String value = PropertyReader.get(properties, key, "");
        if (value.isBlank()) {
            String env = System.getenv(key.toUpperCase().replace('.', '_'));
            value = env == null ? "" : env.trim();
        }
        return value;
    }

    private String api1Secret(String key) {
        String value = api1Optional(key);
        if (value.isBlank()) {
            throw new com.org.apiautomation.exceptions.ConfigurationException(
                    "Missing required secret '" + key + "' - set it in config-local.properties, "
                            + "as -D" + key + ", or as env var " + key.toUpperCase().replace('.', '_'));
        }
        return value;
    }

    /**
     * Escape hatch for a config key that has no dedicated typed getter.
     * Prefer adding a typed getter (+ a {@link ConfigKeys} constant) for any
     * key more than one caller needs.
     */
    public String getProperty(String key) {
        return PropertyReader.get(properties, key);
    }

    public String getProperty(String key, String defaultValue) {
        return PropertyReader.get(properties, key, defaultValue);
    }
}

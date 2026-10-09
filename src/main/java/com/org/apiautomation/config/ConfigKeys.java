package com.org.apiautomation.config;

/**
 * Property key names used across every {@code <env>.properties} file.
 * Centralized so a key name is a compile-time-checked constant, not a string
 * literal repeated (and potentially mistyped) at every call site.
 */
public final class ConfigKeys {

    private ConfigKeys() {
    }

    public static final String BASE_URL = "base.url";
    public static final String API_VERSION = "api.version";
    public static final String CONNECT_TIMEOUT_MS = "connect.timeout.ms";
    public static final String READ_TIMEOUT_MS = "read.timeout.ms";

    public static final String AUTH_TYPE = "auth.type";
    public static final String AUTH_TOKEN_URL = "auth.token.url";
    public static final String AUTH_USERNAME = "auth.username";
    public static final String AUTH_PASSWORD = "auth.password";

    public static final String AUTH_BEARER_TOKEN = "auth.bearer.token";

    public static final String AUTH_APIKEY_HEADER_NAME = "auth.apikey.header.name";
    public static final String AUTH_APIKEY_VALUE = "auth.apikey.value";

    public static final String AUTH_OAUTH2_CLIENT_ID = "auth.oauth2.client.id";
    public static final String AUTH_OAUTH2_CLIENT_SECRET = "auth.oauth2.client.secret";
    public static final String AUTH_OAUTH2_SCOPE = "auth.oauth2.scope";
    public static final String AUTH_OAUTH2_GRANT_TYPE = "auth.oauth2.grant.type";

    // VR dynamic login flow (auth.type=vrlogin) - form params match the
    // login endpoint's required fields exactly, plus what this framework
    // needs to know to call it and to interpret its response.
    public static final String AUTH_VRLOGIN_BASE_URL = "auth.vrlogin.base.url";
    public static final String AUTH_VRLOGIN_USERNAME = "auth.vrlogin.username";
    public static final String AUTH_VRLOGIN_PASSWORD = "auth.vrlogin.password";
    public static final String AUTH_VRLOGIN_PROVIDER = "auth.vrlogin.provider";
    public static final String AUTH_VRLOGIN_SITE_CODE = "auth.vrlogin.site.code";
    public static final String AUTH_VRLOGIN_ORDER_ID = "auth.vrlogin.order.id";
    public static final String AUTH_VRLOGIN_FORM_TOKEN = "auth.vrlogin.form.token";
    public static final String AUTH_VRLOGIN_TARGET = "auth.vrlogin.target";
    public static final String AUTH_VRLOGIN_URL_HASH = "auth.vrlogin.url.hash";
    public static final String AUTH_VRLOGIN_HEADERS = "auth.vrlogin.headers";
    public static final String AUTH_VRLOGIN_RSA_PUBLIC_KEY = "auth.vrlogin.rsa.public.key";
    public static final String AUTH_VRLOGIN_TOKEN_JSON_PATH = "auth.vrlogin.token.jsonpath";
    public static final String AUTH_VRLOGIN_TOKEN_COOKIE_NAME = "auth.vrlogin.token.cookie.name";
    public static final String AUTH_VRLOGIN_TOKEN_EXPIRY_SECONDS = "auth.vrlogin.token.expiry.seconds";

    public static final String RETRY_MAX_ATTEMPTS = "retry.max.attempts";
    public static final String RETRY_DELAY_MS = "retry.delay.ms";

    // Portfolio Planner APIs (docs/test-plans/portfolio-planner-apis.md).
    // Second host: the User Portfolio API is served from the advisor app
    // gateway, not the main app API host - confirmed via a live curl.
    public static final String ADVISOR_API_BASE_URL = "advisor.api.base.url";
    public static final String PLANNER_ENDPOINT = "planner.endpoint";
    public static final String PORTFOLIO_LABEL_OPINION_ENDPOINT = "portfolio.label.opinion.endpoint";
    public static final String FUND_OPINION_ENDPOINT = "fund.opinion.endpoint";
    public static final String FUND_DETAILS_ENDPOINT = "fund.details.endpoint";
    public static final String FUND_PLAN_OPTIONS_ENDPOINT = "fund.plan.options.endpoint";
    public static final String FUND_FILTER_ENDPOINT = "fund.filter.endpoint";
    public static final String GQL_COMPONENT_ENDPOINT = "gql.component.endpoint";
    public static final String CONFIG_MASTER_ENDPOINT = "config.master.endpoint";

    // API-1 (Planner Generate, bearer-JWT flow): login -> token/create -> planner.
    // Secrets (basic auth, json api key, username, password) are never in the
    // checked-in env files - config-local.properties or API1_* env vars only.
    public static final String API1_BASE_URL = "api1.base.url";
    public static final String API1_AUTH_BASE_URL = "api1.auth.base.url";
    public static final String API1_ENDPOINT = "api1.endpoint";
    public static final String API1_TOKEN_URL = "api1.token.url";
    public static final String API1_LOGIN_URL = "api1.login.url";
    public static final String API1_APP_PLATFORM = "api1.app.platform";
    public static final String API1_APP_TYPE = "api1.app.type";
    public static final String API1_APP_VERSION = "api1.app.version";
    public static final String API1_SITE_CODE = "api1.site.code";
    public static final String API1_DEVICE = "api1.device";
    public static final String API1_PLATFORM = "api1.platform";
    public static final String API1_LOGIN_BASIC_AUTH = "api1.login.basic.auth";
    public static final String API1_JSON_API_KEY = "api1.json.api.key";
    public static final String API1_USERNAME = "api1.username";
    public static final String API1_PASSWORD = "api1.password";
    public static final String API1_PASSWORD_ENCRYPTED = "api1.password.encrypted";
}

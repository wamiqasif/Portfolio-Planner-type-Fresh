package com.org.apiautomation.auth;

import com.org.apiautomation.config.ConfigManager;
import com.org.apiautomation.exceptions.ConfigurationException;

/**
 * Maps the active environment's configured {@code auth.type} to the
 * {@link AuthStrategy} that implements it. This is the one place that
 * mapping exists, so {@code core.RequestSpecFactory} can apply
 * authentication without an if/else chain of its own, and switching an
 * environment's auth mechanism is a one-line config change, not a code
 * change.
 *
 * <p>Returns a new strategy instance per call rather than caching one.
 * Every strategy is itself stateless (each reads {@link ConfigManager} - and,
 * for OAuth2, the shared {@link TokenManager} - fresh on every {@code apply()});
 * constructing one is cheap, and doing so avoids needing to reason about
 * whether a shared instance is safe to hand to concurrent callers.
 */
public final class AuthStrategyFactory {

    private AuthStrategyFactory() {
    }

    public static AuthStrategy getStrategy() {
        String authType = ConfigManager.getInstance().getAuthType();
        return switch (authType) {
            case "basic" -> new BasicAuthStrategy();
            case "bearer" -> new BearerTokenAuthStrategy();
            case "apikey" -> new ApiKeyAuthStrategy();
            case "oauth2" -> new OAuth2AuthStrategy();
            case "vrlogin" -> new DynamicLoginAuthStrategy();
            default -> throw new ConfigurationException(
                    "Unsupported auth.type '" + authType + "'. Supported values: basic, bearer, apikey, oauth2, vrlogin");
        };
    }
}

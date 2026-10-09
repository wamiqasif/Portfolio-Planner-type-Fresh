package com.org.apiautomation.auth;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Caches tokens in memory, keyed by mechanism, so a strategy fetches a new
 * one only when there isn't a cached one yet, or the cached one is expired
 * or about to expire - not before every single API call. Without this,
 * every request in a suite would cost an extra network round trip to an
 * auth server before it could even start.
 *
 * <p>Singleton (initialization-on-demand holder, same idiom as {@code
 * ConfigManager}). Keyed by a cache key (e.g. {@code "oauth2"}, {@code
 * "vrlogin"}) rather than a single field: {@link OAuth2AuthStrategy} and
 * {@link DynamicLoginAuthStrategy} each cache their own token here, and
 * must never share a slot - only one {@code auth.type} is active per
 * environment today, so sharing one field would happen to work, but that's
 * a coincidence of how it's currently used, not something the class should
 * rely on.
 *
 * <p>{@code ConcurrentHashMap.compute} does the thread-safety work: it locks
 * per-key (not globally), so concurrent callers for the *same* key are
 * correctly serialized - one fetches, the rest reuse the result - while
 * callers for *different* keys never block each other.
 */
public final class TokenManager {

    private static final int EXPIRY_SAFETY_MARGIN_SECONDS = 30;

    private static final class Holder {
        private static final TokenManager INSTANCE = new TokenManager();
    }

    public static TokenManager getInstance() {
        return Holder.INSTANCE;
    }

    private final ConcurrentHashMap<String, CachedToken> cachedTokens = new ConcurrentHashMap<>();

    private TokenManager() {
    }

    /**
     * Returns a cached, still-valid token for {@code cacheKey}, calling
     * {@code tokenFetcher} to obtain and cache a new one if there isn't a
     * valid one cached.
     */
    public String getToken(String cacheKey, Supplier<CachedToken> tokenFetcher) {
        CachedToken cached = cachedTokens.compute(cacheKey, (key, existing) ->
                (existing == null || existing.isExpiringWithin(EXPIRY_SAFETY_MARGIN_SECONDS))
                        ? tokenFetcher.get()
                        : existing);
        return cached.accessToken();
    }

    /** Forces the next {@link #getToken} call for this key to fetch a fresh token. */
    public void invalidate(String cacheKey) {
        cachedTokens.remove(cacheKey);
    }

    public record CachedToken(String accessToken, Instant expiresAt) {
        boolean isExpiringWithin(int seconds) {
            return Instant.now().isAfter(expiresAt.minusSeconds(seconds));
        }
    }
}

package com.org.apiautomation.auth;

import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.RequestSpecification;

/**
 * Attaches a token obtained via VR's login flow as a custom {@code
 * auth-token} header - confirmed against a real authenticated call that
 * this backend does not use {@code Authorization} for user authentication
 * at all (that header carries a separate, static app-identity credential
 * instead, unrelated to the per-user token this strategy manages).
 *
 * <p>Implements {@link AuthStrategy#apply} exactly like every other
 * mechanism this framework supports - {@code RequestSpecFactory} doesn't
 * know or care that this one involves logging in first rather than reading
 * a static value from config.
 *
 * <p><b>Why this can't reuse {@link BearerTokenAuthStrategy} anymore:</b> an
 * earlier version of this class did, since it also used to set {@code
 * Authorization: Bearer}. That header is confirmed wrong for this backend
 * now, so this class sets {@link #AUTH_TOKEN_HEADER} directly instead.
 *
 * <p><b>Why {@link #apply} explicitly removes the header before setting
 * it, rather than just calling {@code .header(...)} once:</b> plain {@code
 * .header(name, value)} - unlike {@code .auth().preemptive()...}, which
 * {@link BearerTokenAuthStrategy}/{@link OAuth2AuthStrategy} use - adds a
 * second header with the same name on a second call rather than replacing
 * it (verified empirically). {@code core.ApiClient} calls {@link #apply}
 * a second time, on the same spec, when retrying after a 401/403 - without
 * the explicit removal here, that retry would send two conflicting {@code
 * auth-token} values instead of the fresh one.
 */
public final class DynamicLoginAuthStrategy implements AuthStrategy {

    /** Also referenced by {@code core.ApiClient} to decide whether a 401/403 is retry-eligible - kept as one shared constant rather than the literal string in two places. */
    public static final String AUTH_TOKEN_HEADER = "auth-token";

    private final AuthenticationManager authenticationManager = new AuthenticationManager();

    @Override
    public RequestSpecification apply(RequestSpecification requestSpec) {
        String token = authenticationManager.getValidToken();
        ((FilterableRequestSpecification) requestSpec).removeHeader(AUTH_TOKEN_HEADER);
        return requestSpec.header(AUTH_TOKEN_HEADER, token);
    }

    @Override
    public void invalidate() {
        authenticationManager.invalidateToken();
    }
}

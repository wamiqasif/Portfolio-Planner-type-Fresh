package com.org.apiautomation.utils;

/**
 * Masks a sensitive string before it's logged. Exists now, and not before,
 * because there's finally a real need for it: {@code
 * auth.AuthenticationManager} uses {@link #mask} to log "a token was
 * obtained" without ever printing the token itself - the same bar every
 * other utility in this framework was held to (see the Utilities-layer
 * decision not to build {@code JsonUtils}/{@code RandomDataGenerator}/
 * {@code DateUtils} until a real caller existed).
 *
 * <p>{@link #MASKED_VALUE} is also now the single shared placeholder
 * {@code listeners.HttpLoggingFilter} uses for redacted headers, so a
 * reviewer scanning any log file sees one consistent marker for "a secret
 * was here," not two different ones invented independently.
 */
public final class SensitiveDataMasker {

    public static final String MASKED_VALUE = "***REDACTED***";

    private SensitiveDataMasker() {
    }

    /** Returns the masked placeholder for any non-blank value, or the value unchanged if it's already blank (nothing to hide). */
    public static String mask(String value) {
        return (value == null || value.isBlank()) ? value : MASKED_VALUE;
    }
}

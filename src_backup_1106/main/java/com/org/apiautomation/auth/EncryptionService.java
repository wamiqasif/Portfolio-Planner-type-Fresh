package com.org.apiautomation.auth;

import com.org.apiautomation.exceptions.AuthenticationException;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA-encrypts a plaintext string exactly like the real login page's own
 * {@code new JSEncrypt().setPublicKey(...).encrypt(...)} call - confirmed
 * against the frontend, which RSA-encrypts the password client-side before
 * ever sending it; the login backend never accepts a plaintext password
 * field (a plaintext submission is what produced the earlier "Confirm
 * Password is blank" rejection - the server tried to treat it as
 * ciphertext). JSEncrypt is PKCS#1 v1.5-padded RSA, not OAEP - matching
 * {@code Cipher.getInstance("RSA/ECB/PKCS1Padding")} below, not
 * {@code OAEPPadding}.
 */
public final class EncryptionService {

    private EncryptionService() {
    }

    /**
     * RSA-encrypts {@code plaintext} with {@code base64PublicKey} (base64
     * DER, X.509 {@code SubjectPublicKeyInfo} - a PEM {@code PUBLIC KEY}
     * block with the header/footer/line breaks stripped), returning base64
     * ciphertext - the same string shape {@code LoginService} sends as the
     * {@code password} form field.
     */
    public static String encrypt(String plaintext, String base64PublicKey) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, loadPublicKey(base64PublicKey));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ciphertext);
        } catch (Exception e) {
            throw new AuthenticationException("Failed to RSA-encrypt the login password", e);
        }
    }

    public static PublicKey loadPublicKey(String base64PublicKey) {
        try {
            byte[] derBytes = Base64.getDecoder().decode(base64PublicKey.replaceAll("\\s", ""));
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(derBytes));
        } catch (Exception e) {
            throw new AuthenticationException("Failed to parse the configured RSA public key", e);
        }
    }

    /**
     * SHA-256 fingerprint of the key's DER bytes, for diagnostics only - lets
     * a log line or the standalone verifier confirm which key is actually
     * configured/loaded without ever printing the key material itself.
     */
    public static String fingerprint(String base64PublicKey) {
        try {
            byte[] derBytes = Base64.getDecoder().decode(base64PublicKey.replaceAll("\\s", ""));
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(derBytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new AuthenticationException("Failed to compute the RSA public key fingerprint", e);
        }
    }
}

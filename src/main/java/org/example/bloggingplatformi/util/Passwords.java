package org.example.bloggingplatformi.util;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Hashes and checks passwords. The plain password is never stored.
 *
 * <p>It uses PBKDF2, which is built into Java, so no extra library is needed.
 * A random salt is added to each password, so two users with the same password
 * get different hashes. The stored text is {@code salt:hash}, both in Base64.
 */
public final class Passwords {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 65_536;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_BYTES = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    private Passwords() {
    }

    /** @return the text to store in {@code users.password_hash} */
    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt);
        Base64.Encoder base64 = Base64.getEncoder();
        return base64.encodeToString(salt) + ":" + base64.encodeToString(hash);
    }

    /** Hashes the typed password with the stored salt and compares the two hashes. */
    public static boolean matches(String password, String stored) {
        if (password == null || stored == null || !stored.contains(":")) {
            return false;
        }
        String[] parts = stored.split(":", 2);
        try {
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] expected = Base64.getDecoder().decode(parts[1]);
            // MessageDigest.isEqual takes the same time for any input, so it leaks nothing.
            return MessageDigest.isEqual(expected, pbkdf2(password, salt));
        } catch (IllegalArgumentException notBase64) {
            return false;
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is not available in this JVM", e);
        } finally {
            spec.clearPassword();
        }
    }
}

package org.example.bloggingplatformi.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Turns a password into its SHA-256 hash: 64 hexadecimal characters.
 * The database stores the hash, never the password. To sign in, we hash the typed
 * password again and compare the two hashes.
 */
public final class Passwords {

    private Passwords() {
    }

    public static String hash(String password) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] bytes = sha256.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e); // never happens in Java 21
        }
    }

    public static boolean matches(String password, String storedHash) {
        return hash(password).equals(storedHash);
    }
}

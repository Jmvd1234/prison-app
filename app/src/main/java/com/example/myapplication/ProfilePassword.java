package com.example.myapplication;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Fast salted checks for this local prototype, as requested by its owner.
 * SHA-256 avoids plaintext storage but is not a production password-hardening KDF.
 * Older PBKDF2 values can still be verified once and rewritten after successful login.
 */
final class ProfilePassword {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CURRENT_PREFIX = "v2$SHA256$";
    private static final String SHA256 = "PBKDF2WithHmacSHA256";
    private static final String SHA1 = "PBKDF2WithHmacSHA1";
    private ProfilePassword() { }

    static String hash(String password) throws GeneralSecurityException {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return CURRENT_PREFIX + hex(salt) + "$" + hex(fastDigest(password, salt));
    }

    static boolean needsUpdate(String encoded) {
        return encoded == null || !encoded.startsWith(CURRENT_PREFIX);
    }

    private static byte[] fastDigest(String password, byte[] salt) throws GeneralSecurityException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        byte[] bytes = password.getBytes(StandardCharsets.UTF_8);
        try { return digest.digest(bytes); }
        finally { Arrays.fill(bytes, (byte) 0); }
    }

    static boolean matches(String password, String encoded) throws GeneralSecurityException {
        if (password == null || encoded == null) return false;
        String[] parts = encoded.split("\\$", -1);
        if (parts.length == 4 && parts[0].equals("v2") && parts[1].equals("SHA256")) {
            if (parts[2].length() != 32 || parts[3].length() != 64) return false;
            try {
                return MessageDigest.isEqual(unhex(parts[3]), fastDigest(password, unhex(parts[2])));
            } catch (IllegalArgumentException malformed) { return false; }
        }
        if (parts.length != 5 || !parts[0].equals("v1")) return false;
        if (!parts[1].equals(SHA256) && !parts[1].equals(SHA1)) return false;
        if (parts[3].length() != 32 || parts[4].length() != 64) return false;
        try {
            int work = Integer.parseInt(parts[2]);
            if (work != iterations(parts[1])) return false;
            byte[] salt = unhex(parts[3]), expected = unhex(parts[4]);
            if (salt.length != 16 || expected.length != 32) return false;
            return MessageDigest.isEqual(expected, derive(password, parts[1], work, salt));
        } catch (IllegalArgumentException malformed) { return false; }
    }

    static boolean matchesLegacy(String password, String legacy) {
        return password != null && legacy != null && MessageDigest.isEqual(
                password.getBytes(StandardCharsets.UTF_8), legacy.getBytes(StandardCharsets.UTF_8));
    }

    private static int iterations(String algorithm) { return algorithm.equals(SHA256) ? 600_000 : 1_400_000; }

    private static byte[] derive(String password, String algorithm, int work, byte[] salt)
            throws GeneralSecurityException {
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, work, 256);
        Arrays.fill(chars, '\0');
        try { return SecretKeyFactory.getInstance(algorithm).generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) { out.append(Character.forDigit((b & 255) >>> 4, 16)); out.append(Character.forDigit(b & 15, 16)); }
        return out.toString();
    }

    private static byte[] unhex(String value) {
        if (value.length() % 2 != 0) throw new IllegalArgumentException();
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int high = Character.digit(value.charAt(i * 2), 16), low = Character.digit(value.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) throw new IllegalArgumentException();
            bytes[i] = (byte) ((high << 4) | low);
        }
        return bytes;
    }
}

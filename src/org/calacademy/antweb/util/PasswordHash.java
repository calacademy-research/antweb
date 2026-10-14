package org.calacademy.antweb.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Password hashing for AntWeb logins.
 *
 * Passwords used to be stored as plain text in login.password. They are now
 * stored as PBKDF2-HMAC-SHA256 hashes with a random salt, in the form
 *
 *   pbkdf2_sha256$iterations$salt$hash      (salt and hash in base64)
 *
 * An empty password ("") is kept as is: AntWeb uses it to mark an invitation
 * that has not been accepted yet (see LoginDb.findInviteId()).
 *
 * Existing plain-text passwords still work: matches() accepts them, and
 * LoginMgr converts them to hashes in the background at startup, and
 * LoginAction converts them at the next login.
 */
public final class PasswordHash {

    private static final String PREFIX = "pbkdf2_sha256";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    // OWASP Password Storage Cheat Sheet recommendation for PBKDF2-HMAC-SHA256.
    static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHash() {}

    /** Hash a plain-text password. */
    public static String hash(String plain) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(plain, salt, ITERATIONS);
        Base64.Encoder b64 = Base64.getEncoder();
        return PREFIX + "$" + ITERATIONS + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(hash);
    }

    /**
     * The value to write to login.password: hashes a plain-text password,
     * and leaves null, "" and values that are already hashes unchanged.
     */
    public static String forStorage(String value) {
        if (value == null || value.isEmpty() || isHashed(value)) return value;
        return hash(value);
    }

    /** True if the stored value is a hash made by this class. */
    public static boolean isHashed(String stored) {
        return parse(stored) != null;
    }

    /** True if the stored value is still plain text and should be hashed. */
    public static boolean needsRehash(String stored) {
        if (stored == null || stored.isEmpty()) return false;
        Parsed p = parse(stored);
        return p == null || p.iterations < ITERATIONS;
    }

    /**
     * Does the plain-text password typed by a user match the stored value?
     * An empty stored value (an unaccepted invitation) never matches.
     */
    public static boolean matches(String stored, String plain) {
        if (stored == null || stored.isEmpty() || plain == null || plain.isEmpty()) return false;
        Parsed p = parse(stored);
        if (p == null) {
            // Legacy plain-text value, compared in constant time.
            return MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8), plain.getBytes(StandardCharsets.UTF_8));
        }
        byte[] actual = pbkdf2(plain, p.salt, p.iterations);
        return MessageDigest.isEqual(p.hash, actual);
    }

    private static final class Parsed {
        final int iterations; final byte[] salt; final byte[] hash;
        Parsed(int iterations, byte[] salt, byte[] hash) { this.iterations = iterations; this.salt = salt; this.hash = hash; }
    }

    private static Parsed parse(String stored) {
        if (stored == null || !stored.startsWith(PREFIX + "$")) return null;
        String[] parts = stored.split("\\$");
        if (parts.length != 4) return null;
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] hash = Base64.getDecoder().decode(parts[3]);
            if (iterations < 1 || salt.length == 0 || hash.length != HASH_BITS / 8) return null;
            return new Parsed(iterations, salt, hash);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static byte[] pbkdf2(String plain, byte[] salt, int iterations) {
        try {
            KeySpec spec = new PBEKeySpec(plain.toCharArray(), salt, iterations, HASH_BITS);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 not available", e);
        }
    }
}

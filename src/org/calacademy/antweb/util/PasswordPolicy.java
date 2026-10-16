package org.calacademy.antweb.util;

import java.util.Locale;
import java.util.Set;

/**
 * Rules for a new AntWeb password (reset, self-service change, admin-set).
 *
 *   - at least 12 characters
 *   - not the same as the username or the email address
 *   - not one of a short built-in list of very common passwords
 *   - typed the same way twice
 *
 * check() returns null when the password is acceptable, otherwise a short
 * plain-language reason that is safe to show (it never echoes the password).
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;

    private PasswordPolicy() {}

    // Very common passwords (lowercase). Most are shorter than 12 characters and
    // already fail the length rule; they are kept so the list still works if the
    // minimum length is ever lowered.
    static final Set<String> COMMON = Set.of(
        "123456", "123456789", "12345678", "1234567890", "12345", "1234567", "111111", "000000",
        "123123", "654321", "666666", "121212", "112233", "987654321", "123321", "7777777",
        "password", "password1", "password12", "password123", "password1234", "passw0rd", "p@ssw0rd", "p@ssword",
        "qwerty", "qwerty123", "qwertyuiop", "1q2w3e4r", "1q2w3e4r5t", "1qaz2wsx", "zaq12wsx", "asdfghjkl",
        "asdf1234", "abc123", "abcd1234", "abc12345", "iloveyou", "letmein", "letmein123", "welcome",
        "welcome1", "welcome123", "admin", "admin123", "administrator", "root", "toor", "changeme",
        "changeme123", "default", "monkey", "dragon", "football", "baseball", "soccer", "hockey",
        "master", "shadow", "sunshine", "princess", "superman", "batman", "trustno1", "starwars",
        "whatever", "freedom", "michael", "jennifer", "jordan23", "hello123", "login", "secret",
        "access", "mustang", "charlie", "donald", "computer", "internet", "google", "samsung",
        "qwerty12345", "123qwe", "123abc", "a1b2c3d4", "aa123456", "zxcvbnm", "zxcvbnm123", "q1w2e3r4",
        "11111111", "00000000", "88888888", "123456789012", "1234567890ab", "qwertyuiop123", "password1!", "passwordpassword",
        "iloveyou123", "welcomewelcome", "letmeinletmein", "adminadmin", "antweb", "antweb123", "antweb2026",
        "antwebantweb", "formicidae", "formicidae1", "camponotus", "calacademy", "calacademy1", "antsants",
        "ants12345678", "antweb.org", "antcat", "antcat123"
    );

    /** Null if acceptable, else the reason. */
    public static String check(String password, String retype, String userName, String email) {
        if (password == null || password.isEmpty()) return "Please enter a new password.";
        if (retype == null || !password.equals(retype)) return "The two passwords do not match.";
        if (password.length() < MIN_LENGTH) return "The password must be at least " + MIN_LENGTH + " characters long.";
        String lower = password.toLowerCase(Locale.ROOT);
        if (userName != null && !userName.isEmpty() && lower.equals(userName.toLowerCase(Locale.ROOT))) {
            return "The password may not be the same as your username.";
        }
        if (email != null && !email.isEmpty() && lower.equals(email.toLowerCase(Locale.ROOT))) {
            return "The password may not be the same as your email address.";
        }
        if (isCommon(lower)) return "That password is too common. Please choose another.";
        return null;
    }

    /** True if the (any-case) password is in the built-in list of common passwords. */
    public static boolean isCommon(String password) {
        if (password == null) return false;
        return COMMON.contains(password.toLowerCase(Locale.ROOT));
    }
}

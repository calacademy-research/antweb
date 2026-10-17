package org.calacademy.antweb.util;

import java.util.regex.Pattern;

import org.calacademy.antweb.Login;

/**
 * Which characters a login's user name and first/last names may contain: letters (any
 * language), digits, spaces and . _ - @ ' + (so an email address can be a user name).
 * Pages escape these fields too; this keeps markup out of them in the first place.
 *
 * Existing names are not re-checked unless they are changed, so old accounts still save.
 */
public final class LoginNames {

    private LoginNames() {}

    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{M}\\p{N} ._@'+-]{1,100}");
    private static final Pattern PERSON_NAME = Pattern.compile("[\\p{L}\\p{M}\\p{N} ._@'+-]{0,100}");

    public static final String NAME_RULE =
        "A user name may contain only letters, digits, spaces and . _ - @ ' + (at most 100 characters).";
    public static final String PERSON_NAME_RULE =
        "First and last names may contain only letters, digits, spaces and . _ - @ ' + (at most 100 characters).";

    public static boolean isValidName(String name) {
        return name != null && !name.trim().isEmpty() && NAME.matcher(name).matches();
    }

    public static boolean isValidPersonName(String name) {
        return name == null || PERSON_NAME.matcher(name).matches();
    }

    // name@host.tld, no spaces, quotes or angle brackets.
    private static final Pattern EMAIL = Pattern.compile("[^\\s@<>\"'&]+@[^\\s@<>\"'&]+\\.[^\\s@<>\"'&]+");

    public static boolean isValidEmail(String email) {
        return email != null && email.length() >= 6 && email.length() <= 200 && EMAIL.matcher(email).matches();
    }

    private static boolean changed(String newValue, String oldValue) {
        if (newValue == null) return false;
        return !newValue.equals(oldValue);
    }

    /**
     * Null if the new values may be saved, else a message (plain text). Only values that differ
     * from the stored login (null for a new login) are checked; an empty user name is left to
     * the caller's own "required" check. A changed, non-empty email must look like an email.
     */
    public static String problem(String name, String firstName, String lastName, String email, Login stored) {
        if (changed(name, stored == null ? null : stored.getName()) && name != null && !name.isEmpty()
            && !isValidName(name)) return NAME_RULE;
        if (changed(firstName, stored == null ? null : stored.getFirstName()) && !isValidPersonName(firstName)) return PERSON_NAME_RULE;
        if (changed(lastName, stored == null ? null : stored.getLastName()) && !isValidPersonName(lastName)) return PERSON_NAME_RULE;
        if (changed(email, stored == null ? null : stored.getEmail()) && email != null && !email.isEmpty()
            && !isValidEmail(email)) return "Must enter a valid email.";
        return null;
    }
}

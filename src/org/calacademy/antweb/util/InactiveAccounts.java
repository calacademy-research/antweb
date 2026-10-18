package org.calacademy.antweb.util;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Regular AntWeb accounts not used for three years become inactive (Brian, 2026-10-18).
 *
 * An inactive account (login.inactive = 1) cannot log in with its password. Logging in to it,
 * or "Forgot your password?", emails a reactivation link (PasswordResetMgr.sendReactivationLink):
 * a password reset link that also sets inactive back to 0 (ResetPasswordAction). Nothing is
 * deleted. Accounts are made inactive only by an administrator (InactiveAccountsAction), never
 * automatically.
 *
 * The rule (select()), as one sentence: a regular account that was last used more than three
 * years ago, or that never logged in and was created more than three years ago.
 *
 *   regular        not an administrator (is_admin), no group (group_id > 0), no specimen or image
 *                  upload rights, no assigned projects (login_project) and no assigned countries
 *                  (login_country): the same roles Login.isCurator() and the 10-16 reset campaign use.
 *   last used      login.last_login, set at every password login and at account creation.
 *   never logged   last_login is empty. login.created cannot be used for the account's age: it is
 *   in             "ON UPDATE CURRENT_TIMESTAMP" and was overwritten for every row on 2026-10-14/16.
 *                  Login ids are given out in order (max + 1), so an account is older than three
 *                  years if a later account (higher id) had already logged in more than three years
 *                  ago: id < the highest id whose last_login is before the cutoff. Never-used accounts
 *                  newer than that are left alone.
 * Accounts already inactive, and id 0 or below (the anonymous login), are never selected.
 *
 * Pure Java, no database: InactiveAccountDb reads the rows, this class decides (unit tested).
 */
public final class InactiveAccounts {

    public static final int YEARS = 3;

    // What a person sees on the login page (LoginAction). Same level of detail as must_reset today:
    // only after the correct password. A wrong password keeps the usual message.
    public static final String LOGIN_MESSAGE =
        "This account has been inactive for more than three years. If it has an email address, we have sent a link to reactivate it.";
    public static final String LOGIN_MESSAGE_NO_EMAIL =
        "This account has been inactive for more than three years. It has no email address, so please write to antweb@antweb.org to reactivate it.";

    private InactiveAccounts() {}

    /** One row of the login table, as far as the rule needs it. */
    public static final class Candidate {
        public int id;
        public String name;
        public String firstName;
        public String lastName;
        public String email;
        public boolean isAdmin;
        public int groupId;
        public boolean uploadSpecimens;
        public boolean uploadImages;
        public boolean hasProjects;
        public boolean hasCountries;
        public LocalDateTime lastLogin;   // null = never logged in
        public boolean inactive;

        public Candidate() {}

        /** For tests: a regular account with this id and last login. */
        public Candidate(int id, LocalDateTime lastLogin) {
            this.id = id;
            this.groupId = -1;
            this.lastLogin = lastLogin;
        }

        public boolean hasEmail() {
            return email != null && email.contains("@");
        }

        public String getDisplayName() {
            String full = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
            return full.isEmpty() ? (name == null ? "" : name) : full;
        }
    }

    /** now minus three years. */
    public static LocalDateTime cutoff(LocalDateTime now) {
        return now.minusYears(YEARS);
    }

    /** Not an administrator or curator in any way. */
    public static boolean isRegular(Candidate c) {
        return c.id > 0 && !c.isAdmin && c.groupId <= 0 && !c.uploadSpecimens && !c.uploadImages
            && !c.hasProjects && !c.hasCountries;
    }

    /**
     * The highest login id whose last login is before the cutoff (any account, curators too),
     * or 0 if there is none. Never-used accounts with a lower id were created before the cutoff.
     */
    public static int oldIdLimit(List<Candidate> all, LocalDateTime cutoff) {
        int max = 0;
        for (Candidate c : all) {
            if (c.lastLogin != null && c.lastLogin.isBefore(cutoff) && c.id > max) max = c.id;
        }
        return max;
    }

    /** The rule for one account, given the cutoff and oldIdLimit(). */
    public static boolean qualifies(Candidate c, LocalDateTime cutoff, int oldIdLimit) {
        if (c.inactive || !isRegular(c)) return false;
        if (c.lastLogin != null) return c.lastLogin.isBefore(cutoff);
        return c.id < oldIdLimit;
    }

    /** The accounts that would become inactive now, in the order given. */
    public static List<Candidate> select(List<Candidate> all, LocalDateTime now) {
        LocalDateTime cutoff = cutoff(now);
        int limit = oldIdLimit(all, cutoff);
        List<Candidate> out = new ArrayList<>();
        for (Candidate c : all) {
            if (qualifies(c, cutoff, limit)) out.add(c);
        }
        return out;
    }

    /** Backup table name for a run on this day: login_inactive_backup_YYYYMMDD. */
    public static String backupTableName(LocalDateTime now) {
        return String.format("login_inactive_backup_%04d%02d%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
    }
}

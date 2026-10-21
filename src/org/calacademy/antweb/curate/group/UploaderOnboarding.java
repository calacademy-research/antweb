package org.calacademy.antweb.curate.group;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.calacademy.antweb.home.LoginDb;
import org.calacademy.antweb.util.LoginNames;

/**
 * "Add an uploader" (AddUploaderAction): everything an administrator does when someone asks to
 * upload specimen data, in one transaction.
 *
 *   1. Find the person's login by email address or username (case does not matter), or create
 *      a new login for them (username = the email address, no password yet: they set one with
 *      the invitation link).
 *   2. Use an existing upload group (table ant_group), or create a new one (name, short name).
 *   3. Put the login in the upload group, give it the right to upload specimens (and images if
 *      asked), and store the email address on a login that has none.
 *   4. Make the login the group's admin curator when the group has none.
 *
 * Nothing is written when a check fails (all in one transaction). The welcome email
 * (welcomeSubject / welcomeBody) is a draft shown to the administrator, who copies it into their
 * own email and adds what the person needs to know; AntWeb does not send it.
 *
 * Plain JDBC with bound parameters, so the class can be tested against MySQL without the
 * application's in-memory lists (test/.../curate/UploaderOnboardingDbTest.java).
 */
public final class UploaderOnboarding {

    private UploaderOnboarding() {}

    /** login.name was varchar(32) when the table was made; a longer email gets a shorter username. */
    static final int MAX_USERNAME = 32;
    static final int MAX_EMAIL = 128;
    static final int MAX_PERSON_NAME = 64;
    static final int MAX_GROUP_NAME = 100;
    static final int MAX_ABBREV = 20;   // ant_group.abbrev varchar(20)

    private static final Pattern ABBREV = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");

    // ------------------------------------------------------------------ input

    /** What the administrator entered on the form. */
    public static final class Request {
        public String email = "";
        public String firstName = "";
        public String lastName = "";
        /** An existing group's id, or 0 to create the new group below. */
        public int groupId = 0;
        public String newGroupName = "";
        public String newGroupAbbrev = "";
        public boolean uploadImages = false;
        /** The login is already in another upload group: move it anyway. */
        public boolean move = false;

        static String clean(String s) {
            return s == null ? "" : s.trim();
        }

        public Request normalized() {
            email = clean(email);
            firstName = clean(firstName);
            lastName = clean(lastName);
            newGroupName = clean(newGroupName).replaceAll("\\s+", " ");
            newGroupAbbrev = clean(newGroupAbbrev);
            return this;
        }
    }

    /** Problems with the input itself (nothing looked up yet). Empty: fine. */
    public static List<String> inputProblems(Request r) {
        List<String> p = new ArrayList<>();
        if (r.email.isEmpty()) {
            p.add("Enter the person's email address (or the username of their AntWeb account).");
        } else if (r.email.length() > MAX_EMAIL) {
            p.add("The email address is longer than " + MAX_EMAIL + " characters.");
        } else if (r.email.contains("@") && !LoginNames.isValidEmail(r.email)) {
            p.add("\"" + r.email + "\" is not a valid email address.");
        } else if (!r.email.contains("@") && !LoginNames.isValidName(r.email)) {
            p.add(LoginNames.NAME_RULE);
        }
        if (r.firstName.length() > MAX_PERSON_NAME || r.lastName.length() > MAX_PERSON_NAME
            || !LoginNames.isValidPersonName(r.firstName) || !LoginNames.isValidPersonName(r.lastName)) {
            p.add(LoginNames.PERSON_NAME_RULE);
        }
        if (r.groupId < 0) p.add("Choose an upload group.");
        if (r.groupId == 0) {
            if (r.newGroupName.isEmpty()) {
                p.add("Choose an existing upload group, or enter the full name of the new upload group.");
            } else if (r.newGroupName.length() > MAX_GROUP_NAME || r.newGroupName.matches(".*[<>\"].*")) {
                p.add("The upload group's full name must be at most " + MAX_GROUP_NAME + " characters, without < > or \".");
            }
            if (r.newGroupAbbrev.isEmpty()) {
                p.add("Enter a short name for the new upload group (the AntCat institution code when there is one).");
            } else if (r.newGroupAbbrev.length() > MAX_ABBREV || !ABBREV.matcher(r.newGroupAbbrev).matches()) {
                p.add("The short name must start with a letter and contain only letters, digits, - and _ (at most "
                    + MAX_ABBREV + " characters), for example MCZC or Gomez.");
            }
        }
        return p;
    }

    // ------------------------------------------------------------------ lookups

    /** A login row, as far as this page needs it. */
    public static final class LoginRow {
        public int id;
        public String name;
        public String email;
        public String firstName;
        public String lastName;
        public int groupId;
        public boolean isAdmin;
        public boolean uploadSpecimens;
        public boolean uploadImages;
        public boolean hasPassword;
        public boolean inactive;

        public String displayName() {
            String full = ((firstName == null ? "" : firstName.trim()) + " " + (lastName == null ? "" : lastName.trim())).trim();
            return full.isEmpty() ? name : full;
        }
    }

    /** An upload group, with the number of logins in it. */
    public static final class GroupRow {
        public int id;
        public String name;
        public String abbrev;
        public int adminLoginId;
        public int uploaders;

        public String label() {
            String a = abbrev == null || abbrev.trim().isEmpty() ? "" : " (" + abbrev.trim() + ")";
            return name + a + ", group " + id + ", " + uploaders + (uploaders == 1 ? " login" : " logins");
        }
    }

    /** Logins whose email address or username is the given value (case does not matter). */
    public static List<LoginRow> findLogins(Connection c, String emailOrName) throws SQLException {
        List<LoginRow> rows = new ArrayList<>();
        String v = emailOrName == null ? "" : emailOrName.trim().toLowerCase(Locale.ROOT);
        if (v.isEmpty()) return rows;
        boolean hasInactive = hasColumn(c, "login", "inactive");
        String q = "select id, name, email, first_name, last_name, group_id, is_admin, is_upload_specimens, is_upload_images,"
            + " password" + (hasInactive ? ", inactive" : "")
            + " from login where id > 0 and (lower(email) = ? or lower(name) = ?) order by id";
        try (PreparedStatement s = c.prepareStatement(q)) {
            s.setString(1, v);
            s.setString(2, v);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) {
                    LoginRow l = new LoginRow();
                    l.id = r.getInt("id");
                    l.name = r.getString("name");
                    l.email = r.getString("email");
                    l.firstName = r.getString("first_name");
                    l.lastName = r.getString("last_name");
                    l.groupId = r.getInt("group_id");
                    l.isAdmin = r.getBoolean("is_admin");
                    l.uploadSpecimens = r.getBoolean("is_upload_specimens");
                    l.uploadImages = r.getBoolean("is_upload_images");
                    String pw = r.getString("password");
                    l.hasPassword = pw != null && !pw.isEmpty();
                    l.inactive = hasInactive && r.getBoolean("inactive");
                    rows.add(l);
                }
            }
        }
        return rows;
    }

    public static GroupRow getGroup(Connection c, int id) throws SQLException {
        for (GroupRow g : groups(c, id)) return g;
        return null;
    }

    /** All upload groups, by name. */
    public static List<GroupRow> allGroups(Connection c) throws SQLException {
        return groups(c, null);
    }

    private static List<GroupRow> groups(Connection c, Integer id) throws SQLException {
        String q = "select g.id, g.name, g.abbrev, g.admin_login_id,"
            + " (select count(*) from login l where l.group_id = g.id) as uploaders"
            + " from ant_group g where g.id > 0" + (id == null ? "" : " and g.id = ?") + " order by g.name, g.id";
        List<GroupRow> rows = new ArrayList<>();
        try (PreparedStatement s = c.prepareStatement(q)) {
            if (id != null) s.setInt(1, id);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) {
                    GroupRow g = new GroupRow();
                    g.id = r.getInt("id");
                    g.name = r.getString("name");
                    g.abbrev = r.getString("abbrev");
                    g.adminLoginId = r.getInt("admin_login_id");
                    g.uploaders = r.getInt("uploaders");
                    rows.add(g);
                }
            }
        }
        return rows;
    }

    /** An existing group with this full name or short name (case does not matter), or null. */
    static GroupRow groupNamed(Connection c, String name, String abbrev) throws SQLException {
        String q = "select id from ant_group where lower(name) = ? or (? <> '' and lower(abbrev) = ?) order by id limit 1";
        try (PreparedStatement s = c.prepareStatement(q)) {
            s.setString(1, name.toLowerCase(Locale.ROOT));
            String a = abbrev == null ? "" : abbrev.toLowerCase(Locale.ROOT);
            s.setString(2, a);
            s.setString(3, a);
            try (ResultSet r = s.executeQuery()) {
                if (r.next()) return getGroup(c, r.getInt(1));
            }
        }
        return null;
    }

    private static boolean hasColumn(Connection c, String table, String column) throws SQLException {
        String q = "select count(*) from information_schema.columns where table_schema = database() and table_name = ? and column_name = ?";
        try (PreparedStatement s = c.prepareStatement(q)) {
            s.setString(1, table);
            s.setString(2, column);
            try (ResultSet r = s.executeQuery()) {
                return r.next() && r.getInt(1) > 0;
            }
        }
    }

    // ------------------------------------------------------------------ apply

    /** What was done (or why nothing was). */
    public static final class Result {
        /** Empty when it worked. When not empty nothing was changed. */
        public final List<String> problems = new ArrayList<>();
        /** Plain-text lines for the summary, in order. */
        public final List<String> done = new ArrayList<>();
        public LoginRow login;
        public GroupRow group;
        public boolean newLogin;
        public boolean newGroup;
        /** The login has no password yet, or is inactive: the welcome email needs a set-password link. */
        public boolean needsPasswordLink;
        public boolean uploadImages;

        public boolean ok() {
            return problems.isEmpty();
        }
    }

    /**
     * Checks everything, then makes the changes in one transaction (rolled back on any error).
     * The connection's auto-commit setting is restored afterwards.
     */
    public static Result apply(Connection c, Request in) throws SQLException {
        Request r = in.normalized();
        Result res = new Result();
        res.problems.addAll(inputProblems(r));
        if (!res.ok()) return res;

        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            // Who: an existing login, or a new one.
            List<LoginRow> found = findLogins(c, r.email);
            if (found.size() > 1) {
                StringBuilder ids = new StringBuilder();
                for (LoginRow l : found) ids.append(ids.length() == 0 ? "" : ", ").append(l.id).append(" (").append(l.name).append(")");
                res.problems.add("\"" + r.email + "\" matches more than one login: " + ids
                    + ". Open them in the Login Manager and keep one, or enter the username of the one to use.");
                c.rollback();
                return res;
            }
            LoginRow login = found.isEmpty() ? null : found.get(0);
            if (login == null && !r.email.contains("@")) {
                res.problems.add("No login has the username \"" + r.email + "\". Enter the person's email address to invite them.");
                c.rollback();
                return res;
            }

            // Which group.
            GroupRow group;
            if (r.groupId > 0) {
                group = getGroup(c, r.groupId);
                if (group == null) {
                    res.problems.add("Upload group " + r.groupId + " does not exist (any more). Reload the page and choose again.");
                    c.rollback();
                    return res;
                }
            } else {
                GroupRow same = groupNamed(c, r.newGroupName, r.newGroupAbbrev);
                if (same != null) {
                    res.problems.add("An upload group with this name or short name exists already: " + same.label()
                        + ". Choose it under \"Existing upload group\", or use another name.");
                    c.rollback();
                    return res;
                }
                group = null;
            }

            // A login already in another group: its uploads would go elsewhere from now on.
            if (login != null && login.groupId > 0 && (group == null || login.groupId != group.id) && !r.move) {
                GroupRow old = getGroup(c, login.groupId);
                res.problems.add(login.displayName() + " (" + login.name + ") is already in the upload group "
                    + (old == null ? "number " + login.groupId : old.label())
                    + ". To move them, tick \"Move them to this upload group\" and click Add uploader again."
                    + " From then on their uploads replace the specimens of the new upload group, not the old one.");
                c.rollback();
                return res;
            }

            // Changes.
            if (group == null) {
                group = new GroupRow();
                group.id = nextId(c, "ant_group");
                group.name = r.newGroupName;
                group.abbrev = r.newGroupAbbrev;
                group.adminLoginId = 0;
                try (PreparedStatement s = c.prepareStatement("insert into ant_group (id, name, admin_login_id, abbrev) values (?, ?, ?, ?)")) {
                    s.setInt(1, group.id);
                    s.setString(2, group.name);
                    s.setInt(3, 0);
                    s.setString(4, group.abbrev);
                    s.executeUpdate();
                }
                res.newGroup = true;
                res.done.add("Created the upload group \"" + group.name + "\" (short name " + group.abbrev + ", group number " + group.id + ").");
            }

            if (login == null) {
                login = new LoginRow();
                // Above every login number ever used (a deleted login's curator folder is not reused).
                login.id = LoginDb.nextLoginId(c, LoginDb.curatorDir());
                login.email = r.email;
                login.name = freeUsername(c, r.email);
                login.firstName = r.firstName;
                login.lastName = r.lastName;
                login.groupId = group.id;
                login.uploadSpecimens = true;
                login.uploadImages = r.uploadImages;
                // The same columns as LoginDb.saveLogin(); no password: set with the invitation link.
                String ins = "insert into login (id, name, first_name, last_name, email, password, group_id, is_admin,"
                    + " is_upload_specimens, is_upload_images, upload_as) values (?, ?, ?, ?, ?, '', ?, 0, 1, ?, null)";
                try (PreparedStatement s = c.prepareStatement(ins)) {
                    s.setInt(1, login.id);
                    s.setString(2, login.name);
                    s.setString(3, login.firstName);
                    s.setString(4, login.lastName);
                    s.setString(5, login.email);
                    s.setInt(6, group.id);
                    s.setInt(7, r.uploadImages ? 1 : 0);
                    s.executeUpdate();
                }
                res.newLogin = true;
                res.done.add("Created the login " + login.name + " (login " + login.id + ") for " + login.email + ", with no password yet.");
            } else {
                boolean addEmail = (login.email == null || login.email.trim().isEmpty()) && r.email.contains("@");
                boolean addFirst = (login.firstName == null || login.firstName.trim().isEmpty()) && !r.firstName.isEmpty();
                boolean addLast = (login.lastName == null || login.lastName.trim().isEmpty()) && !r.lastName.isEmpty();
                // "created = created": login.created is ON UPDATE CURRENT_TIMESTAMP in production.
                String up = "update login set group_id = ?, is_upload_specimens = 1,"
                    + " is_upload_images = greatest(is_upload_images, ?),"
                    + " email = case when ? then ? else email end,"
                    + " first_name = case when ? then ? else first_name end,"
                    + " last_name = case when ? then ? else last_name end,"
                    + " created = created where id = ?";
                try (PreparedStatement s = c.prepareStatement(up)) {
                    s.setInt(1, group.id);
                    s.setInt(2, r.uploadImages ? 1 : 0);
                    s.setBoolean(3, addEmail);
                    s.setString(4, r.email);
                    s.setBoolean(5, addFirst);
                    s.setString(6, r.firstName);
                    s.setBoolean(7, addLast);
                    s.setString(8, r.lastName);
                    s.setInt(9, login.id);
                    s.executeUpdate();
                }
                int oldGroup = login.groupId;
                String who = login.name + " (login " + login.id + ")";
                if (oldGroup == group.id) {
                    res.done.add(who + " was already in this upload group.");
                } else if (oldGroup > 0) {
                    res.done.add("Moved " + who + " from upload group " + oldGroup + " to this upload group.");
                } else {
                    res.done.add("Put " + who + " in this upload group.");
                }
                res.done.add(login.uploadSpecimens ? who + " could already upload specimens." : "Gave " + who + " the right to upload specimens.");
                if (r.uploadImages && !login.uploadImages) res.done.add("Gave " + who + " the right to upload images.");
                if (addEmail) res.done.add("Stored the email address " + r.email + " on " + who + " (it had none, so password resets and upload emails could not reach them).");
                if (addFirst || addLast) res.done.add("Stored the first and last name given on the form on " + who + ".");
                if (login.inactive) res.done.add(who + " is inactive (unused for three years): the welcome email's link sets a new password and makes it active again.");
                login.groupId = group.id;
                login.uploadSpecimens = true;
                login.uploadImages = login.uploadImages || r.uploadImages;
                if (addEmail) login.email = r.email;
                if (addFirst) login.firstName = r.firstName;
                if (addLast) login.lastName = r.lastName;
            }

            if (group.adminLoginId <= 0) {
                try (PreparedStatement s = c.prepareStatement("update ant_group set admin_login_id = ? where id = ?")) {
                    s.setInt(1, login.id);
                    s.setInt(2, group.id);
                    s.executeUpdate();
                }
                group.adminLoginId = login.id;
                res.done.add("Made " + login.name + " the upload group's admin curator (it had none).");
            }

            c.commit();
            group.uploaders = getGroup(c, group.id).uploaders;
            res.login = login;
            res.group = group;
            res.uploadImages = login.uploadImages;
            res.needsPasswordLink = !login.hasPassword || login.inactive;
            return res;
        } catch (SQLException | RuntimeException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
    }

    private static int nextId(Connection c, String table) throws SQLException {
        // table is the constant above (ant_group), never user input.
        try (PreparedStatement s = c.prepareStatement("select coalesce(max(id), 0) + 1 from " + table + " for update");
             ResultSet r = s.executeQuery()) {
            r.next();
            return Math.max(1, r.getInt(1));
        }
    }

    /** The email address as username; a shorter one when it is too long or taken (jane.doe, jane.doe2, ...). */
    static String freeUsername(Connection c, String email) throws SQLException {
        String base = email.length() <= MAX_USERNAME ? email : email.substring(0, email.indexOf('@') > 0 ? email.indexOf('@') : 0);
        if (base.length() > MAX_USERNAME - 3) base = base.substring(0, MAX_USERNAME - 3);
        if (base.isEmpty()) base = "uploader";
        String candidate = base;
        for (int n = 2; n < 1000; n++) {
            if (findLogins(c, candidate).isEmpty()) return candidate;
            candidate = base + n;
        }
        throw new SQLException("no free username for " + base);
    }

    // ------------------------------------------------------------------ welcome email

    public static final String WELCOME_SUBJECT = "Your AntWeb upload account";

    /** The welcome email's subject. */
    public static String welcomeSubject() {
        return WELCOME_SUBJECT;
    }

    /** The line the administrator replaces with their own name before sending. */
    public static final String SIGNATURE_PLACEHOLDER = "[Your name]";

    /**
     * The draft welcome email, as plain text (the summary page puts it in a read-only text box,
     * escaped there). setPasswordLink is the one-time invitation link (null when the login already
     * has a password). site is e.g. https://www.antweb.org. The administrator sends it from their
     * own address, so it is signed with SIGNATURE_PLACEHOLDER for them to replace.
     */
    public static String welcomeBody(String site, LoginRow login, GroupRow group, String setPasswordLink, int linkDays) {
        // Greet by first and last name; without them (or a username only) a plain "Hello,".
        String name = ((login.firstName == null ? "" : login.firstName.trim()) + " " + (login.lastName == null ? "" : login.lastName.trim())).trim();
        String hello = name.isEmpty() ? "Hello," : "Hello " + name + ",";
        String groupText = group.name + (group.abbrev == null || group.abbrev.isEmpty() ? "" : " (" + group.abbrev + ")");
        String loginUrl = site + "/login.do";
        String curate = site + "/curate.do";
        String template = site + "/web/homepage/AntWeb_specimen_data_requirements_2026v1.xlsx";
        String docs = site + "/documentation.do";
        StringBuilder b = new StringBuilder();
        b.append(hello).append("\n\n");
        b.append("You can now upload specimen data to AntWeb for the upload group ").append(groupText).append(".\n\n");
        b.append("1. Log in. ");
        if (setPasswordLink != null) {
            b.append("This link is for you only: open it to choose your password (it works once, for ").append(linkDays).append(" days):\n")
             .append(setPasswordLink).append("\n")
             .append("Then log in at ").append(loginUrl).append(" with your username ").append(login.name)
             .append(" or your email address. If the link has expired, use \"Forgot your password?\" on that page.\n\n");
        } else {
            b.append("Log in at ").append(loginUrl).append(" with your username ").append(login.name)
             .append(" and your password (\"Forgot your password?\" on that page emails a link to choose a new one).")
             .append(" If you are logged in already, log out and in again so that AntWeb shows your new upload tools.\n\n");
        }
        b.append("2. Open Curator Tools (").append(curate).append("); AntWeb shows the Curator Tools link once you are logged in.\n\n");
        b.append("3. Prepare your data with the specimen template and its instructions:\n").append(template).append("\n")
         .append("Every specimen code must be unique on AntWeb: use your collection's own code prefix, not codes another collection uses.\n\n");
        b.append("4. Upload. Save the sheet as a tab-delimited text file, put it in a ZIP file, and upload it under \"Specimen Data\" on Curator Tools.")
         .append(" Each upload replaces all specimen records of your upload group, so always upload your complete data set, not only new records.\n\n");
        b.append("5. Check the upload report. You get an email when the upload is finished. The report lists the names AntWeb changed to the current valid name (from AntCat) and the lines it could not load.\n\n");
        if (login.uploadImages) {
            b.append("You can also upload specimen images (Curator Tools, Specimen Image Upload); the naming rules are in \"How to upload images\" on ")
             .append(docs).append(".\n\n");
        }
        b.append("More help: ").append(docs).append(". If you have any questions, let me know.\n\n");
        b.append("Best regards,\n").append(SIGNATURE_PLACEHOLDER).append("\nAntWeb, California Academy of Sciences\n");
        return b.toString();
    }
}

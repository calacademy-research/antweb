package org.calacademy.antweb.curate.login;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.calacademy.antweb.util.LoginNames;

/**
 * An administrator saves the login form (Login Manager, open a login): the account fields,
 * the three rights, the upload group, the projects and countries the login may edit, and
 * "Upload as". One database transaction; nothing is changed when any check fails.
 *
 * Stored data is the same as before (login, login_project, login_country); only the way the
 * form sends the assignments changed: the rows to remove and the entries to add, instead of the
 * whole list. Assignments the form does not offer any more (a project that is no longer live, a
 * country that is no longer valid) are therefore kept unless the administrator removes them.
 *
 * Pure Java plus JDBC: no AntWeb settings or caches are read here, so the database part is
 * tested against MySQL in CI (LoginEditDbTest) and the rest without a database (LoginEditTest).
 * Every value in the returned texts is plain text; the pages escape it.
 */
public final class LoginEdit {

    private LoginEdit() {}

    /** group_id of a login without an upload group (as LoginDb.createAccount() stores it). */
    public static final int NO_GROUP = -1;
    static final int MAX_USERNAME = 32;    // login.name varchar(32)
    static final int MAX_EMAIL = 128;      // login.email varchar(128)
    static final int MAX_UPLOAD_AS = 100;  // login.upload_as varchar(100)

    /** A login's settings as stored. */
    public static final class State implements Serializable {
        private static final long serialVersionUID = 1L;
        public int id;
        public String name = "";
        public String email = "";
        public String firstName = "";
        public String lastName = "";
        public boolean isAdmin;
        public boolean uploadSpecimens;
        public boolean uploadImages;
        public int groupId = NO_GROUP;
        public String uploadAs = "";
        public boolean hasPassword;
        public TreeSet<String> projects = new TreeSet<>();
        public TreeSet<String> countries = new TreeSet<>();

        public State copy() {
            State s = new State();
            s.id = id; s.name = name; s.email = email; s.firstName = firstName; s.lastName = lastName;
            s.isAdmin = isAdmin; s.uploadSpecimens = uploadSpecimens; s.uploadImages = uploadImages;
            s.groupId = groupId; s.uploadAs = uploadAs; s.hasPassword = hasPassword;
            s.projects = new TreeSet<>(projects);
            s.countries = new TreeSet<>(countries);
            return s;
        }
    }

    /** Something that can be assigned: key as stored (project name, country name), label as shown. */
    public static final class Choice implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String key;
        public final String label;
        public Choice(String key, String label) {
            this.key = key;
            this.label = label == null || label.isEmpty() ? key : label;
        }
    }

    /** An upload group for the list. */
    public static final class GroupChoice implements Serializable {
        private static final long serialVersionUID = 1L;
        public final int id;
        public final String name;
        public final String abbrev;
        public GroupChoice(int id, String name, String abbrev) {
            this.id = id;
            this.name = name == null ? "" : name;
            this.abbrev = abbrev == null ? "" : abbrev;
        }
        /** "Museum X (MX) — group 71". */
        public String label() {
            String n = name.isEmpty() ? "Upload group " + id : name;
            String a = abbrev.isEmpty() || abbrev.equals(name) ? "" : " (" + abbrev + ")";
            return n + a + " — group " + id;
        }
    }

    /** What the form sent. Null for a field means: not on the form, keep the stored value. */
    public static final class Change {
        public int id;
        public String name;
        public String email;
        public String firstName;
        public String lastName;
        public boolean isAdmin;
        public boolean uploadSpecimens;
        public boolean uploadImages;
        /** An upload group number, "none" for no upload group, or null to keep it. */
        public String group;
        public String uploadAs;
        public String[] removeProjects;
        public String[] addProjects;
        public String[] removeCountries;
        public String[] addCountries;
    }

    public static final class Result {
        public final List<String> problems = new ArrayList<>();
        public final List<String> changes = new ArrayList<>();
        public State before;
        public State after;
        public boolean saved;
    }

    // ------------------------------------------------------------------ pure parts

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * The key of the choice that was typed or picked: its label ("Madagascar (madagascarants)"),
     * its key, or its label without the key when only one choice has it. Case does not matter.
     * Null if nothing matches.
     */
    public static String resolve(String typed, Collection<Choice> options) {
        String t = norm(typed);
        if (t.isEmpty() || options == null) return null;
        for (Choice c : options) if (norm(c.label).equals(t)) return c.key;
        for (Choice c : options) if (norm(c.key).equals(t)) return c.key;
        String found = null;
        for (Choice c : options) {
            String label = c.label;
            int p = label.lastIndexOf(" (");
            String bare = p > 0 ? label.substring(0, p) : label;
            if (norm(bare).equals(t)) {
                if (found != null && !found.equals(c.key)) return null;  // two with this title
                found = c.key;
            }
        }
        return found;
    }

    /**
     * The new list: the current one, without the rows ticked "Remove", plus the entries added
     * (each must be one of the options). Problems are added for entries that match nothing.
     */
    public static TreeSet<String> newList(Set<String> current, String[] remove, String[] add,
                                          Collection<Choice> options, String what, List<String> problems) {
        TreeSet<String> out = new TreeSet<>(current);
        if (remove != null) for (String r : remove) if (r != null) out.remove(r);
        if (add != null) {
            for (String a : add) {
                if (a == null || a.trim().isEmpty()) continue;
                String key = resolve(a, options);
                if (key == null) {
                    problems.add("No " + what + " is called \"" + a.trim() + "\". Choose one from the list that appears when you type.");
                } else {
                    out.add(key);
                }
            }
        }
        return out;
    }

    private static final Pattern ID_LIST = Pattern.compile("\\d{1,9}(,\\d{1,9})*");

    /** "23, 45 ,7" becomes "23,45,7"; blank becomes "". Not a list of numbers: returned as typed (trimmed). */
    public static String normalizeUploadAs(String s) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", "");
        if (t.isEmpty()) return "";
        while (t.endsWith(",")) t = t.substring(0, t.length() - 1);
        while (t.startsWith(",")) t = t.substring(1);
        return ID_LIST.matcher(t).matches() ? t : s.trim();
    }

    /** The login numbers of an "Upload as" value (normalized); empty when it is blank or not numbers. */
    public static List<Integer> uploadAsIds(String s) {
        List<Integer> ids = new ArrayList<>();
        String n = normalizeUploadAs(s);
        if (n.isEmpty() || !ID_LIST.matcher(n).matches()) return ids;
        for (String part : n.split(",")) {
            int id = Integer.parseInt(part);
            if (!ids.contains(id)) ids.add(id);
        }
        return ids;
    }

    private static boolean differs(String a, String b) {
        return !Objects.equals(a == null ? "" : a, b == null ? "" : b);
    }

    private static String quoted(String s) {
        return s == null || s.isEmpty() ? "(empty)" : "\"" + s + "\"";
    }

    private static String groupText(int id, Map<Integer, String> groupLabels) {
        if (id == NO_GROUP) return "No upload group";
        String l = groupLabels == null ? null : groupLabels.get(id);
        return l != null ? l : "group " + id;
    }

    private static String labelOf(String key, Map<String, String> labels) {
        String l = labels == null ? null : labels.get(key);
        return l != null ? l : key;
    }

    /**
     * What changed, one line each, in the words of the form, for example
     * "Upload specimen data: added", "Project Madagascar (madagascarants): removed".
     * Empty when nothing changed.
     */
    public static List<String> describe(State before, State after, Map<Integer, String> groupLabels,
                                        Map<String, String> projectLabels) {
        List<String> out = new ArrayList<>();
        if (differs(before.name, after.name)) out.add("Username: " + quoted(before.name) + " changed to " + quoted(after.name));
        if (differs(before.email, after.email)) out.add("Email: " + quoted(before.email) + " changed to " + quoted(after.email));
        if (differs(before.firstName, after.firstName)) out.add("First name: " + quoted(before.firstName) + " changed to " + quoted(after.firstName));
        if (differs(before.lastName, after.lastName)) out.add("Last name: " + quoted(before.lastName) + " changed to " + quoted(after.lastName));
        if (before.uploadSpecimens != after.uploadSpecimens) out.add("Upload specimen data: " + (after.uploadSpecimens ? "added" : "removed"));
        if (before.uploadImages != after.uploadImages) out.add("Upload specimen images: " + (after.uploadImages ? "added" : "removed"));
        if (before.isAdmin != after.isAdmin) out.add("Administrator: " + (after.isAdmin ? "added" : "removed"));
        if (before.groupId != after.groupId) {
            out.add("Upload group: " + groupText(after.groupId, groupLabels) + " (was: " + groupText(before.groupId, groupLabels) + ")");
        }
        for (String p : after.projects) if (!before.projects.contains(p)) out.add("Project " + labelOf(p, projectLabels) + ": added");
        for (String p : before.projects) if (!after.projects.contains(p)) out.add("Project " + labelOf(p, projectLabels) + ": removed");
        for (String c : after.countries) if (!before.countries.contains(c)) out.add("Country " + c + ": added");
        for (String c : before.countries) if (!after.countries.contains(c)) out.add("Country " + c + ": removed");
        if (!normalizeUploadAs(before.uploadAs).equals(normalizeUploadAs(after.uploadAs))) {
            out.add("Upload as: " + quoted(before.uploadAs) + " changed to " + quoted(after.uploadAs));
        }
        return out;
    }

    /**
     * The checks that need no database: who may save, required and well-formed fields, your own
     * administrator rights. The new state is built in {@code after}.
     */
    public static void checkAndBuild(State before, Change in, int byLoginId, boolean byAdmin,
                              Collection<Choice> projectOptions, Collection<Choice> countryOptions,
                              State after, List<String> problems) {
        if (!byAdmin) {
            problems.add("Only administrators can change logins.");
            return;
        }
        String name = trim(in.name);
        if (name != null) {
            if (name.isEmpty()) problems.add("Enter a username.");
            else if (!name.equals(before.name)) {
                if (!LoginNames.isValidName(name)) problems.add(LoginNames.NAME_RULE);
                else if (name.length() > MAX_USERNAME) problems.add("A username may have at most " + MAX_USERNAME + " characters.");
            }
            after.name = name;
        }
        String email = trim(in.email);
        if (email != null) {
            if (!email.isEmpty() && !email.equals(before.email)) {
                if (!LoginNames.isValidEmail(email)) problems.add("\"" + email + "\" is not a valid email address.");
                else if (email.length() > MAX_EMAIL) problems.add("An email address may have at most " + MAX_EMAIL + " characters.");
            }
            after.email = email;
        }
        String first = trim(in.firstName);
        if (first != null) {
            if (!first.equals(before.firstName) && !LoginNames.isValidPersonName(first)) problems.add(LoginNames.PERSON_NAME_RULE);
            after.firstName = first;
        }
        String last = trim(in.lastName);
        if (last != null) {
            if (!last.equals(before.lastName) && !LoginNames.isValidPersonName(last)) problems.add(LoginNames.PERSON_NAME_RULE);
            after.lastName = last;
        }

        after.uploadSpecimens = in.uploadSpecimens;
        after.uploadImages = in.uploadImages;
        after.isAdmin = in.isAdmin;
        if (before.id == byLoginId && before.isAdmin && !in.isAdmin) {
            problems.add("You cannot remove your own administrator rights (you would lock yourself out). Ask another administrator.");
        }

        if (in.group != null) {
            String g = in.group.trim();
            if (g.isEmpty() || "none".equals(g)) after.groupId = NO_GROUP;
            else if (g.matches("-?\\d{1,9}")) after.groupId = Integer.parseInt(g);
            else problems.add("Choose the upload group from the list.");
        }

        if (in.uploadAs != null) {
            String ua = normalizeUploadAs(in.uploadAs);
            if (!ua.equals(normalizeUploadAs(before.uploadAs))) {
                if (!ua.isEmpty() && !ID_LIST.matcher(ua).matches()) {
                    problems.add("\"Upload as\" takes login numbers separated by commas, for example 23,45.");
                } else if (ua.length() > MAX_UPLOAD_AS) {
                    problems.add("\"Upload as\" may have at most " + MAX_UPLOAD_AS + " characters.");
                }
                after.uploadAs = ua;
            }
        }

        after.projects = newList(before.projects, in.removeProjects, in.addProjects, projectOptions, "live project", problems);
        after.countries = newList(before.countries, in.removeCountries, in.addCountries, countryOptions, "country", problems);
    }

    // ------------------------------------------------------------------ database

    /** The stored settings of a login, or null if there is no such login. */
    public static State read(Connection c, int id) throws SQLException {
        State s = null;
        String q = "select id, name, first_name, last_name, email, password, is_admin, group_id,"
            + " is_upload_specimens, is_upload_images, upload_as from login where id = ?";
        try (PreparedStatement st = c.prepareStatement(q)) {
            st.setInt(1, id);
            try (ResultSet r = st.executeQuery()) {
                if (r.next()) {
                    s = new State();
                    s.id = r.getInt("id");
                    s.name = nz(r.getString("name"));
                    s.firstName = nz(r.getString("first_name"));
                    s.lastName = nz(r.getString("last_name"));
                    s.email = nz(r.getString("email"));
                    s.hasPassword = !nz(r.getString("password")).isEmpty();
                    s.isAdmin = r.getBoolean("is_admin");
                    int g = r.getInt("group_id");
                    s.groupId = r.wasNull() ? NO_GROUP : g;
                    s.uploadSpecimens = r.getBoolean("is_upload_specimens");
                    s.uploadImages = r.getBoolean("is_upload_images");
                    s.uploadAs = nz(r.getString("upload_as"));
                }
            }
        }
        if (s == null) return null;
        try (PreparedStatement st = c.prepareStatement("select project_name from login_project where login_id = ?")) {
            st.setInt(1, id);
            try (ResultSet r = st.executeQuery()) {
                while (r.next()) if (r.getString(1) != null) s.projects.add(r.getString(1));
            }
        }
        try (PreparedStatement st = c.prepareStatement("select country from login_country where login_id = ?")) {
            st.setInt(1, id);
            try (ResultSet r = st.executeQuery()) {
                while (r.next()) if (r.getString(1) != null) s.countries.add(r.getString(1));
            }
        }
        return s;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** All upload groups, sorted by name (then number). */
    public static List<GroupChoice> groups(Connection c) throws SQLException {
        List<GroupChoice> out = new ArrayList<>();
        try (PreparedStatement st = c.prepareStatement("select id, name, abbrev from ant_group order by id");
             ResultSet r = st.executeQuery()) {
            while (r.next()) out.add(new GroupChoice(r.getInt("id"), r.getString("name"), r.getString("abbrev")));
        }
        sortGroups(out);
        return out;
    }

    public static void sortGroups(List<GroupChoice> groups) {
        groups.sort((a, b) -> {
            int n = a.name.compareToIgnoreCase(b.name);
            return n != 0 ? n : Integer.compare(a.id, b.id);
        });
    }

    public static Map<Integer, String> groupLabels(List<GroupChoice> groups) {
        Map<Integer, String> m = new HashMap<>();
        for (GroupChoice g : groups) m.put(g.id, g.label());
        return m;
    }

    private static boolean taken(Connection c, String column, String value, int id) throws SQLException {
        String q = "select count(*) from login where id <> ? and lower(" + column + ") = ?";  // column: fixed names only
        try (PreparedStatement st = c.prepareStatement(q)) {
            st.setInt(1, id);
            st.setString(2, value.toLowerCase(Locale.ROOT));
            try (ResultSet r = st.executeQuery()) {
                return r.next() && r.getInt(1) > 0;
            }
        }
    }

    private static boolean exists(Connection c, String q, int id) throws SQLException {
        try (PreparedStatement st = c.prepareStatement(q)) {
            st.setInt(1, id);
            try (ResultSet r = st.executeQuery()) {
                return r.next();
            }
        }
    }

    /**
     * Check and save an administrator's changes to an existing login. Nothing is written when a
     * problem is found (result.problems) or nothing changed (result.changes empty).
     */
    public static Result apply(Connection c, Change in, int byLoginId, boolean byAdmin,
                               Collection<Choice> projectOptions, Collection<Choice> countryOptions,
                               Map<String, String> projectLabels) throws SQLException {
        Result res = new Result();
        State before = read(c, in.id);
        if (before == null) {
            res.problems.add("Login " + in.id + " does not exist (any more).");
            return res;
        }
        res.before = before;
        State after = before.copy();
        checkAndBuild(before, in, byLoginId, byAdmin, projectOptions, countryOptions, after, res.problems);
        res.after = after;
        if (!byAdmin) return res;

        if (!after.name.equals(before.name) && !after.name.isEmpty() && taken(c, "name", after.name, before.id)) {
            res.problems.add("Another login already has the username \"" + after.name + "\".");
        }
        if (!after.email.equalsIgnoreCase(before.email) && !after.email.isEmpty() && taken(c, "email", after.email, before.id)) {
            res.problems.add("Another login already has the email address \"" + after.email + "\".");
        }
        if (after.groupId != before.groupId && after.groupId != NO_GROUP
            && !exists(c, "select id from ant_group where id = ?", after.groupId)) {
            res.problems.add("Upload group " + after.groupId + " does not exist (any more). Reload the page and choose again.");
        }
        if (!normalizeUploadAs(after.uploadAs).equals(normalizeUploadAs(before.uploadAs))) {
            for (int id : uploadAsIds(after.uploadAs)) {
                if (!exists(c, "select id from login where id = ?", id)) {
                    res.problems.add("\"Upload as\": there is no login number " + id + ".");
                }
            }
        }
        if (!res.problems.isEmpty()) return res;

        List<GroupChoice> groups = groups(c);
        res.changes.addAll(describe(before, after, groupLabels(groups), projectLabels));
        if (res.changes.isEmpty()) return res;

        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            // "created = created": login.created is ON UPDATE CURRENT_TIMESTAMP in production.
            String u = "update login set name = ?, first_name = ?, last_name = ?, email = ?, group_id = ?, is_admin = ?,"
                + " is_upload_specimens = ?, is_upload_images = ?, upload_as = ?, created = created where id = ?";
            try (PreparedStatement st = c.prepareStatement(u)) {
                st.setString(1, after.name);
                st.setString(2, after.firstName);
                st.setString(3, after.lastName);
                st.setString(4, after.email);
                st.setInt(5, after.groupId);
                st.setInt(6, after.isAdmin ? 1 : 0);
                st.setInt(7, after.uploadSpecimens ? 1 : 0);
                st.setInt(8, after.uploadImages ? 1 : 0);
                String ua = after.uploadAs;   // unchanged: as stored (blank is stored as NULL)
                if (ua == null || ua.isEmpty()) st.setNull(9, java.sql.Types.VARCHAR); else st.setString(9, ua);
                st.setInt(10, before.id);
                st.executeUpdate();
            }
            if (!after.projects.equals(before.projects)) replaceRows(c, "login_project", "project_name", before.id, after.projects);
            if (!after.countries.equals(before.countries)) replaceRows(c, "login_country", "country", before.id, after.countries);
            c.commit();
            res.saved = true;
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
        return res;
    }

    // table and column are fixed names from this class, never from a request.
    private static void replaceRows(Connection c, String table, String column, int loginId, Set<String> values) throws SQLException {
        try (PreparedStatement st = c.prepareStatement("delete from " + table + " where login_id = ?")) {
            st.setInt(1, loginId);
            st.executeUpdate();
        }
        if (values.isEmpty()) return;
        try (PreparedStatement st = c.prepareStatement("insert into " + table + " (login_id, " + column + ") values (?, ?)")) {
            for (String v : values) {
                st.setInt(1, loginId);
                st.setString(2, v);
                st.addBatch();
            }
            st.executeBatch();
        }
    }

    /** Project labels "Title (name)" by name, for the change summary. */
    public static Map<String, String> labels(Collection<Choice> choices) {
        Map<String, String> m = new LinkedHashMap<>();
        if (choices != null) for (Choice c : choices) m.put(c.key, c.label);
        return m;
    }

    /** For tests and logs: the lines joined with "; ". */
    public static String join(List<String> lines) {
        return String.join("; ", lines);
    }

}

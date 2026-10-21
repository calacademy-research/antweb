package org.calacademy.antweb.curate.login;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.calacademy.antweb.curate.login.LoginEdit.Choice;
import org.calacademy.antweb.curate.login.LoginEdit.GroupChoice;
import org.calacademy.antweb.curate.login.LoginEdit.State;
import org.calacademy.antweb.util.Html;

/**
 * The login form (viewLogin.do, newLogin.do; saved by saveLogin.do), built here so that every
 * value is escaped in one place and the page can be tested without a server (LoginEditTest).
 *
 * Administrators see five sections: Account; What this person can do; Upload group; Places and
 * projects this curator can edit; Other settings; then the password and the other actions.
 * A curator opening their own login sees their account and password, and what they may do
 * (read-only).
 *
 * The data is read by prepare() in the action and kept in the session (SESSION_ATTR) next to
 * "thisLogin", so the page can be shown again after a refused save.
 */
public final class LoginEditPage {

    private LoginEditPage() {}

    public static final String SESSION_ATTR = "loginEditPage";
    /** Request attribute: the list of changes shown after a save (List of String). */
    public static final String CHANGES_ATTR = "loginChanges";

    /** Everything the page shows. Plain values only (kept in the session). */
    public static final class PageData implements Serializable {
        private static final long serialVersionUID = 1L;
        public State state = new State();
        public boolean isNew;
        public String created = "";
        public String lastLogin = "";
        public boolean inactive;
        public String inactiveSince = "";
        public List<GroupChoice> groups = new ArrayList<>();
        /** Live projects that can be added. */
        public List<Choice> projectOptions = new ArrayList<>();
        /** Valid countries that can be added. */
        public List<Choice> countryOptions = new ArrayList<>();
        /** Labels of every project, live or not, by name (for the assigned rows). */
        public Map<String, String> projectLabels = new LinkedHashMap<>();
        /** The logins of "Upload as", by number: "michele (California Academy of Sciences)". */
        public Map<Integer, String> uploadAsNames = new LinkedHashMap<>();
    }

    // ------------------------------------------------------------------ reading

    private static String fmt(Timestamp t) {
        return t == null ? "" : new SimpleDateFormat("yyyy-MM-dd HH:mm").format(t);
    }

    /**
     * The stored values of a login and the lists for the form. The options (live projects,
     * valid countries, all project labels) come from the caller, which reads AntWeb's caches.
     */
    public static PageData read(Connection c, int id, boolean isNew, List<Choice> projectOptions,
                                List<Choice> countryOptions, Map<String, String> projectLabels) throws SQLException {
        PageData d = new PageData();
        d.isNew = isNew;
        d.projectOptions = projectOptions == null ? new ArrayList<>() : new ArrayList<>(projectOptions);
        d.countryOptions = countryOptions == null ? new ArrayList<>() : new ArrayList<>(countryOptions);
        d.projectLabels = projectLabels == null ? new LinkedHashMap<>() : new LinkedHashMap<>(projectLabels);
        d.groups = LoginEdit.groups(c);
        State s = isNew ? null : LoginEdit.read(c, id);
        if (s == null) {
            s = new State();
            s.id = id;
        }
        d.state = s;
        if (!isNew) {
            try (PreparedStatement st = c.prepareStatement("select created, last_login from login where id = ?")) {
                st.setInt(1, id);
                try (ResultSet r = st.executeQuery()) {
                    if (r.next()) {
                        d.created = fmt(r.getTimestamp("created"));
                        d.lastLogin = fmt(r.getTimestamp("last_login"));
                    }
                }
            } catch (SQLException e) {
                // an older database without last_login: leave the dates blank
            }
            try (PreparedStatement st = c.prepareStatement("select inactive, inactive_since from login where id = ?")) {
                st.setInt(1, id);
                try (ResultSet r = st.executeQuery()) {
                    if (r.next()) {
                        d.inactive = r.getInt("inactive") != 0;
                        d.inactiveSince = fmt(r.getTimestamp("inactive_since"));
                    }
                }
            } catch (SQLException e) {
                // the inactive columns are added by InactiveAccountDb.ensureSchema(); not there yet
            }
            List<Integer> ids = LoginEdit.uploadAsIds(s.uploadAs);
            for (int uid : ids) {
                String q = "select l.name, g.name from login l left join ant_group g on g.id = l.group_id where l.id = ?";
                try (PreparedStatement st = c.prepareStatement(q)) {
                    st.setInt(1, uid);
                    try (ResultSet r = st.executeQuery()) {
                        if (r.next()) {
                            String g = r.getString(2);
                            d.uploadAsNames.put(uid, r.getString(1) + (g == null ? ", no upload group" : " (" + g + ")"));
                        } else {
                            d.uploadAsNames.put(uid, "no such login");
                        }
                    }
                }
            }
        }
        return d;
    }

    // ------------------------------------------------------------------ helpers

    private static String e(Object o) {
        return Html.esc(o);
    }

    private static String checked(boolean b) {
        return b ? " checked" : "";
    }

    private static String or(String s, String dflt) {
        return s == null || s.isEmpty() ? dflt : s;
    }

    static String displayName(State s) {
        String full = (or(s.firstName, "") + " " + or(s.lastName, "")).trim();
        if (!full.isEmpty()) return full + " (" + s.name + ")";
        return or(s.name, or(s.email, "login " + s.id));
    }

    private static String projectLabel(PageData d, String key) {
        String l = d.projectLabels.get(key);
        if (l == null) return key + " (not a project on AntWeb now)";
        for (Choice c : d.projectOptions) if (c.key.equals(key)) return l;
        return l + " (not live)";
    }

    private static String countryLabel(PageData d, String key) {
        for (Choice c : d.countryOptions) if (c.key.equals(key)) return c.label;
        return key + " (not a valid country on AntWeb now)";
    }

    private static GroupChoice group(PageData d, int id) {
        for (GroupChoice g : d.groups) if (g.id == id) return g;
        return null;
    }

    static final String STYLE =
        "<style>\n"
        + ".loginEdit h2 { clear: both; margin: 1.4em 0 .2em; border-bottom: 1px solid #ccc; }\n"
        + ".loginEdit select { max-width: 100%; }\n"
        + ".loginEdit .hint { color: #555; margin: 0 0 .6em; }\n"
        + ".loginEdit table.fields td { padding: 3px 8px 3px 0; vertical-align: top; }\n"
        + ".loginEdit table.fields td:first-child { font-weight: bold; white-space: nowrap; }\n"
        + ".loginEdit input[type=text], .loginEdit input[type=password] { width: 22em; max-width: 100%; }\n"
        // Not "right": the site's .right floats (base.css, antweb_style.css) and pushed the next section aside.
        + ".loginEdit .perm { display: block; clear: both; margin: .5em 0; }\n"
        + ".loginEdit .perm .what { margin: 0 0 0 1.8em; color: #555; }\n"
        + ".loginEdit .danger { color: #b00000; font-weight: bold; }\n"
        + ".loginEdit .adminWarning { border: 2px solid #b00000; color: #b00000; padding: 6px; margin: .4em 0 .4em 1.8em; }\n"
        + ".loginEdit table.assigned td { padding: 2px 10px 2px 0; }\n"
        + ".loginEdit .pending { color: #060; }\n"
        + ".loginEdit .buttons { margin: 1.2em 0; }\n"
        + ".loginEdit .buttons button, .loginEdit .actions button { font-size: 1em; padding: 4px 12px; }\n"
        + ".loginEdit .actions form { margin: .6em 0; }\n"
        // Phone width: the page column (.admin_left, 560px, floated) takes the whole width.
        + "@media (max-width: 760px) {\n"
        + "  .admin_left.loginEditPage { float: none; width: auto; padding: 0 8px; }\n"
        + "  .loginEdit table.fields td { display: block; padding: 2px 0; }\n"
        + "  .loginEdit input[type=text], .loginEdit input[type=password] { width: 100%; box-sizing: border-box; }\n"
        + "}\n"
        + "</style>\n";

    // ------------------------------------------------------------------ administrator's page

    /** The page for an administrator (any login, also their own, or a new one). */
    /** Shown under "Places and projects" when a login has none assigned. */
    static final String NONE_ASSIGNED = "None assigned yet.";
    static final String NOT_NEEDED_FOR_ADMIN = "Not needed: administrators can edit every project and country.";

    public static String renderAdmin(PageData d, int viewerId, String csrfField, String app, String secureApp) {
        State s = d.state;
        boolean isSelf = !d.isNew && s.id == viewerId;
        StringBuilder b = new StringBuilder(STYLE);
        b.append("<div class=\"loginEdit\">\n");
        if (d.isNew) {
            b.append("<h1>New login</h1>\n<p class=\"hint\">For someone who wants to upload specimen data, use <a href=\"")
             .append(e(app)).append("/addUploader.do\">Add an uploader</a> instead: it also sets up the upload group and the welcome email.</p>\n");
        } else {
            b.append("<h1>Login: ").append(e(displayName(s))).append("</h1>\n");
            b.append("<p class=\"hint\">Change what you need and click <b>Save changes</b> at the bottom. The next page lists what changed. ")
             .append("The person sees new rights the next time they log in.</p>\n");
        }

        b.append("<form id=\"loginEditForm\" action=\"").append(e(secureApp)).append("/saveLogin.do\" method=\"POST\">")
         .append(csrfField == null ? "" : csrfField).append("\n");
        b.append("<input type=\"hidden\" name=\"id\" value=\"").append(s.id).append("\">\n");
        b.append("<input type=\"hidden\" name=\"editForm\" value=\"2\">\n");
        if (d.isNew) b.append("<input type=\"hidden\" name=\"newLogin\" value=\"true\">\n");

        // ---- Account
        b.append("<h2>Account</h2>\n<p class=\"hint\">Who this is and how they log in. The email address receives password links and upload emails.</p>\n");
        b.append("<table class=\"fields\">\n");
        field(b, "Username", "name", "text", s.name, "username");
        field(b, "Email", "email", "text", s.email, "email");
        field(b, "First name", "firstName", "text", s.firstName, "given-name");
        field(b, "Last name", "lastName", "text", s.lastName, "family-name");
        if (!d.isNew) {
            b.append("<tr><td>Login number</td><td>").append(s.id).append("</td></tr>\n");
            b.append("<tr><td>Created</td><td>").append(e(or(d.created, "unknown")))
             .append(" <span class=\"hint\">(dates of 14 to 16 October 2026 are not the real creation date: they were overwritten then)</span></td></tr>\n");
            b.append("<tr><td>Last login</td><td>").append(e(or(d.lastLogin, "never (or before AntWeb recorded it)"))).append("</td></tr>\n");
            b.append("<tr><td>Status</td><td>");
            if (d.inactive) {
                b.append("<span class=\"danger\">Inactive</span>").append(d.inactiveSince.isEmpty() ? "" : " since " + e(d.inactiveSince))
                 .append(". They cannot log in with their password until they use the reactivation link that \"Forgot your password?\" emails them.");
            } else {
                b.append("Active");
            }
            b.append("</td></tr>\n");
            b.append("<tr><td>Password</td><td>").append(s.hasPassword ? "set" : "not set yet: they need a set-password link (below)").append("</td></tr>\n");
        }
        b.append("</table>\n");

        // ---- Rights
        b.append("<h2>What this person can do</h2>\n<p class=\"hint\">Tick what this login may do on Curator Tools.</p>\n");
        right(b, "isUploadSpecimens", s.uploadSpecimens, "Upload specimen data",
            "Upload specimen data for their upload group. Each upload replaces all specimens of that upload group.");
        right(b, "isUploadImages", s.uploadImages, "Upload specimen images",
            "Upload specimen images with the Image Uploader.");
        if (isSelf) {
            b.append("<div class=\"perm\"><b>Administrator: yes</b><input type=\"hidden\" name=\"isAdmin\" value=\"on\">")
             .append("<div class=\"what\">You cannot remove your own administrator rights here, so that you do not lock yourself out. Ask another administrator.</div></div>\n");
        } else {
            b.append("<div class=\"perm\"><label class=\"danger\"><input type=\"checkbox\" name=\"isAdmin\" id=\"isAdmin\"").append(checked(s.isAdmin))
             .append("> Administrator</label>\n<div class=\"what\">Full access to all tools, all logins and all data. Give only to AntWeb staff.</div>\n")
             .append("<div class=\"adminWarning\" id=\"adminWarning\"").append(s.isAdmin ? "" : " hidden")
             .append(">Warning: an administrator can change or delete any data, upload for any group, and change every login, including this one. ")
             .append("Only for AntWeb staff.</div></div>\n");
        }
        if (d.isNew) {
            b.append("<p class=\"hint\">A new login without a password gets a one-time link to choose one; administrator rights are added only after that.</p>\n");
        }

        // ---- Upload group
        b.append("<h2>Upload group</h2>\n<p class=\"hint\">The institution, lab or collection whose specimen records this login uploads. ")
         .append("<b>An upload replaces all specimens of that upload group.</b> To set up a new uploader in one step, use <a href=\"")
         .append(e(app)).append("/addUploader.do\">Add an uploader</a>; to add or rename a group, <a href=\"").append(e(app))
         .append("/viewGroups.do\">Upload groups</a>.</p>\n");
        b.append("<select name=\"groups\" id=\"groupSelect\" aria-label=\"Upload group\">\n");
        b.append("<option value=\"none\"").append(s.groupId == LoginEdit.NO_GROUP ? " selected" : "").append(">No upload group</option>\n");
        boolean listed = s.groupId == LoginEdit.NO_GROUP;
        for (GroupChoice g : d.groups) {
            boolean sel = g.id == s.groupId;
            listed |= sel;
            b.append("<option value=\"").append(g.id).append("\"").append(sel ? " selected" : "").append(">").append(e(g.label())).append("</option>\n");
        }
        if (!listed) {
            b.append("<option value=\"").append(s.groupId).append("\" selected>Group ").append(s.groupId).append(" (not in the list of upload groups)</option>\n");
        }
        b.append("</select>\n");
        if (!d.isNew && group(d, s.groupId) != null) {
            b.append(" <a href=\"").append(e(app)).append("/viewGroup.do?id=").append(s.groupId).append("\">Open this upload group</a>\n");
        }

        // ---- Places and projects
        b.append("<h2>Places and projects this curator can edit</h2>\n<p class=\"hint\">This login may change the species lists ")
         .append("(Species List Tool) and the page text of these projects and countries. A country includes its states and provinces. ")
         .append("Administrators can edit all of them, so they need none here.</p>\n");
        assigned(b, "Projects", "removeProjects", "project", s.projects, d, true, s.isAdmin);
        adder(b, "addProjects", "projectOptions", "projects", "Add a project", d.projectOptions);
        assigned(b, "Countries", "removeCountries", "country", s.countries, d, false, s.isAdmin);
        adder(b, "addCountries", "countryOptions", "countries", "Add a country", d.countryOptions);

        // ---- Other settings
        b.append("<h2>Other settings</h2>\n<p class=\"hint\">Rarely needed.</p>\n<table class=\"fields\">\n");
        b.append("<tr><td><label for=\"uploadAs\">Upload as</label></td><td><input type=\"text\" id=\"uploadAs\" name=\"uploadAs\" value=\"")
         .append(e(s.uploadAs)).append("\" placeholder=\"for example 23,45\">\n<div class=\"hint\">Login numbers, separated by commas. ")
         .append("Used only when this login is an administrator: these logins are listed in its \"Upload as\" menu on Curator Tools, ")
         .append("to upload specimen data into their upload group. Administrators may upload for anyone; this only fills the menu.</div>");
        if (!d.uploadAsNames.isEmpty()) {
            b.append("<div>Now: ");
            boolean first = true;
            for (Map.Entry<Integer, String> en : d.uploadAsNames.entrySet()) {
                if (!first) b.append("; ");
                first = false;
                b.append(en.getKey()).append(" = ").append(e(en.getValue()));
            }
            b.append("</div>");
        }
        b.append("</td></tr>\n</table>\n");

        // ---- Password (own login: saved with the other changes)
        if (isSelf || d.isNew) {
            b.append("<h2>Password</h2>\n<p class=\"hint\">")
             .append(d.isNew ? "Leave blank to make a one-time link instead (shown after saving)." : "Leave blank to keep your password.")
             .append("</p>\n<table class=\"fields\">\n");
            if (isSelf) field(b, "Current password", "currentPassword", "password", "", "current-password");
            field(b, "New password", "password", "password", "", "new-password");
            field(b, "Retype new password", "retypePassword", "password", "", "new-password");
            b.append("</table>\n");
        }

        b.append("<div class=\"buttons\"><button type=\"submit\">").append(d.isNew ? "Create login" : "Save changes").append("</button>");
        if (!d.isNew && !isSelf && !s.hasPassword) {
            b.append(" <button type=\"submit\" name=\"step\" value=\"invite\">Save and make a set-password link</button>");
        }
        b.append(" <a href=\"").append(e(app)).append("/viewLogins.do\">Cancel</a></div>\n");
        b.append("</form>\n");

        // ---- Other actions (separate forms, so they never save the fields above)
        if (!d.isNew && !isSelf) {
            b.append("<h2>Other actions</h2>\n<div class=\"actions\">\n");
            b.append("<form action=\"").append(e(secureApp)).append("/saveLogin.do\" method=\"POST\">").append(csrfField == null ? "" : csrfField)
             .append("<input type=\"hidden\" name=\"id\" value=\"").append(s.id).append("\">")
             .append("<b>Set a new password</b> <span class=\"hint\">(they are logged out everywhere; usually better: ask them to use \"Forgot your password?\" on the login page)</span><br>")
             .append("<input type=\"password\" name=\"password\" autocomplete=\"new-password\" aria-label=\"New password\" placeholder=\"new password\"> ")
             .append("<input type=\"password\" name=\"retypePassword\" autocomplete=\"new-password\" aria-label=\"Retype new password\" placeholder=\"retype\"> ")
             .append("<button type=\"submit\" name=\"changePassword\" value=\"Change Password\">Set this password</button></form>\n");
            b.append("<p><a href=\"").append(e(secureApp)).append("/loginAs.do?id=").append(s.id).append("\">Log in as ")
             .append(e(or(s.name, "this login"))).append("</a> <span class=\"hint\">(see AntWeb as they do; log out to return)</span></p>\n");
            b.append("<form action=\"").append(e(secureApp)).append("/saveLogin.do\" method=\"POST\" onsubmit=\"return confirm('Delete the login ")
             .append(e(Html.js(or(s.name, "" + s.id)))).append(" for good? Their uploads and specimens stay.');\">").append(csrfField == null ? "" : csrfField)
             .append("<input type=\"hidden\" name=\"id\" value=\"").append(s.id).append("\">")
             .append("<button type=\"submit\" name=\"delete\" value=\"delete\">Delete this login</button></form>\n");
            b.append("</div>\n");
        }
        b.append(SCRIPT);
        b.append("</div>\n");
        return b.toString();
    }

    private static void field(StringBuilder b, String label, String name, String type, String value, String autocomplete) {
        b.append("<tr><td><label for=\"f_").append(name).append("\">").append(label).append("</label></td><td><input type=\"").append(type)
         .append("\" id=\"f_").append(name).append("\" name=\"").append(name).append("\" value=\"").append(e(value))
         .append("\" autocomplete=\"").append(autocomplete).append("\"></td></tr>\n");
    }

    private static void right(StringBuilder b, String name, boolean on, String label, String what) {
        b.append("<div class=\"perm\"><label><input type=\"checkbox\" name=\"").append(name).append("\"").append(checked(on)).append("> <b>")
         .append(label).append("</b></label>\n<div class=\"what\">").append(what).append("</div></div>\n");
    }

    private static void assigned(StringBuilder b, String title, String removeName, String what, java.util.Set<String> keys,
                                 PageData d, boolean isProject, boolean isAdmin) {
        b.append("<h3>").append(title).append("</h3>\n");
        if (keys.isEmpty()) {
            b.append("<p>").append(isAdmin ? NOT_NEEDED_FOR_ADMIN : NONE_ASSIGNED).append("</p>\n");
            return;
        }
        b.append("<table class=\"assigned\">\n");
        for (String k : keys) {
            String label = isProject ? projectLabel(d, k) : countryLabel(d, k);
            b.append("<tr><td>").append(e(label)).append("</td><td><label><input type=\"checkbox\" name=\"").append(removeName)
             .append("\" value=\"").append(e(k)).append("\"> Remove</label></td></tr>\n");
        }
        b.append("</table>\n");
    }

    private static void adder(StringBuilder b, String name, String listId, String what, String label, List<Choice> options) {
        b.append("<div class=\"adder\" data-name=\"").append(name).append("\"><label>").append(label)
         .append(": <input type=\"text\" name=\"").append(name).append("\" list=\"").append(listId)
         .append("\" placeholder=\"type to search\" autocomplete=\"off\"></label> <button type=\"button\" class=\"addButton\">Add</button>")
         .append(" <span class=\"hint\">(").append(options.size()).append(" ").append(what).append(")</span>\n")
         .append("<ul class=\"pending\"></ul>\n<datalist id=\"").append(listId).append("\">\n");
        for (Choice c : options) b.append("<option value=\"").append(e(c.label)).append("\"></option>\n");
        b.append("</datalist></div>\n");
    }

    // Without JavaScript the boxes still work (one entry each per save). With it, "Add" (or Enter)
    // moves the entry to a list below the box, so several can be added before saving, and the
    // administrator warning shows while the box is ticked.
    static final String SCRIPT =
        "<script>\n(function () {\n"
        + "  var form = document.getElementById('loginEditForm'); if (!form) return;\n"
        + "  var admin = document.getElementById('isAdmin'), warn = document.getElementById('adminWarning');\n"
        + "  if (admin && warn) admin.addEventListener('change', function () { warn.hidden = !admin.checked; });\n"
        + "  form.querySelectorAll('.adder').forEach(function (box) {\n"
        + "    var input = box.querySelector('input[type=text]'), list = box.querySelector('ul'), name = box.getAttribute('data-name');\n"
        + "    var options = Array.prototype.map.call(document.getElementById(input.getAttribute('list')).options, function (o) { return o.value; });\n"
        + "    function add() {\n"
        + "      var v = input.value.trim(); if (!v) return;\n"
        + "      var match = options.filter(function (o) { return o.toLowerCase() === v.toLowerCase(); })[0];\n"
        + "      if (!match) { var some = options.filter(function (o) { return o.toLowerCase().indexOf(v.toLowerCase()) >= 0; });\n"
        + "        if (some.length !== 1) { input.setCustomValidity(some.length ? 'Choose one from the list' : 'Not in the list'); input.reportValidity(); return; }\n"
        + "        match = some[0]; }\n"
        + "      input.setCustomValidity('');\n"
        + "      var li = document.createElement('li'); li.textContent = match + ' (added when you save) ';\n"
        + "      var h = document.createElement('input'); h.type = 'hidden'; h.name = name; h.value = match; li.appendChild(h);\n"
        + "      var x = document.createElement('button'); x.type = 'button'; x.textContent = 'undo'; x.onclick = function () { li.remove(); }; li.appendChild(x);\n"
        + "      list.appendChild(li); input.value = '';\n"
        + "    }\n"
        + "    box.querySelector('.addButton').addEventListener('click', add);\n"
        + "    input.addEventListener('input', function () { input.setCustomValidity(''); });\n"
        + "    input.addEventListener('keydown', function (ev) { if (ev.key === 'Enter') { ev.preventDefault(); add(); } });\n"
        + "  });\n"
        + "})();\n</script>\n";

    // ------------------------------------------------------------------ a curator's own page

    /** A curator (not an administrator) opening their own login: account and password; rights read-only. */
    public static String renderSelf(PageData d, String csrfField, String app, String secureApp) {
        State s = d.state;
        StringBuilder b = new StringBuilder(STYLE);
        b.append("<div class=\"loginEdit\">\n<h1>Your login</h1>\n");
        b.append("<form action=\"").append(e(secureApp)).append("/saveLogin.do\" method=\"POST\">").append(csrfField == null ? "" : csrfField).append("\n");
        b.append("<input type=\"hidden\" name=\"id\" value=\"").append(s.id).append("\">\n");
        b.append("<h2>Account</h2>\n<p class=\"hint\">Your email address receives password links and upload emails.</p>\n<table class=\"fields\">\n");
        field(b, "Username", "name", "text", s.name, "username");
        field(b, "Email", "email", "text", s.email, "email");
        field(b, "First name", "firstName", "text", s.firstName, "given-name");
        field(b, "Last name", "lastName", "text", s.lastName, "family-name");
        b.append("</table>\n<h2>Password</h2>\n<p class=\"hint\">Leave blank to keep your password.</p>\n<table class=\"fields\">\n");
        field(b, "Current password", "currentPassword", "password", "", "current-password");
        field(b, "New password", "password", "password", "", "new-password");
        field(b, "Retype new password", "retypePassword", "password", "", "new-password");
        b.append("</table>\n<div class=\"buttons\"><button type=\"submit\">Save changes</button> <a href=\"").append(e(app))
         .append("/curate.do\">Cancel</a></div>\n</form>\n");

        b.append("<h2>What you can do</h2>\n<p class=\"hint\">To change any of this, write to antweb@antweb.org.</p>\n<ul>\n");
        b.append("<li>Upload specimen data: ").append(s.uploadSpecimens ? "yes" : "no").append("</li>\n");
        b.append("<li>Upload specimen images: ").append(s.uploadImages ? "yes" : "no").append("</li>\n");
        GroupChoice g = group(d, s.groupId);
        b.append("<li>Upload group: ").append(g != null ? e(g.label()) : s.groupId == LoginEdit.NO_GROUP ? "none" : "group " + s.groupId)
         .append(g != null && s.uploadSpecimens ? " (each upload replaces all specimens of this upload group)" : "").append("</li>\n");
        b.append("</ul>\n<h3>Places and projects you can edit</h3>\n");
        if (s.projects.isEmpty() && s.countries.isEmpty()) {
            b.append("<p>").append(s.isAdmin ? NOT_NEEDED_FOR_ADMIN : NONE_ASSIGNED).append("</p>\n");
        } else {
            b.append("<ul>\n");
            for (String p : s.projects) b.append("<li>Project ").append(e(projectLabel(d, p))).append("</li>\n");
            for (String c : s.countries) b.append("<li>Country ").append(e(countryLabel(d, c))).append("</li>\n");
            b.append("</ul>\n");
        }
        b.append("</div>\n");
        return b.toString();
    }

    /** The list of changes after a save, escaped. */
    public static String renderChanges(List<String> changes) {
        if (changes == null) return "";
        if (changes.isEmpty()) return "<p>Nothing was changed: the form had the values already stored.</p>\n";
        StringBuilder b = new StringBuilder("<h2>What changed</h2>\n<ul class=\"loginChanges\">\n");
        for (String c : changes) b.append("<li>").append(e(c)).append("</li>\n");
        return b.append("</ul>\n").toString();
    }
}

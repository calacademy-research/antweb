package org.calacademy.antweb.curate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.calacademy.antweb.Group;
import org.calacademy.antweb.Login;
import org.calacademy.antweb.util.Html;

/**
 * The "Upload as" choices of the specimen upload form on the Curate page (administrators; curate-body.jsp).
 *
 * An upload replaces all specimens of the GROUP of the login it is made as. The list used to show only
 * login names ("antweb", "MEsposito@calacademy.org", "aerochild"), which read as if it chose the group
 * (Brian Fisher, 2026-10-20). Each choice now names the group the upload goes into,
 * "aerochild — Curator Peter Hawkes AFRC (group 31)", sorted by group name, with a warning next to the
 * list. The submitted value is still the login id (uploadAs), so the upload itself is unchanged.
 */
public final class UploadAsOptions {

    public static final String WARNING = "The upload replaces all specimens of the group shown.";

    private UploadAsOptions() {}

    /** One choice: the login (its id is the submitted value) and the group an upload as that login goes into. */
    public static final class Option {
        public final int loginId;
        public final String loginName;
        public final String groupName;   // null when the login has no group
        public final int groupId;
        public Option(int loginId, String loginName, String groupName, int groupId) {
            this.loginId = loginId;
            this.loginName = loginName == null ? "" : loginName;
            this.groupName = groupName;
            this.groupId = groupId;
        }
    }

    /** The choice for a login, with its group (GroupMgr). */
    public static Option of(Login login) {
        Group group = null;
        try {
            group = login.getGroup();
        } catch (RuntimeException e) {
            // groups not loaded
        }
        String groupName = group != null && group.getName() != null && !group.getName().isBlank() ? group.getName() : null;
        return new Option(login.getId(), login.getName(), groupName, login.getGroupId());
    }

    /** "aerochild — Curator Peter Hawkes AFRC (group 31)"; without a group: "aerochild — no group". */
    public static String label(Option o) {
        if (o.groupName == null) return o.loginName + (o.groupId > 0 ? " \u2014 group " + o.groupId : " \u2014 no group");
        return o.loginName + " \u2014 " + o.groupName + " (group " + o.groupId + ")";
    }

    /** Sorted by group name (case ignored), then login name; logins without a group last. */
    public static List<Option> sorted(List<Option> options) {
        List<Option> list = new ArrayList<>(options);
        list.sort(Comparator.comparing((Option o) -> o.groupName == null)
            .thenComparing(o -> o.groupName == null ? "" : o.groupName.toLowerCase(Locale.ROOT))
            .thenComparing(o -> o.loginName.toLowerCase(Locale.ROOT)));
        return list;
    }

    /** Escaped, the dash as a character reference (whatever the page's encoding). */
    static String html(String text) {
        return Html.esc(text).replace("\u2014", "&#8212;");
    }

    /** The option elements, each login once, selectedLoginId selected. */
    public static String optionsHtml(List<Option> options, int selectedLoginId) {
        StringBuilder b = new StringBuilder();
        List<Integer> seen = new ArrayList<>();
        for (Option o : sorted(options)) {
            if (seen.contains(o.loginId)) continue;
            seen.add(o.loginId);
            b.append("<option value=\"").append(o.loginId).append("\"").append(o.loginId == selectedLoginId ? " selected" : "")
             .append(">").append(html(label(o))).append("</option>\n");
        }
        return b.toString();
    }
}

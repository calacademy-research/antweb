package org.calacademy.antweb.util;

import java.util.*;

import org.calacademy.antweb.home.SpecimenDb.PartGroup;
import org.calacademy.antweb.home.SpecimenDb.PartRow;

/**
 * The "Specimen parts (-D codes) with different names" report (list.do?action=casentDAnomalies), split by the
 * group that uploaded each record. A specimen whose base record and parts were uploaded by different groups is
 * listed under every group involved; rows of other groups are marked, since a curator can only fix their own.
 */
public final class PartsReport {

    private PartsReport() {}

    public static final String NO_GROUP = "No group";

    /** One group's list: the specimens with at least one record uploaded by that group. */
    public static final class GroupList {
        public final int id;
        public final String name;
        public final List<PartGroup> specimens = new ArrayList<>();
        GroupList(int id, String name) { this.id = id; this.name = name; }
    }

    static String groupName(PartRow r) {
        if (r.groupName != null && !r.groupName.isBlank()) return r.groupName;
        return r.groupId > 0 ? "Group " + r.groupId : NO_GROUP;
    }

    /** Every group with at least one hit, sorted by name ("No group" last); each specimen under every group involved, by base code. */
    public static List<GroupList> byGroup(List<PartGroup> specimens) {
        Map<Integer, GroupList> lists = new HashMap<>();
        for (PartGroup g : specimens) {
            for (PartRow r : g.parts) {
                GroupList l = lists.computeIfAbsent(r.groupId, id -> new GroupList(id, groupName(r)));
                if (l.specimens.isEmpty() || l.specimens.get(l.specimens.size() - 1) != g) l.specimens.add(g);
            }
        }
        List<GroupList> out = new ArrayList<>(lists.values());
        out.sort(Comparator.comparing((GroupList l) -> l.id <= 0)
            .thenComparing(l -> l.name, String.CASE_INSENSITIVE_ORDER).thenComparingInt(l -> l.id));
        for (GroupList l : out) l.specimens.sort(Comparator.comparing((PartGroup g) -> g.base, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /** The lists to show: all groups, or only the chosen one (empty when that group has no hit). */
    public static List<GroupList> select(List<GroupList> all, Integer groupId) {
        if (groupId == null) return all;
        for (GroupList l : all) if (l.id == groupId) return List.of(l);
        return List.of();
    }

    /** groupId request parameter: null when missing or not a number. */
    public static Integer parseGroupId(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Integer.valueOf(value.trim()); } catch (NumberFormatException e) { return null; }
    }

    static String specimenUrl(String domainApp, String code) {
        return domainApp + "/specimen.do?code=" + Html.url(code);
    }

    static String nz(String s) { return s == null ? "" : s; }

    /**
     * Most specimens listed on the page (2026-10-22): the page is a message page, and a list of every
     * specimen of every group could run to megabytes. The CSV download and the group filter give the rest.
     */
    public static final int MAX_SHOWN = 1000;

    /** The HTML page: explanation, group filter, download link, then one table per group. */
    public static String html(List<PartGroup> specimens, Integer groupId, String domainApp) {
        return html(specimens, groupId, domainApp, MAX_SHOWN);
    }

    public static String html(List<PartGroup> specimens, Integer groupId, String domainApp, int maxShown) {
        List<GroupList> all = byGroup(specimens);
        List<GroupList> shown = select(all, groupId);
        String base = domainApp + "/list.do?action=casentDAnomalies";

        StringBuilder b = new StringBuilder("</b><h1>Specimen parts (-D codes) with different names</h1>");
        b.append(ReportHelp.PARTS);
        b.append("<p><b>").append(specimens.size()).append(specimens.size() == 1 ? " specimen" : " specimens")
         .append("</b> with parts under different names.</p>");
        if (specimens.isEmpty()) {
            b.append("<p>None: every specimen's parts carry the same name.</p>");
            return b.toString();
        }

        b.append("<form method=\"get\" action=\"").append(Html.escAttr(domainApp + "/list.do")).append("\" style=\"font-weight:normal\">")
         .append("<input type=\"hidden\" name=\"action\" value=\"casentDAnomalies\">")
         .append("<label for=\"groupId\"><b>Group:</b></label> <select id=\"groupId\" name=\"groupId\" onchange=\"this.form.submit()\">")
         .append("<option value=\"\"").append(groupId == null ? " selected" : "").append(">All groups (")
         .append(specimens.size()).append(")</option>");
        for (GroupList l : all) {
            b.append("<option value=\"").append(l.id).append("\"").append(groupId != null && groupId == l.id ? " selected" : "")
             .append(">").append(Html.esc(l.name)).append(" (").append(l.specimens.size()).append(")</option>");
        }
        b.append("</select> <input type=\"submit\" value=\"Show\"></form>");

        String csv = base + (groupId == null ? "" : "&groupId=" + groupId) + "&format=csv";
        b.append("<p style=\"font-weight:normal\"><a class=\"parts-csv\" href=\"").append(Html.escAttr(csv)).append("\">Download this list (CSV)</a>");
        if (groupId != null) b.append(" &nbsp; <a href=\"").append(Html.escAttr(base)).append("\">Show all groups</a>");
        b.append("</p>");

        if (shown.isEmpty()) {
            b.append("<p>No specimens for this group.</p>");
            return b.toString();
        }
        if (groupId == null) {
            b.append("<p style=\"font-weight:normal\">A specimen whose records were uploaded by more than one group is listed under each of them.</p>");
        }
        int total = 0;
        for (GroupList l : shown) total += l.specimens.size();
        if (total > maxShown) {
            b.append("<p class=\"parts-cut\"><b>Showing the first ").append(String.format("%,d", maxShown)).append(" of ")
             .append(String.format("%,d", total)).append(" listed specimens.</b> Download the CSV or choose a group for the rest.</p>");
        }
        int listed = 0;
        for (GroupList l : shown) {
            if (listed >= maxShown) break;
            b.append("<h2>").append(Html.esc(l.name)).append(" (").append(l.specimens.size())
             .append(l.specimens.size() == 1 ? " specimen" : " specimens").append(")</h2>");
            b.append("<table class=\"parts-report\" cellpadding=6 border=1 style=\"border-collapse:collapse;font-weight:normal\"><tr><th>#</th><th>Specimen</th><th>Part</th>")
             .append("<th>Taxon name</th><th>Uploaded name</th><th>Determined by</th><th>Date determined</th><th>Uploaded by</th><th>Museum</th></tr>");
            int i = 0;
            for (PartGroup g : l.specimens) {
                if (listed++ >= maxShown) break;
                ++i;
                boolean first = true;
                for (PartRow r : g.parts) {
                    boolean other = r.groupId != l.id;
                    b.append(other ? "<tr class=\"other-group\" style=\"background:#eee;color:#555\">" : "<tr>");
                    if (first) {
                        int rows = g.parts.size();
                        b.append("<td rowspan=").append(rows).append(">").append(i).append(".</td>")
                         .append("<td rowspan=").append(rows).append("><a href=\"").append(Html.escAttr(specimenUrl(domainApp, g.base)))
                         .append("\">").append(Html.esc(g.base)).append("</a></td>");
                        first = false;
                    }
                    b.append("<td><a href=\"").append(Html.escAttr(specimenUrl(domainApp, r.code))).append("\">")
                     .append(Html.esc(r.code)).append("</a></td>")
                     .append("<td>").append(Html.esc(nz(r.taxonName))).append("</td>")
                     .append("<td>").append(Html.esc(nz(r.uploadedName()))).append("</td>")
                     .append("<td>").append(Html.esc(nz(r.determinedBy))).append("</td>")
                     .append("<td>").append(Html.esc(nz(r.dateDetermined))).append("</td>")
                     .append("<td>").append(Html.esc(groupName(r)));
                    if (other) b.append(" <i>(other group: only its curator can fix this row)</i>");
                    b.append("</td><td>").append(Html.esc(nz(r.museum))).append("</td></tr>");
                }
            }
            b.append("</table>");
        }
        return b.toString();
    }

    public static final String CSV_HEADER = "group,specimen code,part code,taxon name,determined by,date determined,"
        + "uploaded name,uploaded by,this group can fix,museum,specimen page";

    /**
     * The CSV for the current filter: one line per record, sorted by group then base code. "group" is the list
     * the specimen is in; "uploaded by" is the group of that record ("this group can fix" says whether they match).
     */
    public static String csv(List<PartGroup> specimens, Integer groupId, String domainApp) {
        StringBuilder b = new StringBuilder(CSV_HEADER).append("\r\n");
        for (GroupList l : select(byGroup(specimens), groupId)) {
            for (PartGroup g : l.specimens) {
                for (PartRow r : g.parts) {
                    String[] f = {l.name, g.base, r.code, r.taxonName, r.determinedBy, r.dateDetermined, r.uploadedName(),
                        groupName(r), r.groupId == l.id ? "yes" : "no", r.museum, specimenUrl(domainApp, r.code)};
                    for (int k = 0; k < f.length; k++) {
                        if (k > 0) b.append(',');
                        b.append(csvField(f[k]));
                    }
                    b.append("\r\n");
                }
            }
        }
        return b.toString();
    }

    /** One CSV field: quoted when needed; a leading = + - @ is prefixed with ' so spreadsheets do not run it as a formula. */
    public static String csvField(String v) {
        if (v == null) return "";
        String s = v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /** File name for the download: parts-different-names[-group-N].csv */
    public static String csvFileName(Integer groupId) {
        return "parts-different-names" + (groupId == null ? "" : "-group-" + groupId) + ".csv";
    }
}

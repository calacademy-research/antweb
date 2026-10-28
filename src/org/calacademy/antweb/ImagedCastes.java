package org.calacademy.antweb;

import java.sql.*;
import java.util.*;

import org.calacademy.antweb.home.StatisticsDb;
import org.calacademy.antweb.util.DBUtil;
import org.calacademy.antweb.util.Html;

/**
 * Which castes of a species are imaged (Brian Fisher, 2026-10-27), for the species page (description.do of a species
 * or subspecies): "Imaged: worker (12 specimens: 9 worker, 3 major/soldier), queen (2), male (1)". A specimen is imaged when it has an image of the
 * ant; a specimen with only label images is not (the rule of StatisticsDb.ANT_IMAGE, Q12). Specimens of the name from
 * anywhere in AntWeb (images help identification wherever the specimen came from). One grouped query on the name's
 * specimens (index specimen_taxon_name) joined to their images (image_of_id), as StatisticsDb.ANT_IMAGED_NAMES_SQL.
 *
 * docs/numbers.md row 120; tests: ImagedCastesTest (junit, no database), tests/e2e/test_images_anywhere.py.
 */
public final class ImagedCastes {

    /** Castes in the order shown, as stored in specimen.caste; then specimens with no caste. */
    public static final String[] CASTES = {Caste.WORKER, Caste.QUEEN, Caste.MALE, Caste.OTHER};
    public static final String UNKNOWN = "unknown";

    /** Subcaste filter of images.do (Caste.getSpecimenClause) for a caste and subcaste as stored, or null. */
    static String subcasteFilter(String caste, String subcaste) {
        if (caste == null || subcaste == null) return null;
        switch (caste + "|" + subcaste) {
            case "worker|major/soldier": return "majorSoldier";
            case "worker|normal": return "normal";
            case "queen|ergatoid": return "ergatoidQueen";
            case "queen|alate/dealate": return "alateDealateQueen";
            case "queen|brachypterous": return "brachypterous";
            case "male|ergatoid": return "ergatoidMale";
            case "male|alate": return "alateMale";
            case "other|intercaste": return "intercaste";
            case "other|gynandromorph": return "gynandromorph";
            case "other|larva/pupa": return "larvaPupa";
        }
        return null;
    }

    /** The subcaste as the line shows it: the ordinary worker subcaste ("normal" in the data) is "worker" (Brian Fisher,
     *  2026-10-27: "9 worker, 3 major/soldier"). */
    static String subcasteLabel(String caste, String subcaste) {
        if (Caste.WORKER.equals(caste) && "normal".equals(subcaste)) return "worker";
        return subcaste;
    }

    /** The query: imaged specimens of the name by caste and subcaste. */
    public static String sql() {
        return "select lower(trim(coalesce(s.caste, ''))) caste, lower(trim(coalesce(s.subcaste, ''))) subcaste, count(distinct s.code) specimens"
            + " from specimen s join image i on i.image_of_id = s.code"
            + " where s.taxon_name = ? and " + StatisticsDb.ANT_IMAGE
            + " group by 1, 2";
    }

    /** caste -> subcaste ("" when none) -> imaged specimens, castes in the order of CASTES then UNKNOWN. */
    public static LinkedHashMap<String, LinkedHashMap<String, Integer>> count(Connection connection, String taxonName) throws SQLException {
        List<String[]> rows = new ArrayList<>();
        PreparedStatement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getPreparedStatement(connection, "ImagedCastes.count()", sql());
            stmt.setString(1, taxonName);
            rset = stmt.executeQuery();
            while (rset.next()) rows.add(new String[] {rset.getString(1), rset.getString(2), String.valueOf(rset.getInt(3))});
        } finally {
            DBUtil.close(stmt, rset, "ImagedCastes.count()");
        }
        return group(rows);
    }

    /** Rows of (caste, subcaste, specimens) grouped as count() returns them. */
    public static LinkedHashMap<String, LinkedHashMap<String, Integer>> group(List<String[]> rows) {
        LinkedHashMap<String, LinkedHashMap<String, Integer>> out = new LinkedHashMap<>();
        for (String c : CASTES) out.put(c, new LinkedHashMap<>());
        out.put(UNKNOWN, new LinkedHashMap<>());
        for (String[] r : rows) {
            String caste = r[0] == null ? "" : r[0].trim().toLowerCase();
            if (!out.containsKey(caste) || UNKNOWN.equals(caste)) caste = UNKNOWN;
            String sub = r[1] == null ? "" : r[1].trim().toLowerCase();
            int n = Integer.parseInt(r[2]);
            out.get(caste).merge(sub, n, Integer::sum);
        }
        return out;
    }

    public static int total(java.util.Map<String, Integer> subcastes) {
        int t = 0;
        for (int n : subcastes.values()) t += n;
        return t;
    }

    /**
     * The line on the species page. imagesUrl: the species images page (images.do?...) to which "&caste=X" is added;
     * null for no links. Example: "Imaged: worker (12 specimens: 9 worker, 3 major/soldier), queen (2), male (1)".
     * The numbers are also in data- attributes for the tests.
     */
    public static String toHtml(LinkedHashMap<String, LinkedHashMap<String, Integer>> counts, String imagesUrl) {
        StringBuilder data = new StringBuilder();
        List<String> parts = new ArrayList<>();
        boolean first = true;
        for (java.util.Map.Entry<String, LinkedHashMap<String, Integer>> e : counts.entrySet()) {
            String caste = e.getKey();
            int n = total(e.getValue());
            data.append(" data-").append(caste).append("=\"").append(n).append("\"");
            if (n == 0) continue;
            String label = UNKNOWN.equals(caste) ? "caste not given" : caste;
            String head = (imagesUrl != null && !UNKNOWN.equals(caste))
                ? "<a href=\"" + Html.escAttr(imagesUrl + "&caste=" + caste) + "\">" + label + "</a>" : label;
            String count = first ? n + " specimen" + (n == 1 ? "" : "s") : String.valueOf(n);
            first = false;
            // subcastes when more than one is given
            List<String> subs = new ArrayList<>();
            int given = 0;
            for (java.util.Map.Entry<String, Integer> s : e.getValue().entrySet()) if (!"".equals(s.getKey())) given++;
            if (given > 1 || (given == 1 && e.getValue().size() > 1)) {
                for (java.util.Map.Entry<String, Integer> s : e.getValue().entrySet()) {
                    if ("".equals(s.getKey())) continue;
                    String filter = subcasteFilter(caste, s.getKey());
                    String sl = Html.esc(subcasteLabel(caste, s.getKey()));
                    if (imagesUrl != null && filter != null) sl = "<a href=\"" + Html.escAttr(imagesUrl + "&caste=" + filter) + "\">" + sl + "</a>";
                    subs.add(s.getValue() + " " + sl);
                }
            }
            parts.add(head + " (" + count + (subs.isEmpty() ? "" : ": " + String.join(", ", subs)) + ")");
        }
        String text = parts.isEmpty() ? "no specimen of this name has an image of the ant yet" : String.join(", ", parts);
        return "<div id=\"imaged_castes\"" + data + "><b>Imaged:</b> " + text + "</div>";
    }
}

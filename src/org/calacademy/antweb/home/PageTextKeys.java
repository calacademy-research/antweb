package org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.calacademy.antweb.util.Html;

/**
 * Page text of overview pages (countries, adm1, regions, subregions, projects, museums,
 * bioregions) is stored in object_edit, its history in object_hist, both keyed by object_key:
 * the page's name as DescriptionAction writes it (Describable.getName()). When a place is
 * renamed, its text and history must move to the new name in the same transaction, or the page
 * shows no text and the old rows are orphaned.
 *
 * Renames that change an object_key today: editGeolocale.do (GeolocaleDb.updateGeolocale).
 * Project names (project_name), museum codes and bioregion names cannot be changed in the
 * application.
 *
 * Both tables have a created column (object_hist: ON UPDATE CURRENT_TIMESTAMP), so the moves
 * set created = created: the dates of the texts and versions stay as they are.
 */
public final class PageTextKeys {

    private PageTextKeys() {}

    /**
     * Why the text of oldKey cannot move to newKey, or null if it can: the new name already has
     * page text (it would be mixed with or overwrite that text). A new key that is the same as the
     * old one for the database (only upper/lower case differs) is not a conflict. The rows found
     * are locked until the transaction ends. Messages are HTML-escaped.
     */
    public static String refusal(Connection c, String oldKey, String newKey) throws SQLException {
        if (oldKey == null || newKey == null) return null;
        // "object_key <> ?" uses the column's collation, as the unique key (object_key, title) does.
        String q = "select count(*) from object_edit where object_key = ? and object_key <> ? for update";
        int n;
        try (PreparedStatement s = c.prepareStatement(q)) {
            s.setString(1, newKey);
            s.setString(2, oldKey);
            try (ResultSet r = s.executeQuery()) {
                r.next();
                n = r.getInt(1);
            }
        }
        if (n == 0) return null;
        return "The name " + Html.esc(newKey) + " already has page text (" + n + (n == 1 ? " field" : " fields")
            + " in object_edit), so the page text of " + Html.esc(oldKey) + " cannot move there."
            + " Rename to another name, or first move or clear the page text of " + Html.esc(newKey) + ".";
    }

    /**
     * Move the page text and its history from oldKey to newKey. Runs in the caller's transaction.
     * Returns {object_edit rows, object_hist rows} moved.
     */
    public static int[] move(Connection c, String oldKey, String newKey) throws SQLException {
        int[] moved = new int[2];
        if (oldKey == null || newKey == null || oldKey.equals(newKey)) return moved;
        String[] tables = {"object_edit", "object_hist"};
        for (int i = 0; i < tables.length; i++) {
            // Table names are the constants above, never user input.
            try (PreparedStatement s = c.prepareStatement(
                    "update " + tables[i] + " set object_key = ?, created = created where object_key = ?")) {
                s.setString(1, newKey);
                s.setString(2, oldKey);
                moved[i] = s.executeUpdate();
            }
        }
        return moved;
    }
}

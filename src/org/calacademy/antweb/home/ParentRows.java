package org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.calacademy.antweb.Countable;
import org.calacademy.antweb.Museum;
import org.calacademy.antweb.Project;
import org.calacademy.antweb.Source;
import org.calacademy.antweb.TaxonSet;
import org.calacademy.antweb.geolocale.Bioregion;
import org.calacademy.antweb.geolocale.Geolocale;

/**
 * The missing parent rows of a place's list (B76; Brian Fisher, 2026-11-03: "missing parent rows: add them"). A row's
 * numbers count its child rows by the taxon tree (taxon.parent_taxon_name, CountDb.countChildren), and the place's
 * Genera and Species count every counted row of the rank (PlaceTotals.rankCondition). A counted species or subspecies
 * whose genus row is not on the list, or a counted genus whose subfamily row is not, was in the place's number and under
 * no row, so the admin column totals of the lists were below the place's numbers (staging 2026-10-27: Afrotropical
 * rank subfamily 121 against Genera 130; Americas rank genus 4952 against Species 4953).
 *
 * The nightly count adds them first, for every kind of list (geolocale_taxon, proj_taxon, museum_taxon,
 * bioregion_taxon; CountDb.childrenCountCrawl), in two steps: the genus rows of the counted species and subspecies
 * (PlaceTotals.rankCondition SPECIES), then the subfamily rows of the counted genera (rankCondition GENUS), the new genus
 * rows included. A parent is added only when it is in the taxon table with the rank genus or subfamily, an ant, of a
 * counted status (PlaceTotals.COUNTABLE) and not a placeholder (PlaceTotals.notPlaceholder; a genus also identified to
 * genus, without a parenthesis): a name whose parent is a placeholder, of another status, or not in the taxon table
 * (B36) stays under no row. A parent a curator removed from the place's list (geolocale_taxon_dispute,
 * proj_taxon_dispute) is not added back.
 *
 * The new rows follow the conventions of the rows the lists already derive from others: source "proxy" + the child's
 * source (as GeolocaleTaxonDb.insert and ProjTaxonDb give the parent rows of an entry; the best source of the children,
 * Source.aTrumpsB), insert_method "parentRow" where the table has the column; museum lists have no source. Rows are
 * never removed here: the rebuilds of the lists (set 2, set 3) and the clean-ups of set 5 (genus rows without children)
 * already do that. One query per step and place, the inserts batched.
 */
public final class ParentRows {

    /** Written on the "Set 3 done" line of compute.log once a nightly run has added them (the e2e tests read it). */
    public static final String LOG = "missing parent rows added (2026-11-03)";

    /** insert_method of the rows added here. */
    public static final String INSERT_METHOD = "parentRow";

    /** The place type (PlaceTotals.GEOLOCALE, ...) of a Countable, or null. */
    public static String type(Countable countable) {
        if (countable instanceof Geolocale) return PlaceTotals.GEOLOCALE;
        if (countable instanceof Project) return PlaceTotals.PROJECT;
        if (countable instanceof Museum) return PlaceTotals.MUSEUM;
        if (countable instanceof Bioregion) return PlaceTotals.BIOREGION;
        return null;
    }

    /** The key of a Countable on its list (geolocale id, project name, museum code, bioregion name), or null. */
    public static String key(Countable countable) {
        if (countable instanceof Geolocale) return "" + ((Geolocale) countable).getId();
        if (countable instanceof Project) return ((Project) countable).getName();
        if (countable instanceof Museum) return ((Museum) countable).getCode();
        if (countable instanceof Bioregion) return ((Bioregion) countable).getName();
        return null;
    }

    /** SQL (alias p, a taxon row) of a parent that may be added: rank, an ant, a counted status, not a placeholder. */
    static String parentCondition(String parentRank) {
        String c = "p.taxarank = '" + parentRank + "' and p.family = 'formicidae' and p.status in " + PlaceTotals.COUNTABLE
            + " and " + PlaceTotals.notPlaceholder("p");
        if (PlaceTotals.GENUS.equals(parentRank)) c += " and " + PlaceTotals.identifiedToGenus("p") + " and " + PlaceTotals.genusWithoutParenthesis("p");
        return c;
    }

    /** The disputes table of a list type (a curator removed the name from the place's list), or null. */
    static String disputeTable(String type) {
        if (PlaceTotals.GEOLOCALE.equals(type)) return "geolocale_taxon_dispute";
        if (PlaceTotals.PROJECT.equals(type)) return "proj_taxon_dispute";
        return null;
    }

    /**
     * The query of one step at one place (parameter: the key, once): the missing parent (column parent) of each counted
     * child row of childRank (species: species and subspecies; genus), with the child's source (column source, null when
     * the table has none). withSource: the list table has a source column; withDispute: its dispute table exists.
     */
    public static String missingParentsSql(String type, String childRank, boolean withSource, boolean withDispute) {
        String l = PlaceTotals.listTable(type);
        String k = PlaceTotals.listKey(type);
        String parentRank = PlaceTotals.SPECIES.equals(childRank) ? PlaceTotals.GENUS : PlaceTotals.SUBFAMILY;
        String sql = "select distinct t.parent_taxon_name parent, " + (withSource ? "c.source" : "null") + " source"
            + " from " + l + " c join taxon t on t.taxon_name = c.taxon_name"
            + " join taxon p on p.taxon_name = t.parent_taxon_name"
            + " where c." + k + " = ? and " + PlaceTotals.rankCondition(childRank)
            + " and " + parentCondition(parentRank)
            + " and not exists (select 1 from " + l + " x where x." + k + " = c." + k + " and x.taxon_name = t.parent_taxon_name)";
        String d = disputeTable(type);
        if (withDispute && d != null) {
            sql += " and not exists (select 1 from " + d + " d where d." + k + " = c." + k + " and d.taxon_name = t.parent_taxon_name)";
        }
        return sql;
    }

    /** The source of a parent row added for children of these sources: "proxy" + the best (Source.aTrumpsB), or null. */
    static String parentSource(String best) {
        if (best == null || best.isEmpty()) return null;
        return best.startsWith(TaxonSet.PROXY) ? best : TaxonSet.PROXY + best;
    }

    private final Connection connection;
    /** Table and column lookups of this run (a nightly crawl reuses one ParentRows for every place). */
    private final Map<String, Boolean> exists = new java.util.HashMap<>();

    public ParentRows(Connection connection) {
        this.connection = connection;
    }

    /** Adds the missing genus rows, then subfamily rows, of one place's list. Returns the rows added. */
    public static int add(Connection connection, Countable countable) throws SQLException {
        return new ParentRows(connection).add(countable);
    }

    /** The same, with the lookups of this ParentRows. */
    public int add(Countable countable) throws SQLException {
        String type = type(countable);
        String key = key(countable);
        if (type == null || key == null) return 0;
        return add(type, key);
    }

    /** The same, by place type and key. */
    public int add(String type, String key) throws SQLException {
        int n = add(type, key, PlaceTotals.SPECIES);
        return n + add(type, key, PlaceTotals.GENUS);
    }

    private int add(String type, String key, String childRank) throws SQLException {
        String l = PlaceTotals.listTable(type);
        boolean withSource = columnExists(l, "source");
        boolean withMethod = columnExists(l, "insert_method");
        String d = disputeTable(type);
        boolean withDispute = d != null && tableExists(d);
        Map<String, String> parents = new LinkedHashMap<>();   // parent -> best source of its children
        try (PreparedStatement stmt = connection.prepareStatement(missingParentsSql(type, childRank, withSource, withDispute))) {
            setKey(stmt, 1, type, key);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) {
                    String parent = r.getString(1);
                    String source = r.getString(2);
                    if (parent == null) continue;
                    if (!parents.containsKey(parent)) parents.put(parent, source);
                    else if (Source.aTrumpsB(source, parents.get(parent))) parents.put(parent, source);
                }
            }
        }
        if (parents.isEmpty()) return 0;
        String cols = PlaceTotals.listKey(type) + ", taxon_name" + (withSource ? ", source" : "") + (withMethod ? ", insert_method" : "");
        String vals = "?, ?" + (withSource ? ", ?" : "") + (withMethod ? ", ?" : "");
        int added = 0;
        try (PreparedStatement stmt = connection.prepareStatement("insert ignore into " + l + " (" + cols + ") values (" + vals + ")")) {
            for (Map.Entry<String, String> e : parents.entrySet()) {
                int i = 1;
                setKey(stmt, i++, type, key);
                stmt.setString(i++, e.getKey());
                if (withSource) stmt.setString(i++, parentSource(e.getValue()));
                if (withMethod) stmt.setString(i, INSERT_METHOD);
                stmt.addBatch();
            }
            for (int c : stmt.executeBatch()) if (c > 0) added += c;
        }
        return added;
    }

    private static void setKey(PreparedStatement stmt, int i, String type, String key) throws SQLException {
        if (PlaceTotals.GEOLOCALE.equals(type)) stmt.setInt(i, Integer.parseInt(key));
        else stmt.setString(i, key);
    }

    private boolean columnExists(String table, String column) throws SQLException {
        String k = table + "." + column;
        Boolean b = exists.get(k);
        if (b != null) return b;
        try (PreparedStatement stmt = connection.prepareStatement(
                "select 1 from information_schema.columns where table_schema = database() and table_name = ? and column_name = ?")) {
            stmt.setString(1, table);
            stmt.setString(2, column);
            try (ResultSet r = stmt.executeQuery()) { b = r.next(); }
        }
        exists.put(k, b);
        return b;
    }

    private boolean tableExists(String table) throws SQLException {
        Boolean b = exists.get(table);
        if (b != null) return b;
        try (PreparedStatement stmt = connection.prepareStatement(
                "select 1 from information_schema.tables where table_schema = database() and table_name = ?")) {
            stmt.setString(1, table);
            try (ResultSet r = stmt.executeQuery()) { b = r.next(); }
        }
        exists.put(table, b);
        return b;
    }
}

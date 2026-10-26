package org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The API's /taxa subfamilyCount, genusCount, speciesCount and specimenCount (docs/numbers.md row 103, finding B11).
 * The API reads them from taxon.subfamily_count, genus_count, species_count and specimen_count, which no code wrote
 * (2026-10-26: 29,942 names showed 0). Each night, after the counts are written, they are copied from the name's
 * Global row (proj_taxon, project allantwebants), the numbers the taxon pages show in their Global column; a name with
 * no Global row gets 0. One update, joined on the primary keys of both tables (no temporary tables). The count check
 * (NumberChecks api.taxa) compares the two.
 */
public final class TaxonApiCountDb {

    private static final Log s_log = LogFactory.getLog(TaxonApiCountDb.class);

    static final String[] COLUMNS = {"subfamily_count", "genus_count", "species_count", "specimen_count"};

    private TaxonApiCountDb() {}

    /** The update: taxon columns = the Global row (0 without one), only where they differ. */
    public static String updateSql() {
        StringBuilder set = new StringBuilder();
        StringBuilder differ = new StringBuilder();
        for (String c : COLUMNS) {
            if (set.length() > 0) { set.append(", "); differ.append(" or "); }
            set.append("t.").append(c).append(" = coalesce(a.").append(c).append(", 0)");
            differ.append("coalesce(t.").append(c).append(", -1) <> coalesce(a.").append(c).append(", 0)");
        }
        return "update taxon t left join proj_taxon a on a.project_name = 'allantwebants' and a.taxon_name = t.taxon_name"
            + " set " + set + " where " + differ;
    }

    /** Copies the counts; a line for compute.log. Never throws (the nightly goes on). */
    public static String copyGlobalCounts(Connection connection) {
        long start = System.currentTimeMillis();
        try {
            if (!hasColumns(connection)) return "API taxon counts: taxon has no count columns, nothing to do";
            int n;
            try (Statement stmt = connection.createStatement()) {
                n = stmt.executeUpdate(updateSql());
            }
            return "API taxon counts: " + n + " names updated from their Global row in " + (System.currentTimeMillis() - start) / 1000 + " s";
        } catch (SQLException e) {
            s_log.warn("copyGlobalCounts() e:" + e);
            return "API taxon counts not written (" + e + ")";
        }
    }

    private static boolean hasColumns(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(
                "select count(*) from information_schema.columns where table_schema = database() and table_name = 'taxon'"
                + " and column_name in ('subfamily_count', 'genus_count', 'species_count', 'specimen_count')")) {
            return r.next() && r.getInt(1) == COLUMNS.length;
        }
    }
}

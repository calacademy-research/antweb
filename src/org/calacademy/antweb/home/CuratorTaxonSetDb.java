package org.calacademy.antweb.home;

import java.sql.*;
import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.util.*;

/**
 * Keeps the names curators add to region (geolocale_taxon) and project (proj_taxon) species lists
 * with the Species List Tool.
 *
 * A list row needs a row in taxon to be shown. Morphospecies and other curator names only have a
 * taxon row while at least one specimen uses the name, and the automated cleanups (nightly set 2
 * dataCleanup, the orphan step at the end of a specimen upload) delete list rows whose taxon row is
 * gone. A morphospecies whose specimens are briefly missing (an upload that is re-run, a group that
 * removes and reloads its data, a name that changes and changes back) therefore lost its place in
 * every curated list for good, and the curator had to add it again.
 *
 * Rule (Brian Fisher, 2026-10): regional lists are specimen records of valid species, AntCat type
 * localities and records added by curators. A curator's record stays until the curator removes it
 * or the name no longer exists. A name that becomes invalid moves to its current valid name (the
 * nightly updateTaxonSets step).
 *
 * So, before a cleanup deletes list rows that have no taxon, curator rows are copied to
 * taxon_set_parked. When the name exists again (its specimens are back), restore() puts the row back
 * through the normal insert, which also adds the genus and subfamily rows and the parent regions. A
 * name that never comes back stays parked (it is not shown anywhere). Restored rows keep their
 * parked entry with the time of the restore, as the record for an undo.
 *
 * ensureSchema() creates the table when missing. Same DDL: db/upgrade/8.113/2026-10-19-taxon-set-parked.sql.
 */
public class CuratorTaxonSetDb extends AntwebDb {

    private static final Log s_log = LogFactory.getLog(CuratorTaxonSetDb.class);

    /** Sources written by curators (Source.getSourceCanonical gives "curator"); not the proxy parents. */
    public static final List<String> CURATOR_SOURCES = List.of("curator", "speciesList", "speciesListTool", "speciesListUpload");

    /** Project lists that are built by the system, never by curators. */
    public static final List<String> SYSTEM_PROJECTS = List.of("worldants", "allantwebants");

    public static final String GEOLOCALE = "geolocale";
    public static final String PROJECT = "project";

    /** The table, in the character set and collation of taxon.taxon_name (so names compare without a collation clash). */
    public static String createTable(String charset, String collation) {
        String defaults = "";
        if (charset != null && charset.matches("[a-z0-9_]+")) defaults += " DEFAULT CHARSET=" + charset;
        if (collation != null && collation.matches("[a-z0-9_]+")) defaults += " COLLATE=" + collation;
        return "CREATE TABLE IF NOT EXISTS taxon_set_parked ("
        + " list_type varchar(10) NOT NULL,"
        + " list_key varchar(128) NOT NULL,"
        + " taxon_name varchar(128) NOT NULL,"
        + " source varchar(30) DEFAULT NULL,"
        + " created timestamp NULL DEFAULT NULL,"
        + " parked timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,"
        + " restored timestamp NULL DEFAULT NULL,"
        + " PRIMARY KEY (list_type, list_key, taxon_name),"
        + " KEY taxon_name (taxon_name)"
        + ") ENGINE=InnoDB" + defaults;
    }

    public CuratorTaxonSetDb(Connection connection) {
        super(connection);
    }

    /** True for a source a curator wrote (not a proxy parent row). */
    public static boolean isCuratorSource(String source) {
        return source != null && CURATOR_SOURCES.contains(source);
    }

    /** SQL list of the curator sources: ('curator', 'speciesList', ...). */
    public static String curatorSourceList() {
        StringBuilder b = new StringBuilder("(");
        for (int i = 0; i < CURATOR_SOURCES.size(); i++) {
            if (i > 0) b.append(", ");
            b.append("'").append(CURATOR_SOURCES.get(i)).append("'");
        }
        return b.append(")").toString();
    }

    /** SQL list of the system projects: ('worldants', 'allantwebants'). */
    public static String systemProjectList() {
        StringBuilder b = new StringBuilder("(");
        for (int i = 0; i < SYSTEM_PROJECTS.size(); i++) {
            if (i > 0) b.append(", ");
            b.append("'").append(SYSTEM_PROJECTS.get(i)).append("'");
        }
        return b.append(")").toString();
    }

    /** Copies curator rows of geolocale_taxon whose taxon row is missing into taxon_set_parked. */
    public static String parkGeolocaleSql() {
        return "insert into taxon_set_parked (list_type, list_key, taxon_name, source, created, parked, restored)"
            + " select '" + GEOLOCALE + "', gt.geolocale_id, gt.taxon_name, gt.source, gt.created, now(), null"
            + " from geolocale_taxon gt"
            + " where gt.source in " + curatorSourceList()
            + " and gt.taxon_name not in (select taxon_name from taxon)"
            + " on duplicate key update source = values(source), parked = now(), restored = null";
    }

    /** Copies curator rows of proj_taxon (curated projects only) whose taxon row is missing into taxon_set_parked. */
    public static String parkProjectSql() {
        return "insert into taxon_set_parked (list_type, list_key, taxon_name, source, created, parked, restored)"
            + " select '" + PROJECT + "', pt.project_name, pt.taxon_name, pt.source, pt.created, now(), null"
            + " from proj_taxon pt"
            + " where pt.source in " + curatorSourceList()
            + " and pt.project_name not in " + systemProjectList()
            + " and pt.taxon_name not in (select taxon_name from taxon)"
            + " on duplicate key update source = values(source), parked = now(), restored = null";
    }

    /** Parked rows whose name exists again in taxon. */
    public static String restorableSql() {
        return "select p.list_type, p.list_key, p.taxon_name, p.source from taxon_set_parked p"
            + " where p.restored is null and p.taxon_name in (select taxon_name from taxon)"
            + " order by p.list_type, p.list_key, p.taxon_name";
    }

    private static volatile boolean s_schemaChecked = false;

    /** Create taxon_set_parked if missing. Safe to run repeatedly. */
    public void ensureSchema() throws SQLException {
        if (s_schemaChecked) return;
        String charset = null;
        String collation = null;
        String query = "select character_set_name, collation_name from information_schema.columns"
            + " where table_schema = database() and table_name = 'taxon' and column_name = 'taxon_name'";
        try (Statement stmt = getConnection().createStatement()) {
            try (ResultSet rset = stmt.executeQuery(query)) {
                if (rset.next()) {
                    charset = rset.getString(1);
                    collation = rset.getString(2);
                }
            }
            stmt.executeUpdate(createTable(charset, collation));
        }
        s_schemaChecked = true;
    }

    /**
     * Copy every curator list row whose taxon row is missing into taxon_set_parked. Call it right
     * before a step that deletes list rows without a taxon. Returns the number of rows parked (new
     * or parked again).
     */
    public int parkCuratorRowsWithoutTaxon() throws SQLException {
        ensureSchema();
        int c = 0;
        try (Statement stmt = getConnection().createStatement()) {
            c += stmt.executeUpdate(parkGeolocaleSql());
            c += stmt.executeUpdate(parkProjectSql());
        }
        if (c > 0) LogMgr.appendLog("taxonSet.log", "CuratorTaxonSetDb.parkCuratorRowsWithoutTaxon:" + c, true);
        return c;
    }

    /**
     * Copy the curator list rows of one name into taxon_set_parked. For a step that deletes one
     * taxon and its list rows (OrphansDb.deleteTaxon). Returns the number of rows parked.
     */
    public int parkCuratorRows(String taxonName) throws SQLException {
        ensureSchema();
        int c = 0;
        String geo = parkGeolocaleSql().replace(" and gt.taxon_name not in (select taxon_name from taxon)", " and gt.taxon_name = ?");
        String proj = parkProjectSql().replace(" and pt.taxon_name not in (select taxon_name from taxon)", " and pt.taxon_name = ?");
        for (String dml : new String[] {geo, proj}) {
            try (PreparedStatement stmt = getConnection().prepareStatement(dml)) {
                stmt.setString(1, taxonName);
                c += stmt.executeUpdate();
            }
        }
        if (c > 0) LogMgr.appendLog("taxonSet.log", "CuratorTaxonSetDb.parkCuratorRows(" + taxonName + "):" + c, true);
        return c;
    }

    /** A parked curator list row. */
    public static final class Parked {
        public final String listType;
        public final String listKey;
        public final String taxonName;
        public final String source;
        public Parked(String listType, String listKey, String taxonName, String source) {
            this.listType = listType;
            this.listKey = listKey;
            this.taxonName = taxonName;
            this.source = source;
        }
        public String toString() { return listType + ":" + listKey + ":" + taxonName; }
    }

    public List<Parked> getRestorable() throws SQLException {
        ensureSchema();
        List<Parked> list = new ArrayList<>();
        try (Statement stmt = getConnection().createStatement();
             ResultSet rset = stmt.executeQuery(restorableSql())) {
            while (rset.next()) {
                list.add(new Parked(rset.getString("list_type"), rset.getString("list_key"),
                    rset.getString("taxon_name"), rset.getString("source")));
            }
        }
        return list;
    }

    /** Puts one parked row back into its list. Returns false if the list no longer exists (the row stays parked). */
    public interface Restorer {
        boolean restore(Parked parked) throws SQLException;
    }

    /** The normal list insert: also adds the genus and subfamily rows and the parent regions. */
    public Restorer defaultRestorer() {
        return parked -> {
            String source = isCuratorSource(parked.source) ? parked.source : "speciesListTool";
            if (GEOLOCALE.equals(parked.listType)) {
                int id;
                try { id = Integer.parseInt(parked.listKey); } catch (NumberFormatException e) { return false; }
                Geolocale geolocale = GeolocaleMgr.getGeolocale(id);
                if (geolocale == null) return false;
                new GeolocaleTaxonDb(getConnection()).insert(geolocale, parked.taxonName, source);
                return true;
            }
            if (PROJECT.equals(parked.listType)) {
                Project project = ProjectMgr.getProject(parked.listKey);
                if (project == null) return false;
                new ProjTaxonDb(getConnection()).insert(project, parked.taxonName, source);
                return true;
            }
            return false;
        };
    }

    /** Restore every parked row whose name exists again. */
    public String restore() throws SQLException {
        return restore(defaultRestorer());
    }

    public String restore(Restorer restorer) throws SQLException {
        List<Parked> list = getRestorable();
        int restored = 0;
        int skipped = 0;
        String update = "update taxon_set_parked set restored = now() where list_type = ? and list_key = ? and taxon_name = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(update)) {
            for (Parked parked : list) {
                if (!restorer.restore(parked)) {
                    ++skipped;
                    continue;
                }
                stmt.setString(1, parked.listType);
                stmt.setString(2, parked.listKey);
                stmt.setString(3, parked.taxonName);
                stmt.executeUpdate();
                ++restored;
            }
        }
        String message = "CuratorTaxonSetDb.restore restored:" + restored + (skipped > 0 ? " skipped (list gone):" + skipped : "");
        if (restored > 0 || skipped > 0) {
            LogMgr.appendLog("taxonSet.log", message, true);
            s_log.info("restore() " + message + " " + list);
        }
        return message + " ";
    }
}

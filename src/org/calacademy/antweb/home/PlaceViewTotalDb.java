package org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.calacademy.antweb.ListView;
import org.calacademy.antweb.Museum;
import org.calacademy.antweb.Overview;
import org.calacademy.antweb.Project;
import org.calacademy.antweb.Rank;
import org.calacademy.antweb.StatusSet;
import org.calacademy.antweb.TaxaPage;
import org.calacademy.antweb.geolocale.Bioregion;
import org.calacademy.antweb.geolocale.Geolocale;
import org.calacademy.antweb.util.BioregionMgr;
import org.calacademy.antweb.util.GeolocaleMgr;
import org.calacademy.antweb.util.MuseumMgr;
import org.calacademy.antweb.util.ProjectMgr;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The Genera and Species of a place page equal the header of the place's list in its default view (Brian Fisher,
 * 2026-11-04; claude/antweb-list-design-rules.md, "Place page numbers and the views"): Curated on every kind of list,
 * Valid (with fossils) on the AntCat list and Fossil Ants (StatusSet.defaultChoice). Production 11-04: the place pages
 * counted every genus row and every morphospecies (Afrotropical Species 6,951, its Curated list 2,891; Mozambique
 * Genera 83, list 76), so the numbers and the lists they open disagreed.
 *
 * The numbers are not counted by a second SQL rule (it would drift again): for every place with a list, the nightly
 * builds the place's genus and species lists in the default view with the list page's own code (TaxaPage.fetchChildren,
 * loadCollected and findGenusRows, as TaxaPageAction) and stores how many rows each shows, in place_view_total
 * (list_type, list_key, rank, view, n). The place page shows them (OverviewAction.setPlaceImages); a place not counted
 * yet keeps its stored total. Nightly set 4, after the Not curated count (the lists read place_specimen_name);
 * admins: utilData.do?action=placeViewTotals. Writes "Place list totals counted: ..." to compute.log.
 * Places: valid geolocales whose list has rows, bioregions, museums, projects; not the AntWeb-wide list (allantwebants,
 * its species list is refused as too expensive), which keeps its stored totals.
 *
 * Only the places whose data changed are counted again (Brian Fisher, 2026-11-09: the step took 60 minutes on 11-07 and
 * 11-08 and about 4 hours on 11-09, production). Every place has a fingerprint (fingerprint(): counts and checksums of
 * what its lists are built from: its list rows with the taxon rows of their names, the names of the specimens collected
 * there as the Not curated count stored them tonight (place_specimen_name, with the specimens of each name and the taxon
 * row), the names a curator removed there (disputes), and, for every place, the curated morphospecies of AntWeb (a
 * morphospecies on any curator's list entry, ListView.vettedCondition) and the counting rules (RULES)). Specimen dates are no signal: an upload, the
 * nightly TaxonWorks upload of CAS among them, deletes its group's specimens and loads them again (SpecimenUploadDb
 * .dropSpecimens), so every CAS specimen gets a new last_modified every night. A place is counted again when its
 * fingerprint differs from the one stored with its totals (place_view_total_state), when the specimen names it was
 * compared with were not stored again since (the Not curated count failed or did not run: unsure, so counted), when its
 * last count is over 7 days old, when RULES changed, on Sundays (everything, weekly), when its totals are missing, and
 * by the admin action with param=all. Each place commits on its own (autocommit; until 2026-11-09 one transaction per
 * 100 places held metadata locks on specimen, taxon and the lists for minutes, and an online ALTER TABLE timed out
 * waiting for them). The line in compute.log: "Place list totals counted: N places in S s (M recounted, K unchanged)".
 *
 * Tests: home.PlaceViewTotalDbTest (unchanged places skipped, changed ones counted, rules change, commit per place),
 * tests/e2e/test_numbers_live.py (test_list_headers_equal_place_numbers), test_numbers_lists.py
 * (test_list_headers_equal_place_numbers_of_every_kind), test_nightly_jobs_ran.py (test_place_list_totals_counted_every_place).
 */
public class PlaceViewTotalDb {

    private static final Log s_log = LogFactory.getLog(PlaceViewTotalDb.class);

    public static final String LOG = "Place list totals counted";

    /**
     * The counting rules of the list totals: bump when the rules of what a list shows change (ListView, StatusSet,
     * TaxaPage, PlaceTotals: a view's criteria, the names a view adds, which genus rows show), so every place is counted
     * again by the next run instead of waiting for Sunday. 2026-11-09: the adm1 rule (B26 fixed: an adm1's specimens by
     * its country and name).
     */
    public static final String RULES = "2026-11-09 adm1 by country";

    /** A place counted longer ago than this is counted again, whatever its fingerprint. */
    static final int MAX_AGE_DAYS = 7;

    private final Connection connection;

    public PlaceViewTotalDb(Connection connection) {
        this.connection = connection;
    }

    public static String createTable(String charset, String collation) {
        return "CREATE TABLE IF NOT EXISTS place_view_total ("
            + " list_type varchar(16) NOT NULL,"
            + " list_key varchar(128) NOT NULL,"
            + " taxarank varchar(16) NOT NULL,"
            + " view_name varchar(32) NOT NULL,"
            + " n int NOT NULL DEFAULT 0,"
            + " counted datetime NULL,"
            + " PRIMARY KEY (list_type, list_key, taxarank)"
            + ") ENGINE=InnoDB" + PlaceGenusCurationDb.defaults(charset, collation);
    }

    /**
     * Per place (2026-11-09): the rules and fingerprint its totals were counted with (counted), and when its fingerprint
     * was last compared (checked). Kept apart from place_view_total (new table: no ALTER of a table the pages read).
     */
    public static String createStateTable(String charset, String collation) {
        return "CREATE TABLE IF NOT EXISTS place_view_total_state ("
            + " list_type varchar(16) NOT NULL,"
            + " list_key varchar(128) NOT NULL,"
            + " rules varchar(64) NOT NULL,"
            + " fingerprint varchar(255) NOT NULL,"
            + " counted datetime NULL,"
            + " checked datetime NULL,"
            + " PRIMARY KEY (list_type, list_key)"
            + ") ENGINE=InnoDB" + PlaceGenusCurationDb.defaults(charset, collation);
    }

    private static volatile boolean s_schemaChecked = false;

    public void ensureSchema() throws SQLException {
        if (s_schemaChecked) return;
        String charset = null;
        String collation = null;
        try (Statement stmt = connection.createStatement()) {
            try (ResultSet r = stmt.executeQuery("select character_set_name, collation_name from information_schema.columns"
                    + " where table_schema = database() and table_name = 'taxon' and column_name = 'taxon_name'")) {
                if (r.next()) { charset = r.getString(1); collation = r.getString(2); }
            }
            stmt.executeUpdate(createTable(charset, collation));
            stmt.executeUpdate(createStateTable(charset, collation));
        }
        s_schemaChecked = true;
    }

    /** (list_type, list_key) of a place, as PlaceImageCountDb, or null. */
    public static String[] typeAndKey(Overview overview) {
        if (overview instanceof Geolocale) return new String[] {PlaceImageCountDb.GEOLOCALE, "" + ((Geolocale) overview).getId()};
        if (overview instanceof Project) return new String[] {PlaceImageCountDb.PROJECT, overview.getName()};
        if (overview instanceof Museum) return new String[] {PlaceImageCountDb.MUSEUM, ((Museum) overview).getCode()};
        if (overview instanceof Bioregion) return new String[] {PlaceImageCountDb.BIOREGION, overview.getName()};
        return null;
    }

    /** Whether the nightly counts this place (not the AntWeb-wide list). */
    public static boolean counts(Overview overview) {
        return overview != null && !Project.ALLANTWEBANTS.equals(overview.getName());
    }

    /** The rows the place's list of this rank shows in its default view, counted by the list page's own code. */
    public int listRows(Overview overview, String rank) throws SQLException {
        String choice = StatusSet.defaultChoice(overview);
        TaxaPage page = new TaxaPage();
        if ((Rank.GENUS.equals(rank) && ListView.readsPlaceSpecimens(overview))
                || (Rank.SPECIES.equals(rank) && ListView.addsSpecimenNames(overview))) {
            page.loadCollected(connection, overview, rank);
        }
        // withImages false, withTaxa true (the list's names; false left the query empty: "0 places", 2026-11-05),
        // withSpecimen false, withFossil true: as TaxaPageAction calls it
        page.fetchChildren(connection, overview, rank, false, true, false, true, null, StatusSet.listStatusSet(choice, overview.getName()));
        if (Rank.GENUS.equals(rank)) page.findGenusRows(connection, overview, choice);
        return page.getChildrenCount();
    }

    /** The stored rows of a place: {genera, species, subfamilies}, -1 where not counted. */
    public int[] read(Overview overview) throws SQLException {
        int[] out = {-1, -1, -1};
        String[] tk = typeAndKey(overview);
        if (tk == null || !tableExists()) return out;
        try (PreparedStatement stmt = connection.prepareStatement(
                "select taxarank, n from place_view_total where list_type = ? and list_key = ?")) {
            stmt.setString(1, tk[0]);
            stmt.setString(2, tk[1]);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) {
                    if (Rank.GENUS.equals(r.getString(1))) out[0] = r.getInt(2);
                    if (Rank.SPECIES.equals(r.getString(1))) out[1] = r.getInt(2);
                    if (Rank.SUBFAMILY.equals(r.getString(1))) out[2] = r.getInt(2);
                }
            }
        }
        return out;
    }

    /** The database's clock (the counted column is set with now()). */
    private java.sql.Timestamp dbNow() throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("select now()"); ResultSet r = stmt.executeQuery()) {
            return r.next() ? r.getTimestamp(1) : null;
        }
    }

    /** Whether the table exists; once it does, not asked again (it is read on every place page; review of #324). */
    private static volatile boolean s_tableSeen = false;

    private boolean tableExists() throws SQLException {
        if (s_tableSeen) return true;
        try (PreparedStatement stmt = connection.prepareStatement(
                "select 1 from information_schema.tables where table_schema = database() and table_name = 'place_view_total'");
             ResultSet r = stmt.executeQuery()) {
            s_tableSeen = r.next();
            return s_tableSeen;
        }
    }

    /** Stores one place's totals (replaces its rows). */
    void store(Overview overview, int genera, int species, int subfamilies) throws SQLException {
        String[] tk = typeAndKey(overview);
        if (tk == null) return;
        String view = StatusSet.defaultChoice(overview);
        try (PreparedStatement stmt = connection.prepareStatement("replace into place_view_total"
                + " (list_type, list_key, taxarank, view_name, n, counted) values (?, ?, ?, ?, ?, now())")) {
            for (Object[] row : new Object[][] {{Rank.GENUS, genera}, {Rank.SPECIES, species}, {Rank.SUBFAMILY, subfamilies}}) {
                stmt.setString(1, tk[0]);
                stmt.setString(2, tk[1]);
                stmt.setString(3, (String) row[0]);
                stmt.setString(4, view);
                stmt.setInt(5, (Integer) row[1]);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    /** The geolocale ids with at least one list row. */
    private Set<Integer> geolocalesWithList() throws SQLException {
        Set<Integer> out = new HashSet<>();
        try (Statement stmt = connection.createStatement();
             ResultSet r = stmt.executeQuery("select distinct geolocale_id from geolocale_taxon")) {
            while (r.next()) out.add(r.getInt(1));
        }
        return out;
    }

    /** Every place with a list: valid geolocales with list rows, bioregions, museums, projects. */
    public List<Overview> places() throws SQLException {
        List<Overview> out = new ArrayList<>();
        Set<Integer> withList = geolocalesWithList();
        List<Geolocale> geolocales = GeolocaleMgr.getGeolocales(null, true);
        if (geolocales != null) for (Geolocale g : geolocales) if (withList.contains(g.getId())) out.add(g);
        if (BioregionMgr.getBioregions() != null) out.addAll(BioregionMgr.getBioregions());
        if (MuseumMgr.getMuseums() != null) out.addAll(MuseumMgr.getMuseums());
        if (ProjectMgr.getAntProjects() != null) for (Project p : ProjectMgr.getAntProjects()) if (counts(p)) out.add(p);
        return out;
    }

    // ------------------------------------------------------------------ what changed (2026-11-09)

    /** The taxon columns whose values decide what a list shows (those the table has). */
    static final String[] TAXON_COLUMNS = {"taxarank", "status", "parent_taxon_name", "family", "subfamily", "genus", "species",
        "subspecies", "fossil", "current_valid_name"};

    private final java.util.Map<String, Boolean> columns = new java.util.HashMap<>();

    private boolean columnExists(String table, String column) throws SQLException {
        String k = table + "." + column;
        Boolean b = columns.get(k);
        if (b != null) return b;
        try (PreparedStatement stmt = connection.prepareStatement("select 1 from information_schema.columns"
                + " where table_schema = database() and table_name = ? and column_name = ?")) {
            stmt.setString(1, table);
            stmt.setString(2, column);
            try (ResultSet r = stmt.executeQuery()) { b = r.next(); }
        }
        columns.put(k, b);
        return b;
    }

    private final java.util.Map<String, Boolean> tables = new java.util.HashMap<>();

    private boolean hasTable(String table) throws SQLException {
        Boolean b = tables.get(table);
        if (b != null) return b;
        try (PreparedStatement stmt = connection.prepareStatement(
                "select 1 from information_schema.tables where table_schema = database() and table_name = ?")) {
            stmt.setString(1, table);
            try (ResultSet r = stmt.executeQuery()) { b = r.next(); }
        }
        tables.put(table, b);
        return b;
    }

    /** SQL: one text per row of the given columns (nulls as ~), for crc32. */
    private static String rowText(List<String> cols) {
        StringBuilder b = new StringBuilder("concat_ws('|'");
        for (String c : cols) b.append(", coalesce(").append(c).append(", '~')");
        return b.append(")").toString();
    }

    private List<String> taxonColumns(String alias) throws SQLException {
        List<String> out = new ArrayList<>();
        for (String c : TAXON_COLUMNS) if (columnExists("taxon", c)) out.add(alias + "." + c);
        return out;
    }

    /** "n:sum" of the rows of a query of one crc32 column (parameters bound in order). */
    private String checksum(String select, String from, Object... params) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("select count(*), coalesce(sum(crc32(" + select + ")), 0) from " + from)) {
            for (int i = 0; i < params.length; i++) stmt.setObject(i + 1, params[i]);
            try (ResultSet r = stmt.executeQuery()) {
                return r.next() ? r.getLong(1) + ":" + r.getLong(2) : "0:0";
            }
        }
    }

    /** The list table, its key column and the key value of a place's list. */
    private static Object[] listOf(Overview overview) {
        if (overview instanceof Geolocale) return new Object[] {"geolocale_taxon", "geolocale_id", ((Geolocale) overview).getId()};
        if (overview instanceof Project) return new Object[] {"proj_taxon", "project_name", overview.getName()};
        if (overview instanceof Museum) return new Object[] {"museum_taxon", "code", ((Museum) overview).getCode()};
        if (overview instanceof Bioregion) return new Object[] {"bioregion_taxon", "bioregion_name", overview.getName()};
        return null;
    }

    /**
     * What every place's lists depend on beyond its own rows: the curated morphospecies (a morphospecies on any curator's
     * list entry is curated in every place, and its morphogenus with it: ListView.vettedCondition) and the rules. Only
     * names of status morphotaxon: a curator adding a valid name to a list changes no other place. Read once per run.
     */
    String globalFingerprint() throws SQLException {
        String sources = CuratorTaxonSetDb.curatorSourceList();
        StringBuilder sys = new StringBuilder();
        for (String p : CuratorTaxonSetDb.SYSTEM_PROJECTS) sys.append(sys.length() == 0 ? "" : ", ").append(org.calacademy.antweb.util.DBUtil.sqlLiteral(p));
        String vetted = "(select taxon_name from geolocale_taxon where source in " + sources
            + " union select pt.taxon_name from proj_taxon pt join project p on p.project_name = pt.project_name"
            + " where pt.source in " + sources + " and pt.project_name not in (" + sys + ")) v"
            + " join taxon t on t.taxon_name = v.taxon_name and t.status = 'morphotaxon'";
        return "v" + checksum("v.taxon_name", vetted) + ";r" + RULES;
    }

    /**
     * A place's fingerprint: its list rows with the taxon rows of their names, the specimen names collected there as
     * stored tonight (place_specimen_name, with the specimens per name and the taxon row), its disputes, and global.
     */
    public String fingerprint(Overview overview, String global) throws SQLException {
        StringBuilder f = new StringBuilder();
        List<String> taxon = taxonColumns("t");
        Object[] list = listOf(overview);
        if (list != null) {
            String table = (String) list[0];
            List<String> cols = new ArrayList<>();
            cols.add("l.taxon_name");
            if (columnExists(table, "source")) cols.add("l.source");
            cols.addAll(taxon);
            f.append("l").append(checksum(rowText(cols), table + " l left join taxon t on t.taxon_name = l.taxon_name where l." + list[1] + " = ?", list[2]));
        }
        String[] names = PlaceGenusCurationDb.typeAndKey(overview);
        if (names != null && hasTable("place_specimen_name")) {
            List<String> cols = new ArrayList<>();
            cols.add("p.taxon_name");
            cols.add("p.specimens");
            cols.addAll(taxon);
            f.append(";s").append(checksum(rowText(cols), "place_specimen_name p left join taxon t on t.taxon_name = p.taxon_name"
                + " where p.list_type = ? and p.list_key = ?", names[0], names[1]));
        }
        if (overview instanceof Geolocale && hasTable("geolocale_taxon_dispute")) {
            f.append(";d").append(checksum("d.taxon_name", "geolocale_taxon_dispute d where d.geolocale_id = ?", ((Geolocale) overview).getId()));
        }
        if (overview instanceof Project && hasTable("proj_taxon_dispute")) {
            f.append(";d").append(checksum("d.taxon_name", "proj_taxon_dispute d where d.project_name = ?", overview.getName()));
        }
        f.append(";").append(global);
        return f.length() > 255 ? f.substring(0, 255) : f.toString();
    }

    /** A place's stored state: rules, fingerprint, counted, checked. */
    static final class State {
        String rules;
        String fingerprint;
        java.sql.Timestamp counted;
        java.sql.Timestamp checked;
    }

    private static String key(String type, String key) {
        return type + "\u0000" + key;
    }

    private java.util.Map<String, State> readStates() throws SQLException {
        java.util.Map<String, State> out = new java.util.HashMap<>();
        try (Statement stmt = connection.createStatement();
             ResultSet r = stmt.executeQuery("select list_type, list_key, rules, fingerprint, counted, checked from place_view_total_state")) {
            while (r.next()) {
                State st = new State();
                st.rules = r.getString(3);
                st.fingerprint = r.getString(4);
                st.counted = r.getTimestamp(5);
                st.checked = r.getTimestamp(6);
                out.put(key(r.getString(1), r.getString(2)), st);
            }
        }
        return out;
    }

    /** The places with totals (list_type, list_key). */
    private Set<String> placesWithTotals() throws SQLException {
        Set<String> out = new HashSet<>();
        try (Statement stmt = connection.createStatement();
             ResultSet r = stmt.executeQuery("select list_type, list_key, count(*) from place_view_total group by list_type, list_key")) {
            while (r.next()) if (r.getInt(3) >= 3) out.add(key(r.getString(1), r.getString(2)));
        }
        return out;
    }

    /** When place_specimen_name was last filled, per kind (PlaceGenusCurationDb types), or an empty map. */
    private java.util.Map<String, java.sql.Timestamp> namesFilled() throws SQLException {
        java.util.Map<String, java.sql.Timestamp> out = new java.util.HashMap<>();
        if (!hasTable("place_specimen_name")) return out;
        try (Statement stmt = connection.createStatement();
             ResultSet r = stmt.executeQuery("select list_type, max(counted) from place_specimen_name group by list_type")) {
            while (r.next()) out.put(r.getString(1), r.getTimestamp(2));
        }
        return out;
    }

    private void writeState(String[] tk, String fingerprint, boolean counted) throws SQLException {
        String sql = counted
            ? "replace into place_view_total_state (list_type, list_key, rules, fingerprint, counted, checked) values (?, ?, ?, ?, now(), now())"
            : "update place_view_total_state set checked = now() where list_type = ? and list_key = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, tk[0]);
            stmt.setString(2, tk[1]);
            if (counted) {
                stmt.setString(3, RULES);
                stmt.setString(4, fingerprint);
            }
            stmt.executeUpdate();
        }
    }

    /**
     * Why a place is counted again, or null when its totals stand (same rules and fingerprint, counted within
     * MAX_AGE_DAYS, its specimen names stored again since it was last compared, totals present).
     */
    static String recountReason(State st, String fingerprint, boolean hasTotals, java.sql.Timestamp namesFilled, boolean usesNames,
                                java.sql.Timestamp now) {
        if (st == null) return "not counted yet";
        if (!hasTotals) return "totals missing";
        if (!RULES.equals(st.rules)) return "rules changed";
        if (st.counted == null || now == null || now.getTime() - st.counted.getTime() > MAX_AGE_DAYS * 86_400_000L) return "counted over " + MAX_AGE_DAYS + " days ago";
        if (usesNames && (namesFilled == null || st.checked == null || !namesFilled.after(st.checked))) return "specimen names not stored again since the last count";
        if (!fingerprint.equals(st.fingerprint)) return "data changed";
        return null;
    }

    /** Tests only: called after each place is done (its list_type and list_key), to see that it is committed. */
    private static volatile java.util.function.BiConsumer<String, String> s_afterPlace = null;

    public static void setAfterPlaceForTests(java.util.function.BiConsumer<String, String> afterPlace) {
        s_afterPlace = afterPlace;
    }

    /** Whether today is the weekly day of the full count (Sunday, the server's time zone). */
    public static boolean weeklyFullCount(java.time.LocalDate day) {
        return day.getDayOfWeek() == java.time.DayOfWeek.SUNDAY;
    }

    /** The nightly fill: on Sundays every place, other days the places whose data changed (fillAll(all)). */
    public String fillAll() throws SQLException {
        return fillAll(weeklyFullCount(java.time.LocalDate.now()));
    }

    /**
     * Counts the places whose data changed (all: every place), each committed on its own. A place that fails is skipped
     * and named; its totals and state are removed, so its page shows its stored totals and the next run counts it.
     */
    public String fillAll(boolean all) throws SQLException {
        return fill(places(), all);
    }

    /** fillAll of the places given (the tests give theirs). */
    public String fill(List<Overview> placeList, boolean all) throws SQLException {
        ensureSchema();
        long start = System.currentTimeMillis();
        boolean auto = connection.getAutoCommit();
        int recounted = 0;
        int unchanged = 0;
        int failedCount = 0;
        List<String> failed = new ArrayList<>();
        Set<String> handled = new HashSet<>();
        try {
            // autocommit: every statement its own transaction, so no lock is held from one place to the next
            if (!auto) {
                connection.commit();
                connection.setAutoCommit(true);
            }
            String global = globalFingerprint();
            java.sql.Timestamp now = dbNow();
            java.util.Map<String, State> states = readStates();
            Set<String> withTotals = placesWithTotals();
            java.util.Map<String, java.sql.Timestamp> filled = namesFilled();
            for (Overview place : placeList) {
                String[] tk = typeAndKey(place);
                if (tk == null) continue;
                String k = key(tk[0], tk[1]);
                try {
                    String fingerprint = fingerprint(place, global);
                    String[] names = PlaceGenusCurationDb.typeAndKey(place);
                    String reason = all ? "full count" : recountReason(states.get(k), fingerprint, withTotals.contains(k),
                        names == null ? null : filled.get(names[0]), names != null, now);
                    if (reason == null) {
                        writeState(tk, fingerprint, false);
                        ++unchanged;
                    } else {
                        // subfamilies too (2026-11-04: North America showed 15 stored, its Curated list 11)
                        store(place, listRows(place, Rank.GENUS), listRows(place, Rank.SPECIES), listRows(place, Rank.SUBFAMILY));
                        writeState(tk, fingerprint, true);
                        ++recounted;
                    }
                    handled.add(k);
                    if (s_afterPlace != null) s_afterPlace.accept(tk[0], tk[1]);
                } catch (SQLException | RuntimeException e) {
                    failedCount++;
                    if (failed.size() < 5) failed.add(place.getName() + " (" + e.getMessage() + ")");
                    s_log.warn("fillAll() " + place.getName() + " e:" + e);
                }
            }
            // Rows of places not counted or kept tonight (a place no longer listed, or one that failed tonight) are removed,
            // so its page shows its stored totals and data-view-counted="false" rather than an old number (review of #324).
            int stale = removeOthers("place_view_total", handled);
            removeOthers("place_view_total_state", handled);
            if (stale > 0) s_log.info("fillAll() " + stale + " rows not counted tonight removed");
        } finally {
            if (!auto) connection.setAutoCommit(false);
        }
        int done = recounted + unchanged;
        // none counted: FAILED, so the morning check reports it (2026-11-05: every place failed and the line read "0 places")
        return LOG + ": " + (done == 0 && !failed.isEmpty() ? "FAILED, " : "") + done + " places in " + (System.currentTimeMillis() - start) / 1000 + " s"
            + " (" + recounted + " recounted, " + unchanged + " unchanged)"
            + (failed.isEmpty() ? "" : "; FAILED for " + failedCount + " places (their stored totals are shown), e.g. " + String.join(", ", failed));
    }

    /** Deletes a table's rows of the places not in keep; returns the rows deleted. One place per statement. */
    private int removeOthers(String table, Set<String> keep) throws SQLException {
        List<String[]> drop = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet r = stmt.executeQuery("select distinct list_type, list_key from " + table)) {
            while (r.next()) if (!keep.contains(key(r.getString(1), r.getString(2)))) drop.add(new String[] {r.getString(1), r.getString(2)});
        }
        int n = 0;
        try (PreparedStatement d = connection.prepareStatement("delete from " + table + " where list_type = ? and list_key = ?")) {
            for (String[] tk : drop) {
                d.setString(1, tk[0]);
                d.setString(2, tk[1]);
                n += d.executeUpdate();
            }
        }
        return n;
    }
}

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
 * Tests: tests/e2e/test_numbers_live.py (test_place_numbers_equal_their_default_list).
 */
public class PlaceViewTotalDb {

    private static final Log s_log = LogFactory.getLog(PlaceViewTotalDb.class);

    public static final String LOG = "Place list totals counted";

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

    private boolean tableExists() throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "select 1 from information_schema.tables where table_schema = database() and table_name = 'place_view_total'");
             ResultSet r = stmt.executeQuery()) {
            return r.next();
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

    /** The nightly fill: every place's totals, committed every 100 places. A place that fails is skipped and named. */
    public String fillAll() throws SQLException {
        ensureSchema();
        long start = System.currentTimeMillis();
        int done = 0;
        List<String> failed = new ArrayList<>();
        boolean auto = connection.getAutoCommit();
        try {
            if (auto) connection.setAutoCommit(false);
            for (Overview place : places()) {
                try {
                    // subfamilies too (2026-11-04: North America showed 15 stored, its Curated list 11)
                    store(place, listRows(place, Rank.GENUS), listRows(place, Rank.SPECIES), listRows(place, Rank.SUBFAMILY));
                    if (++done % 100 == 0) connection.commit();
                } catch (SQLException | RuntimeException e) {
                    if (failed.size() < 5) failed.add(place.getName() + " (" + e.getMessage() + ")");
                    s_log.warn("fillAll() " + place.getName() + " e:" + e);
                }
            }
            connection.commit();
        } finally {
            if (auto) connection.setAutoCommit(true);
        }
        // none counted: FAILED, so the morning check reports it (2026-11-05: every place failed and the line read "0 places")
        return LOG + ": " + (done == 0 && !failed.isEmpty() ? "FAILED, " : "") + done + " places in " + (System.currentTimeMillis() - start) / 1000 + " s"
            + (failed.isEmpty() ? "" : "; not counted: " + String.join(", ", failed));
    }
}

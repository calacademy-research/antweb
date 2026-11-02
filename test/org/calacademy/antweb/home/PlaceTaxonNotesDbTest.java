package test.org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.ListView;
import org.calacademy.antweb.PlaceTaxonNotes;
import org.calacademy.antweb.StatusSet;
import org.calacademy.antweb.Project;
import org.calacademy.antweb.geolocale.Bioregion;
import org.calacademy.antweb.geolocale.Country;
import org.calacademy.antweb.geolocale.Subregion;
import org.calacademy.antweb.home.GeolocaleTaxonDb;

import test.org.calacademy.antweb.util.NumbersTestDb;

import static test.org.calacademy.antweb.util.NumbersTestDb.*;

/**
 * Specimens identified only to genus in places (Brian Fisher, 2026-10-29), against the NumbersTestDb database:
 *
 *   genus rows   the country and adm1 lists get the genus row of every specimen identified to genus, of any counted
 *                status (GeolocaleTaxonDb.GENUS_ROWS_FROM_SPECIMENS_SQL, nightly set 2): "Carebara (indet)" collected in
 *                Mozambique puts Carebara on its list; the indet name itself, names not identified to genus, red-flagged
 *                specimens and valid names (added by the query before it) are not rows of this query
 *   indet line   N and M of "Not identified to species: Carebara (indet), N specimens (M imaged)" in a country, a
 *                subregion (its countries), a bioregion and all of AntWeb; a label image does not make a specimen imaged
 *   notice       whether a taxon has a specimen in the place, and the bioregions whose list has it
 *   views        (2026-11-02, ListView) the names of Valid, Vetted and All specimen names in a place; a vetted
 *                morphospecies follows its specimens; disputes; the genus rows' Species, Other specimens and which
 *                rows each view shows; the genus page's line; the place's specimens by name (red flags never count)
 *
 * Runs in CI against MySQL 8.4 (host "mysql"); ANTWEB_TEST_DB_URL overrides.
 */
public class PlaceTaxonNotesDbTest extends TestCase {

    private Connection connection;

    static final String CAREBARA = "myrmicinaecarebara";
    static final String CAREBARA_INDET = "myrmicinaecarebara (indet)";
    static final String NOT_TO_GENUS = "(indet)(myrmicinae) (indet)";

    @Override
    protected void setUp() throws Exception {
        connection = connect();
        fill(connection);
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily, genus, species) values"
            + " ('" + CAREBARA + "', 'genus', 'valid', '" + MYRM + "', 'formicidae', '" + MYRM + "', 'carebara', null),"
            + " ('" + CAREBARA_INDET + "', 'species', 'indetermined', '" + CAREBARA + "', 'formicidae', '" + MYRM + "', 'carebara', '(indet)'),"
            + " ('" + NOT_TO_GENUS + "', 'species', 'indetermined', null, 'formicidae', '" + MYRM + "', '(myrmicinae)', '(indet)')");
        exec("update taxon set genus = 'pheidole' where taxon_name like 'myrmicinaepheidole%'");
        exec("update taxon set genus = 'hypoponera' where taxon_name like 'ponerinaehypoponera%'");
        // Carebara (indet): two in Mozambique (Cabo Delgado; one with an image of the ant, one with a label image only),
        // one in Madagascar, one red-flagged in Mozambique; a specimen not identified to genus in Mozambique.
        specimen("moz1", CAREBARA_INDET, "indetermined", "Mozambique", "Cabo Delgado", "Afrotropical", null);
        specimen("moz2", CAREBARA_INDET, "indetermined", "Mozambique", "Cabo Delgado", "Afrotropical", null);
        specimen("mad1", CAREBARA_INDET, "indetermined", "Madagascar", null, "Malagasy", null);
        specimen("mozred", CAREBARA_INDET, "indetermined", "Mozambique", null, "Afrotropical", "red");
        specimen("moz9", NOT_TO_GENUS, "indetermined", "Mozambique", null, "Afrotropical", null);
        exec("insert into image (id, image_of_id, shot_type) values (101, 'moz1', 'h'), (102, 'moz2', 'l')");
        exec("insert into geolocale (id, name, georank, parent, is_valid, is_live) values (40, 'Mozambique', 'country', 'Southern Africa', 1, 1)");
        exec("insert into bioregion_taxon (bioregion_name, taxon_name, source) values ('Malagasy', '" + CAREBARA + "', 'specimen'),"
            + " ('Afrotropical', '" + CAREBARA + "', 'specimen')");
    }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) connection.close();
    }

    private void exec(String sql) throws Exception {
        NumbersTestDb.exec(connection, sql);
    }

    private void specimen(String code, String name, String status, String country, String adm1, String bioregion, String flag) throws Exception {
        exec("insert into specimen (code, taxon_name, status, country, adm1, bioregion, family, subfamily, genus, flag) values ('" + code + "', '"
            + name + "', '" + status + "', '" + country + "', " + (adm1 == null ? "null" : "'" + adm1 + "'") + ", '" + bioregion
            + "', 'formicidae', '" + MYRM + "', 'carebara', " + (flag == null ? "null" : "'" + flag + "'") + ")");
    }

    private List<String> rows(String sql) throws Exception {
        List<String> out = new ArrayList<>();
        try (PreparedStatement s = connection.prepareStatement(sql); ResultSet r = s.executeQuery()) {
            while (r.next()) out.add(r.getString(1) + "|" + r.getString(2) + "|" + r.getString(3));
        }
        return out;
    }

    public void testGenusRowsComeFromSpecimensIdentifiedToGenus() throws Exception {
        List<String> rows = rows(GeolocaleTaxonDb.GENUS_ROWS_FROM_SPECIMENS_SQL + " order by country, adm1, taxon_name");
        assertTrue(rows.toString(), rows.contains("Mozambique|Cabo Delgado|" + CAREBARA));
        assertTrue(rows.toString(), rows.contains("Madagascar|null|" + CAREBARA));
        // the morphospecies pheidole b (Madagascar) puts its genus on the list too; valid names come from the query before
        assertTrue(rows.toString(), rows.contains("Madagascar|null|" + PHEIDOLE));
        assertFalse("the red-flagged Mozambique specimen without adm1 adds no row", rows.contains("Mozambique|null|" + CAREBARA));
        for (String r : rows) {
            assertFalse("never the indet name itself: " + r, r.endsWith(CAREBARA_INDET));
            assertFalse("names not identified to genus make no row: " + r, r.contains("(myrmicinae)") || r.endsWith("|null"));
            assertFalse("only genus rows: " + r, r.endsWith(HYPO));   // hypoponera b is valid: added by the valid-name query
        }
    }

    public void testIndetLineCountsThePlacesSpecimens() throws Exception {
        Country moz = new Country();
        moz.setId(40);
        moz.setName("Mozambique");
        int[] nm = PlaceTaxonNotes.countIndet(connection, CAREBARA, moz);
        assertEquals("Mozambique: 3 specimens (the line follows the indet page, which lists red-flagged ones too)", 3, nm[0]);
        assertEquals("imaged: the image of the ant, not the label", 1, nm[1]);
        int[] all = PlaceTaxonNotes.countIndet(connection, CAREBARA, new Project(Project.ALLANTWEBANTS));
        assertEquals(4, all[0]);
        assertEquals(1, all[1]);
        Bioregion malagasy = new Bioregion();
        malagasy.setName("Malagasy");
        assertEquals(1, PlaceTaxonNotes.countIndet(connection, CAREBARA, malagasy)[0]);
        Subregion indianOcean = new Subregion();
        indianOcean.setId(INDIAN_OCEAN);
        indianOcean.setName("Indian Ocean");
        assertEquals("a subregion: the specimens of its countries (Madagascar)", 1, PlaceTaxonNotes.countIndet(connection, CAREBARA, indianOcean)[0]);
        assertNull("a curated project selects no specimens", PlaceTaxonNotes.countIndet(connection, CAREBARA, new Project("madants")));
        assertEquals(0, PlaceTaxonNotes.countIndet(connection, HYPO, moz)[0]);
    }

    public void testNoticeQueries() throws Exception {
        Country moz = new Country();
        moz.setId(40);
        moz.setName("Mozambique");
        assertTrue("a genus with only indet specimens in Mozambique is recorded there", hasSpecimen(CAREBARA, moz));
        assertFalse(hasSpecimen(PHEIDOLE, moz));
        Country mad = new Country();
        mad.setId(MADAGASCAR);
        mad.setName("Madagascar");
        assertTrue("a morphospecies collected in Madagascar is recorded there", hasSpecimen(PB, mad));
        assertTrue("a species with a subspecies collected there", hasSpecimen(PA, mad));
        assertFalse("pheidole a is not pheidole ab", hasSpecimen("myrmicinaepheidole a", moz));
        List<String> bio = new ArrayList<>();
        try (PreparedStatement s = connection.prepareStatement(PlaceTaxonNotes.BIOREGIONS_SQL)) {
            s.setString(1, CAREBARA);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) bio.add(r.getString(1));
            }
        }
        assertEquals("[Afrotropical, Malagasy]", bio.toString());
    }

    private boolean hasSpecimen(String name, org.calacademy.antweb.Overview place) throws Exception {
        try (PreparedStatement s = connection.prepareStatement(PlaceTaxonNotes.placeSpecimenSql(PlaceTaxonNotes.placeSpecimenCriteria(place)))) {
            s.setString(1, name);
            s.setString(2, PlaceTaxonNotes.childrenPattern(name));
            try (ResultSet r = s.executeQuery()) {
                return r.next();
            }
        }
    }

    // ---------------------------------------------- specimens collected here not on the species list (2026-10-30)

    static final String CAREBARA_MG01 = "myrmicinaecarebara mg01";
    static final String PHEIDOLE_ZZ = "myrmicinaepheidole zz";

    /**
     * Madagascar's list has Pheidole a, b (morphospecies), a x and Hypoponera b. Collected in Madagascar and not species
     * of the list: Carebara (indet) (1), the morphospecies Carebara mg01 (2, a third red-flagged), the unrecognized
     * Pheidole zz (1). Pheidole b, a morphospecies on the list, is not one of them.
     */
    private void otherNamesFixture() throws Exception {
        exec("alter table taxon add column subspecies varchar(64)");   // as the live table
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily, genus, species) values"
            + " ('" + CAREBARA_MG01 + "', 'species', 'morphotaxon', '" + CAREBARA + "', 'formicidae', '" + MYRM + "', 'carebara', 'mg01'),"
            + " ('" + PHEIDOLE_ZZ + "', 'species', 'unrecognized', '" + PHEIDOLE + "', 'formicidae', '" + MYRM + "', 'pheidole', 'zz')");
        exec("update taxon set species = 'a' where taxon_name = '" + PA + "'");
        exec("update taxon set species = 'b' where taxon_name = '" + PB + "'");
        exec("update specimen set genus = 'pheidole' where taxon_name like 'myrmicinaepheidole%'");
        specimen("mg1", CAREBARA_MG01, "morphotaxon", "Madagascar", null, "Malagasy", null);
        specimen("mg2", CAREBARA_MG01, "morphotaxon", "Madagascar", null, "Malagasy", null);
        specimen("mgred", CAREBARA_MG01, "morphotaxon", "Madagascar", null, "Malagasy", "red");
        exec("insert into specimen (code, taxon_name, status, country, bioregion, family, subfamily, genus) values"
            + " ('zz1', '" + PHEIDOLE_ZZ + "', 'unrecognized', 'Madagascar', 'Malagasy', 'formicidae', '" + MYRM + "', 'pheidole')");
    }

    private static Country madagascar() {
        Country mad = new Country();
        mad.setId(MADAGASCAR);
        mad.setName("Madagascar");
        return mad;
    }

    private static Country mozambique() {
        Country moz = new Country();
        moz.setId(40);
        moz.setName("Mozambique");
        return moz;
    }

    private List<String> names(String sql) throws Exception {
        List<String> out = new ArrayList<>();
        try (PreparedStatement s = connection.prepareStatement(sql); ResultSet r = s.executeQuery()) {
            while (r.next()) out.add(r.getString(1));
        }
        java.util.Collections.sort(out);
        return out;
    }

    /** The species and subspecies names of a view in a place (the list's own query and its specimen half). */
    private List<String> viewNames(Country place, String choice) throws Exception {
        return names(ListView.viewNamesSql(place, StatusSet.listStatusSet(choice), null, null));
    }

    /**
     * The views of 2026-11-02 (ListView). Valid: the list's valid names. Vetted: and the morphospecies on a curated
     * entry of any list (a curator's source in geolocale_taxon or proj_taxon; not a proxy row, not the system lists).
     * All specimen names: every counted name, on the list or collected here.
     */
    public void testViewsOfMadagascar() throws Exception {
        otherNamesFixture();
        Country mad = madagascar();
        assertEquals("[" + PA + ", " + PAX + "]", viewNames(mad, "valid").toString().replace(", " + HB, "").replace(HB + ", ", ""));
        assertFalse("Pheidole b, a morphospecies on no curated entry, is not vetted", viewNames(mad, "vetted").contains(PB));
        exec("update geolocale_taxon set source = 'proxyspeciesListTool' where geolocale_id = " + INDIAN_OCEAN + " and taxon_name = '" + PB + "'");
        assertFalse("a proxy row of a parent region is not a curator's entry", viewNames(mad, "vetted").contains(PB));
        exec("update proj_taxon set source = 'curator' where project_name = 'allantwebants' and taxon_name = '" + PB + "'");
        assertFalse("nor an entry of a system list", viewNames(mad, "vetted").contains(PB));
        exec("update proj_taxon set source = 'speciesListTool' where project_name = 'madants' and taxon_name = '" + PB + "'");
        assertTrue("on a curated project list: vetted, on Madagascar's list too", viewNames(mad, "vetted").contains(PB));
        exec("update proj_taxon set source = null where project_name = 'madants'");
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, source) values (40, '" + PB + "', 'curator')");
        assertTrue("curated in Mozambique: vetted everywhere", viewNames(mad, "vetted").contains(PB));
        List<String> all = viewNames(mad, "all");
        assertTrue(all.toString(), all.contains(PB) && all.contains(PA) && all.contains(CAREBARA_MG01) && all.contains(CAREBARA_INDET)
            && all.contains(PHEIDOLE_ZZ));
        assertFalse("never a name not identified to genus", all.contains(NOT_TO_GENUS));
    }

    /**
     * A vetted morphospecies is in the Vetted view of every country where its specimens (not red-flagged) were collected,
     * though not on its list; a curator who removed it from one place's list (a dispute) takes it off that place only.
     */
    public void testVettedMorphospeciesFollowsItsSpecimens() throws Exception {
        otherNamesFixture();
        exec("update specimen set country = 'Mozambique' where code in ('mg1', 'mgred')");
        Country moz = mozambique();
        assertEquals("not vetted yet: All specimen names only", "[]", viewNames(moz, "vetted").toString());
        assertTrue(viewNames(moz, "all").contains(CAREBARA_MG01));
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, source) values (" + MADAGASCAR + ", '" + CAREBARA_MG01 + "', 'speciesListTool')");
        assertEquals("vetted in Madagascar: in Mozambique's Vetted view too", "[" + CAREBARA_MG01 + "]", viewNames(moz, "vetted").toString());
        assertTrue("and Madagascar's", viewNames(madagascar(), "vetted").contains(CAREBARA_MG01));
        exec("update specimen set flag = 'red' where code = 'mg1'");
        assertEquals("only red-flagged specimens in Mozambique: not there", "[]", viewNames(moz, "vetted").toString());
        assertFalse("red flags never count, in any view", viewNames(moz, "all").contains(CAREBARA_MG01));
        exec("update specimen set flag = null where code = 'mg1'");
        exec("insert into geolocale_taxon_dispute (geolocale_id, taxon_name, source, curator_id) values (40, '" + CAREBARA_MG01 + "', 'speciesListTool', 5)");
        assertEquals("disputed in Mozambique: off Mozambique", "[]", viewNames(moz, "vetted").toString());
        assertTrue("still in Madagascar", viewNames(madagascar(), "vetted").contains(CAREBARA_MG01));
    }

    /**
     * The genus rows of each view (ListView.GenusRows, three queries): Species in the view, Other specimens (the
     * specimens collected here under names not in the view, red-flagged left out) and which rows Valid and Vetted show.
     */
    public void testGenusRowsOfEachView() throws Exception {
        otherNamesFixture();
        Country mad = madagascar();
        ListView.GenusRows valid = ListView.loadGenusRows(connection, mad, "valid", null);
        assertEquals("Pheidole a, a x", 2, valid.species(MYRM, "pheidole"));
        assertEquals(0, valid.species(MYRM, "carebara"));
        assertEquals("Carebara (indet) 1 + mg01 2; the red-flagged one left out", 3, valid.other(MYRM, "carebara").specimens);
        assertEquals("Pheidole b (a morphospecies not vetted) and zz", 2, valid.other(MYRM, "pheidole").specimens);
        assertNull("Hypoponera: every specimen under a name of the view", valid.other(PON, "hypoponera"));
        assertTrue(valid.hasSpecimens(MYRM, "carebara"));
        ListView.GenusRows all = ListView.loadGenusRows(connection, mad, "all", null);
        assertEquals("All specimen names: Pheidole a, b, a x, zz", 4, all.species(MYRM, "pheidole"));
        assertNull("its names are in the view", all.other(MYRM, "carebara"));
        // Which rows: Carebara is known here from a morphospecies (mg01): in Valid with no species
        org.calacademy.antweb.Genus carebara = new org.calacademy.antweb.Genus();
        carebara.setSubfamily(MYRM);
        carebara.setGenus("carebara");
        carebara.setStatus("valid");
        assertTrue(valid.keep(carebara));
        // only specimens identified to genus: All specimen names only
        exec("update specimen set taxon_name = '" + CAREBARA_INDET + "', status = 'indetermined' where code in ('mg1', 'mg2')");
        assertFalse(ListView.loadGenusRows(connection, mad, "valid", null).keep(carebara));
        assertFalse(ListView.loadGenusRows(connection, mad, "vetted", null).keep(carebara));
        assertTrue(ListView.loadGenusRows(connection, mad, "all", null).keep(carebara));
        // one subfamily (browse.do of a subfamily): its genera only
        ListView.GenusRows pon = ListView.loadGenusRows(connection, mad, "valid", PON);
        assertEquals(1, pon.species(PON, "hypoponera"));
        assertEquals(0, pon.species(MYRM, "pheidole"));
    }

    /**
     * Speed (2026-11-02): a genus list reads the place's specimens once and gives their names to the view queries
     * (ListView.namedIn) instead of a subquery per query; the rows are the same either way, in every view.
     */
    public void testNamesReadOnceGiveTheSameRows() throws Exception {
        otherNamesFixture();
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, source) values (" + MADAGASCAR + ", '" + CAREBARA_MG01 + "', 'speciesListTool')");
        exec("update specimen set country = 'Mozambique' where code = 'mg1'");
        for (Country place : new Country[] {madagascar(), mozambique()}) {
            ListView.PlaceSpecimens ps = ListView.loadPlaceSpecimens(connection, place, null, null);
            for (String choice : StatusSet.LIST_CHOICES) {
                StatusSet set = StatusSet.listStatusSet(choice);
                assertEquals(place.getName() + " " + choice, names(ListView.viewNamesSql(place, set, null, null)),
                    names(ListView.viewNamesSql(place, set, null, null, ps.taxonNames())));
                for (String rank : new String[] {"genus", "subfamily"}) {
                    String sub = org.calacademy.antweb.TaxaPage.viewQuery(place, rank, true, set, "taxon.subfamily", "");
                    String named = org.calacademy.antweb.TaxaPage.viewQuery(place, rank, true, set, "taxon.subfamily", "", ps.taxonNames());
                    assertEquals(place.getName() + " " + choice + " " + rank, names(sub), names(named));
                }
                ListView.GenusRows once = ListView.loadGenusRows(connection, place, choice, null, ps);
                ListView.GenusRows again = ListView.loadGenusRows(connection, place, choice, null);
                for (String g : new String[] {"carebara", "pheidole"}) {
                    assertEquals(choice + " " + g, again.species(MYRM, g), once.species(MYRM, g));
                    PlaceTaxonNotes.OtherNames.Genus a = again.other(MYRM, g), b = once.other(MYRM, g);
                    assertEquals(choice + " " + g, a == null ? 0 : a.specimens, b == null ? 0 : b.specimens);
                }
            }
        }
        assertTrue(names(ListView.viewNamesSql(mozambique(), StatusSet.listStatusSet("vetted"), null, null,
            ListView.loadPlaceSpecimens(connection, mozambique(), null, null).taxonNames())).contains(CAREBARA_MG01));
    }

    /** The genus page's line (Valid and Vetted only) and the place's specimens by name (source icons). */
    public void testGenusPageLineAndPlaceSpecimens() throws Exception {
        otherNamesFixture();
        Country mad = madagascar();
        assertEquals(3, ListView.genusOtherNames(connection, mad, "vetted", MYRM, "carebara").specimens);
        assertNull("All specimen names: the names are on the list", ListView.genusOtherNames(connection, mad, "all", MYRM, "carebara"));
        assertNull("all of AntWeb: no place specimens", ListView.genusOtherNames(connection, new Project(Project.ALLANTWEBANTS), "valid", MYRM, "carebara"));
        ListView.PlaceSpecimens ps = ListView.loadPlaceSpecimens(connection, mad, null, null);
        assertEquals("the red-flagged one never counts", 2, ps.count(CAREBARA_MG01));
        assertTrue(ps.has(PA));
        // species lists read only the names whose icon or number needs it
        ListView.PlaceSpecimens some = ListView.loadPlaceSpecimens(connection, mad, null, null, java.util.Arrays.asList(CAREBARA_MG01, "nothing"));
        assertNotNull(some);
        assertEquals(2, some.count(CAREBARA_MG01));
        assertFalse(some.has(PA));
        assertEquals("Mozambique: its two Carebara (indet)", 2, ListView.loadPlaceSpecimens(connection, mozambique(), null, null).count(CAREBARA_INDET));
    }
}

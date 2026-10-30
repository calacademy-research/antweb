package test.org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.PlaceTaxonNotes;
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

    public void testOtherNamesByGenusInOneQuery() throws Exception {
        otherNamesFixture();
        PlaceTaxonNotes.OtherNames o = PlaceTaxonNotes.loadOtherNames(connection, madagascar(), null, null);
        PlaceTaxonNotes.OtherNames.Genus carebara = o.genus(MYRM, "carebara");
        assertNotNull("Carebara, with no species on the list, has specimens collected here", carebara);
        assertEquals("Carebara (indet) 1 + mg01 2; the red-flagged one left out", 3, carebara.specimens);
        List<String> names = new ArrayList<>();
        for (String[] n : carebara.names) names.add(n[0] + "=" + n[4]);
        assertEquals("[" + CAREBARA_INDET + "=1, " + CAREBARA_MG01 + "=2]", names.toString());
        PlaceTaxonNotes.OtherNames.Genus pheidole = o.genus(MYRM, "pheidole");
        assertNotNull("Pheidole has species on the list and one specimen of another name", pheidole);
        assertEquals("only Pheidole zz: valid a, a x and the morphospecies b are on the list", 1, pheidole.specimens);
        assertEquals(PHEIDOLE_ZZ, pheidole.names.get(0)[0]);
        assertNull("Hypoponera: every specimen under a name on the list", o.genus(PON, "hypoponera"));
        // Mozambique: no list rows; its two Carebara (indet), not the red-flagged one nor the one not identified to genus
        Country moz = new Country();
        moz.setId(40);
        moz.setName("Mozambique");
        PlaceTaxonNotes.OtherNames m = PlaceTaxonNotes.loadOtherNames(connection, moz, null, null);
        assertEquals(2, m.genus(MYRM, "carebara").specimens);
        assertEquals("one genus only", 1, m.genera().size());
        assertNull("all of AntWeb: no place list", PlaceTaxonNotes.loadOtherNames(connection, new Project(Project.ALLANTWEBANTS), null, null));
    }

    public void testOtherNamesOfOneGenusOrSubfamily() throws Exception {
        otherNamesFixture();
        PlaceTaxonNotes.OtherNames one = PlaceTaxonNotes.loadOtherNames(connection, madagascar(), MYRM, "carebara");
        assertEquals("the genus page reads its own genus", 1, one.genera().size());
        assertEquals(3, one.genus(MYRM, "carebara").specimens);
        PlaceTaxonNotes.OtherNames sub = PlaceTaxonNotes.loadOtherNames(connection, madagascar(), MYRM, null);
        assertEquals("a subfamily's browse page: its genera", 2, sub.genera().size());
        assertTrue(PlaceTaxonNotes.loadOtherNames(connection, madagascar(), PON, null).isEmpty());
    }
}

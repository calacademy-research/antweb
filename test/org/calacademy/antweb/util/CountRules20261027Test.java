package test.org.calacademy.antweb.util;

import java.util.*;

import junit.framework.TestCase;

import org.calacademy.antweb.Caste;
import org.calacademy.antweb.Museum;
import org.calacademy.antweb.Species;
import org.calacademy.antweb.Specimen;
import org.calacademy.antweb.SpecimenImage;
import org.calacademy.antweb.Taxon;
import org.calacademy.antweb.home.OrphansDb;
import org.calacademy.antweb.home.PlaceTotals;
import org.calacademy.antweb.util.MuseumMgr;
import org.calacademy.antweb.util.NamedQuery;
import org.calacademy.antweb.util.Queries;

/**
 * Counting rules decided by Brian Fisher on 2026-10-27 (docs/numbers.md B67 to B73), without a database:
 * the rules are written once and every user of a number takes it from there. The database behaviour of the same
 * queries is covered by PlaceTotalsDbTest, CountCheckTest and NumberChecksTest (CI, MySQL 8.4) and end-to-end on
 * staging (test_numbers_lists.py, test_numbers_species.py, test_home_and_stats.py, test_named_queries.py).
 */
public class CountRules20261027Test extends TestCase {

    // ---------------------------------------------------------------- B67 project Subfamilies

    public void testCuratedProjectSubfamiliesCountEveryStatusAsTheirListRows() {
        String c = PlaceTotals.listedCondition(PlaceTotals.PROJECT, PlaceTotals.SUBFAMILY);
        assertTrue(c, c.contains("t.taxarank = 'subfamily'"));
        assertTrue("worldants and allantwebants keep the counted statuses: " + c,
            c.contains("or l.project_name not in ('worldants', 'allantwebants')"));
        assertTrue("ants only: " + c, c.endsWith(PlaceTotals.ANTS));
        // every other number keeps its rule
        for (String type : PlaceTotals.TYPES) {
            for (String rank : new String[] {PlaceTotals.GENUS, PlaceTotals.SPECIES}) {
                assertEquals(type + " " + rank, PlaceTotals.rankCondition(rank), PlaceTotals.listedCondition(type, rank));
            }
            if (!PlaceTotals.PROJECT.equals(type)) {
                assertEquals(type, PlaceTotals.rankCondition(PlaceTotals.SUBFAMILY), PlaceTotals.listedCondition(type, PlaceTotals.SUBFAMILY));
            }
        }
        // the nightly run (one place, all places) and the count check use the same condition
        assertTrue(PlaceTotals.listedCountSql(PlaceTotals.PROJECT, PlaceTotals.SUBFAMILY).contains(c));
        assertTrue(PlaceTotals.listedCountsByPlaceSql(PlaceTotals.PROJECT, PlaceTotals.SUBFAMILY).contains(c));
    }

    // ---------------------------------------------------------------- B68 orphan genera and subfamilies

    public void testOrphanReportIsNullSafeAndTheDeletesAreOff() throws Exception {
        assertFalse("deletes stay off until the list is reviewed on staging", OrphansDb.ORPHAN_GENUS_SUBFAMILY_DELETES_ON);
        String genera = OrphansDb.getOrphanedGeneraQuery();
        java.lang.reflect.Method m = OrphansDb.class.getDeclaredMethod("getOrphanedSubfamiliesQuery");
        m.setAccessible(true);
        String subfamilies = (String) m.invoke(null);
        for (String q : new String[] {genera, subfamilies}) {
            assertTrue(q, q.contains(OrphansDb.NO_CHILD));
            assertFalse("NOT IN over a column with NULLs finds nothing: " + q, q.contains("not in (select parent_taxon_name"));
        }
        for (String name : new String[] {"orphanGenera", "orphanSubfamilies"}) {
            NamedQuery q = Queries.getNamedQuery(name);
            assertNotNull(name, q);
            assertTrue(name, q.getQuery().contains("not exists (select 1 from taxon c where c.parent_taxon_name = t.taxon_name)"));
            assertFalse(name, q.getQuery().toLowerCase().contains("not in (select parent_taxon_name"));
            assertTrue(name + ": the page says the deletes are off: " + q.getDesc(), q.getDesc().contains("the deletes are off until"));
        }
    }

    // ---------------------------------------------------------------- B69 caste

    private static String caste(String lifeStage) {
        String[] v = Caste.getCasteValues(lifeStage);
        return v[0] + "/" + v[1];
    }

    public void testAlateMaleIsAMale() {
        assertEquals("male/alate", caste("alate male"));
        assertEquals("male/alate", caste("Alate Male"));
        assertEquals("male/alate", caste("alate males"));
        assertEquals("male/ergatoid", caste("ergatoid male"));
        assertEquals("queen/alate/dealate", caste("alate female"));
        assertEquals("queen/alate/dealate", caste("dealate queen"));
        assertEquals("queen/alate/dealate", caste("alate queen"));
        assertEquals("worker/normal", caste("worker"));
        assertEquals("male/alate", caste("male"));
        assertEquals("male/alate", caste("2m"));
    }

    public void testAndAndSlashSplitWithoutAComma() {
        assertEquals("male/alate", caste("male and worker"));
        assertEquals("worker/normal", caste("worker and male"));
        assertEquals("queen/alate/dealate", caste("queen/male"));
        assertEquals("male/alate", caste("male/queen"));
        assertEquals("queen/alate/dealate", caste("microgyne and worker"));
        assertEquals("queen/alate/dealate", caste("dealate queen & 3 workers"));
        // as before: the comma, and "and" or "/" before a comma
        assertEquals("queen/alate/dealate", caste("2 dealate queens, 12 workers"));
        assertEquals("male/alate", caste("male and worker, queen"));
        assertEquals("worker/major/soldier", caste("major/minor workers"));
        assertEquals("other/larva/pupa", caste("larva/pupa"));
        assertEquals("null/null", caste(null));
    }

    // ---------------------------------------------------------------- B70 endemic and introduced lists

    public void testEndemicAndIntroducedListsAndNumbersShareTheStatusRule() {
        for (String field : new String[] {"endemic", "introduced"}) {
            String c = PlaceTotals.flaggedCondition(field);
            assertTrue(c, c.startsWith("l.is_" + field + " = 1 and "));
            // Endemic: valid names only since 2026-10-28 (Brian Fisher; CountRules20261028Test)
            String statuses = "endemic".equals(field) ? PlaceTotals.ENDEMIC_STATUSES : PlaceTotals.COUNTABLE;
            assertTrue("counted statuses, so no synonyms: " + c, c.contains("ft.status in " + statuses));
            assertFalse(PlaceTotals.COUNTABLE.contains("synonym"));
            for (String type : new String[] {PlaceTotals.GEOLOCALE, PlaceTotals.BIOREGION}) {
                assertTrue("the list: " + type, PlaceTotals.flaggedNamesSql(type, field).contains(c));
                assertTrue("the number: " + type, PlaceTotals.countsFromFlagsSql(type, field).contains(c));
            }
        }
        try {
            PlaceTotals.flaggedCondition("is_endemic = 1 or 1");
            fail("only endemic and introduced");
        } catch (IllegalArgumentException expected) {
        }
    }

    // ---------------------------------------------------------------- B71 label photos

    private static SpecimenImage shot(String type) {
        SpecimenImage i = new SpecimenImage();
        i.setShot(type);
        i.setNumber(1);
        return i;
    }

    private static void setImages(Specimen s, String... shots) throws Exception {
        Hashtable<String, SpecimenImage> images = new Hashtable<>();
        for (String type : shots) images.put(type + "1", shot(type));
        java.lang.reflect.Field f = Taxon.class.getDeclaredField("images");
        f.setAccessible(true);
        f.set(s, images);
    }

    public void testASpecimenWithOnlyLabelPhotosIsNotImaged() throws Exception {
        assertFalse(Specimen.isAntShot("l"));
        assertFalse(Specimen.isAntShot(" L "));
        assertTrue(Specimen.isAntShot("h"));
        assertTrue(Specimen.isAntShot(null));
        Specimen s = new Specimen();
        assertFalse("no images", s.getHasImages());
        setImages(s, "l");
        assertFalse("label only", s.getHasImages());
        setImages(s, "l", "h");
        assertTrue("label and head", s.getHasImages());
        setImages(s, "p");
        assertTrue("profile", s.getHasImages());
        Specimen fromSearch = new Specimen();
        fromSearch.setHasImages(true);   // set from a search result (labels already excluded there)
        assertTrue(fromSearch.getHasImages());
    }

    // ---------------------------------------------------------------- B72 habitat summary

    public void testSpecimensWithoutACollectionCodeAreCollectionEventsOfTheirOwn() {
        String e = Species.COLLECTION_EVENTS;
        assertTrue(e, e.contains("count(distinct nullif(trim(collectioncode), ''))"));
        assertTrue(e, e.contains("sum(nullif(trim(collectioncode), '') is null)"));
    }

    // ---------------------------------------------------------------- B73 collaborating institutions

    public void testInstitutionsAreTheMuseumsListed() {
        assertEquals(0, MuseumMgr.countOf(null));
        List<Museum> museums = new ArrayList<>();
        for (int i = 0; i < 3; i++) museums.add(new Museum());
        assertEquals(3, MuseumMgr.countOf(museums));
    }
}

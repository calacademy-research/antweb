package test.org.calacademy.antweb.util;

import java.util.*;

import junit.framework.TestCase;

import org.calacademy.antweb.util.NamedQuery;
import org.calacademy.antweb.util.Queries;
import org.calacademy.antweb.util.QueriesWithParams;
import org.calacademy.antweb.util.QueryManager;

/**
 * The named queries of the Query Manager (query.do), reviewed one by one on 2026-10-22 (Brian Fisher: "part of
 * your checking of all queries is to make sure they are working and explained"). Without a database:
 * every query has a plain-language explanation and one heading per column of its select, only reads, and
 * every list of queries (Integrity Check, Admin Alerts, Relevant Queries boxes...) names queries that exist.
 * That each query runs on the real database, in time, is tests/e2e/test_named_queries.py (staging).
 */
public class QueriesReviewTest extends TestCase {

    static List<NamedQuery> all() {
        List<NamedQuery> queries = new ArrayList<>(Queries.getNamedQueries());
        queries.add(QueriesWithParams.geolocaleSpeciesListWithRangeData("Madagascar", "country"));
        queries.add(QueriesWithParams.geolocaleSpeciesListRangeSummary("Madagascar", "country"));
        queries.add(QueriesWithParams.bioregionSpeciesListWithRangeData("Malagasy"));
        queries.add(QueriesWithParams.bioregionSpeciesListRangeSummary("Malagasy"));
        return queries;
    }

    public void testEveryQueryIsExplained() {
        for (NamedQuery q : all()) {
            String desc = q.getDesc();
            assertNotNull(q.getName(), desc);
            assertTrue(q.getName() + ": explanation too short: " + desc, desc.trim().length() >= 60);
            assertTrue(q.getName() + ": explanation is a sentence: " + desc, desc.trim().endsWith("."));
            assertFalse(q.getName() + ": plain text, no HTML: " + desc, desc.contains("<"));
            String summary = q.getSummary();
            assertTrue(q.getName() + ": summary: " + summary, summary.length() >= 20 && summary.endsWith("."));
        }
    }

    public void testHeadingsMatchTheColumns() {
        for (NamedQuery q : all()) {
            String[] headings = q.getHeaderArray();
            assertNotNull(q.getName() + ": no headings", headings);
            for (String heading : headings) {
                assertTrue(q.getName() + ": empty heading", heading != null && !heading.trim().isEmpty());
                assertFalse(q.getName() + ": heading is text, not HTML: " + heading, heading.contains("<"));
            }
            int columns = NamedQuery.selectColumnCount(q.getQuery());
            assertEquals(q.getName() + ": " + Arrays.toString(headings) + " / " + q.getQuery(), columns, headings.length);
        }
    }

    public void testEveryQueryOnlyReads() {
        for (NamedQuery q : all()) {
            assertTrue(q.getName(), QueryManager.isReadOnly(q.getQuery()));
            assertFalse(q.getName() + ": no trailing semicolon", q.getQuery().trim().endsWith(";"));
        }
    }

    /**
     * "x not in (select taxon_name from specimen)" finds nothing once one specimen has no name (NOT IN with a
     * NULL), and the current valid name already holds the subfamily (concat(subfamily, current_valid_name) is
     * never a name). Both were in queries until 2026-10-22.
     */
    public void testKnownSqlTrapsStayOut() {
        for (NamedQuery q : all()) {
            String sql = q.getQuery().toLowerCase().replaceAll("\\s+", " ");
            assertFalse(q.getName() + ": NOT IN over a column that can be null: " + sql,
                sql.matches(".*not in \\(select (distinct )?taxon_name from specimen\\).*"));
            assertFalse(q.getName() + ": " + sql, sql.contains("concat(subfamily, current_valid_name)"));
            assertFalse(q.getName() + ": AntCat names have source worldants: " + sql, sql.contains("'worldants.txt'"));
        }
    }

    public void testNamesAreUnique() {
        Set<String> seen = new HashSet<>();
        for (NamedQuery q : all()) assertTrue("defined twice: " + q.getName(), seen.add(q.getName()));
    }

    /** A name in a list that has no query was silently skipped (noFamilyForFamily, distinctMales ...). */
    public void testListedQueriesExist() {
        List<String> names = new ArrayList<>();
        names.addAll(Arrays.asList(Queries.getIntegrityNames()));
        names.addAll(Arrays.asList(Queries.getDevIntegrityNames()));
        names.addAll(Arrays.asList(Queries.getAdminCheckNames()));
        names.addAll(Arrays.asList(Queries.getCuriousNames()));
        names.addAll(Arrays.asList(Queries.getBatteryNames()));
        names.addAll(Queries.getGeolocaleQueries());
        names.addAll(Queries.getLoginQueries());
        names.addAll(Queries.getGroupQueries());
        List<String> missing = new ArrayList<>();
        for (String name : names) if (Queries.getNamedQuery(name) == null) missing.add(name);
        assertTrue("listed queries that do not exist: " + missing, missing.isEmpty());
        for (NamedQuery q : Queries.getNamedQueries()) {
            if (q.getDetailQuery() != null) assertNotNull(q.getName() + " detail", Queries.getNamedQuery(q.getDetailQuery()));
        }
        assertEquals(Queries.getIntegrityNames().length, Queries.getIntegrityQueries().size());
    }

    /** Removed 2026-10-22 as duplicates of another query, broken beyond repair or comparing with data frozen long ago. */
    public void testRemovedQueriesStayRemoved() {
        String[] removed = {"brokenTaxaHierarchy", "badSpecies", "longCurrentValidNames", "generaInMultipleSubfamilies2",
            "antwebUniqueValidTaxa", "antwebUniqueFossilTaxa", "sourceWithBadParent", "sourceWithBadParentDetail1",
            "sourceWithBadParentDetail2", "oldBadBioregionsInSpecimen", "multiBioregionSpecimenTaxa",
            "validNonfossilSpeciesWithMuseums", "validNonfossilTaxaWithBioregions", "extentAndCoords",
            "notValidTaxaFromSpeciesList", "incorrectNameInSpeciesList", "geolocaleListMorphotaxa",
            "notValidGeolocaleTaxonNames", "ancillaryFiles"};
        for (String name : removed) assertNull(name, Queries.getNamedQuery(name));
    }

    /** lastSpecimenUpload's "Last Upload" was the newest specimen record's date; both dates are shown now, labeled. */
    public void testLastSpecimenUploadShowsBothDates() {
        NamedQuery q = Queries.getNamedQuery("lastSpecimenUpload");
        List<String> headings = Arrays.asList(q.getHeaderArray());
        assertTrue(headings.toString(), headings.contains("Last Upload (upload table)"));
        assertTrue(headings.toString(), headings.contains("Newest Specimen Record"));
        assertFalse(headings.toString(), headings.contains("Last Upload"));
        assertTrue(q.getQuery(), q.getQuery().contains(" from upload "));
    }

    /** The permalink of a query with a place uses the name pages link to. */
    public void testPlaceQueriesUseTheirLinkedName() {
        assertEquals("geolocaleSpeciesListWithRangeData", QueriesWithParams.geolocaleSpeciesListWithRangeData("X", "country").getName());
        assertEquals("geolocaleSpeciesListRangeSummary", QueriesWithParams.geolocaleSpeciesListRangeSummary("X", "country").getName());
        assertEquals("bioregionSpeciesListWithRangeData", QueriesWithParams.bioregionSpeciesListWithRangeData("X").getName());
        assertEquals("bioregionSpeciesListRangeSummary", QueriesWithParams.bioregionSpeciesListRangeSummary("X").getName());
        assertTrue(QueriesWithParams.geolocaleSpeciesListWithRangeData("O'Higgins", "adm1").getQuery().contains("'O''Higgins'"));
        assertTrue(QueriesWithParams.bioregionSpeciesListWithRangeData("x\\' or 1=1 -- ").getQuery().contains("'x\\\\'' or 1=1 -- '"));
    }

    public void testQueryManagerListsEveryQueryWithItsSummary() {
        String page = Queries.getQueryManagerPage();
        for (NamedQuery q : Queries.getNamedQueries()) {
            String item = "/query.do?name=" + q.getName() + "'>" + q.getName() + "</a> - ";
            int at = page.indexOf(item);
            assertTrue(q.getName(), at >= 0);
            assertEquals(q.getName() + " listed twice", at, page.lastIndexOf(item));
        }
        assertTrue(page.contains(QueryManager.QUERY_TIMEOUT_SECS + " seconds"));
        // tests/e2e/test_named_queries.py skips on a server whose Query Manager lacks this phrase.
        assertTrue(page.contains("none may run longer than " + QueryManager.QUERY_TIMEOUT_SECS + " seconds"));
    }

    public void testGeolocaleBoxExplainsEachQuery() {
        String box = Queries.getQueryList("Geolocale");
        for (String name : Queries.getGeolocaleQueries()) {
            assertTrue(name + ": " + box, box.contains("name=" + name + "'>" + name + "</a> <span class=\"query-help\">"));
        }
    }

    /** Links built in SQL write spaces as %20, so the result page keeps them as links (QueryManager.cell). */
    public void testSqlLinksSurviveTheResultPage() {
        String app = "https://www.antweb.org";
        String value = "<a href='" + app + "/description.do?taxonName=myrmicinaepheidole%20x'>myrmicinaepheidole x</a>";
        assertEquals(value, QueryManager.cell(value, app));
        String spaced = "<a href='" + app + "/description.do?taxonName=myrmicinaepheidole x'>myrmicinaepheidole x</a>";
        assertTrue("an address with a space is shown as text", QueryManager.cell(spaced, app).startsWith("&lt;a "));
        for (NamedQuery q : Queries.getNamedQueries()) {
            if (q.getQuery().contains("taxonName=\", taxon_name") || q.getQuery().contains("taxonName=\", t.taxon_name")) {
                fail(q.getName() + ": link without %20: " + q.getQuery());
            }
        }
    }

    public void testSelectColumnCount() {
        assertEquals(1, NamedQuery.selectColumnCount("select count(*) from taxon"));
        assertEquals(3, NamedQuery.selectColumnCount("select a, concat(b, ', ', c), d from t where x in (1, 2)"));
        assertEquals(2, NamedQuery.selectColumnCount("select 'a,b', \"c, (d\" from t"));
        assertEquals(2, NamedQuery.selectColumnCount("select 'it''s, ok', x from t"));
        assertEquals(2, NamedQuery.selectColumnCount("select (select count(*) from s), y\n from t"));
        assertEquals(4, NamedQuery.selectColumnCount("select a, b, c, d from p union select e, f, g, h from q"));
        assertEquals(1, NamedQuery.selectColumnCount("select fromage from t"));
        assertEquals(2, NamedQuery.selectColumnCount("select a, b from(select 1 a, 2 b) x"));
        assertEquals(2, NamedQuery.selectColumnCount("select `a\\`, b from t"));
        assertEquals(-1, NamedQuery.selectColumnCount("delete from t"));
    }

    public void testSummaryIsTheFirstSentence() {
        assertEquals("Names (one per row, e.g. a.b) here.", NamedQuery.summary("Names (one per row, e.g. a.b) here. Fix: x."));
        assertEquals("No full stop", NamedQuery.summary("No full stop"));
        assertEquals("Names used 1.5 times.", NamedQuery.summary("Names used 1.5 times. More."));
        assertEquals("", NamedQuery.summary(null));
    }
}

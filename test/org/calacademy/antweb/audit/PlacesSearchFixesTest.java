package test.org.calacademy.antweb.audit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.geolocale.AntwikiTaxonCountry;
import org.calacademy.antweb.geolocale.Country;
import org.calacademy.antweb.geolocale.Geolocale;
import org.calacademy.antweb.search.AdvancedSearch;
import org.calacademy.antweb.search.GenericSearch;
import org.calacademy.antweb.search.SearchListAction;

/**
 * Fixes of 2026-10-28 in the place model and the advanced search that need no database: place names are escaped
 * in the links built for pages and quoted in SQL, place sorting keeps its contract, the elevation box is one
 * number, an unknown date comparison matches nothing, and search download files have a random part in their name.
 * Runs in CI (.github/workflows/tests.yaml).
 */
public class PlacesSearchFixesTest extends TestCase {

    private static final String NASTY = "a'\"<b>";

    private static Country country(String name) {
        Country c = new Country();
        c.setName(name);
        c.setGeorank("country");
        return c;
    }

    public void testPlaceLinksEscapeTheName() {
        Country c = country(NASTY);
        for (String html : new String[] {c.getTag(), c.getLink(), c.getThisPageTag()}) {
            assertFalse(html, html.contains("<b>"));
            assertFalse(html, html.contains("a'\""));
            assertTrue(html, html.contains("&lt;b&gt;"));
        }
    }

    public void testThisPageTagLinksToThePageOnce() {
        Country c = country("Madagascar");
        String tag = c.getThisPageTag();
        assertTrue(tag, tag.contains("?name=Madagascar\""));
        assertFalse(tag, tag.contains("MadagascarMadagascar"));
    }

    public void testLocalityQuotesTheName() {
        assertEquals(" country = 'Cote d''Ivoire'", country("Cote d'Ivoire").getLocality());
        assertEquals(" country = 'x\\\\'", country("x\\").getLocality());
    }

    public void testPlacesWithoutANameSortLast() {
        List<Geolocale> list = new ArrayList<>();
        for (int i = 0; i < 40; i++) list.add(country(i % 3 == 0 ? null : "c" + (i % 7)));
        Collections.shuffle(list, new java.util.Random(1));
        Collections.sort(list);
        assertNull(list.get(list.size() - 1).getName());
        assertEquals("c0", list.get(0).getName());   // i = 7, 14, 28, 35 give "c0"
        assertEquals(0, country(null).compareTo(country(null)));
        assertTrue(country("a").compareTo(country(null)) < 0);
        assertTrue(country(null).compareTo(country("a")) > 0);
    }

    public void testAntwikiNamesWithoutANameSortLast() {
        AntwikiTaxonCountry a = new AntwikiTaxonCountry();
        AntwikiTaxonCountry b = new AntwikiTaxonCountry();
        b.setTaxonName("x");
        assertTrue(a.compareTo(b) > 0);
        assertTrue(b.compareTo(a) < 0);
        assertEquals(0, a.compareTo(new AntwikiTaxonCountry()));
    }

    public void testElevationIsOneNumber() {
        assertEquals("1000", AdvancedSearch.elevationValue("1,000"));
        assertEquals("1000", AdvancedSearch.elevationValue(" 1 000 "));
        assertEquals("500", AdvancedSearch.elevationValue("500"));
        assertEquals("-20", AdvancedSearch.elevationValue("-20"));
        assertEquals("abc", AdvancedSearch.elevationValue("abc"));
        assertEquals(Arrays.asList("1000"), GenericSearch.searchEntries("sp.elevation", AdvancedSearch.elevationValue("1,000")));
    }

    /** getDateSearchString is protected: reach it through a subclass. */
    private static class Search extends GenericSearch {
        String date(String searchType, String value) {
            return getDateSearchString("sp.datecollectedstart", searchType, value);
        }
    }

    public void testUnknownDateComparisonMatchesNothing() {
        assertEquals(" 1 = 0 ", new Search().date("foo", "2020-01-01"));
        assertEquals(" 1 = 0 ", new Search().date(null, "2020-01-01"));
        assertTrue(new Search().date("greaterThanOrEqual", "2020-01-01").startsWith("sp.datecollectedstart >= convert("));
    }

    public void testDownloadFileNamesHaveARandomPart() {
        String a = SearchListAction.randomPart();
        String b = SearchListAction.randomPart();
        assertTrue(a, a.matches("[0-9a-f]{32}"));
        assertFalse(a.equals(b));
    }
}

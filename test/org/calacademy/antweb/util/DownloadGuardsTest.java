package test.org.calacademy.antweb.util;

import junit.framework.TestCase;

import org.calacademy.antweb.search.SearchPaging;
import org.calacademy.antweb.util.DownloadLimits;

/**
 * Download guards (Brian Fisher, 2026-10-21): no free bulk downloads of specimen data.
 * DownloadLimits: genus, species and subspecies files only, at most 5,000 records (administrators: no cap).
 * SearchPaging: the advanced search list is 200 specimens a page; only listed sort columns reach SQL.
 */
public class DownloadGuardsTest extends TestCase {

    // --- DownloadLimits ------------------------------------------------------------

    public void testOnlyGenusSpeciesSubspeciesDownload() {
        assertTrue(DownloadLimits.isDownloadRank("genus"));
        assertTrue(DownloadLimits.isDownloadRank("species"));
        assertTrue(DownloadLimits.isDownloadRank("subspecies"));
        assertFalse(DownloadLimits.isDownloadRank("family"));
        assertFalse(DownloadLimits.isDownloadRank("subfamily"));
        assertFalse(DownloadLimits.isDownloadRank("tribe"));
        assertFalse(DownloadLimits.isDownloadRank(null));
        assertEquals("Downloads are available for a genus or species. For larger data sets, contact antweb@antweb.org about API access.",
            DownloadLimits.RANK_REFUSED);
    }

    public void testCapAndMessage() {
        assertEquals(5000, DownloadLimits.MAX_RECORDS);
        assertEquals(5000, DownloadLimits.capFor(false));
        assertEquals(0, DownloadLimits.capFor(true));                // administrators: everything
        assertFalse(DownloadLimits.isCapped(5000, 5000));
        assertTrue(DownloadLimits.isCapped(5001, 5000));
        assertFalse(DownloadLimits.isCapped(141728, 0));
        assertEquals("This download is limited to 5,000 records (35,851 match). For larger data sets, contact antweb@antweb.org about API access.",
            DownloadLimits.capMessage(35851));
    }

    // --- SearchPaging --------------------------------------------------------------

    public void testShowingText() {
        assertEquals("Showing 1–200 of 141,728", SearchPaging.showingText(1, 141728));
        assertEquals("Showing 201–400 of 141,728", SearchPaging.showingText(2, 141728));
        assertEquals("Showing 141,601–141,728 of 141,728", SearchPaging.showingText(709, 141728));
        assertEquals("Showing 1–12 of 12", SearchPaging.showingText(1, 12));
        assertEquals("Showing 0 of 0", SearchPaging.showingText(1, 0));
    }

    public void testPagesAndOffsets() {
        assertEquals(0, SearchPaging.offset(1));
        assertEquals(200, SearchPaging.offset(2));
        assertEquals(709, SearchPaging.lastPage(141728));
        assertEquals(1, SearchPaging.lastPage(0));
        assertEquals(1, SearchPaging.pageFromParam(null));
        assertEquals(1, SearchPaging.pageFromParam("-3"));
        assertEquals(1, SearchPaging.pageFromParam("2 or 1=1"));
        assertEquals(7, SearchPaging.pageFromParam("7"));
        // A page past the end shows the last page.
        SearchPaging p = new SearchPaging(450, 99, null, null, null);
        assertEquals(3, p.getPage());
        assertEquals(400, p.getOffset());
        assertTrue(p.hasPrevious());
        assertFalse(p.hasNext());
        assertEquals("Showing 401–450 of 450", p.getShowingText());
    }

    public void testOrderByOnlyListedColumns() {
        assertEquals("sp.code", SearchPaging.orderBy(null, null));
        assertEquals("sp.code desc", SearchPaging.orderBy("code", "down"));
        assertEquals("sp.code", SearchPaging.orderBy("sp.code; drop table specimen", null));   // unknown: by code
        String byName = SearchPaging.orderBy("taxonname", null);
        assertTrue(byName, byName.startsWith("coalesce(sp.genus, '') = '', sp.genus, "));
        assertTrue(byName, byName.endsWith(", sp.code"));
        assertEquals("imagecount desc, sp.code", SearchPaging.orderBy("images", null));      // most images first, as before
        assertEquals("imagecount, sp.code", SearchPaging.orderBy("images", "down"));
        assertTrue(SearchPaging.orderBy("country", "down").contains("sp.country desc"));
    }

    public void testPageUrlReplacesPage() {
        String q = "searchMethod=advancedSearch&country=Madagascar&page=3&sortBy=code";
        assertEquals("https://x/advancedSearch.do?searchMethod=advancedSearch&country=Madagascar&sortBy=code&page=4",
            SearchPaging.pageUrl("https://x/advancedSearch.do", q, 4));
        assertEquals("https://x/a.do?page=2", SearchPaging.pageUrl("https://x/a.do", null, 2));
        // "pages=" or "pageX=" are other parameters and stay.
        assertEquals("https://x/a.do?pages=1&page=2", SearchPaging.pageUrl("https://x/a.do", "pages=1", 2));
    }
}

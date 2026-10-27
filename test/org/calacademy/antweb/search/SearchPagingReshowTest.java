package test.org.calacademy.antweb.search;

import junit.framework.TestCase;

import org.calacademy.antweb.search.SearchPaging;

/**
 * SearchPaging.reshowUrl (2026-10-27, finding F25): advancedSearchResults.do (the results shown again) sends a
 * paged specimen search back to advancedSearch.do with its query string and page, so the total and Next link
 * are those of the search, not of the 200 rows kept in the session. Map and Compare views (with parameters),
 * sessions without a paged search and searches whose query string is unknown keep the session list.
 */
public class SearchPagingReshowTest extends TestCase {

    private static final String PATH = "/advancedSearch.do";
    private static final String QUERY = "searchMethod=advancedSearch&advanced=true&country=Madagascar&page=3&sortBy=code";

    private static SearchPaging paging(int total, int page, String queryString) {
        SearchPaging p = new SearchPaging(total, page, "code", null, null);
        p.setQueryString(queryString);
        return p;
    }

    public void testPagedSearchGoesBackToTheSearchAtItsPage() {
        assertEquals("/advancedSearch.do?searchMethod=advancedSearch&advanced=true&country=Madagascar&sortBy=code&page=3",
            SearchPaging.reshowUrl(PATH, null, paging(3000, 3, QUERY)));
        assertEquals("/advancedSearch.do?searchMethod=advancedSearch&advanced=true&country=Madagascar&sortBy=code&page=3",
            SearchPaging.reshowUrl(PATH, "", paging(3000, 3, QUERY)));
    }

    public void testPageIsTheOneKeptNotTheOneAskedFor() {
        // A page past the end was kept as the last page.
        assertEquals("/advancedSearch.do?country=Comoros&page=2",
            SearchPaging.reshowUrl(PATH, null, paging(250, 9, "country=Comoros&page=9")));
    }

    public void testRequestWithParametersKeepsTheSessionList() {
        assertNull(SearchPaging.reshowUrl(PATH, "mode=map", paging(3000, 1, QUERY)));
        assertNull(SearchPaging.reshowUrl(PATH, "mode=compare", paging(3000, 1, QUERY)));
        assertNull(SearchPaging.reshowUrl(PATH, "sortBy=code", paging(3000, 1, QUERY)));
    }

    public void testNoPagedSearchKeepsTheSessionList() {
        assertNull(SearchPaging.reshowUrl(PATH, null, null));
        assertNull(SearchPaging.reshowUrl(PATH, null, paging(3000, 1, null)));
        assertNull(SearchPaging.reshowUrl(PATH, null, paging(3000, 1, "")));
    }
}

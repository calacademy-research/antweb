package test.org.calacademy.antweb.util;

import junit.framework.TestCase;

import org.calacademy.antweb.util.HttpUtil;

/**
 * HttpUtil.getAfterQuestionMark (2026-10-26, finding F26): an address without a query, such as
 * advancedSearchResults.do shown again, has an empty query string; it read the first character and the page
 * showed the error page (StringIndexOutOfBoundsException).
 */
public class HttpUtilQueryTest extends TestCase {

    public void testEmptyQueryIsEmpty() {
        assertEquals("", HttpUtil.getAfterQuestionMark(""));
    }

    public void testQueryWithoutItsQuestionMark() {
        assertEquals("genus=mystrium&rank=genus", HttpUtil.getAfterQuestionMark("?genus=mystrium&rank=genus"));
        assertNull(HttpUtil.getAfterQuestionMark(null));
    }
}

package test.org.calacademy.antweb.search;

import java.util.Arrays;

import junit.framework.TestCase;

import org.calacademy.antweb.search.FieldGuideResultsAction;
import org.calacademy.antweb.search.GenericSearch;

/**
 * Search rules decided by Brian Fisher on 2026-10-27 (docs/flows/search.md): a value of several words is one phrase,
 * entries are separated by commas (code boxes: also spaces and line breaks), "not equals" excludes every entry,
 * taxon name "equals" is not a suffix match; field guides from a search use the caste chosen in the search (worker
 * when none). No database. Runs in CI (.github/workflows/tests.yaml).
 */
public class SearchRulesTest extends TestCase {

    /** getSearchString is protected: reach it through a subclass. */
    private static class Search extends GenericSearch {
        String clause(String property, String searchType, String value) {
            return getSearchString(property, searchType, value);
        }
    }

    private static String clause(String property, String searchType, String value) {
        return new Search().clause(property, searchType, value);
    }

    public void testSeveralWordsAreOnePhrase() {
        assertEquals(Arrays.asList("Santa Cruz"), GenericSearch.searchEntries("sp.localityname", "Santa   Cruz"));
        assertEquals("sp.localityname like '%Santa Cruz%'", clause("sp.localityname", "contains", "Santa Cruz"));
        assertEquals("sp.collectedby = 'B. L. Fisher'", clause("sp.collectedby", "equals", "B. L. Fisher"));
    }

    public void testCommasSeparateEntries() {
        assertEquals(Arrays.asList("Santa Cruz", "Bolivia"), GenericSearch.searchEntries("sp.localityname", "Santa Cruz, Bolivia,"));
        assertEquals("sp.museum = 'CASC' or sp.museum = 'MHNG'", clause("sp.museum", "equals", "CASC, MHNG"));
        // Double quotes still make one entry, also with a comma inside.
        assertEquals(Arrays.asList("Ranomafana, Madagascar", "Ifanadiana"),
            GenericSearch.searchEntries("sp.localityname", "\"Ranomafana, Madagascar\", Ifanadiana"));
    }

    public void testCodeBoxesTakeListsSeparatedBySpacesAndLineBreaks() {
        assertEquals(Arrays.asList("casent0001", "casent0002", "casent0003", "casent0004"),
            GenericSearch.searchEntries("sp.code", "casent0001 casent0002\r\ncasent0003,\tcasent0004"));
        assertEquals(2, GenericSearch.searchEntries("sp.localitycode", "BLF001 BLF002").size());
        assertEquals(2, GenericSearch.searchEntries("sp.collectioncode", "BLF001\nBLF002").size());
    }

    public void testNotEqualsExcludesEveryEntry() {
        assertEquals("sp.family != 'Formicidae' and sp.family != 'Apidae'", clause("sp.family", "notEquals", "Formicidae, Apidae"));
        assertEquals("sp.family != 'Formicidae'", clause("sp.family", "notEquals", "Formicidae"));
    }

    public void testTaxonNameEqualsIsNotASuffix() {
        String c = GenericSearch.getTaxonNameEqualsString("equals", "rufa");
        assertEquals("(sp.taxon_name = 'rufa' or sp.taxon_name = concat(sp.subfamily, 'rufa') or sp.taxon_name like '% rufa')", c);
        assertFalse("not a suffix match", c.contains("'%rufa'"));
        assertEquals("(sp.taxon_name = 'formica rufa' or sp.taxon_name = concat(sp.subfamily, 'formica rufa')"
            + " or sp.taxon_name like '% formica rufa')", GenericSearch.getTaxonNameEqualsString("equals", "formica  rufa"));
        // Two names: either; not equals: neither.
        assertTrue(GenericSearch.getTaxonNameEqualsString("equals", "rufa, fusca").contains(") or ("));
        assertTrue(GenericSearch.getTaxonNameEqualsString("notEquals", "rufa, fusca").startsWith("not ("));
        assertTrue(GenericSearch.getTaxonNameEqualsString("notEquals", "rufa, fusca").contains(") and not ("));
    }

    public void testTaxonNameValueIsQuotedAndLikeEscaped() {
        String c = GenericSearch.getTaxonNameEqualsString("equals", "o'x_1%");
        assertTrue(c, c.contains("sp.taxon_name = 'o''x_1%'"));
        assertTrue(c, c.contains("like '% o''x\\\\_1\\\\%'"));
        assertEquals(" 1 = 1 ", GenericSearch.getTaxonNameEqualsString("equals", " , "));
    }

    public void testFieldGuideUsesTheSearchCaste() {
        assertEquals("male", FieldGuideResultsAction.fieldGuideCaste(null, "male"));
        assertEquals("other", FieldGuideResultsAction.fieldGuideCaste(null, "other"));
        assertEquals("worker", FieldGuideResultsAction.fieldGuideCaste(null, null));
        assertEquals("worker", FieldGuideResultsAction.fieldGuideCaste("default", ""));
        assertEquals("queen", FieldGuideResultsAction.fieldGuideCaste("queen", "male"));
    }

    public void testCollectionResultsDoNotUseAnEarlierSearchCaste() {
        assertEquals("male", FieldGuideResultsAction.searchCaste("male", new Object()));
        assertNull(FieldGuideResultsAction.searchCaste("male", new org.calacademy.antweb.Collection()));
        assertNull(FieldGuideResultsAction.searchCaste(null, new Object()));
    }
}

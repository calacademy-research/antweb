package test.org.calacademy.antweb.upload;

import junit.framework.TestCase;

import org.calacademy.antweb.Coordinate;
import org.calacademy.antweb.Specimen;
import org.calacademy.antweb.search.AdvancedSearch;
import org.calacademy.antweb.upload.SpecimenUploadParse;

/**
 * Locality codes are stored as written (2026-10-22), except that a double quote becomes - (2026-10-23). Until then the specimen upload passed every locality code
 * through HttpUtil.encodePath, so production held 1,117 specimens in 67 codes such as Ahi_%3C5m (Ahi_<5m),
 * MontagnedesFran%E7ais_150 and KM55%22%22805, and the links encoded them a second time
 * (locality.do?code=Ahi_%253C5m). Now: the code is stored plain (UploadUtil.cleanCode still removes spaces, %, #
 * and apostrophes), every link encodes it once in UTF-8, and the specimen search for one code takes it whole.
 * docs/flows/specimen-upload.md, "Locality and collection codes".
 */
public class LocalityCodePlainTest extends TestCase {

    private static final Coordinate MADAGASCAR = new Coordinate(47.52f, -18.91f);   // (lon, lat)

    public void testStoredPlain() {
        assertEquals("Ahi_<5m", SpecimenUploadParse.plainLocalityCode("Ahi_<5m", null));
        assertEquals("Nosy_Mangabe_<5", SpecimenUploadParse.plainLocalityCode("Nosy_Mangabe_<5", MADAGASCAR));
        assertEquals("Bequia_Peggy?s_Rock", SpecimenUploadParse.plainLocalityCode("Bequia_Peggy?s_Rock", null));
        // A double quote becomes "-" (Brian Fisher, 2026-10-23): KM55""805 and KM55"805 are the same place.
        assertEquals("KM55-805", SpecimenUploadParse.plainLocalityCode("KM55\"\"805", null));
        assertEquals("KM55-805", SpecimenUploadParse.plainLocalityCode("KM55\"805", null));
        assertEquals("a-b-c", org.calacademy.antweb.upload.UploadUtil.quotesToDash("a\"b\"\"c"));
        assertEquals("MontagnedesFrançais_150", SpecimenUploadParse.plainLocalityCode("Montagne des Français_150", null));
        assertEquals("a>b<c", SpecimenUploadParse.plainLocalityCode("a > b < c", null));
        assertEquals("Peggy’s_Rock", SpecimenUploadParse.plainLocalityCode("Peggy’s_Rock", null));   // was %3F
        assertEquals("ant?_site", SpecimenUploadParse.plainLocalityCode("ant\uD83D\uDC1C_site", null));   // utf8mb3 column: no emoji
        for (String code : new String[] {"Ahi_<5m", "KM55\"\"805", "Français_150", "x?y>z"}) {
            String stored = SpecimenUploadParse.plainLocalityCode(code, MADAGASCAR);
            assertFalse(stored, stored.matches(".*%[0-9A-Fa-f]{2}.*"));
        }
    }

    public void testCleanCodeStillRemovesSpacesPercentHashApostrophe() {
        assertEquals("ab_cd5", SpecimenUploadParse.plainLocalityCode("a'b #_c%d 5", null));
        // A code that was stored encoded and is uploaded again as such loses its % (as before).
        assertEquals("Ahi_3C5m", SpecimenUploadParse.plainLocalityCode("Ahi_%3C5m", null));
    }

    public void testCodeFromCoordinates() {
        assertEquals("loc-18.91,47.52", SpecimenUploadParse.plainLocalityCode(null, MADAGASCAR));
        assertEquals("loc-18.91,47.52", SpecimenUploadParse.plainLocalityCode("Null", MADAGASCAR));
        assertEquals("ab-18.91,47.52", SpecimenUploadParse.plainLocalityCode("a b", MADAGASCAR));
        assertEquals("Ahi_<5m", SpecimenUploadParse.plainLocalityCode("Ahi_<5m", MADAGASCAR));
        assertNull(SpecimenUploadParse.plainLocalityCode(null, null));
        assertEquals("ab", SpecimenUploadParse.plainLocalityCode("ab", null));
    }

    private static Specimen specimen(String localityCode, String localityName) {
        Specimen s = new Specimen();
        s.setCode("casent0000001");
        s.setLocalityCode(localityCode);
        s.setLocalityName(localityName);
        s.setCountry("Madagascar");
        return s;
    }

    /** The specimen page and the specimen lists link locality.do?code= with the code encoded once (UTF-8). */
    public void testLinksEncodeOnce() {
        String[][] cases = {
            {"Ahi_<5m", "Ahi_%3C5m"},
            {"KM55\"\"805", "KM55%22%22805"},
            {"MontagnedesFrançais_150", "MontagnedesFran%C3%A7ais_150"},
            {"Bequia_Peggy?s_Rock", "Bequia_Peggy%3Fs_Rock"},
            {"a>b&c#d", "a%3Eb%26c%23d"},
            {"loc-18.91,47.52", "loc-18.91%2C47.52"},
        };
        for (String[] c : cases) {
            Specimen s = specimen(c[0], "Some place <b>");
            for (String link : new String[] {s.getLocalityLink(), s.getLocalityInfoString(null)}) {
                assertTrue(c[0] + ": " + link, link.contains("/locality.do?code=" + c[1].replace("&", "&amp;") + "\""));
                assertFalse(link, link.matches("(?s).*%25[0-9A-Fa-f]{2}.*"));
                assertTrue(link, link.contains("Some place &lt;b&gt;"));
            }
        }
        // No code: the link uses the name, as before.
        Specimen s = specimen(null, "Ambohidahy");
        assertTrue(s.getLocalityLink(), s.getLocalityLink().contains("/locality.do?name=Ambohidahy\""));
    }

    /**
     * The locality page links its specimens as advancedSearch.do?localityCode="<code>": the code is taken whole
     * (it may hold a double quote or a comma), and a double quote in it stays one character in the SQL.
     */
    public void testDoubleQuoteAllowedOnlyInCodes() {
        // locality.do and collection.do: a quote in the code or name is allowed; anywhere else it is still refused.
        assertFalse(org.calacademy.antweb.util.HttpUtil.queryWithoutCodes("/locality.do", "code=KM55%22%22805").contains("%22"));
        assertFalse(org.calacademy.antweb.util.HttpUtil.queryWithoutCodes("/collection.do", "name=A%22B").contains("%22"));
        assertTrue(org.calacademy.antweb.util.HttpUtil.queryWithoutCodes("/locality.do", "code=x&other=%22").contains("%22"));
        // advancedSearch.do: only the locality and collection code fields.
        String q = "searchMethod=advancedSearch&localityCodeSearchType=equals&localityCode=%22KM55%22%22805%22";
        assertFalse(org.calacademy.antweb.util.HttpUtil.queryWithoutCodes("/advancedSearch.do", q).contains("%22"));
        assertTrue(org.calacademy.antweb.util.HttpUtil.queryWithoutCodes("/advancedSearch.do", "genus=%22x").contains("%22"));
        // Other pages: unchanged.
        assertTrue(org.calacademy.antweb.util.HttpUtil.queryWithoutCodes("/description.do", "code=%22").contains("%22"));
    }

    public void testSpecimenSearchByCode() {
        AdvancedSearch search = new AdvancedSearch();
        assertEquals("sp.localitycode = 'KM55\"\"805'", search.getCodeSearchString("sp.localitycode", "equals", "\"KM55\"\"805\""));
        assertEquals("sp.localitycode = 'loc-18.91,47.52'", search.getCodeSearchString("sp.localitycode", "equals", "\"loc-18.91,47.52\""));
        assertEquals("sp.localitycode = 'Ahi_<5m'", search.getCodeSearchString("sp.localitycode", "equals", "\"Ahi_<5m\""));
        assertEquals("sp.localitycode = 'Français'", search.getCodeSearchString("sp.localitycode", null, "\"Français\""));
        assertEquals("sp.localitycode like '%a''b\\\\c%'", search.getCodeSearchString("sp.localitycode", "contains", "\"a'b\\c\""));
        assertEquals("sp.collectioncode = 'BLF01'", search.getCodeSearchString("sp.collectioncode", "equals", "BLF01"));
        // Every advanced-search text field: an apostrophe doubled, a backslash escaped, a double quote kept as one.
        Search plain = new Search();
        assertEquals("sp.localityname = 'O''Brien\\\\x'", plain.field("sp.localityname", "equals", "O'Brien\\x"));
        assertEquals("sp.habitat like '%5\"%'", plain.field("sp.habitat", "contains", "5\""));
        // Not one quoted code: read as before (two codes).
        assertEquals("sp.localitycode = 'a' or sp.localitycode = 'b'", search.getCodeSearchString("sp.localitycode", "equals", "\"a\" \"b\""));
    }

    /** GenericSearch.getSearchString is protected: reached through a subclass. */
    static class Search extends AdvancedSearch {
        String field(String property, String searchType, String value) {
            return getSearchString(property, searchType, value);
        }
    }
}

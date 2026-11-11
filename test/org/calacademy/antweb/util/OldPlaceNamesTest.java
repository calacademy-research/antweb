package test.org.calacademy.antweb.util;

import java.util.LinkedHashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.apache.commons.collections4.map.MultiKeyMap;
import org.calacademy.antweb.geolocale.Adm1;
import org.calacademy.antweb.geolocale.Country;
import org.calacademy.antweb.util.GeolocaleMgr;
import org.calacademy.antweb.util.OldPlaceNames;

/**
 * An old place name in a link goes to the same address with the current name (2026-11-10; OldPlaceNames,
 * GeolocaleMgr.currentCountryName, currentAdm1Name). AntWeb keeps an old name as a not-valid place whose valid name is
 * the current place (USA: United States; MA: Massachusetts; after the renames of 2026-11, Ivory Coast: Côte d'Ivoire
 * and Turkey: Türkiye). Until now such a link opened the empty page of the not-valid place.
 *
 * Without a database: which names resolve (an old name with a valid name; not a valid name, an unknown name, an old
 * name without a valid name or one that points to a place that is not valid), and the address a request moves to:
 * the parameters looked at on each page, the new names URL-encoded (accent and apostrophe), every other parameter
 * exactly as written, the tools for the place rows left alone. The redirect itself (301): tests/e2e/test_old_place_names.py.
 */
public class OldPlaceNamesTest extends TestCase {

    private final Map<String, Country> countries = new LinkedHashMap<>();
    private final MultiKeyMap<String, Adm1> adm1s = new MultiKeyMap<>();

    private void country(String name, boolean valid, String validName) {
        Country c = new Country();
        c.setName(name);
        c.setGeorank("country");
        c.setIsValid(valid);
        c.setValidName(validName);
        countries.put(name, c);
    }

    private void adm1(String name, String country, boolean valid, String validName) {
        Adm1 a = new Adm1();
        a.setName(name);
        a.setGeorank("adm1");
        a.setParent(country);
        a.setIsValid(valid);
        a.setValidName(validName);
        adm1s.put(name, country, a);
    }

    /** Rows as production has them (2026-11-10), and the countries as they are after their rename. */
    @Override
    protected void setUp() {
        country("United States", true, "none");
        country("USA", false, "United States");
        country("Côte d'Ivoire", true, null);
        country("Ivory Coast", false, "Côte d'Ivoire");
        country("Türkiye", true, null);
        country("Turkey", false, "Türkiye");
        country("Curaçao", true, "none");
        country("Curacao", false, "Curaçao");
        country("Madagascar", true, null);
        country("Bouvet Island", false, null);               // an old name without a valid name
        country("Lao People's Democratic Republic", false, "none");
        country("Old A", false, "Old B");                    // points to a place that is not valid
        country("Old B", false, null);
        country("Self", false, "Self");
        adm1("Massachusetts", "United States", true, null);
        adm1("MA", "United States", false, "Massachusetts");
        adm1("Badakhshān", "Afghanistan", true, null);
        adm1("Badakhshan", "Afghanistan", false, "Badakhshān");
        adm1("Lagunes", "Côte d'Ivoire", true, null);
        adm1("Lagunes District", "Côte d'Ivoire", false, "Lagunes");
        adm1("Lost", "United States", false, "none");
        adm1("Western", "Kenya", false, "Nowhere");
    }

    private final OldPlaceNames.Names names = new OldPlaceNames.Names() {
        public String country(String name) { return GeolocaleMgr.currentCountryName(countries, name); }
        public String adm1(String adm1Name, String countryName) { return GeolocaleMgr.currentAdm1Name(adm1s, adm1Name, countryName); }
    };

    private String moved(String path, String query) {
        return OldPlaceNames.movedQuery(path, query, names);
    }

    public void testAnOldCountryNameWithAValidNameResolves() {
        assertEquals("United States", GeolocaleMgr.currentCountryName(countries, "USA"));
        assertEquals("Côte d'Ivoire", GeolocaleMgr.currentCountryName(countries, "Ivory Coast"));
        assertEquals("Türkiye", GeolocaleMgr.currentCountryName(countries, "Turkey"));
        assertEquals("Curaçao", GeolocaleMgr.currentCountryName(countries, "Curacao"));
    }

    public void testOtherCountryNamesStayAsTheyAre() {
        assertNull("a valid name", GeolocaleMgr.currentCountryName(countries, "United States"));
        assertNull("a valid name", GeolocaleMgr.currentCountryName(countries, "Côte d'Ivoire"));
        assertNull("not known", GeolocaleMgr.currentCountryName(countries, "Atlantis"));
        assertNull("another spelling is another name", GeolocaleMgr.currentCountryName(countries, "usa"));
        assertNull("old, no valid name", GeolocaleMgr.currentCountryName(countries, "Bouvet Island"));
        assertNull("old, valid name the text none", GeolocaleMgr.currentCountryName(countries, "Lao People's Democratic Republic"));
        assertNull("points to a place that is not valid", GeolocaleMgr.currentCountryName(countries, "Old A"));
        assertNull(GeolocaleMgr.currentCountryName(countries, "Self"));
        assertNull(GeolocaleMgr.currentCountryName(countries, ""));
        assertNull(GeolocaleMgr.currentCountryName(countries, null));
        assertNull("before the places are loaded", GeolocaleMgr.currentCountryName(null, "USA"));
    }

    public void testAnOldAdm1NameResolvesInItsCountryOnly() {
        assertEquals("Massachusetts", GeolocaleMgr.currentAdm1Name(adm1s, "MA", "United States"));
        assertEquals("Badakhshān", GeolocaleMgr.currentAdm1Name(adm1s, "Badakhshan", "Afghanistan"));
        assertNull("a valid name", GeolocaleMgr.currentAdm1Name(adm1s, "Massachusetts", "United States"));
        assertNull("another country", GeolocaleMgr.currentAdm1Name(adm1s, "MA", "Madagascar"));
        assertNull("the country by its old name: the country is resolved first", GeolocaleMgr.currentAdm1Name(adm1s, "MA", "USA"));
        assertNull("old, no valid name", GeolocaleMgr.currentAdm1Name(adm1s, "Lost", "United States"));
        assertNull("valid name not a place", GeolocaleMgr.currentAdm1Name(adm1s, "Western", "Kenya"));
        assertNull(GeolocaleMgr.currentAdm1Name(adm1s, "MA", null));
        assertNull(GeolocaleMgr.currentAdm1Name(adm1s, null, "United States"));
        assertNull(GeolocaleMgr.currentAdm1Name(null, "MA", "United States"));
    }

    public void testCountryPageAndListLinksMove() {
        assertEquals("name=United+States", moved("/country.do", "name=USA"));
        assertEquals("rank=genus&countryName=United+States&images=true", moved("/taxonomicPage.do", "rank=genus&countryName=USA&images=true"));
        assertEquals("searchMethod=advancedSearch&advanced=true&country=United+States&resultRank=specimen",
            moved("/advancedSearch.do", "searchMethod=advancedSearch&advanced=true&country=USA&resultRank=specimen"));
        assertEquals("taxonName=myrmicinaepheidole&countryName=United+States", moved("/bigMap.do", "taxonName=myrmicinaepheidole&countryName=USA"));
        // written encoded: read, and written again encoded
        assertEquals("name=C%C3%B4te+d%27Ivoire", moved("/country.do", "name=Ivory+Coast"));
        assertEquals("name=C%C3%B4te+d%27Ivoire", moved("/country.do", "name=Ivory%20Coast"));
        assertEquals("countryName=T%C3%BCrkiye&rank=species", moved("/taxonomicPage.do", "countryName=Turkey&rank=species"));
        assertEquals("name=Cura%C3%A7ao", moved("/country.do", "name=Curacao"));
        // both parameters of a search link
        assertEquals("subregion=North+America&country=United+States", moved("/advancedSearch.do", "subregion=North+America&country=USA"));
    }

    public void testEveryOtherParameterStaysAsWritten() {
        assertEquals("a=%41&b&c=&countryName=United+States&d=x%20y&e=%C3%A9",
            moved("/taxonomicPage.do", "a=%41&b&c=&countryName=USA&d=x%20y&e=%C3%A9"));
        assertEquals("countryName=United+States&countryName=T%C3%BCrkiye", moved("/x.do", "countryName=USA&countryName=Turkey"));
    }

    public void testNothingMovesForOtherNames() {
        assertNull(moved("/country.do", "name=United+States"));
        assertNull(moved("/country.do", "name=C%C3%B4te+d%27Ivoire"));
        assertNull(moved("/country.do", "name=Atlantis"));
        assertNull("old, no valid name: the page of today", moved("/country.do", "name=Bouvet+Island"));
        assertNull(moved("/country.do", "name="));
        assertNull(moved("/country.do", "name"));
        assertNull(moved("/country.do", "id=281"));
        assertNull(moved("/country.do", ""));
        assertNull(moved("/country.do", null));
        assertNull(moved(null, "name=USA"));
        // name is a country only on country.do: a taxon, a museum or a bioregion of that name is not one
        assertNull(moved("/description.do", "name=USA&rank=species"));
        assertNull(moved("/bioregion.do", "name=USA"));
        assertNull(moved("/region.do", "name=USA"));
        // not valid percent-encoding: left to the page
        assertNull(moved("/country.do", "name=USA%"));
        assertNull(moved("/country.do", "name=USA&x=%zz"));
        // the search for specimens of a state is by the state as the file wrote it: adm1= is not changed
        assertNull(moved("/advancedSearch.do", "country=United+States&adm1=MA"));
        assertEquals("country=United+States&adm1=MA", moved("/advancedSearch.do", "country=USA&adm1=MA"));
    }

    public void testAdm1PagesMove() {
        assertEquals("name=Massachusetts&country=United+States", moved("/adm1.do", "name=MA&country=United+States"));
        // the country by its old name too: both move at once
        assertEquals("name=Massachusetts&country=United+States", moved("/adm1.do", "name=MA&country=USA"));
        assertEquals("name=Massachusetts&country=United+States", moved("/adm1.do", "name=Massachusetts&country=USA"));
        assertEquals("rank=genus&adm1Name=Massachusetts&countryName=United+States", moved("/taxonomicPage.do", "rank=genus&adm1Name=MA&countryName=USA"));
        assertEquals("countryName=Afghanistan&adm1Name=Badakhsh%C4%81n", moved("/taxonomicPage.do", "countryName=Afghanistan&adm1Name=Badakhshan"));
        assertEquals("name=Lagunes&country=C%C3%B4te+d%27Ivoire", moved("/adm1.do", "name=Lagunes+District&country=Ivory+Coast"));
        assertNull(moved("/adm1.do", "name=Massachusetts&country=United+States"));
        assertNull("no country given: as today", moved("/adm1.do", "name=MA"));
        assertNull("another country", moved("/adm1.do", "name=MA&country=Madagascar"));
        assertNull("old, no valid name", moved("/adm1.do", "name=Lost&country=United+States"));
        // name is an adm1 only on adm1.do
        assertEquals("name=MA&country=United+States", moved("/country.do", "name=MA&country=USA"));
    }

    /** The administrators' tools for the place rows work on the old-name rows themselves. */
    public void testThePlaceToolsAreLeftAlone() {
        assertNull(moved("/editGeolocale.do", "name=USA&georank=country&country=USA"));
        assertNull(moved("/geolocaleMgr.do", "georank=adm1&countryName=USA"));
        assertNull(moved("/adm1Mgr.do", "adm1Name=MA&countryName=USA&groupId=1"));
    }

    /** The redirect's path is the server's own path of this page; anything else gives no redirect (review of #342). */
    public void testTheRedirectStaysOnThisSite() {
        assertEquals("/country.do", OldPlaceNames.localPath("", "/country.do", null));
        assertEquals("/antweb/taxonomicPage.do", OldPlaceNames.localPath("/antweb", "/taxonomicPage.do", null));
        assertEquals("/a/b.do/x", OldPlaceNames.localPath(null, "/a/b.do", "/x"));
        assertNull("two slashes would name another host", OldPlaceNames.localPath("", "//other.example/country.do", null));
        assertNull(OldPlaceNames.localPath("", "country.do", null));
        assertNull(OldPlaceNames.localPath("", "", null));
        assertNull(OldPlaceNames.localPath(null, null, null));
        assertNull("a backslash", OldPlaceNames.localPath("", "/\\other.example/country.do", null));
        assertNull("a colon", OldPlaceNames.localPath("", "/http://other.example/country.do", null));
        assertNull("a blank or a line break", OldPlaceNames.localPath("", "/country.do\r\nSet-Cookie: x", null));
        assertNull(OldPlaceNames.localPath("", "/country.do?x", null));
        assertNull(OldPlaceNames.localPath("", "/pays\u00e9.do", null));
    }
}

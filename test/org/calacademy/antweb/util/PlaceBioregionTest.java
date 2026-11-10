package test.org.calacademy.antweb.util;

import java.util.LinkedHashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.apache.commons.collections4.map.MultiKeyMap;
import org.calacademy.antweb.geolocale.Adm1;
import org.calacademy.antweb.geolocale.Country;
import org.calacademy.antweb.util.GeolocaleMgr;

/**
 * The bioregion the specimen upload stores for a country and state (GeolocaleMgr.getGeolocaleBioregion), 2026-11-10.
 * Brian Fisher: the valid countries and states on bioregionCountryList.do are AntWeb's definition. So: the state's
 * bioregion when the state is valid, or is an old name that points to a valid state, and has exactly one; otherwise
 * the country's when the country has exactly one; otherwise none (the upload then keeps the bioregion of the
 * curator's file). A row for an old state name that points to no valid state gives nothing: such rows were filled
 * with the country's first bioregion when they were made (production: Indonesia's Irian Jaya, Sulawesi and Moluccas
 * say Indomalaya while AntWeb's map says Australasia; a Lithuanian old name says Oceania).
 *
 * The same rule as decide() in tools/place/place_fix.py (version 3), which corrects the specimens already stored;
 * the cases and places are those of tests/unit/test_place_fix.py. No database: the places are made here
 * (GeolocaleMgr.geolocaleBioregion, validAdm1).
 */
public class PlaceBioregionTest extends TestCase {

    private final Map<String, Country> countries = new LinkedHashMap<>();
    private final MultiKeyMap<String, Adm1> adm1s = new MultiKeyMap<>();

    private void country(String name, boolean valid, String validName, String bioregion, String altBioregion) {
        Country c = new Country();
        c.setName(name);
        c.setGeorank("country");
        c.setIsValid(valid);
        c.setValidName(validName);
        c.setBioregion(bioregion);
        c.setAltBioregion(altBioregion);
        countries.put(name, c);
    }

    private void adm1(String name, String country, boolean valid, String validName, String bioregion, String altBioregion) {
        Adm1 a = new Adm1();
        a.setName(name);
        a.setGeorank("adm1");
        a.setParent(country);
        a.setIsValid(valid);
        a.setValidName(validName);
        a.setBioregion(bioregion);
        a.setAltBioregion(altBioregion);
        adm1s.put(name, country, a);
    }

    /** The places of tests/unit/test_place_fix.py. "none" is how older saves of Edit Geolocale stored "no bioregion". */
    @Override
    protected void setUp() {
        country("Canada", true, "", "Nearctic", "");
        country("Mexico", true, "", "Neotropical", "Nearctic");
        country("United States", true, "", "Nearctic", "Oceania");
        country("USA", false, "United States", "", "");
        country("Panama", true, "", "Neotropical", "");
        adm1("Chihuahua", "Mexico", true, "", "Nearctic", "none");
        adm1("Jalisco", "Mexico", true, "", "Neotropical", "Nearctic");
        adm1("Nuevo León", "Mexico", true, "", "Nearctic", "none");
        adm1("Nuevo Leon", "Mexico", false, "Nuevo León", "Neotropical", "");
        adm1("Baja California", "Mexico", true, "none", "Nearctic", "none");
        adm1("Baja California Norte", "Mexico", false, "Baja California", "Neotropical", "");
        adm1("Old Lost State", "Mexico", false, "", "Neotropical", "");
        adm1("California", "United States", true, "", "Nearctic", "");
        adm1("Ontario", "Canada", true, "", "", "");
        adm1("Old Canadian Name", "Canada", false, "", "Oceania", "");
    }

    private String bioregion(String country, String adm1) {
        return GeolocaleMgr.geolocaleBioregion(countries, adm1s, country, adm1);
    }

    /** Chihuahua: its second bioregion is the text "none", so it has one (production: 1,340 specimens were blank). */
    public void testNoneTextIsNoSecondBioregion() {
        assertEquals("Nearctic", bioregion("Mexico", "Chihuahua"));
        assertEquals("Nearctic", bioregion("United States", "California"));
    }

    public void testOldStateNameTakesTheValidStatesBioregionNotItsOwn() {
        assertEquals("Nearctic", bioregion("Mexico", "Nuevo Leon"));
        assertEquals("Nearctic", bioregion("Mexico", "Nuevo León"));
        assertEquals("Nearctic", bioregion("Mexico", "Baja California Norte"));
    }

    /** An old name that points to no valid state gives nothing: the country decides when it has one bioregion. */
    public void testOldStateNameThatPointsNowhereGivesNothing() {
        assertNull(bioregion("Mexico", "Old Lost State"));
        assertEquals("the country's, not the old row's Oceania", "Nearctic", bioregion("Canada", "Old Canadian Name"));
    }

    public void testStateWithTwoBioregionsGivesNone() {
        assertNull(bioregion("Mexico", "Jalisco"));
    }

    public void testCountryWithOneBioregion() {
        assertEquals("Nearctic", bioregion("Canada", null));
        assertEquals("Nearctic", bioregion("Canada", ""));
        assertEquals("a state without a bioregion of its own", "Nearctic", bioregion("Canada", "Ontario"));
        assertEquals("a state AntWeb does not know", "Nearctic", bioregion("Canada", "Nowhere"));
        assertEquals("a state of another country", "Nearctic", bioregion("Canada", "Chihuahua"));
        assertEquals("Neotropical", bioregion("Panama", null));
    }

    public void testCountryWithTwoBioregionsAndNoOrUnknownState() {
        assertNull(bioregion("Mexico", null));
        assertNull(bioregion("Mexico", ""));
        assertNull(bioregion("Mexico", "Cancun"));
        assertNull(bioregion("United States", null));
        assertNull("a state of another country", bioregion("Mexico", "California"));
    }

    /** An old country name finds the states of its valid country; an unknown country gives nothing. */
    public void testOldAndUnknownCountry() {
        assertEquals("Nearctic", bioregion("USA", "California"));
        assertNull(bioregion("Equador", null));
        assertNull(bioregion("México", "Chihuahua"));   // the upload has stored Mexico by then (CountrySpellingTest)
        assertNull(bioregion(null, "Chihuahua"));
        assertNull(bioregion(null, null));
        // before the places are loaded
        assertNull(GeolocaleMgr.geolocaleBioregion(null, adm1s, "Canada", null));
        assertEquals("Nearctic", GeolocaleMgr.geolocaleBioregion(countries, null, "Canada", "Ontario"));
    }

    /** The lookup of a valid state is as it was: the state itself, or the valid state an old name points to, in that exact country. */
    public void testValidAdm1AsBefore() {
        assertSame(adm1s.get("Chihuahua", "Mexico"), GeolocaleMgr.validAdm1(adm1s, "Chihuahua", "Mexico"));
        assertSame(adm1s.get("Nuevo León", "Mexico"), GeolocaleMgr.validAdm1(adm1s, "Nuevo Leon", "Mexico"));
        assertSame(adm1s.get("Baja California", "Mexico"), GeolocaleMgr.validAdm1(adm1s, "Baja California Norte", "Mexico"));
        assertNull(GeolocaleMgr.validAdm1(adm1s, "Old Lost State", "Mexico"));
        assertNull(GeolocaleMgr.validAdm1(adm1s, "Cancun", "Mexico"));
        assertNull(GeolocaleMgr.validAdm1(adm1s, "Chihuahua", "Canada"));
        assertNull(GeolocaleMgr.validAdm1(adm1s, null, "Mexico"));
        assertNull(GeolocaleMgr.validAdm1(null, "Chihuahua", "Mexico"));
    }
}

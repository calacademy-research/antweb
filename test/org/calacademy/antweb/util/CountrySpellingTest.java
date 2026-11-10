package test.org.calacademy.antweb.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.calacademy.antweb.geolocale.Country;
import org.calacademy.antweb.upload.MessageMgr;
import org.calacademy.antweb.upload.SpecimenUploadParse;
import org.calacademy.antweb.util.GeolocaleMgr;

/**
 * A country written with other capitals, accents or blanks than AntWeb's name (2026-11-10). The specimen upload
 * looked a country up by its exact name only, so a file that wrote "México" kept "México": the specimen was outside
 * every search for Mexico and got no bioregion (production: México 3, Bolívia 1, ...). tools/place/place_fix.py
 * corrects the specimens already stored; the upload now applies the same rule, so the next upload of the same file
 * does not bring the other spelling back: a name that differs from exactly one valid country, not an island, only
 * by capitals, accents or blanks is stored as that country, and the upload report lists it. None or several such
 * countries: stored as uploaded and reported as "Not Antweb valid countries", as before.
 *
 * The cases are those of tests/unit/test_place_fix.py (test_country_spelling_and_old_name), with the same places.
 * No database: the countries are made here (GeolocaleMgr.countriesBySpelling, validCountry, countrySpelledLike).
 */
public class CountrySpellingTest extends TestCase {

    private static Country country(String name, boolean valid, String validName, boolean island) {
        Country c = new Country();
        c.setName(name);
        c.setGeorank("country");
        c.setIsValid(valid);
        c.setValidName(validName);
        c.setIsIsland(island);
        return c;
    }

    /** The places of tests/unit/test_place_fix.py: USA is an old name of United States, Hawaii an island, Zaire an old name that points nowhere. */
    private static List<Country> places() {
        List<Country> list = new ArrayList<>();
        list.add(country("Canada", true, null, false));
        list.add(country("Mexico", true, null, false));
        list.add(country("United States", true, null, false));
        list.add(country("USA", false, "United States", false));
        list.add(country("Panama", true, null, false));
        list.add(country("Hawaii", true, null, true));
        list.add(country("Zaire", false, null, false));
        return list;
    }

    /** All names, valid or not, as GeolocaleMgr keeps them (countryNameMap). */
    private static Map<String, Country> byName(List<Country> countries) {
        Map<String, Country> map = new LinkedHashMap<>();
        for (Country c : countries) map.put(c.getName(), c);
        return map;
    }

    /** What the upload stores for a country name: SpecimenUploadParse.parseLine asks for the exact name first, then for the spelling. */
    private static String stored(List<Country> countries, String name) {
        Country c = GeolocaleMgr.validCountry(byName(countries), name);
        if (c == null) c = GeolocaleMgr.countrySpelledLike(GeolocaleMgr.countriesBySpelling(countries), name);
        return c == null ? null : c.getName();
    }

    private static String spelledLike(List<Country> countries, String name) {
        Country c = GeolocaleMgr.countrySpelledLike(GeolocaleMgr.countriesBySpelling(countries), name);
        return c == null ? null : c.getName();
    }

    /** The expected values are what normal() of tools/place/place_fix.py gives for the same text. */
    public void testNormalNameIsThePlaceFixToolsRule() {
        assertEquals("mexico", GeolocaleMgr.normalName("México"));
        assertEquals("panama", GeolocaleMgr.normalName("  panamá "));
        assertEquals("mexico", GeolocaleMgr.normalName("MEXICO"));
        assertEquals("equador", GeolocaleMgr.normalName("Equador"));
        assertEquals("bolivia", GeolocaleMgr.normalName("Bolívia"));
        assertEquals("cote d'ivoire", GeolocaleMgr.normalName("Côte d'Ivoire"));
        assertEquals("sao tome and principe", GeolocaleMgr.normalName("São  Tomé and  Príncipe"));
        assertEquals("turkiye", GeolocaleMgr.normalName("Türkiye"));
        assertEquals("curacao", GeolocaleMgr.normalName("Curaçao"));
        assertEquals("reunion", GeolocaleMgr.normalName("RÉUNION"));
        assertEquals("aland", GeolocaleMgr.normalName("Åland"));
        assertEquals("strasse", GeolocaleMgr.normalName("Straße"));
        // the accent as its own character (e + U+0301), a no-break space, a tab, full-width letters
        assertEquals("mexico", GeolocaleMgr.normalName("México"));
        assertEquals("united states", GeolocaleMgr.normalName("United States"));
        assertEquals("mexico", GeolocaleMgr.normalName("Mexico\t"));
        assertEquals("mexico", GeolocaleMgr.normalName("Ｍｅｘｉｃｏ"));
        // a letter that is not a plain letter plus an accent stays what it is
        assertEquals("søby", GeolocaleMgr.normalName("Søby"));
        assertEquals("", GeolocaleMgr.normalName(""));
        assertEquals("", GeolocaleMgr.normalName("   "));
        assertEquals("", GeolocaleMgr.normalName(null));
    }

    public void testAccentsCapitalsAndBlanksGiveTheOneValidCountry() {
        assertEquals("Mexico", spelledLike(places(), "México"));
        assertEquals("Panama", spelledLike(places(), "  panamá "));
        assertEquals("Mexico", spelledLike(places(), "MEXICO"));
        assertEquals("United States", spelledLike(places(), "united  states"));
        assertEquals("Mexico", stored(places(), "México"));
        assertEquals("Panama", stored(places(), "  panamá "));
        assertEquals("Mexico", stored(places(), "MEXICO"));
    }

    /** Nothing is guessed: a different word, an old name that points nowhere, a blank. */
    public void testAnotherWordIsNotCorrected() {
        assertNull(stored(places(), "Equador"));
        assertNull(stored(places(), "Zaire"));
        assertNull(stored(places(), "zaïre"));
        assertNull(stored(places(), ""));
        assertNull(stored(places(), "   "));
        assertNull(spelledLike(places(), null));
        // an old name is found by its exact name only: "usa" is not a spelling of a valid country
        assertNull(stored(places(), "usa"));
        // before the places are loaded
        assertNull(GeolocaleMgr.countrySpelledLike(null, "México"));
        assertNull(GeolocaleMgr.validCountry(null, "Mexico"));
    }

    public void testTwoValidCountriesWithTheSameNormalNameGiveNone() {
        List<Country> countries = places();
        countries.add(country("Panamá", true, null, false));
        assertNull(spelledLike(countries, "PANAMA"));
        assertNull(spelledLike(countries, "panamá"));
        assertNull(stored(countries, "PANAMA"));
        // each of the two is still found by its exact name, and the other countries are not affected
        assertEquals("Panama", stored(countries, "Panama"));
        assertEquals("Panamá", stored(countries, "Panamá"));
        assertEquals("Mexico", stored(countries, "México"));
        // three with the same normal name: still none
        countries.add(country("PANAMA", true, null, false));
        assertNull(spelledLike(countries, "panama"));
        // a country that is not valid does not count as a second one
        List<Country> withOld = places();
        withOld.add(country("Panamá", false, null, false));
        assertEquals("Panama", spelledLike(withOld, "PANAMA"));
    }

    public void testAnIslandIsNotATarget() {
        assertNull(spelledLike(places(), "HAWAII"));
        assertNull(spelledLike(places(), "hawaii"));
        assertNull(stored(places(), "HAWAII"));
        // not even under its own name: the upload finds an island by its exact name (getValidCountry, getValidIsland)
        assertNull(spelledLike(places(), "Hawaii"));
        assertEquals("Hawaii", stored(places(), "Hawaii"));
        // an island does not hide a country, and does not make its name count twice
        List<Country> countries = places();
        countries.add(country("Panamá", true, null, true));
        assertEquals("Panama", spelledLike(countries, "PANAMA"));
    }

    /** The exact lookup is as it was: a valid name gives its own row, an old name its valid country, anything else nothing. */
    public void testExactNameAndOldNameAsBefore() {
        List<Country> countries = places();
        Map<String, Country> names = byName(countries);
        assertSame(names.get("Mexico"), GeolocaleMgr.validCountry(names, "Mexico"));
        assertSame(names.get("United States"), GeolocaleMgr.validCountry(names, "USA"));
        assertSame(names.get("Hawaii"), GeolocaleMgr.validCountry(names, "Hawaii"));
        assertNull(GeolocaleMgr.validCountry(names, "Zaire"));
        assertNull(GeolocaleMgr.validCountry(names, "México"));
        assertNull(GeolocaleMgr.validCountry(names, "MEXICO"));
        assertNull(GeolocaleMgr.validCountry(names, null));
        assertEquals("United States", stored(countries, "USA"));
        assertEquals("Mexico", stored(countries, "Mexico"));
        // the exact name wins: an old name written with an accent goes where AntWeb points it, not to the look-alike
        countries.add(country("Panamá", false, "Canada", false));
        assertEquals("Canada", stored(countries, "Panamá"));
        assertEquals("Panama", stored(countries, "PANAMÁ"));
    }

    /** The upload report: its own section, counted as corrected (the curator is asked to fix the file), the line escaped. */
    public void testReportLineAndSection() {
        assertEquals("México -&gt; Mexico", SpecimenUploadParse.countrySpellingLine("México", "Mexico"));
        assertEquals("&lt;b&gt;MEXICO -&gt; Mexico", SpecimenUploadParse.countrySpellingLine("<b>MEXICO", "Mexico"));

        assertTrue(MessageMgr.CORRECTED.contains(MessageMgr.countrySpellingCorrected));
        assertFalse(MessageMgr.NAME_CHANGES.contains(MessageMgr.countrySpellingCorrected));
        String heading = MessageMgr.getMessageDisplay(MessageMgr.countrySpellingCorrected);
        assertTrue(heading, heading.contains("Country spelling corrected"));
        assertEquals("corrected", MessageMgr.attention(MessageMgr.countrySpellingCorrected, heading));

        MessageMgr m = new MessageMgr();
        m.addToMessages(MessageMgr.countrySpellingCorrected, SpecimenUploadParse.countrySpellingLine("México", "Mexico"));
        m.addToMessages(MessageMgr.countrySpellingCorrected, SpecimenUploadParse.countrySpellingLine("México", "Mexico"));
        m.addToMessages(MessageMgr.countrySpellingCorrected, SpecimenUploadParse.countrySpellingLine("Bolívia", "Bolivia"));
        m.addToMessages(MessageMgr.notValidCountries, "Equador");   // a country AntWeb does not know: a note, as before
        assertEquals(3, m.getCorrectedCount());
        assertEquals(0, m.getNotLoadedCount());
        assertEquals(0, m.getNameChangeCount());
        MessageMgr.ReportSection section = null;
        for (MessageMgr.ReportSection s : m.reportSections()) {
            if (MessageMgr.countrySpellingCorrected.equals(s.key)) section = s;
        }
        assertNotNull(section);
        assertEquals(MessageMgr.CORRECTED_KIND, section.kind);
        assertEquals(3, section.count);
        assertEquals("one row per spelling", 2, section.rows.size());
    }
}

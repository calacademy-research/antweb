package test.org.calacademy.antweb.upload;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.apache.commons.collections4.map.MultiKeyMap;
import org.calacademy.antweb.geolocale.Adm1;
import org.calacademy.antweb.geolocale.Country;
import org.calacademy.antweb.upload.RecordPlace;
import org.calacademy.antweb.util.GeolocaleMgr;

/**
 * Oceanic islands are their own countries (Brian Fisher, 2026-11-11: "An oceanic island or archipelago with its own ant
 * fauna is its own country in AntWeb. Its specimens never count toward the political parent."). This round: Hawaii and
 * Galapagos Islands, stored as Reunion, Mayotte and Rodrigues already are (specimen.country = the island's name).
 *
 * The country an uploaded record is filed under (RecordPlace, called by SpecimenUploadParse.parseLine), for every form
 * a file can write the place in, on the place rows production has (2026-11-11; ids in the comments):
 *   Ecuador + adm1 Galapagos or Galápagos, country Galapagos Islands (or its old names)   country Galapagos Islands
 *   United States + adm1 Hawaii, country Hawaii                                            country Hawaii
 *   United States + adm1 Oahu (row 1017, an old name of Hawaii)                            country Hawaii, adm1 dropped
 *   country Reunion or Réunion                                                             country Reunion (no rule here)
 *   Mauritius + adm1 Rodrigues (row 691), once the row names the country Rodrigues         country Rodrigues
 * and what stays as it was: every other record; an adm1 that is not the place's own or old name is kept; an old state
 * name that points to a state of the same country is not a country. Also the islands of a political parent and the
 * line its page shows (GeolocaleMgr.islandsOf, Country.ownPagesNote). No database.
 */
public class IslandCountryTest extends TestCase {

    private final Map<String, Country> countries = new LinkedHashMap<>();
    private final MultiKeyMap<String, Adm1> adm1s = new MultiKeyMap<>();
    private final List<Adm1> adm1Rows = new ArrayList<>();

    private Country country(int id, String name, String subregion, String politicalParent, boolean island, boolean valid, String validName, String bioregion) {
        Country c = new Country();
        c.setId(id);
        c.setName(name);
        c.setGeorank("country");
        c.setParent(subregion);
        c.setSubregion(subregion);
        c.setCountry(politicalParent);
        c.setIsIsland(island);
        c.setIsValid(valid);
        c.setValidName(validName);
        c.setBioregion(bioregion);
        countries.put(name, c);
        return c;
    }

    private void adm1(int id, String name, String parent, boolean island, boolean valid, String validName) {
        Adm1 a = new Adm1();
        a.setId(id);
        a.setName(name);
        a.setGeorank("adm1");
        a.setParent(parent);
        a.setIsIsland(island);
        a.setIsValid(valid);
        a.setValidName(validName);
        adm1s.put(name, parent, a);
        adm1Rows.add(a);
    }

    /** The rows of production, 2026-11-11. Rodrigues of Mauritius (691) has no valid name yet. */
    @Override
    protected void setUp() {
        country(97, "United States", "North America", null, false, true, "none", "Nearctic");
        country(103, "Ecuador", "South America", null, false, true, null, "Neotropical");
        country(9, "Mauritius", "Eastern Africa", null, false, true, null, "Malagasy");
        country(423, "Hawaii", "Polynesia", "United States", true, true, "none", "Oceania");
        country(1721, "Galapagos Islands", "South America", "Ecuador", true, true, null, "Neotropical");
        country(1732, "Galapagos", "South America", "Galapagos", true, false, "Galapagos Islands", "Neotropical");
        country(8864, "Galápagos", "South America", null, true, false, "Galapagos Islands", "Neotropical");
        country(10, "Mayotte", "Eastern Africa", "Mayotte", false, true, null, "Malagasy");
        country(12, "Reunion", "Eastern Africa", "Reunion", false, true, null, "Malagasy");
        country(8737, "Réunion", "Eastern Africa", "Réunion", false, false, "Reunion", "Malagasy");
        country(8865, "Rodrigues", "Eastern Africa", null, false, true, null, "Malagasy");
        adm1(620, "Galapagos", "Ecuador", false, false, "Galapagos Islands");
        adm1(2394, "Galápagos", "Ecuador", true, false, "Galapagos Islands");
        adm1(1017, "Oahu", "United States", false, false, "Hawaii");
        adm1(691, "Rodrigues", "Mauritius", false, false, null);
        adm1(445, "Massachusetts", "United States", false, true, null);
        adm1(1696, "MA", "United States", false, false, "Massachusetts");
        adm1(461, "Georgia", "United States", false, true, "none");
        adm1(833, "Saint-Denis", "Reunion", false, true, null);
        adm1(5001, "Pichincha", "Ecuador", false, true, null);
    }

    private RecordPlace place(String country, String adm1) {
        return RecordPlace.of(countries, adm1s, adm1Rows, country, adm1);
    }

    /** "country / adm1 / island" as the upload stores them; null when the record is handled as before. */
    private String stored(String country, String adm1) {
        RecordPlace p = place(country, adm1);
        if (p == null) return null;
        return p.place.getName() + " / " + p.adm1 + " / " + (p.isIsland() ? p.place.getName() : null);
    }

    public void testGalapagosInEveryForm() {
        String galapagos = "Galapagos Islands / null / Galapagos Islands";
        assertEquals(galapagos, stored("Ecuador", "Galapagos"));
        assertEquals(galapagos, stored("Ecuador", "Galápagos"));
        assertEquals(galapagos, stored("Galapagos Islands", null));
        // the upload has made an old country name the valid one before it asks; the old names resolve here too
        assertEquals(galapagos, stored("Galapagos", null));
        assertEquals(galapagos, stored("Galápagos", null));
        assertEquals(galapagos, stored("Galapagos Islands", "Galapagos"));
        assertEquals(galapagos, stored("Galapagos Islands", "Galapagos Islands"));
        assertEquals(1721, place("Ecuador", "Galapagos").place.getId());
        assertEquals("the island's subregion, bioregion and political parent come with its row",
            "South America Neotropical Ecuador", place("Ecuador", "Galapagos").place.getSubregion() + " "
            + place("Ecuador", "Galapagos").place.getBioregion() + " " + place("Ecuador", "Galapagos").place.getCountry());
    }

    public void testHawaiiInEveryForm() {
        String hawaii = "Hawaii / null / Hawaii";
        assertEquals(hawaii, stored("United States", "Hawaii"));
        assertEquals(hawaii, stored("Hawaii", null));
        assertEquals(hawaii, stored("Hawaii", "Hawaii"));
        // no country in the file: the island by the adm1 alone, as before
        assertEquals(hawaii, stored(null, "Hawaii"));
        RecordPlace p = place("United States", "Hawaii");
        assertEquals(423, p.place.getId());
        assertEquals("Polynesia", p.place.getSubregion());
        assertEquals("Oceania", p.place.getBioregion());
    }

    /** Row 1017: Oahu of United States is an old name of Hawaii. The adm1 goes: it was a name of the island. */
    public void testOahuIsAnOldNameOfHawaii() {
        assertEquals("Hawaii / null / Hawaii", stored("United States", "Oahu"));
        assertEquals("the same when the file already says Hawaii", "Hawaii / null / Hawaii", stored("Hawaii", "Oahu"));
        assertNull("only in the country of the row", place("Ecuador", "Oahu"));
        assertEquals(new LinkedHashSet<>(Arrays.asList("Oahu")), RecordPlace.aliasNames(countries, adm1s, adm1Rows, countries.get("Hawaii")));
        assertEquals(new LinkedHashSet<>(Arrays.asList("Galapagos", "Galápagos")),
            RecordPlace.aliasNames(countries, adm1s, adm1Rows, countries.get("Galapagos Islands")));
    }

    /** Any other adm1 of an island record is kept as the file wrote it (the islands have no adm1 rows yet). */
    public void testAnotherAdm1IsKept() {
        assertEquals("Hawaii / Maui / Hawaii", stored("Hawaii", "Maui"));
        assertEquals("Galapagos Islands / Santa Cruz / Galapagos Islands", stored("Galapagos Islands", "Santa Cruz"));
        assertEquals("Galapagos Islands / Isabela / Galapagos Islands", stored("Galapagos", "Isabela"));
    }

    /** Reunion is a plain country: no rule applies, the upload stores country Reunion as before (Réunion: its valid name). */
    public void testReunionAndMayotteAreCountriesAlready() {
        assertNull(place("Reunion", null));
        assertNull(place("Reunion", "Saint-Denis"));
        assertNull(place("Réunion", null));
        assertNull(place("Mayotte", null));
        assertNull(place("Rodrigues", null));
        // the upload's own step before: the old name Réunion is the valid country Reunion
        assertEquals("Reunion", GeolocaleMgr.validCountry(countries, "Réunion").getName());
        assertEquals("Reunion", GeolocaleMgr.validCountry(countries, "Reunion").getName());
    }

    /**
     * Mauritius + adm1 Rodrigues. Row 691 (adm1 Rodrigues of Mauritius, not valid) has no valid name on production, so
     * nothing changes; once its valid name is the country Rodrigues (tools/place/island_countries.py sets it), the
     * record is filed under the country Rodrigues, which is not flagged as an island: no island_country.
     */
    public void testRodriguesOfMauritius() {
        assertNull("as today, until the row names the country", place("Mauritius", "Rodrigues"));
        adm1s.get("Rodrigues", "Mauritius").setValidName("Rodrigues");
        assertEquals("Rodrigues / null / null", stored("Mauritius", "Rodrigues"));
        RecordPlace p = place("Mauritius", "Rodrigues");
        assertEquals(8865, p.place.getId());
        assertFalse(p.isIsland());
        assertEquals("Eastern Africa Malagasy", p.place.getSubregion() + " " + p.place.getBioregion());
        assertEquals(new LinkedHashSet<>(Arrays.asList("Rodrigues")), RecordPlace.aliasNames(countries, adm1s, adm1Rows, countries.get("Rodrigues")));
        assertNull("the country Rodrigues itself needs no rule", place("Rodrigues", null));
        assertNull(place("Mauritius", null));
        assertNull(place("Mauritius", "Port Louis"));
    }

    public void testEveryOtherRecordIsHandledAsBefore() {
        assertNull(place("United States", "Massachusetts"));
        assertNull("an old state name that points to a state of the same country is not a country", place("United States", "MA"));
        assertNull("a state named like a country elsewhere (Georgia)", place("United States", "Georgia"));
        assertNull(place("United States", null));
        assertNull(place("Ecuador", "Pichincha"));
        assertNull(place("Ecuador", null));
        assertNull(place("Atlantis", "Nowhere"));
        assertNull(place(null, null));
        assertNull("names are exact", place("United States", "hawaii"));
        assertNull("before the places are loaded", RecordPlace.of(null, adm1s, adm1Rows, "United States", "Hawaii"));
        // an old state name whose valid name is neither a state of the country nor a country: nothing
        adm1(7001, "Somewhere", "Ecuador", false, false, "Nowhere");
        assertNull(place("Ecuador", "Somewhere"));
        // an old name that points to its own country is not a move
        adm1(7002, "Mainland", "Ecuador", false, false, "Ecuador");
        assertNull(place("Ecuador", "Mainland"));
        // the target must be a valid country
        adm1(7003, "Old Galapagos", "Ecuador", false, false, "Galapagos");
        assertNull(place("Ecuador", "Old Galapagos"));
        // an island row that is not valid and names no valid island
        country(9001, "Lost Island", "Polynesia", null, true, false, "none", "Oceania");
        assertNull(place("Lost Island", null));
        assertNull(RecordPlace.validIsland(countries, "Reunion"));
        assertEquals("Hawaii", RecordPlace.validIsland(countries, "Hawaii").getName());
        assertEquals("Galapagos Islands", RecordPlace.validIsland(countries, "Galápagos").getName());
    }

    /**
     * Region and subregion of a record filed under such a place (automatic review, 2026-11-11): the place row's value
     * where it has one, otherwise the record's own; null, a blank and the texts "none" and "null" are no value and are
     * never stored. The same rule as the one-time update (tools/place/island_countries.py, new_value and blank).
     */
    public void testRegionAndSubregionAreTheRowsOrTheRecordsOwn() {
        assertEquals("Oceania", RecordPlace.rowValueOr("Oceania", "Americas"));
        assertEquals("Oceania", RecordPlace.rowValueOr("Oceania", null));
        for (String none : new String[] {null, "", "  ", "none", "None", "null", "NULL", " none "}) {
            assertTrue("[" + none + "]", RecordPlace.hasNoValue(none));
            assertEquals("[" + none + "]", "Americas", RecordPlace.rowValueOr(none, "Americas"));
            assertNull("[" + none + "]", RecordPlace.rowValueOr(none, null));
            assertNull("the record's own text none is not stored either", RecordPlace.rowValueOr(none, "none"));
        }
        assertFalse(RecordPlace.hasNoValue("Nonesuch"));
        assertFalse(RecordPlace.hasNoValue("North America"));
    }

    /** The parent's page: its islands that are countries of their own, and the line it shows. */
    public void testIslandsOfThePoliticalParentAndThePageNote() {
        List<Country> islands = new ArrayList<>();
        for (Country c : countries.values()) if (c.isIsland()) islands.add(c);
        List<Country> ofUs = GeolocaleMgr.islandsOf(islands, "United States");
        assertEquals(1, ofUs.size());
        assertEquals("Hawaii", ofUs.get(0).getName());
        assertEquals("only the valid island row", "Galapagos Islands", GeolocaleMgr.islandsOf(islands, "Ecuador").get(0).getName());
        assertEquals(1, GeolocaleMgr.islandsOf(islands, "Ecuador").size());
        assertTrue(GeolocaleMgr.islandsOf(islands, "Mauritius").isEmpty());
        assertTrue("an island is not its own parent", GeolocaleMgr.islandsOf(islands, "Galapagos").isEmpty());
        assertTrue(GeolocaleMgr.islandsOf(islands, null).isEmpty());
        assertTrue(GeolocaleMgr.islandsOf(null, "United States").isEmpty());

        assertEquals("", Country.ownPagesNote(new ArrayList<>()));
        assertEquals("", Country.ownPagesNote(null));
        String one = Country.ownPagesNote(ofUs);
        assertTrue(one, one.matches("<a href=\"[^\"]*/island\\.do\\?name=Hawaii\">Hawaii</a> has its own page and is not counted here\\."));
        Country other = country(9002, "Guam & Co", "Micronesia", "United States", true, true, null, "Oceania");
        List<Country> two = GeolocaleMgr.islandsOf(Arrays.asList(countries.get("Hawaii"), other), "United States");
        String both = Country.ownPagesNote(two);
        assertTrue(both, both.contains(">Guam &amp; Co</a> and <a href=") && both.endsWith(">Hawaii</a> have their own pages and are not counted here."));
        assertTrue("the name in the address is encoded", both.contains("name=Guam+%26+Co\""));
    }
}

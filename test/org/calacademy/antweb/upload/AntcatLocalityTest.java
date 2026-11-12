package test.org.calacademy.antweb.upload;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.apache.commons.collections4.map.MultiKeyMap;
import org.calacademy.antweb.geolocale.Adm1;
import org.calacademy.antweb.geolocale.Country;
import org.calacademy.antweb.upload.AntcatLocality;

/**
 * The country on whose list the AntCat import files a name, from AntCat's type locality (AntcatLocality, 2026-11-12).
 *
 * Brian Fisher, 2026-11-11: "An oceanic island or archipelago with its own ant fauna is its own country in AntWeb. Its
 * specimens never count toward the political parent." Acceptance check: Ecuador's list has no Galapagos-only species.
 * Production, 2026-11-12: 19 subspecies of Camponotus macilentus and C. planus, described from the Galapagos and with
 * no specimen, were on Ecuador's list from AntCat: AntCat writes their locality "Ecuador (Galapagos Is)" (read on
 * antcat.org, protonym 159724) and the import filed a name under the text before the parenthesis.
 *
 * The place in the parentheses is read by the upload's rule (RecordPlace), on the place rows production has
 * (2026-11-11; Rodrigues of Mauritius with the valid name the island tool gave it). No database.
 */
public class AntcatLocalityTest extends TestCase {

    private final Map<String, Country> countries = new LinkedHashMap<>();
    private final MultiKeyMap<String, Adm1> adm1s = new MultiKeyMap<>();
    private final List<Adm1> adm1Rows = new ArrayList<>();

    private Country country(int id, String name, String subregion, String politicalParent, boolean island, boolean valid, String validName) {
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
        countries.put(name, c);
        return c;
    }

    private void adm1(int id, String name, String parent, boolean valid, String validName) {
        Adm1 a = new Adm1();
        a.setId(id);
        a.setName(name);
        a.setGeorank("adm1");
        a.setParent(parent);
        a.setIsValid(valid);
        a.setValidName(validName);
        adm1s.put(name, parent, a);
        adm1Rows.add(a);
    }

    @Override
    protected void setUp() {
        country(97, "United States", "North America", null, false, true, "none");
        country(8700, "U.S.A.", "North America", null, false, false, "United States");
        country(103, "Ecuador", "South America", null, false, true, null);
        country(98, "Mexico", "Central America", null, false, true, null);
        country(9, "Mauritius", "Eastern Africa", null, false, true, null);
        country(50, "Indonesia", "South-Eastern Asia", null, false, true, null);
        country(60, "Spain", "Southern Europe", null, false, true, null);
        country(423, "Hawaii", "Polynesia", "United States", true, true, "none");
        country(1721, "Galapagos Islands", "South America", "Ecuador", true, true, null);
        country(1732, "Galapagos", "South America", "Galapagos", true, false, "Galapagos Islands");
        country(8864, "Galápagos", "South America", null, true, false, "Galapagos Islands");
        country(12, "Reunion", "Eastern Africa", "Reunion", false, true, null);
        country(8865, "Rodrigues", "Eastern Africa", null, false, true, null);
        adm1(620, "Galapagos", "Ecuador", false, "Galapagos Islands");
        adm1(2394, "Galápagos", "Ecuador", false, "Galapagos Islands");
        adm1(1017, "Oahu", "United States", false, "Hawaii");
        adm1(691, "Rodrigues", "Mauritius", false, "Rodrigues");
        adm1(446, "California", "United States", true, null);
        adm1(700, "Java", "Indonesia", true, null);
        adm1(701, "Canary Islands", "Spain", true, null);
    }

    private String filedUnder(String locality) {
        Country c = AntcatLocality.listCountry(countries, adm1s, adm1Rows, locality);
        return c == null ? null : c.getName();
    }

    /** The 19 rows of production: "Ecuador (Galapagos Is)" is the Galapagos Islands' name, not Ecuador's. */
    public void testANameDescribedFromTheGalapagosIsFiledUnderTheIslandOnly() {
        assertEquals("Galapagos Islands", filedUnder("Ecuador (Galapagos Is)"));
        assertEquals("Galapagos Islands", filedUnder("Ecuador (Galapagos Is.)"));
        assertEquals("Galapagos Islands", filedUnder("Ecuador (Galapagos Islands)"));
        assertEquals("Galapagos Islands", filedUnder("Ecuador (Galapagos)"));
        assertEquals("Galapagos Islands", filedUnder("Ecuador (Galápagos Is)"));
        assertEquals("Galapagos Islands", filedUnder("Ecuador (Galapagos Is, Albemarle I.)"));
        assertEquals("Galapagos Islands", filedUnder("Ecuador (Galapagos Is: Isabela)"));
        assertEquals("Galapagos Islands", filedUnder("  Ecuador  ( Galapagos Is )"));
        // the island written as the country, by its name or an old name: as before
        assertEquals("Galapagos Islands", filedUnder("Galapagos Islands"));
        assertEquals("Galapagos Islands", filedUnder("Galapagos"));
        assertEquals("Galapagos Islands", filedUnder("Galapagos Islands (Santa Cruz I.)"));
        // the mainland stays Ecuador's
        assertEquals("Ecuador", filedUnder("Ecuador"));
        assertEquals("Ecuador", filedUnder("Ecuador (Pichincha)"));
        assertEquals("Ecuador", filedUnder("Ecuador (Guayas, Puna I.)"));
    }

    /** AntCat writes Hawaii as the country ("Hawaii (Oahu)", protonym 172131); the parent form is the island's too. */
    public void testHawaii() {
        assertEquals("Hawaii", filedUnder("Hawaii (Oahu)"));
        assertEquals("Hawaii", filedUnder("Hawaii"));
        assertEquals("Hawaii", filedUnder("Hawaii (Kauai I.)"));
        assertEquals("Hawaii", filedUnder("United States (Hawaii)"));
        assertEquals("Hawaii", filedUnder("U.S.A. (Hawaii)"));              // an old name of the country, then the island
        assertEquals("Hawaii", filedUnder("U.S.A. (Hawaii, Oahu)"));
        assertEquals("Hawaii", filedUnder("United States (Oahu)"));        // row 1017: an old name of Hawaii
        assertEquals("Hawaii", filedUnder("U.S.A. (Oahu I.)"));
        assertEquals("United States", filedUnder("U.S.A. (California)"));
        assertEquals("United States", filedUnder("United States"));
        // a spelling no row covers stays with the country before the parentheses (add an old-name row to cover it)
        assertEquals("United States", filedUnder("United States (Hawaiian Is)"));
    }

    /** The upload's rule, not one of its own: the same answers as RecordPlace gives a specimen record. */
    public void testTheUploadsRuleDecides() {
        // an island's name in the parentheses of another country moves nothing (Mexico + Hawaii stays Mexico)
        assertEquals("Mexico", filedUnder("Mexico (Hawaii)"));
        assertEquals("Mexico", filedUnder("Mexico (Galapagos Is)"));
        assertEquals("United States", filedUnder("United States (Galapagos Is)"));
        assertEquals("Ecuador", filedUnder("Ecuador (Hawaii)"));
        // an old state name that names a country (rule 2): Rodrigues of Mauritius
        assertEquals("Rodrigues", filedUnder("Mauritius (Rodrigues)"));
        assertEquals("Rodrigues", filedUnder("Mauritius (Rodrigues I.)"));
        assertEquals("Rodrigues", filedUnder("Rodrigues"));
        assertEquals("Mauritius", filedUnder("Mauritius"));
        // a valid state, or a place AntWeb has no row for: the country
        assertEquals("Indonesia", filedUnder("Indonesia (Java)"));
        assertEquals("Indonesia", filedUnder("Indonesia (Krakatau I.)"));
        assertEquals("Spain", filedUnder("Spain (Canary Is)"));
        assertEquals("Reunion", filedUnder("Reunion"));
        // an island row that names no political parent: by its own name only as the country; the old state names of
        // the parent still name it (rule 2: the rows Galapagos of Ecuador, Oahu of United States)
        countries.get("Galapagos Islands").setCountry(null);
        countries.get("Hawaii").setCountry(null);
        assertEquals("United States", filedUnder("United States (Hawaii)"));
        assertEquals("Hawaii", filedUnder("United States (Oahu)"));
        assertEquals("Hawaii", filedUnder("Hawaii (Oahu)"));
        assertEquals("Galapagos Islands", filedUnder("Ecuador (Galapagos Is)"));
    }

    /** A country AntWeb does not have is not found, as before, whatever the parentheses say. */
    public void testACountryAntwebDoesNotHave() {
        assertNull(filedUnder("Atlantis"));
        assertNull(filedUnder("Atlantis (Hawaii)"));
        assertNull(filedUnder("Pacific Ocean (Galapagos Is)"));
        assertNull(filedUnder(""));
        assertNull(filedUnder("   "));
        assertNull(filedUnder(null));
        assertNull(filedUnder("(Hawaii)"));
        assertNull(AntcatLocality.listCountry(null, adm1s, adm1Rows, "Ecuador"));
        // no adm1 rows loaded: the country by its text
        assertEquals("Ecuador", AntcatLocality.listCountry(countries, null, null, "Ecuador (Pichincha)").getName());
    }

    public void testThePartsOfALocality() {
        assertEquals("Ecuador", AntcatLocality.countryPart("Ecuador (Galapagos Is)"));
        assertEquals("Ecuador", AntcatLocality.countryPart(" Ecuador "));
        assertEquals("", AntcatLocality.countryPart("(Galapagos Is)"));
        assertNull(AntcatLocality.countryPart(null));
        assertEquals("Galapagos Is", AntcatLocality.placePart("Ecuador (Galapagos Is)"));
        assertEquals("Galapagos Is", AntcatLocality.placePart("Ecuador ( Galapagos Is ) (again)"));
        assertEquals("no closing parenthesis", "Galapagos Is", AntcatLocality.placePart("Ecuador (Galapagos Is"));
        assertNull(AntcatLocality.placePart("Ecuador"));
        assertNull(AntcatLocality.placePart("Ecuador ()"));
        assertNull(AntcatLocality.placePart(null));
    }

    /** AntCat's "Is" (Islands) and "I." (Island), and more than one place: the spellings asked, as written first. */
    public void testTheSpellingsOfAPlace() {
        assertEquals(Arrays.asList("Galapagos Is", "Galapagos Islands", "Galapagos"), AntcatLocality.placeSpellings("Galapagos Is"));
        assertEquals(Arrays.asList("Galapagos Is.", "Galapagos Islands", "Galapagos"), AntcatLocality.placeSpellings("Galapagos Is."));
        assertEquals(Arrays.asList("Rodrigues I.", "Rodrigues Island", "Rodrigues"), AntcatLocality.placeSpellings("Rodrigues I."));
        assertEquals(Arrays.asList("Rodrigues I", "Rodrigues Island", "Rodrigues"), AntcatLocality.placeSpellings("Rodrigues I"));
        assertEquals(Arrays.asList("Galapagos Islands", "Galapagos"), AntcatLocality.placeSpellings("Galapagos Islands"));
        assertEquals(Arrays.asList("Rodrigues Island", "Rodrigues"), AntcatLocality.placeSpellings("Rodrigues Island"));
        assertEquals(Arrays.asList("Oahu"), AntcatLocality.placeSpellings(" Oahu "));
        assertEquals(Arrays.asList("Galapagos Is, Albemarle I.", "Galapagos Is, Albemarle Island", "Galapagos Is, Albemarle",
            "Galapagos Is", "Galapagos Islands", "Galapagos"), AntcatLocality.placeSpellings("Galapagos Is, Albemarle I."));
        assertEquals(Arrays.asList("Hawaii: Oahu", "Hawaii"), AntcatLocality.placeSpellings("Hawaii: Oahu"));
        // a word that only ends like the abbreviation is left alone; a bare abbreviation has no place before it
        assertEquals(Arrays.asList("Paris"), AntcatLocality.placeSpellings("Paris"));
        assertEquals(Arrays.asList("Is"), AntcatLocality.placeSpellings("Is"));
        assertTrue(AntcatLocality.placeSpellings(null).isEmpty());
        assertTrue(AntcatLocality.placeSpellings("  ").isEmpty());
    }
}

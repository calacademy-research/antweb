package test.org.calacademy.antweb.upload;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import junit.framework.TestCase;

import org.apache.commons.collections4.map.MultiKeyMap;
import org.calacademy.antweb.geolocale.Adm1;
import org.calacademy.antweb.geolocale.Country;
import org.calacademy.antweb.upload.AntcatLocality;
import org.calacademy.antweb.util.GeolocaleMgr;

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
 *
 * Review of #347 (2026-11-12): the country before the parentheses is resolved exactly as the import resolved it before
 * (testTheCountryBeforeTheParenthesesIsResolvedAsBefore: the import's old two lines against the new code, on the same
 * spellings); and a place in the parentheses that AntWeb does not know, in a country that has island countries, is
 * listed in the import's report (testAPlaceThatIsNotKnownIsNoted).
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
        // other names AntCat writes, as AntWeb has them: an old name with a valid name, one without, a valid name with
        // an accent, and countries whose islands are plain countries (France: Reunion names itself as its country)
        country(70, "France", "Western Europe", null, false, true, null);
        country(71, "Côte d'Ivoire", "Western Africa", null, false, true, null);
        country(8701, "Ivory Coast", "Western Africa", null, false, false, "Côte d'Ivoire");
        country(72, "Democratic Republic of Congo", "Middle Africa", null, false, true, null);
        country(8702, "Zaire", "Middle Africa", null, false, false, null);
        country(73, "Myanmar", "South-Eastern Asia", null, false, true, null);
        country(8703, "Burma", "South-Eastern Asia", null, false, false, "Myanmar");
        country(8704, "USA", "North America", null, false, false, "United States");
        country(74, "Panama", "Central America", null, false, true, null);
        country(75, "Canada", "North America", null, false, true, null);
        adm1(620, "Galapagos", "Ecuador", false, "Galapagos Islands");
        adm1(2394, "Galápagos", "Ecuador", false, "Galapagos Islands");
        adm1(1017, "Oahu", "United States", false, "Hawaii");
        adm1(691, "Rodrigues", "Mauritius", false, "Rodrigues");
        adm1(446, "California", "United States", true, null);
        adm1(1697, "CA", "United States", false, "California");
        adm1(702, "Pichincha", "Ecuador", true, null);
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

    /** The import's lines until 2026-11-12: the text before the parenthesis, trimmed, then GeolocaleMgr.getValidCountry. */
    private static Country asTheImportDidBefore(String thisCountry) {
        String tempCountry = thisCountry;
        if (tempCountry.contains("(")) tempCountry = tempCountry.substring(0, tempCountry.indexOf("("));
        tempCountry = tempCountry.trim();
        return GeolocaleMgr.getValidCountry(tempCountry);
    }

    /** The places in memory, for the manager's own methods (private caches; no other way in without a database). */
    private Map<Field, Object> placesInMemory() throws Exception {
        Map<Field, Object> before = new LinkedHashMap<>();
        Object[][] values = {{"countryNameMap", countries}, {"adm1CountryMap", adm1s}, {"adm1List", new ArrayList<>(adm1Rows)}};
        for (Object[] value : values) {
            Field f = GeolocaleMgr.class.getDeclaredField((String) value[0]);
            f.setAccessible(true);
            before.put(f, f.get(null));
            f.set(null, value[1]);
        }
        return before;
    }

    /**
     * Review of #347: the import resolved the country with GeolocaleMgr.getValidCountry(text before the parenthesis);
     * AntcatLocality looks it up with GeolocaleMgr.validCountry on the manager's map. getValidCountry is that lookup on
     * that map, and the import calls getValidCountry itself again; this test feeds the old lines and the new code the
     * same spellings (those of CountrySpellingTest and forms AntCat writes) and requires the same country, the same
     * object. Neither ever matched other capitals or accents: the name letter for letter, or an old name through its
     * valid name (the spelled-like lookup is the specimen upload's, before and after).
     */
    public void testTheCountryBeforeTheParenthesesIsResolvedAsBefore() throws Exception {
        String[] spellings = {
            // CountrySpellingTest
            "Mexico", "México", "MEXICO", "Mexico\t", "Ｍｅｘｉｃｏ", "Panama", "  panamá ", "Panamá", "PANAMA", "United States", "united  states",
            "USA", "usa", "Canada", "Hawaii", "HAWAII", "hawaii", "Zaire", "zaïre", "Equador", "", "   ",
            // AntCat's forms
            "U.S.A.", "U.S.A. (California)", "U.S.A. (Hawaii)", "United States (Hawaiian Is)", "Ecuador (Galapagos Is)", "Ecuador (Pichincha)",
            "ECUADOR (Galapagos Is)", "Hawaii (Oahu)", "Galapagos Islands", "Galapagos", "Galápagos (Isabela I.)", "Democratic Republic of Congo",
            "Democratic Republic of the Congo", "Ivory Coast", "Ivory Coast (Abidjan)", "Côte d'Ivoire", "Cote d'Ivoire", "Burma", "Burma (Tenasserim)",
            "Myanmar", "Indonesia (Java)", "Indonesia (Krakatau I.)", "Mauritius (Rodrigues I.)", "Mauritius", "Rodrigues", "Reunion", "France (Corsica)",
            "Mexico (Hawaii)", "Spain (Canary Is)", "Borneo", "New Guinea", "Atlantis (Hawaii)", "(Hawaii)", " Ecuador ( Galapagos Is )", "Ecuador(Galapagos Is)",
        };
        Map<Field, Object> before = placesInMemory();
        try {
            Set<String> parents = GeolocaleMgr.getOwnCountryParents();
            int found = 0;
            for (String s : spellings) {
                Country old = asTheImportDidBefore(s);
                String label = "[" + s + "]";
                // the import now: the same call on the same text
                assertSame(label, old, GeolocaleMgr.getValidCountry(AntcatLocality.countryPart(s)));
                // the lookup on a given map (listCountry, the tests) is that call
                assertSame(label, old, GeolocaleMgr.validCountry(countries, AntcatLocality.countryPart(s)));
                AntcatLocality.Filing filing = GeolocaleMgr.getAntcatFiling(parents, old, s);
                Country listed = AntcatLocality.listCountry(countries, adm1s, adm1Rows, s);
                if (old == null) {
                    assertNull(label, filing);
                    assertNull(label, listed);
                    continue;
                }
                found++;
                assertSame(label, old, filing.before);
                assertSame(label, filing.country, listed);
                // only a place in the parentheses changes the list, and only to a country of its own
                if (AntcatLocality.placePart(s) == null) assertSame(label, old, filing.country);
                if (filing.country != old) assertTrue(label, Arrays.asList("Hawaii", "Galapagos Islands", "Rodrigues").contains(filing.country.getName()));
            }
            assertEquals("spellings with a country, of " + spellings.length, 35, found);
            // what the lookup does, said once: old names through their valid name, nothing else
            assertEquals("United States", asTheImportDidBefore("U.S.A.").getName());
            assertEquals("Côte d'Ivoire", asTheImportDidBefore("Ivory Coast (Abidjan)").getName());
            assertEquals("Myanmar", asTheImportDidBefore("Burma").getName());
            assertNull("an old name with no valid name", asTheImportDidBefore("Zaire"));
            assertNull("other capitals", asTheImportDidBefore("ECUADOR (Galapagos Is)"));
            assertNull("no accent where AntWeb has one", asTheImportDidBefore("Cote d'Ivoire"));
            assertNull(asTheImportDidBefore("Democratic Republic of the Congo"));
        } finally {
            for (Map.Entry<Field, Object> e : before.entrySet()) e.getKey().set(null, e.getValue());
        }
    }

    private String note(Set<String> parents, String locality) {
        AntcatLocality.Filing filing = AntcatLocality.filing(countries, adm1s, adm1Rows, parents,
            GeolocaleMgr.validCountry(countries, AntcatLocality.countryPart(locality)), locality);
        return filing == null ? "no country" : filing.note;
    }

    /**
     * Review of #347: a spelling of an island that AntWeb has no row for stays with the country before the parentheses
     * and was not seen anywhere. For a country that has island countries the import's report now lists such a
     * locality; a state AntWeb knows in the country is not listed, nor is any locality of another country.
     */
    public void testAPlaceThatIsNotKnownIsNoted() {
        Set<String> parents = AntcatLocality.parentsOfOwnCountries(countries, adm1s, adm1Rows);
        // the political parents of the flagged islands, and Mauritius through its old state name Rodrigues; France is
        // not one: Reunion is a plain country and its row names no parent
        assertEquals(new TreeSet<>(Arrays.asList("Ecuador", "Mauritius", "United States")), parents);

        assertEquals("United States (Hawaiian Is): filed under United States", note(parents, "United States (Hawaiian Is)"));
        assertEquals("the locality as AntCat writes it", "U.S.A. (Hawaiian Is): filed under United States", note(parents, "U.S.A. (Hawaiian Is)"));
        assertEquals("Ecuador (Galapagos Archipelago): filed under Ecuador", note(parents, "Ecuador (Galapagos Archipelago)"));
        assertEquals("Mauritius (Round I.): filed under Mauritius", note(parents, " Mauritius (Round I.) "));
        assertEquals("United States", filedUnder("United States (Hawaiian Is)"));

        // a place that is a country of its own: filed there, nothing to say
        assertNull(note(parents, "Ecuador (Galapagos Is)"));
        assertNull(note(parents, "U.S.A. (Hawaii)"));
        assertNull(note(parents, "United States (Oahu I.)"));
        assertNull(note(parents, "Mauritius (Rodrigues I.)"));
        assertNull(note(parents, "Hawaii (Kauai I.)"));
        // a state AntWeb knows in the country, by its name or an old name: not listed (or every "(California)" would be)
        assertNull(note(parents, "U.S.A. (California)"));
        assertNull(note(parents, "United States (CA)"));
        assertNull(note(parents, "Ecuador (Pichincha)"));
        assertNull(note(parents, "Ecuador (Pichincha, Quito)"));
        // no parentheses, and countries without island countries
        assertNull(note(parents, "United States"));
        assertNull(note(parents, "Ecuador ()"));
        assertNull(note(parents, "Indonesia (Krakatau I.)"));
        assertNull(note(parents, "Mexico (Hawaii)"));
        assertNull(note(parents, "France (Corsica)"));
        assertEquals("no country", note(parents, "Atlantis (Hawaiian Is)"));
        // the tests' lookup asks for no notes
        assertNull(note(null, "United States (Hawaiian Is)"));
        assertNull(note(new TreeSet<String>(), "United States (Hawaiian Is)"));

        // an island row that names itself or nothing as its country gives no parent; no rows, no parents
        countries.get("Hawaii").setCountry("Hawaii");
        countries.get("Galapagos Islands").setCountry(null);
        assertEquals("only through the old state names", new TreeSet<>(Arrays.asList("Ecuador", "Mauritius", "United States")),
            AntcatLocality.parentsOfOwnCountries(countries, adm1s, adm1Rows));
        assertTrue(AntcatLocality.parentsOfOwnCountries(countries, null, null).isEmpty());
        assertTrue(AntcatLocality.parentsOfOwnCountries(null, adm1s, adm1Rows).isEmpty());

        assertEquals("localities of a country with island countries whose place in parentheses is not a known name: 3",
            AntcatLocality.placesNotKnownLog(3));
    }

    /** The report's section for such localities: each locality once, next to "Country not found". */
    public void testTheReportListsALocalityWhosePlaceIsNotKnown() {
        org.calacademy.antweb.upload.MessageMgr messages = new org.calacademy.antweb.upload.MessageMgr();
        String line = note(AntcatLocality.parentsOfOwnCountries(countries, adm1s, adm1Rows), "United States (Hawaiian Is)");
        messages.addToMessages(org.calacademy.antweb.upload.MessageMgr.antcatPlaceNotKnown, org.calacademy.antweb.util.Html.esc(line));
        messages.addToMessages(org.calacademy.antweb.upload.MessageMgr.antcatPlaceNotKnown, org.calacademy.antweb.util.Html.esc(line));
        org.calacademy.antweb.Group group = new org.calacademy.antweb.Group();
        group.setId(1);
        group.setName("AntCat");
        messages.compileMessages(group);
        String report = messages.getMessagesReport();
        assertTrue(report, report.contains("<b>Place in parentheses not a known island name (filed under the country before it)</b>"));
        assertEquals(report, 1, report.split("United States \\(Hawaiian Is\\): filed under United States", -1).length - 1);
        assertNull("not a correction and not a refusal", new org.calacademy.antweb.upload.MessageMgr().attentionByKey()
            .get(org.calacademy.antweb.upload.MessageMgr.antcatPlaceNotKnown));
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

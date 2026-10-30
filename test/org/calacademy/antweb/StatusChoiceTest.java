/*
 * StatusChoiceTest: the status choice of lists decided by Brian Fisher on 2026-10-30 (StatusSet), without a database:
 * the four choices and their labels, old address values mapped to the nearest choice, the default of each kind of place,
 * the SQL criteria of each choice, and the list headers.
 */
package test.org.calacademy.antweb;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import junit.framework.TestCase;

import org.calacademy.antweb.Museum;
import org.calacademy.antweb.Project;
import org.calacademy.antweb.StatusSet;
import org.calacademy.antweb.TaxaPage;
import org.calacademy.antweb.geolocale.Adm1;
import org.calacademy.antweb.geolocale.Bioregion;
import org.calacademy.antweb.geolocale.Country;
import org.calacademy.antweb.geolocale.Region;
import org.calacademy.antweb.geolocale.Subregion;
import org.calacademy.antweb.home.PlaceTotals;

/**
 * 1. Four choices, plain labels: Valid species, Valid species + morphospecies, All names, Fossil species (genus lists:
 *    genera, morphogenera); address values valid, validmorpho, all, fossil.
 * 2. Old address values map to the nearest choice (all determined -> validmorpho; all indetermined, complete, type ->
 *    all; valid extant -> valid; valid fossil -> fossil); an unknown value gives the page's default.
 * 3. Defaults: geolocales and curated projects Valid species; bioregions, museums, All AntWeb Valid species +
 *    morphospecies; the AntCat list and Fossil Ants their own set (Valid (with fossils)), their address values as they are.
 * 4. Criteria: valid = valid, not fossil; validmorpho = PlaceTotals.speciesStatusCondition (the one rule); all = the five
 *    counted statuses, non-species names not hidden; fossil = valid fossil. Other users of StatusSet are unchanged.
 * 5. Headers say what the list shows ("2,672 Valid species (of 6,960 species and morphospecies)").
 */
public class StatusChoiceTest extends TestCase {

    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, Map<String, Object> values) {
        return (T) Proxy.newProxyInstance(StatusChoiceTest.class.getClassLoader(), new Class<?>[] {type}, (p, method, args) -> {
            switch (method.getName()) {
                case "getParameter": case "getAttribute": return values.get((String) args[0]);
                case "setAttribute": values.put((String) args[0], args[1]); return null;
                case "getSession": return values.get("session");
                default: return null;
            }
        });
    }

    public void testFourChoicesWithPlainLabels() {
        assertEquals(java.util.Arrays.asList("valid", "validmorpho", "all", "fossil"), java.util.Arrays.asList(StatusSet.LIST_CHOICES));
        assertEquals("Valid species", StatusSet.choiceLabel("valid", "species"));
        assertEquals("Valid species + morphospecies", StatusSet.choiceLabel("validmorpho", "species"));
        assertEquals("All names", StatusSet.choiceLabel("all", "species"));
        assertEquals("Fossil species", StatusSet.choiceLabel("fossil", "species"));
        assertEquals("Valid genera", StatusSet.choiceLabel("valid", "genus"));
        assertEquals("Valid genera + morphogenera", StatusSet.choiceLabel("validmorpho", "genus"));
        assertEquals("All names", StatusSet.choiceLabel("all", "genus"));
        assertEquals("Fossil genera", StatusSet.choiceLabel("fossil", "genus"));
        // no removed choice is offered
        for (String removed : new String[] {StatusSet.ALL_DETERMINED, StatusSet.ALL_INDETERMINED, StatusSet.COMPLETE, StatusSet.TYPE}) {
            assertFalse(removed, java.util.Arrays.asList(StatusSet.choicesFor("madants")).contains(removed));
            assertFalse(removed, java.util.Arrays.asList(StatusSet.choicesFor(Project.WORLDANTS)).contains(removed));
        }
        assertEquals(java.util.Arrays.asList(StatusSet.OWN_SET_CHOICES), java.util.Arrays.asList(StatusSet.choicesFor(Project.FOSSILANTS)));
    }

    public void testOldValuesMapToTheNearestChoice() {
        String[][] cases = {
            {"valid", "valid"}, {"valid extant", "valid"}, {"Valid Extant", "valid"}, {"Valid (with fossils)", "valid"},
            {"validmorpho", "validmorpho"}, {"all determined", "validmorpho"},
            {"all", "all"}, {"all indetermined", "all"}, {"complete", "all"}, {"type", "all"}, {"worldants", "all"},
            {"morphotaxon", "all"}, {"indetermined", "all"}, {"unrecognized", "all"},
            {"fossil", "fossil"}, {"valid fossil", "fossil"}, {"  VALID   FOSSIL ", "fossil"},
        };
        for (String[] c : cases) assertEquals(c[0], c[1], StatusSet.listChoice(c[0]));
        assertNull(StatusSet.listChoice(null));
        assertNull(StatusSet.listChoice(" "));
        assertNull(StatusSet.listChoice("nonsense"));
    }

    public void testDefaultOfEachKindOfPlace() {
        assertEquals("valid", StatusSet.defaultChoice(new Country()));
        assertEquals("valid", StatusSet.defaultChoice(new Adm1()));
        assertEquals("valid", StatusSet.defaultChoice(new Region()));
        assertEquals("valid", StatusSet.defaultChoice(new Subregion()));
        assertEquals("validmorpho", StatusSet.defaultChoice(new Bioregion()));
        assertEquals("validmorpho", StatusSet.defaultChoice(new Museum()));
        assertEquals("validmorpho", StatusSet.defaultChoice(null));
        assertEquals(PlaceTotals.GEOLOCALE, StatusSet.placeType(new Country()));
        assertEquals(PlaceTotals.BIOREGION, StatusSet.placeType(new Bioregion()));
        assertEquals(PlaceTotals.MUSEUM, StatusSet.placeType(new Museum()));
        assertEquals(PlaceTotals.PROJECT, StatusSet.placeType(new Project()));
        assertEquals("valid", StatusSet.defaultChoice(PlaceTotals.PROJECT, "madants"));
        assertEquals("validmorpho", StatusSet.defaultChoice(PlaceTotals.PROJECT, Project.ALLANTWEBANTS));
        assertEquals(StatusSet.VALID_WITH_FOSSIL, StatusSet.defaultChoice(PlaceTotals.PROJECT, Project.WORLDANTS));
        assertEquals(StatusSet.VALID_WITH_FOSSIL, StatusSet.defaultChoice(PlaceTotals.PROJECT, Project.FOSSILANTS));
    }

    public void testChoiceComesFromTheAddressOnly() {
        Map<String, Object> session = new HashMap<>();
        session.put("statusSet", "fossil");   // never read
        Map<String, Object> params = new HashMap<>();
        params.put("session", proxy(HttpSession.class, session));
        HttpServletRequest request = proxy(HttpServletRequest.class, params);
        Country country = new Country();
        assertEquals("valid", StatusSet.getStatusSet(request, country));
        assertEquals("validmorpho", StatusSet.getStatusSet(request, new Bioregion()));
        params.put("statusSet", "complete");
        assertEquals("all", StatusSet.getStatusSet(request, country));
        params.put("statusSet", "bogus");
        assertEquals("valid", StatusSet.getStatusSet(request, country));
        // the AntCat list and Fossil Ants: the address value as it is, else their own default
        Project worldants = new Project();
        worldants.setName(Project.WORLDANTS);
        params.put("statusSet", "complete");
        assertEquals("complete", StatusSet.getStatusSet(request, worldants));
        params.remove("statusSet");
        assertEquals(StatusSet.VALID_WITH_FOSSIL, StatusSet.getStatusSet(request, worldants));
        assertEquals(1, session.size());
    }

    public void testCriteriaOfEachChoice() {
        String valid = StatusSet.listStatusSet("valid").getCriteria();
        assertTrue(valid, valid.contains("taxon.status = 'valid'") && valid.contains("fossil = 0"));
        assertEquals(" " + PlaceTotals.speciesStatusCondition("taxon"), StatusSet.listStatusSet("validmorpho").getCriteria());
        assertEquals(" " + PlaceTotals.speciesStatusCondition("specimen"), StatusSet.listStatusSet("validmorpho").getCriteria("specimen"));
        String all = StatusSet.listStatusSet("all").getCriteria();
        for (String s : new String[] {"'valid'", "'morphotaxon'", "'indetermined'", "'unidentifiable'", "'unrecognized'"}) {
            assertTrue(all, all.contains(s));
        }
        String fossil = StatusSet.listStatusSet("fossil").getCriteria();
        assertTrue(fossil, fossil.contains("taxon.status = 'valid'") && fossil.contains("fossil = 1"));
        // All names lists the names that are not species too; the other choices are filtered by their criteria
        assertFalse(TaxaPage.hidesNonSpecies(StatusSet.listStatusSet("all")));
        // other users of StatusSet are unchanged: ALL (counts, museums, bioregions) and worldants' valid
        assertEquals(new StatusSet(StatusSet.ALL).getCriteria(), all);
        assertTrue(TaxaPage.hidesNonSpecies(new StatusSet(StatusSet.ALL)));
        assertFalse(new StatusSet(StatusSet.VALID).getCriteria().contains("fossil"));
        assertEquals("valid fossil", StatusSet.listStatusSet("valid fossil").getValue());
        // on the AntCat list "valid" is its own set (valid names with fossils), not the list choice
        assertEquals("valid", StatusSet.listStatusSet("valid", Project.WORLDANTS).getValue());
        assertEquals(StatusSet.VALID_EXTANT, StatusSet.listStatusSet("valid", "madants").getValue());
    }

    public void testHeadersSayWhatTheListShows() {
        assertEquals("2,672 Valid species (of 6,960 species and morphospecies)", StatusSet.listHeading("valid", 2672, "Species", "Species", 6960));
        assertEquals("6,960 Species (valid species and morphospecies)", StatusSet.listHeading("validmorpho", 6960, "Species", "Species", 6960));
        assertEquals("116 Genera (valid genera and morphogenera)", StatusSet.listHeading("validmorpho", 116, "Genera", "Genus", -1));
        assertEquals("7,215 Species (all names)", StatusSet.listHeading("all", 7215, "Species", "Species", 6960));
        assertEquals("12 Fossil species", StatusSet.listHeading("fossil", 12, "Species", "Species", 6960));
        assertEquals("98 Valid genera", StatusSet.listHeading("valid", 98, "Genera", "Genus", 6960));
        assertEquals("1 Valid genus", StatusSet.listHeading("valid", 1, "Genera", "Genus", -1));
        assertEquals("No Valid species", StatusSet.listHeading("valid", 0, "Species", "Species", -1));
        assertEquals("346 Specimens", StatusSet.listHeading(null, 346, "Specimens", "Specimen", -1));
        assertEquals("40 Valid (with fossils) Species", StatusSet.listHeading(StatusSet.VALID_WITH_FOSSIL, 40, "Species", "Species", -1));
        // the AntCat list keeps its own values and the old form
        assertEquals("40 Valid Species", StatusSet.listHeading("valid", 40, "Species", "Species", 6960, Project.WORLDANTS));
        assertEquals("Valid", StatusSet.choiceLabel("valid", "species", Project.WORLDANTS));
        assertEquals("Valid species", StatusSet.choiceLabel("valid", "species", "madants"));
    }
}

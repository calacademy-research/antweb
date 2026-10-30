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
        // the AntCat list and Fossil Ants: one of their own choices, any other value their default (2026-10-30)
        Project worldants = new Project();
        worldants.setName(Project.WORLDANTS);
        params.put("statusSet", "complete");
        assertEquals(StatusSet.VALID_WITH_FOSSIL, StatusSet.getStatusSet(request, worldants));
        params.put("statusSet", "validmorpho");
        assertEquals(StatusSet.VALID_WITH_FOSSIL, StatusSet.getStatusSet(request, worldants));
        params.put("statusSet", "valid fossil");
        assertEquals(StatusSet.VALID_FOSSIL, StatusSet.getStatusSet(request, worldants));
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
        // on the AntCat list "valid" is not one of its choices: its default, valid names with fossils (ownChoice)
        assertEquals(StatusSet.VALID_WITH_FOSSIL, StatusSet.listStatusSet("valid", Project.WORLDANTS).getValue());
        assertEquals(StatusSet.VALID_EXTANT, StatusSet.listStatusSet("valid", "madants").getValue());
    }

    /**
     * Review of #307 (2026-10-30): the choices Valid and Valid + morphotaxa list the rows the place page's number of the
     * rank counts, by its rule (PlaceTotals), so placeholder rows such as genus "amblyoponinae)" or subfamily
     * "(formicidae)" (status morphotaxon) are not listed under "Valid genera + morphogenera" and the header equals the
     * number; specimen queries of valid extant, valid fossil and Valid (with fossils) filter by status and the name's
     * fossil flag (they fell through to the five counted statuses).
     */
    public void testListCriteriaFollowTheRulesOfTheNumbersPerRank() {
        for (String choice : new String[] {"valid", "validmorpho"}) {
            StatusSet set = StatusSet.listStatusSet(choice);
            assertEquals(choice, " and " + PlaceTotals.countsAsGenus("taxon"), set.listRankCondition("genus", "taxon"));
            assertEquals(choice, " and " + PlaceTotals.countsAsSubfamily("taxon"), set.listRankCondition("subfamily", "taxon"));
            assertEquals(choice, " and " + PlaceTotals.identifiedToGenus("taxon"), set.listRankCondition("species", "taxon"));
            String genus = set.getAndCriteria("taxon", "genus");
            assertTrue(genus, genus.startsWith(" and " + set.getCriteria("taxon")) && genus.contains(PlaceTotals.notPlaceholder("taxon")));
            assertTrue(set.getAndCriteria("taxon", "subfamily").contains("taxon.status = 'valid'"));
            // specimen rows (TaxaPage's specimen half): the rule on their genus and subfamily values
            String sg = set.listRankCondition("genus", "specimen");
            assertTrue(sg, sg.contains(PlaceTotals.genusWithoutParenthesis("specimen")) && sg.contains(PlaceTotals.identifiedToGenus("specimen")));
            assertTrue(set.listRankCondition("subfamily", "specimen").contains("specimen.subfamily"));
        }
        // the Java rule says the same of the placeholders
        assertFalse(PlaceTotals.isCountedGenus("morphotaxon", "amblyoponinae)", "amblyoponinae)"));
        assertFalse(PlaceTotals.isCountedSubfamily("morphotaxon", "(formicidae)"));
        // All names and Fossil add none (All names lists every row, placeholders too)
        assertEquals("", StatusSet.listStatusSet("all").listRankCondition("genus", "taxon"));
        assertEquals("", StatusSet.listStatusSet("fossil").listRankCondition("genus", "taxon"));
        assertEquals("", new StatusSet(StatusSet.ALL).listRankCondition("subfamily", "taxon"));
        assertEquals(StatusSet.listStatusSet("all").getAndCriteria(), StatusSet.listStatusSet("all").getAndCriteria("taxon", "genus"));
        // Valid species + morphospecies hides the names that are not species (named in hidesNonSpecies too)
        assertTrue(TaxaPage.hidesNonSpecies(StatusSet.listStatusSet("validmorpho")));
        // specimen criteria of the sets with a fossil condition (2026-10-30)
        for (String table : new String[] {"specimen", "sp"}) {
            String extant = new StatusSet(StatusSet.VALID_EXTANT).getCriteria(table);
            assertTrue(extant, extant.contains(table + ".status = 'valid'") && extant.contains("fst.taxon_name = " + table + ".taxon_name and fst.fossil = 0"));
            String fossil = new StatusSet(StatusSet.VALID_FOSSIL).getCriteria(table);
            assertTrue(fossil, fossil.contains(table + ".status = 'valid'") && fossil.contains("fst.fossil = 1"));
            assertEquals(" " + table + ".status = 'valid'", new StatusSet(StatusSet.VALID_WITH_FOSSIL).getCriteria(table));
            String all = new StatusSet(StatusSet.ALL).getCriteria(table);
            for (String choice : StatusSet.LIST_CHOICES) {
                String c = StatusSet.listStatusSet(choice).getCriteria(table);
                if (!"all".equals(choice)) assertFalse(choice + " " + table + ": not the five counted statuses", all.equals(c));
            }
        }
        // the taxon criteria are unchanged
        assertEquals(" taxon.status = 'valid' and fossil = 0 ", new StatusSet(StatusSet.VALID_EXTANT).getCriteria("taxon"));
    }

    public void testHeadersSayWhatTheListShows() {
        assertEquals("2,672 Valid species (of 6,960 species and morphospecies)", StatusSet.listHeading("valid", 2672, "Species", "Species", 6960));
        assertEquals("6,960 Species (valid species and morphospecies)", StatusSet.listHeading("validmorpho", 6960, "Species", "Species", 6960));
        assertEquals("116 Genera (valid genera and morphogenera)", StatusSet.listHeading("validmorpho", 116, "Genera", "Genus", -1));
        assertEquals("7,215 Species (all names)", StatusSet.listHeading("all", 7215, "Species", "Species", 6960));
        assertEquals("12 Fossil species", StatusSet.listHeading("fossil", 12, "Species", "Species", 6960));
        assertEquals("98 Valid genera", StatusSet.listHeading("valid", 98, "Genera", "Genus", 6960));
        // subfamily lists (2026-10-30): the rows of the Subfamilies number (valid subfamilies, extant and fossil)
        assertEquals("21 Subfamilies (valid subfamilies, extant and fossil)", StatusSet.listHeading("validmorpho", 21, "Subfamilies", "Subfamily", -1));
        assertEquals("Valid subfamilies, extant and fossil", StatusSet.choiceLabel("validmorpho", "subfamily"));
        assertEquals("1 Valid genus", StatusSet.listHeading("valid", 1, "Genera", "Genus", -1));
        assertEquals("No Valid species", StatusSet.listHeading("valid", 0, "Species", "Species", -1));
        assertEquals("346 Specimens", StatusSet.listHeading(null, 346, "Specimens", "Specimen", -1));
        assertEquals("40 Valid (with fossils) Species", StatusSet.listHeading(StatusSet.VALID_WITH_FOSSIL, 40, "Species", "Species", -1));
        // the AntCat list keeps its own choices and the old form ("valid" is not one of them: its default)
        assertEquals("40 Valid extant Species", StatusSet.listHeading(StatusSet.VALID_EXTANT, 40, "Species", "Species", 6960, Project.WORLDANTS));
        assertEquals("40 Valid (with fossils) Species", StatusSet.listHeading("valid", 40, "Species", "Species", 6960, Project.WORLDANTS));
        assertEquals("346 Specimens", StatusSet.listHeading(null, 346, "Specimens", "Specimen", -1, Project.WORLDANTS));
        assertEquals("Valid (with fossils)", StatusSet.choiceLabel("valid", "species", Project.WORLDANTS));
        assertEquals("Valid species", StatusSet.choiceLabel("valid", "species", "madants"));
    }

    /** Brian Fisher, 2026-10-30: the images view's header names the status choice, in the words of the list's header. */
    public void testImagesViewHeaderNamesTheStatusChoice() {
        assertEquals("40 Valid species imaged (out of 98 valid species)", StatusSet.imagedHeading("valid", 40, 98, "Species", "Species"));
        assertEquals("1,116 Species imaged (out of 2,140 valid species and morphospecies)",
            StatusSet.imagedHeading("validmorpho", 1116, 2140, "Species", "Species"));
        assertEquals("80 Genera imaged (out of 116 valid genera and morphogenera)", StatusSet.imagedHeading("validmorpho", 80, 116, "Genera", "Genus"));
        assertEquals("70 Valid genera imaged (out of 98 valid genera)", StatusSet.imagedHeading("valid", 70, 98, "Genera", "Genus"));
        assertEquals("1 Valid genus imaged (out of 1 valid genus)", StatusSet.imagedHeading("valid", 1, 1, "Genera", "Genus"));
        assertEquals("0 Valid species imaged (out of 3 valid species)", StatusSet.imagedHeading("valid", 0, 3, "Species", "Species"));
        assertEquals("5 Species imaged (out of 7 names, all statuses)", StatusSet.imagedHeading("all", 5, 7, "Species", "Species"));
        assertEquals("3 Fossil species imaged (out of 12 fossil species)", StatusSet.imagedHeading("fossil", 3, 12, "Species", "Species"));
        // subfamilies (2026-10-30): the rows of the Subfamilies number, valid subfamilies
        assertEquals("9 Subfamilies imaged (out of 10 valid subfamilies, extant and fossil)",
            StatusSet.imagedHeading("validmorpho", 9, 10, "Subfamilies", "Subfamily"));
        // the AntCat list keeps its own choices
        assertEquals("40 Valid extant Species imaged (out of 98 valid extant species)",
            StatusSet.imagedHeading(StatusSet.VALID_EXTANT, 40, 98, "Species", "Species", Project.WORLDANTS));
        assertEquals("40 Valid (with fossils) Species imaged (out of 98 valid (with fossils) species)",
            StatusSet.imagedHeading("validmorpho", 40, 98, "Species", "Species", Project.WORLDANTS));
        // a species' specimens (images.do of a species): no status words
        assertEquals("18 Specimens imaged (out of 56 specimens)", StatusSet.imagedHeading(null, 18, 56, "Specimens", "Specimen", "madants", true));
        // the list header and the images header use the same words for the same choice
        for (String choice : StatusSet.LIST_CHOICES) {
            String list = StatusSet.listHeading(choice, 98, "Genera", "Genus", -1);
            String images = StatusSet.imagedHeading(choice, 40, 98, "Genera", "Genus");
            assertTrue(choice + ": " + images, images.contains(" imaged (out of 98 "));
            // the rows named the same way: "Valid genera", "Genera", "Fossil genera"
            String rows = list.substring(list.indexOf(' ') + 1).replaceAll(" \\(.*$", "");
            assertTrue(choice + ": " + list + " / " + images, images.startsWith("40 " + rows + " imaged (out of 98 "));
        }
    }

    /**
     * Brian Fisher, 2026-10-30: a subfamily list of Valid + morphotaxa says "extant and fossil" only when the place has a
     * fossil subfamily ("23 Subfamilies (valid subfamilies, extant and fossil)" on All AntWeb, "8 Subfamilies (valid
     * subfamilies)" on Algeria); header, images view header and selector label alike. Other ranks and choices unchanged.
     */
    public void testSubfamilyWordingNamesFossilsOnlyWhenThereAreSome() {
        assertEquals("23 Subfamilies (valid subfamilies, extant and fossil)",
            StatusSet.listHeading("validmorpho", 23, "Subfamilies", "Subfamilies", -1, Project.ALLANTWEBANTS, true));
        assertEquals("8 Subfamilies (valid subfamilies)", StatusSet.listHeading("validmorpho", 8, "Subfamilies", "Subfamilies", -1, "algeria", false));
        assertEquals("1 Subfamily (valid subfamilies)", StatusSet.listHeading("validmorpho", 1, "Subfamilies", "Subfamily", -1, false));
        assertEquals("7 Subfamilies imaged (out of 8 valid subfamilies)",
            StatusSet.imagedHeading("validmorpho", 7, 8, "Subfamilies", "Subfamily", "algeria", false));
        assertEquals("22 Subfamilies imaged (out of 23 valid subfamilies, extant and fossil)",
            StatusSet.imagedHeading("validmorpho", 22, 23, "Subfamilies", "Subfamily", Project.ALLANTWEBANTS, true));
        assertEquals("Valid subfamilies", StatusSet.choiceLabel("validmorpho", "subfamily", "algeria", false));
        assertEquals("Valid subfamilies, extant and fossil", StatusSet.choiceLabel("validmorpho", "subfamily", Project.ALLANTWEBANTS, true));
        // not known (no request attribute): the wording that names both
        assertTrue(StatusSet.fossilSubfamilies(null));
        Map<String, Object> attrs = new HashMap<>();
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{HttpServletRequest.class}, (p, m, a) -> "getAttribute".equals(m.getName()) ? attrs.get(a[0]) : null);
        assertTrue(StatusSet.fossilSubfamilies(request));
        attrs.put(StatusSet.FOSSIL_SUBFAMILIES_ATTR, Boolean.FALSE);
        assertFalse(StatusSet.fossilSubfamilies(request));
        attrs.put(StatusSet.FOSSIL_SUBFAMILIES_ATTR, Boolean.TRUE);
        assertTrue(StatusSet.fossilSubfamilies(request));
        // no fossil subfamily: one Valid choice on the subfamily list (Valid + morphotaxa hidden); still accepted from the
        // address, with the same label as Valid
        assertEquals(java.util.Arrays.asList("valid", "all", "fossil"), java.util.Arrays.asList(StatusSet.choicesFor("algeria", "subfamily", false)));
        assertEquals(java.util.Arrays.asList("valid", "all", "fossil"), java.util.Arrays.asList(StatusSet.choicesFor("algeria", "Subfamilies", false)));
        assertEquals(java.util.Arrays.asList(StatusSet.LIST_CHOICES), java.util.Arrays.asList(StatusSet.choicesFor(Project.ALLANTWEBANTS, "subfamily", true)));
        assertEquals(java.util.Arrays.asList(StatusSet.LIST_CHOICES), java.util.Arrays.asList(StatusSet.choicesFor("algeria", "genus", false)));
        assertEquals(java.util.Arrays.asList(StatusSet.LIST_CHOICES), java.util.Arrays.asList(StatusSet.choicesFor("algeria", "species", false)));
        assertEquals(java.util.Arrays.asList(StatusSet.OWN_SET_CHOICES), java.util.Arrays.asList(StatusSet.choicesFor(Project.WORLDANTS, "subfamily", false)));
        assertEquals("validmorpho", StatusSet.listChoice("validmorpho"));
        assertEquals(StatusSet.choiceLabel("valid", "subfamily", "algeria", false), StatusSet.choiceLabel("validmorpho", "subfamily", "algeria", false));
        // other ranks and choices do not name fossils either way
        for (boolean f : new boolean[]{true, false}) {
            assertEquals("116 Genera (valid genera and morphogenera)", StatusSet.listHeading("validmorpho", 116, "Genera", "Genus", -1, f));
            assertEquals("8 Valid subfamilies", StatusSet.listHeading("valid", 8, "Subfamilies", "Subfamily", -1, f));
            assertEquals("Valid genera + morphogenera", StatusSet.choiceLabel("validmorpho", "genus", f));
        }
    }

    /**
     * Review of #307 (2026-10-30): the AntCat list (worldants) and Fossil Ants take only their own choices. A value they
     * do not offer (a list choice of another list, a single status, an unknown value) gives their default, Valid (with
     * fossils), for the criteria, the header and the selector's label; their own values in any case or spacing are kept.
     */
    public void testOwnStatusSetListsMapOtherValuesToTheirDefault() {
        for (String list : new String[]{Project.WORLDANTS, Project.FOSSILANTS}) {
            for (String v : new String[]{null, "", "validmorpho", "fossil", "valid", "all", "morphotaxon", "complete", "nonsense"}) {
                assertEquals(list + " " + v, StatusSet.VALID_WITH_FOSSIL, StatusSet.ownChoice(v));
                assertEquals(list + " " + v, StatusSet.VALID_WITH_FOSSIL, StatusSet.listStatusSet(v, list).getValue());
                assertEquals(list + " " + v, "Valid (with fossils)", StatusSet.choiceLabel(v, "species", list));
                if (v != null) assertEquals(list + " " + v, "40 Valid (with fossils) Species", StatusSet.listHeading(v, 40, "Species", "Species", -1, list));
            }
            for (String own : StatusSet.OWN_SET_CHOICES) {
                assertEquals(own, StatusSet.ownChoice(own));
                assertEquals(own, StatusSet.ownChoice("  " + own.toUpperCase(java.util.Locale.ROOT) + " "));
                assertEquals(own, StatusSet.listStatusSet(own, list).getValue());
                assertTrue(java.util.Arrays.asList(StatusSet.OWN_SET_CHOICES).contains(own));
            }
            assertEquals("Valid fossil", StatusSet.choiceLabel("valid fossil", "genus", list));
            assertEquals("Valid extant", StatusSet.choiceLabel("Valid  Extant", "genus", list));
            // every label the selector can show is the label of one of its choices
            java.util.Set<String> offered = new java.util.HashSet<>();
            for (String c : StatusSet.choicesFor(list)) offered.add(StatusSet.choiceLabel(c, "species", list));
            for (String v : new String[]{"validmorpho", "fossil", "Validmorpho", "valid fossil", "x"}) {
                assertTrue(v, offered.contains(StatusSet.choiceLabel(v, "species", list)));
            }
        }
    }
}

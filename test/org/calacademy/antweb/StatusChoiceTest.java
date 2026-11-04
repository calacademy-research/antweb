/*
 * StatusChoiceTest: the views of lists (StatusSet), without a database: decided by Brian Fisher on 2026-10-30 (four
 * status choices) and replaced on 2026-11-02 by the four views Valid, Curated, All specimen names and Fossil: the views
 * and their labels, old address values mapped to the nearest view, the default (Curated everywhere), the SQL criteria of
 * each view, and the list headers.
 */
package test.org.calacademy.antweb;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import junit.framework.TestCase;

import org.calacademy.antweb.ListView;
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
 * 1. Four views, the same labels on every list and rank: Valid, Curated, All specimen names, Fossil; address values
 *    valid, curated, all, fossil.
 * 2. Old address values map to the nearest view (validmorpho, all determined, all indetermined, complete, type -> all;
 *    valid extant -> valid; valid fossil -> fossil); an unknown value gives the default.
 * 3. Default: Curated on every kind of list; the AntCat list and Fossil Ants their own set (Valid (with fossils)).
 * 4. Criteria: valid = valid, not fossil; curated = valid extant or a morphotaxon on a curated entry
 *    (ListView.vettedCondition); all = the five counted statuses, non-species names not hidden; fossil = valid fossil.
 * 5. Headers say what the list shows, the number of the view ("2,700 Species (valid and curated)").
 * 6. The view Not curated (2026-11-02): reached from a genus row's Not curated number, not one of the four buttons.
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
        assertEquals(java.util.Arrays.asList("valid", "curated", "all", "fossil"), java.util.Arrays.asList(StatusSet.LIST_CHOICES));
        for (String rank : new String[] {"species", "genus", "subfamily"}) {
            assertEquals("Valid", StatusSet.choiceLabel("valid", rank));
            assertEquals("Curated", StatusSet.choiceLabel("curated", rank));
            assertEquals("All specimen names", StatusSet.choiceLabel("all", rank));
            assertEquals("Fossil", StatusSet.choiceLabel("fossil", rank));
            // the same four on every list and rank (2026-11-02), subfamily lists without a fossil subfamily too
            assertEquals(java.util.Arrays.asList(StatusSet.LIST_CHOICES), java.util.Arrays.asList(StatusSet.choicesFor("algeria", rank, false)));
        }
        assertEquals("an old address value: the label of the view it opens", "All specimen names", StatusSet.choiceLabel("validmorpho", "species"));
        assertTrue(ListView.choiceTitle("curated", "species").contains("curated morphospecies"));
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
            {"curated", "curated"}, {" Curated ", "curated"}, {"valid and curated", "curated"},
            // the first name of Curated (Vetted, on staging for a few hours on 2026-11-02): old links open Curated
            {"vetted", "curated"}, {"Vetted", "curated"}, {"valid and vetted", "curated"},
            {"validmorpho", "all"}, {"all determined", "all"}, {"all specimen names", "all"},
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
        assertEquals("curated", StatusSet.defaultChoice(new Country()));
        assertEquals("curated", StatusSet.defaultChoice(new Adm1()));
        assertEquals("curated", StatusSet.defaultChoice(new Region()));
        assertEquals("curated", StatusSet.defaultChoice(new Subregion()));
        assertEquals("curated", StatusSet.defaultChoice(new Bioregion()));
        assertEquals("curated", StatusSet.defaultChoice(new Museum()));
        assertEquals("curated", StatusSet.defaultChoice(null));
        assertEquals(PlaceTotals.GEOLOCALE, StatusSet.placeType(new Country()));
        assertEquals(PlaceTotals.BIOREGION, StatusSet.placeType(new Bioregion()));
        assertEquals(PlaceTotals.MUSEUM, StatusSet.placeType(new Museum()));
        assertEquals(PlaceTotals.PROJECT, StatusSet.placeType(new Project()));
        assertEquals("curated", StatusSet.defaultChoice(PlaceTotals.PROJECT, "madants"));
        assertEquals("curated", StatusSet.defaultChoice(PlaceTotals.PROJECT, Project.ALLANTWEBANTS));
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
        assertEquals("curated", StatusSet.getStatusSet(request, country));
        assertEquals("curated", StatusSet.getStatusSet(request, new Bioregion()));
        params.put("statusSet", "complete");
        assertEquals("all", StatusSet.getStatusSet(request, country));
        params.put("statusSet", "valid");
        assertEquals("valid", StatusSet.getStatusSet(request, country));
        params.put("statusSet", "bogus");
        assertEquals("curated", StatusSet.getStatusSet(request, country));
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
        // Curated: valid extant names, and morphotaxa on a curated entry of any list (2026-11-02)
        String curated = StatusSet.listStatusSet("curated").getCriteria();
        assertEquals(" ((taxon.status = 'valid' and taxon.fossil = 0) or (taxon.status = 'morphotaxon' and "
            + ListView.vettedCondition("taxon") + "))", curated);
        String vs = StatusSet.listStatusSet("curated").getCriteria("specimen");
        assertTrue(vs, vs.contains("specimen.status = 'morphotaxon' and " + ListView.vettedCondition("specimen")) && vs.contains("fst.fossil = 0"));
        assertEquals(" (taxon.status = 'morphotaxon' and " + ListView.vettedCondition("taxon") + ")", new StatusSet(StatusSet.VETTED_MORPHO).getCriteria());
        // the old Valid + morphospecies (validmorpho) opens All specimen names; its set stays for the numbers
        assertEquals(StatusSet.ALL_NAMES, StatusSet.listStatusSet("validmorpho").getValue());
        assertEquals(" " + PlaceTotals.speciesStatusCondition("taxon"), new StatusSet(StatusSet.VALID_MORPHO).getCriteria());
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
        for (StatusSet set : new StatusSet[] {StatusSet.listStatusSet("valid"), StatusSet.listStatusSet("curated"), new StatusSet(StatusSet.VALID_MORPHO)}) {
            String choice = set.getValue();
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
        // the set VALID_MORPHO hides the names that are not species (named in hidesNonSpecies too); Curated's criteria
        // have no such names (valid and morphotaxon only)
        assertTrue(TaxaPage.hidesNonSpecies(new StatusSet(StatusSet.VALID_MORPHO)));
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
        assertEquals("2,700 Species (valid and curated)", StatusSet.listHeading("curated", 2700, "Species", "Species", 6960));
        assertEquals("116 Genera (valid and curated)", StatusSet.listHeading("curated", 116, "Genera", "Genus", -1));
        assertEquals("7,215 Species (all specimen names)", StatusSet.listHeading("all", 7215, "Species", "Species", 6960));
        assertEquals("an old address value: the heading of the view it opens", "7,215 Species (all specimen names)",
            StatusSet.listHeading("validmorpho", 7215, "Species", "Species", 6960));
        assertEquals("12 Fossil species", StatusSet.listHeading("fossil", 12, "Species", "Species", 6960));
        assertEquals("98 Valid genera", StatusSet.listHeading("valid", 98, "Genera", "Genus", 6960));
        assertEquals("21 Subfamilies (valid and curated)", StatusSet.listHeading("curated", 21, "Subfamilies", "Subfamily", -1));
        assertEquals("1 Valid genus", StatusSet.listHeading("valid", 1, "Genera", "Genus", -1));
        assertEquals("No Valid species", StatusSet.listHeading("valid", 0, "Species", "Species", -1));
        assertEquals("346 Specimens", StatusSet.listHeading(null, 346, "Specimens", "Specimen", -1));
        assertEquals("40 Valid (with fossils) Species", StatusSet.listHeading(StatusSet.VALID_WITH_FOSSIL, 40, "Species", "Species", -1));
        // the AntCat list keeps its own choices and the old form ("valid" is not one of them: its default)
        assertEquals("40 Valid extant Species", StatusSet.listHeading(StatusSet.VALID_EXTANT, 40, "Species", "Species", 6960, Project.WORLDANTS));
        assertEquals("40 Valid (with fossils) Species", StatusSet.listHeading("valid", 40, "Species", "Species", 6960, Project.WORLDANTS));
        assertEquals("346 Specimens", StatusSet.listHeading(null, 346, "Specimens", "Specimen", -1, Project.WORLDANTS));
        assertEquals("Valid (with fossils)", StatusSet.choiceLabel("valid", "species", Project.WORLDANTS));
        assertEquals("Valid", StatusSet.choiceLabel("valid", "species", "madants"));
        // the wording no longer depends on the place's fossil subfamilies (2026-11-02)
        assertEquals(StatusSet.listHeading("curated", 8, "Subfamilies", "Subfamily", -1, "algeria", false),
            StatusSet.listHeading("curated", 8, "Subfamilies", "Subfamily", -1, "algeria", true));
    }

    /** The images view's header names the view, in the words of the list's header (2026-10-30, views 2026-11-02). */
    public void testImagesViewHeaderNamesTheStatusChoice() {
        assertEquals("40 Valid species imaged (out of 98 valid species)", StatusSet.imagedHeading("valid", 40, 98, "Species", "Species"));
        assertEquals("1,116 Species imaged (out of 2,140 species, valid and curated)", StatusSet.imagedHeading("curated", 1116, 2140, "Species", "Species"));
        assertEquals("80 Genera imaged (out of 116 genera, valid and curated)", StatusSet.imagedHeading("curated", 80, 116, "Genera", "Genus"));
        assertEquals("70 Valid genera imaged (out of 98 valid genera)", StatusSet.imagedHeading("valid", 70, 98, "Genera", "Genus"));
        assertEquals("1 Valid genus imaged (out of 1 valid genus)", StatusSet.imagedHeading("valid", 1, 1, "Genera", "Genus"));
        assertEquals("0 Valid species imaged (out of 3 valid species)", StatusSet.imagedHeading("valid", 0, 3, "Species", "Species"));
        assertEquals("5 Species imaged (out of 7 species, all specimen names)", StatusSet.imagedHeading("all", 5, 7, "Species", "Species"));
        assertEquals("3 Fossil species imaged (out of 12 fossil species)", StatusSet.imagedHeading("fossil", 3, 12, "Species", "Species"));
        // the AntCat list keeps its own choices
        assertEquals("40 Valid extant Species imaged (out of 98 valid extant species)",
            StatusSet.imagedHeading(StatusSet.VALID_EXTANT, 40, 98, "Species", "Species", Project.WORLDANTS));
        assertEquals("40 Valid (with fossils) Species imaged (out of 98 valid (with fossils) species)",
            StatusSet.imagedHeading("validmorpho", 40, 98, "Species", "Species", Project.WORLDANTS));
        // a species' specimens (images.do of a species): no status words
        assertEquals("18 Specimens imaged (out of 56 specimens)", StatusSet.imagedHeading(null, 18, 56, "Specimens", "Specimen", "madants", true));
        // the list header and the images header use the same words for the same view
        for (String choice : StatusSet.LIST_CHOICES) {
            String list = StatusSet.listHeading(choice, 98, "Genera", "Genus", -1);
            String images = StatusSet.imagedHeading(choice, 40, 98, "Genera", "Genus");
            assertTrue(choice + ": " + images, images.contains(" imaged (out of 98 "));
            String rows = list.substring(list.indexOf(' ') + 1).replaceAll(" \\(.*$", "");
            assertTrue(choice + ": " + list + " / " + images, images.startsWith("40 " + rows + " imaged (out of 98 "));
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

    /**
     * The view Not curated (Brian Fisher, 2026-11-02, 19:44): address value notcurated, reached from a genus row's Not
     * curated number; not one of the four buttons; criteria the counted names that are neither valid nor curated
     * morphospecies (ListView.notCuratedCondition), the indet names included; its own label and headers.
     */
    public void testNotCuratedView() {
        assertEquals(StatusSet.LIST_NOT_CURATED, StatusSet.listChoice("notcurated"));
        assertEquals(StatusSet.LIST_NOT_CURATED, StatusSet.listChoice(" Not Curated "));
        assertFalse("not one of the four buttons", java.util.Arrays.asList(StatusSet.LIST_CHOICES).contains(StatusSet.LIST_NOT_CURATED));
        assertFalse(java.util.Arrays.asList(StatusSet.choicesFor("madants")).contains(StatusSet.LIST_NOT_CURATED));
        Country moz = new Country();
        moz.setId(40);
        moz.setName("Mozambique");
        assertEquals(StatusSet.LIST_NOT_CURATED, StatusSet.getStatusSet("notcurated", null, moz));
        assertEquals("the AntCat list keeps its own sets", StatusSet.VALID_WITH_FOSSIL, StatusSet.getStatusSet("notcurated", null, new Project(Project.WORLDANTS)));
        StatusSet set = StatusSet.listStatusSet(StatusSet.LIST_NOT_CURATED);
        assertEquals(StatusSet.NOT_CURATED, set.getValue());
        assertEquals(" " + ListView.notCuratedCondition("taxon"), set.getCriteria("taxon"));
        assertEquals(" " + ListView.notCuratedCondition("specimen"), set.getCriteria("specimen"));
        assertFalse("indet names are listed (not hidden as non-species)", TaxaPage.hidesNonSpecies(set));
        assertTrue("species rows identified to genus", set.listRankCondition("species", "taxon").contains(PlaceTotals.identifiedToGenus("taxon")));
        assertEquals("Not curated", StatusSet.choiceLabel(StatusSet.LIST_NOT_CURATED, "species"));
        assertEquals("12 Species (not curated)", StatusSet.listHeading(StatusSet.LIST_NOT_CURATED, 12, "Species", "Species", -1));
        assertEquals("3 Species imaged (out of 12 species, not curated)", StatusSet.imagedHeading(StatusSet.LIST_NOT_CURATED, 3, 12, "Species", "Species"));
        // old values still open their views
        assertEquals(StatusSet.LIST_VETTED, StatusSet.listChoice("vetted"));
        assertEquals(StatusSet.LIST_ALL, StatusSet.listChoice("validmorpho"));
    }

    /** Fossil ants (Valid (with fossils)) lists morphotaxa too, as its Genera counts them (2026-11-03, B78 option a). */
    public void testValidWithFossilsListsMorphotaxa() {
        String c = new StatusSet(StatusSet.VALID_WITH_FOSSIL).getCriteria("taxon");
        assertTrue(c, c.contains("'valid'") && c.contains("'morphotaxon'"));
        String sp = new StatusSet(StatusSet.VALID_WITH_FOSSIL).getCriteria("specimen");
        assertFalse("specimen queries unchanged: " + sp, sp.contains("morphotaxon"));
    }
}

package test.org.calacademy.antweb.upload;

import java.util.Map;
import java.util.function.Function;

import junit.framework.TestCase;

import org.calacademy.antweb.upload.SpecimenUploadParse;

/**
 * Brian Fisher, 2026-10-22: an upload line whose subfamily is missing or "(indet)" takes the subfamily of its genus when
 * the genus is an AntCat genus ('(indet)decamorium decem' becomes 'myrmicinaedecamorium decem', counted under its genus);
 * a line given as an ant whose genus is not an ant genus in AntCat (Termes, a termite) or is not a name is kept and
 * flagged in the upload report ("Genus not in AntCat (not an ant?)").
 */
public class SubfamilyFromGenusTest extends TestCase {

    /** AntCat as the parse sees it: genus name -> subfamily. */
    static final Function<String, String> ANTCAT = Map.of("decamorium", "myrmicinae", "paltotyreus", "ponerinae")::get;

    private static String subfamily(String uploaded, String genus) {
        // the parse strips parentheses from a subfamily first (GitHub #94): "(indet)" arrives as "indet"
        return SpecimenUploadParse.subfamilyFromGenus(SpecimenUploadParse.stripSubfamilyParentheses(uploaded), genus, ANTCAT);
    }

    public void testMissingOrIndetSubfamilyComesFromTheGenus() {
        assertEquals("myrmicinae", subfamily("(indet)", "decamorium"));
        assertEquals("myrmicinae" + "decamorium" + " decem", subfamily("(indet)", "decamorium") + "decamorium decem");
        assertEquals("ponerinae", subfamily("(indet)", "paltotyreus"));
        assertEquals("ponerinae", subfamily("indet", "Paltotyreus"));
        assertEquals("no subfamily in the file (AntWeb's placeholder)", "myrmicinae", subfamily("(formicidae)", "decamorium"));
        assertEquals("myrmicinae", subfamily(null, "decamorium"));
        assertEquals("myrmicinae", subfamily("", "decamorium"));
        assertEquals("myrmicinae", subfamily("undet", "decamorium"));
    }

    public void testAGivenSubfamilyOrAnUnknownGenusIsKept() {
        assertEquals("a subfamily given is kept here (the genus check reports a different one)", "formicinae", subfamily("formicinae", "decamorium"));
        assertEquals("Termes is not an AntCat genus", "indet", subfamily("(indet)", "termes"));
        assertEquals("(formicidae)", subfamily("(formicidae)", "dont"));
        assertEquals("a genus placeholder is not a genus", "indet", subfamily("(indet)", "(formicinae)"));
        assertEquals("indet", subfamily("(indet)", "indet"));
        assertEquals("indet", subfamily("(indet)", null));
    }

    public void testMissingSubfamily() {
        for (String s : new String[] {null, "", " ", "(formicidae)", "formicidae", "indet", "(indet)", "Indet", "undet", "(undet)"}) {
            assertTrue(String.valueOf(s), SpecimenUploadParse.isMissingSubfamily(s));
        }
        for (String s : new String[] {"myrmicinae", "(myrmicinae)", "gryllinae"}) {
            assertFalse(s, SpecimenUploadParse.isMissingSubfamily(s));
        }
    }

    public void testGenusNotInAntcatIsFlaggedForAntLinesOnly() {
        assertTrue("Termes, given as an ant", SpecimenUploadParse.isGenusNotInAntcat("termes", "formicidae"));
        assertTrue("no family in the file: an ant", SpecimenUploadParse.isGenusNotInAntcat("termes", null));
        assertTrue("junk text", SpecimenUploadParse.isGenusNotInAntcat("dont", ""));
        assertFalse("a declared non-ant", SpecimenUploadParse.isGenusNotInAntcat("termes", "termitidae"));
        assertFalse("a genus placeholder", SpecimenUploadParse.isGenusNotInAntcat("(formicinae)", "formicidae"));
        assertFalse("indet", SpecimenUploadParse.isGenusNotInAntcat("indet", "formicidae"));
        assertFalse("a morphogenus", SpecimenUploadParse.isGenusNotInAntcat("amblyoponine_genus8", "formicidae"));
    }

    public void testAGenusNameInTheSpeciesColumnIsFlagged() {
        java.util.function.Predicate<String> genera = java.util.Set.of("strumigenys", "pyramica", "pheidole")::contains;
        assertTrue("Marcio Pie Ant Lab 2019: Pyramica strumigenys", SpecimenUploadParse.speciesIsGenusName("strumigenys", genera));
        assertTrue("capitals and spaces", SpecimenUploadParse.speciesIsGenusName(" Pheidole ", genera));
        assertFalse("an ordinary epithet", SpecimenUploadParse.speciesIsGenusName("alberti", genera));
        assertFalse("indet placeholder", SpecimenUploadParse.speciesIsGenusName("(indet)", genera));
        assertFalse("a morphospecies code", SpecimenUploadParse.speciesIsGenusName("mg01", genera));
        assertFalse("blank", SpecimenUploadParse.speciesIsGenusName(" ", genera));
        assertFalse("missing", SpecimenUploadParse.speciesIsGenusName(null, genera));
        // production 11-07: Atta cephalotes (cephalotes is also a genus) is a real species, not flagged
        assertFalse("a known combination", SpecimenUploadParse.flagSpeciesIsGenusName("cephalotes", java.util.Set.of("cephalotes")::contains, () -> true));
        assertTrue("an unknown combination", SpecimenUploadParse.flagSpeciesIsGenusName("strumigenys", genera, () -> false));
        assertFalse("not a genus name: no lookup needed", SpecimenUploadParse.flagSpeciesIsGenusName("alberti", genera, () -> { throw new AssertionError(); }));
    }

    /** Brian Fisher, 2026-11-08: a non-ant without a subfamily is filed under its family, never under "(formicidae)"
     *  (production: 5,098 University of Utah and CAS specimens, e.g. "(formicidae)(curculionidae) (curculionidae)"). */
    public void testANonAntWithoutSubfamilyIsFiledUnderItsFamily() {
        assertEquals("(scarabaeidae)", SpecimenUploadParse.nonAntSubfamilyPlaceholder("Scarabaeidae"));
        assertEquals("a higher group given as family", "(coleoptera)", SpecimenUploadParse.nonAntSubfamilyPlaceholder("(coleoptera)"));
        assertEquals("(myriapoda/arachnida)", SpecimenUploadParse.nonAntSubfamilyPlaceholder("(myriapoda/arachnida)"));
        assertNull("an ant keeps the ant placeholder", SpecimenUploadParse.nonAntSubfamilyPlaceholder("formicidae"));
        assertNull("no family is read as Formicidae", SpecimenUploadParse.nonAntSubfamilyPlaceholder(""));
        assertNull(SpecimenUploadParse.nonAntSubfamilyPlaceholder(null));
    }

    /** Brian Fisher, 2026-11-08: a non-ant's species column that repeats its group, or "_indet_", is "(indet)". */
    public void testANonAntSpeciesThatRepeatsItsGroupIsIndet() {
        assertEquals("(indet)", SpecimenUploadParse.nonAntIndetSpecies("(curculionidae)", "(curculionidae)", "curculionidae"));
        assertEquals("(indet)", SpecimenUploadParse.nonAntIndetSpecies("(arthropoda)", "(arthropoda)", "(arthropoda)"));
        assertEquals("Leptinus, University of Utah", "(indet)", SpecimenUploadParse.nonAntIndetSpecies("_indet_", "leptinus", "leiodidae"));
        assertEquals("a real species", "madagascariensis", SpecimenUploadParse.nonAntIndetSpecies("madagascariensis", "zanna", "fulgoridae"));
        assertEquals("ants unchanged", "(myrmicinae)", SpecimenUploadParse.nonAntIndetSpecies("(myrmicinae)", "(myrmicinae)", "formicidae"));
        assertEquals("another group in parentheses is kept", "(sp1)", SpecimenUploadParse.nonAntIndetSpecies("(sp1)", "(curculionidae)", "curculionidae"));
    }
}

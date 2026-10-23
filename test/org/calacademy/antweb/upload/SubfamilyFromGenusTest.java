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
}

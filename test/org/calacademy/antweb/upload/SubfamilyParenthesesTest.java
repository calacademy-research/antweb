package test.org.calacademy.antweb.upload;

import junit.framework.TestCase;
import org.calacademy.antweb.upload.SpecimenUploadParse;

/** GitHub #94: "(Ampulicinae)" in an upload file must not become a stored subfamily. */
public class SubfamilyParenthesesTest extends TestCase {

    public void testParenthesesAreStripped() {
        assertEquals("Ampulicinae", SpecimenUploadParse.stripSubfamilyParentheses("(Ampulicinae)"));
        assertEquals("pepsinae", SpecimenUploadParse.stripSubfamilyParentheses(" (pepsinae) "));
    }

    public void testFormicidaePlaceholderAndPlainNamesAreKept() {
        assertEquals("(formicidae)", SpecimenUploadParse.stripSubfamilyParentheses("(formicidae)"));
        assertEquals("myrmicinae", SpecimenUploadParse.stripSubfamilyParentheses("myrmicinae"));
        assertEquals("()", SpecimenUploadParse.stripSubfamilyParentheses("()"));
        assertNull(SpecimenUploadParse.stripSubfamilyParentheses(null));
    }
}

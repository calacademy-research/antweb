package test.org.calacademy.antweb.curate.specimen;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;
import org.calacademy.antweb.Specimen;
import org.calacademy.antweb.curate.specimen.SpecimenListLookupResult;
import org.calacademy.antweb.curate.specimen.SpecimenListLookupService;
import org.calacademy.antweb.curate.specimen.SpecimenListLookupSource;

public class SpecimenListLookupServiceTest extends TestCase {

    public void testValidLookupPreservesHeaderOrderDuplicatesAndInputCase() throws Exception {
        RecordingSource source = new RecordingSource();
        source.add(specimen("casent0104501", "myrmicinaetest species"));
        source.add(specimen("casent0104502", "myrmicinaeother species"));

        String input = "\ufeffCASENT0104501\n\ncasent0104502\nCASENT0104501\n";
        StringWriter output = new StringWriter();

        SpecimenListLookupResult result = new SpecimenListLookupService().lookup(
                stream(input), source, output);

        assertTrue(result.isSuccess());
        assertEquals(3, result.getRowCount());
        assertEquals(2, source.requestedCodes.size());

        String[] lines = output.toString().split("\n");
        assertEquals(Specimen.getTabDelimHeader(), lines[0]);
        assertTrue(lines[1].startsWith("CASENT0104501\t"));
        assertTrue(lines[2].startsWith("casent0104502\t"));
        assertTrue(lines[3].startsWith("CASENT0104501\t"));
    }

    public void testMissingCodesAreListedAndFoundCodesStillReturned() throws Exception {
        RecordingSource source = new RecordingSource();
        source.add(specimen("casent0104501", "myrmicinaetest species"));
        source.add(specimen("casent0104502", "myrmicinaeother species"));
        StringWriter output = new StringWriter();

        SpecimenListLookupResult result = new SpecimenListLookupService().lookup(
                stream("casent0104501\nMISSING002\ncasent0104502\nmissing001\nmissing002\nCASENT0104501\n"),
                source, output);

        assertTrue(result.isSuccess());
        assertEquals(0, result.getErrorCount());
        assertEquals(6, result.getRowCount());
        assertEquals(3, result.getFoundRowCount());
        assertEquals(3, result.getNotFoundRowCount());
        // Each missing code once, as first typed, in file order.
        assertEquals(List.of("MISSING002", "missing001"), result.getNotFoundCodes());
        assertEquals("MISSING002\nmissing001", result.getNotFoundText());

        String[] lines = output.toString().split("\n");
        assertEquals(4, lines.length);
        assertEquals(Specimen.getTabDelimHeader(), lines[0]);
        assertTrue(lines[1].startsWith("casent0104501\t"));
        assertTrue(lines[2].startsWith("casent0104502\t"));
        assertTrue(lines[3].startsWith("CASENT0104501\t"));
    }

    public void testAllMissingCodesAreReportedBeyondTheErrorCap() throws Exception {
        RecordingSource source = new RecordingSource();
        StringBuilder input = new StringBuilder();
        for (int i = 0; i < 250; i++) input.append("missing").append(i).append('\n');
        StringWriter output = new StringWriter();

        SpecimenListLookupResult result = new SpecimenListLookupService().lookup(
                stream(input.toString()), source, output);

        assertTrue(result.isSuccess());
        assertEquals(250, result.getNotFoundCodes().size());
        assertEquals("missing249", result.getNotFoundCodes().get(249));
        assertEquals(0, result.getFoundRowCount());
        assertEquals("", output.toString());
    }

    public void testAllFoundHasNoMissingCodes() throws Exception {
        RecordingSource source = new RecordingSource();
        source.add(specimen("casent0104501", "myrmicinaetest species"));
        SpecimenListLookupResult result = new SpecimenListLookupService().lookup(
                stream("casent0104501\n"), source, new StringWriter());
        assertTrue(result.isSuccess());
        assertFalse(result.hasNotFoundCodes());
        assertEquals(1, result.getFoundRowCount());
    }

    public void testEscapeHtml() {
        assertEquals("a&lt;b&gt;&amp;&quot;&#39;", SpecimenListLookupResult.escapeHtml("a<b>&\"'"));
    }

    public void testWhitespaceAndEmptyFilesAreRejectedBeforeLookup() throws Exception {
        RecordingSource source = new RecordingSource();
        SpecimenListLookupService service = new SpecimenListLookupService();

        SpecimenListLookupResult malformed = service.lookup(
                stream("casent 0104501\ncasent\t0104502\n"), source, new StringWriter());
        assertFalse(malformed.isSuccess());
        assertEquals(2, malformed.getErrorCount());
        assertEquals(0, source.calls);

        SpecimenListLookupResult empty = service.lookup(
                stream("\n  \n"), source, new StringWriter());
        assertFalse(empty.isSuccess());
        assertEquals("File must contain at least one specimen code.", empty.getErrors().get(0));
        assertEquals(0, source.calls);
    }

    public void testConfiguredRowLimitIsEnforced() throws Exception {
        SpecimenListLookupService service = new SpecimenListLookupService(2);
        RecordingSource source = new RecordingSource();

        SpecimenListLookupResult result = service.lookup(
                stream("one\ntwo\nthree\n"), source, new StringWriter());

        assertFalse(result.isSuccess());
        assertEquals("File contains more than 2 specimen codes.", result.getErrors().get(0));
        assertEquals(0, source.calls);
    }

    public void testFilenameValidationIsCaseInsensitive() {
        assertNull(SpecimenListLookupService.validateFilename("My_Specimens.txt"));
        assertEquals("Filename must contain 'specimen'.",
                SpecimenListLookupService.validateFilename("codes.txt"));
        assertEquals("File must be a plain .txt file.",
                SpecimenListLookupService.validateFilename("my_specimens.tsv"));
    }

    private static ByteArrayInputStream stream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private static Specimen specimen(String code, String taxonName) {
        Specimen specimen = new Specimen();
        specimen.setCode(code);
        specimen.setParentTaxonName(taxonName);
        return specimen;
    }

    private static class RecordingSource implements SpecimenListLookupSource {
        private final Map<String, Specimen> specimens = new LinkedHashMap<>();
        private final List<String> requestedCodes = new ArrayList<>();
        private int calls;

        void add(Specimen specimen) {
            specimens.put(specimen.getCode().toLowerCase(), specimen);
        }

        @Override
        public Map<String, Specimen> findByCodes(List<String> codes) {
            calls++;
            requestedCodes.addAll(codes);
            return specimens;
        }
    }
}

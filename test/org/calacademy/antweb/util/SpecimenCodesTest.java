package test.org.calacademy.antweb.util;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.Group;
import org.calacademy.antweb.curate.NameUpdatesAction;
import org.calacademy.antweb.upload.MessageMgr;
import org.calacademy.antweb.upload.SpecimenNameUpdate;
import org.calacademy.antweb.upload.SpecimenNameUpdateDb;
import org.calacademy.antweb.util.AntwebProps;
import org.calacademy.antweb.util.SpecimenCodes;

/**
 * "N specimens" and the collapsed list of codes on each report line (Brian Fisher, 2026-10-21): one helper
 * (SpecimenCodes) for the Name updates page and every section of the specimen upload report (MessageMgr).
 * Count, cap, escaping, plain codes for lines not uploaded, the report-wide limit, and the same markup on
 * both pages.
 */
public class SpecimenCodesTest extends TestCase {

    static final String APP = "https://www.antweb.org";

    public void testCountAndLinks() {
        assertEquals("no specimens, no list", "", SpecimenCodes.html(0, List.of(), APP, true));
        String one = SpecimenCodes.html(1, List.of("casent0000001"), APP, true);
        assertEquals(" <details class=\"specimen-codes\" style=\"display:inline\"><summary>1 specimen</summary>"
            + "<a href=\"https://www.antweb.org/specimen.do?name=casent0000001\">casent0000001</a></details>", one);
        SpecimenCodes c = new SpecimenCodes();
        c.add("casent0000002");
        c.add("casent0000002");   // the same line reported twice
        c.add("casent0000003");
        c.add(null);
        c.add(" ");
        c.add("casent0000002");   // already listed
        assertEquals(2, c.count());
        String two = c.html(APP, true);
        assertTrue(two, two.contains("<summary>2 specimens</summary>"));
        assertTrue(two, two.contains(">casent0000002</a>, <a href=\"https://www.antweb.org/specimen.do?name=casent0000003\">"));
        assertFalse("no bold: the upload report's sections start at <b>", two.contains("<b>"));
    }

    public void testCap() {
        SpecimenCodes c = new SpecimenCodes();
        for (int i = 0; i < 1250; i++) c.add(String.format("casent%07d", i));
        assertEquals(1250, c.count());
        assertEquals(SpecimenCodes.MAX_CODES, c.codes().size());
        String html = c.html(APP, true);
        assertTrue(html, html.contains("<summary>1,250 specimens</summary>"));
        assertEquals(SpecimenCodes.MAX_CODES, html.split("<a href=", -1).length - 1);
        assertTrue(html, html.contains("casent0000199</a> ... and 1,050 more</details>"));
        assertFalse(html.contains("casent0000200"));
        // A longer list given to html() is cut at the cap too.
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 300; i++) many.add("c" + i);
        String cut = SpecimenCodes.html(300, many, APP, false);
        assertTrue(cut, cut.endsWith("c199 ... and 100 more</details>"));
        assertFalse(cut.contains("c200"));
        // No code listed (the report's limit reached): the count and a plain note.
        String none = SpecimenCodes.html(7, List.of(), APP, true);
        assertTrue(none, none.contains("<summary>7 specimens</summary>" + SpecimenCodes.NOT_LISTED + "</details>"));
    }

    public void testEscapingAndPlainCodes() {
        String code = "x<script>&\"'1";
        String linked = SpecimenCodes.html(1, List.of(code), APP, true);
        assertFalse(linked, linked.contains("<script>"));
        assertTrue(linked, linked.contains("specimen.do?name=x%3Cscript%3E%26%22%271\""));
        assertTrue(linked, linked.contains(">x&lt;script&gt;&amp;&quot;&#39;1</a>"));
        String plain = SpecimenCodes.html(2, List.of("zztest0004!", "a<b"), APP, false);
        assertFalse("lines not uploaded: plain codes", plain.contains("<a "));
        assertTrue(plain, plain.contains("</summary>zztest0004!, a&lt;b</details>"));
    }

    /** The Name updates page's lines use the helper: the same markup as the upload report. */
    public void testNameUpdatesPageUsesTheSameMarkup() {
        SpecimenNameUpdateDb.Run run = new SpecimenNameUpdateDb.Run();
        run.runDate = new Timestamp(System.currentTimeMillis());
        run.mode = SpecimenNameUpdate.CHANGED;
        run.specimens = 3;
        SpecimenNameUpdateDb.Line line = new SpecimenNameUpdateDb.Line();
        line.runDate = run.runDate;
        line.mode = run.mode;
        line.groupId = 1;
        line.oldName = "myrmicinaestrumigenys ohioensis";
        line.newName = "myrmicinaestrumigenys pilinasis";
        line.count = 3;
        line.codes.add("casent0000001");
        line.codes.add("casent0000002");
        String page = NameUpdatesAction.linesHtml(List.of(run), List.of(line), APP);
        assertTrue(page, page.contains(SpecimenCodes.html(3, line.codes, APP, true) + "</li>"));
        assertTrue(page, page.contains("casent0000002</a> ... and 1 more</details>"));
    }

    static Group group() {
        Group g = new Group(9999);
        g.setName("Test group");
        return g;
    }

    /** The upload report: each line lists the specimens that were being read when it was added. */
    public void testUploadReportLines() {
        MessageMgr m = new MessageMgr();
        String app = AntwebProps.getDomainApp();
        String line = "<a href='x'>Ohioensis</a> -> <a href='y'>Pilinasis</a>";
        for (String code : new String[] {"zztest0002", "zztest0007"}) {
            m.setCurrentCode(code);
            m.addToMessages(MessageMgr.recognizedInvalidSpecies, line);
        }
        m.setCurrentCode("zztest0003");
        m.addToMessages(MessageMgr.unrecognizedInvalidSpecies, "Zztestunknown");
        m.setCurrentCode("zztest0009");
        m.addToMessages(MessageMgr.invalidSpeciesName, "a\"b");          // (not uploaded): plain code
        m.addToMessages(MessageMgr.invalidSpecimenCode, "zztest0009");   // already gives the code: no list
        m.addToMapMessages(MessageMgr.subfamilyGenusComboNotInBolton, "Myrmicinae Foo", "foo bar");
        m.setCurrentCode(null);
        m.addToMessages(MessageMgr.groupMorphoGenera, "Myrmicinae Ma01");   // after the file is read
        m.addCode(MessageMgr.groupMorphoGenera, "Myrmicinae Ma01", "zztest0010");
        m.compileMessages(group());
        String report = String.join("\n", m.getMessages());

        assertTrue(report, report.contains(line + " <details class=\"specimen-codes\" style=\"display:inline\"><summary>2 specimens</summary>"
            + "<a href=\"" + app + "/specimen.do?name=zztest0002\">zztest0002</a>, <a href=\"" + app + "/specimen.do?name=zztest0007\">zztest0007</a></details>"));
        assertTrue(report, report.contains("Zztestunknown <details class=\"specimen-codes\" style=\"display:inline\"><summary>1 specimen</summary><a href=\""
            + app + "/specimen.do?name=zztest0003\">zztest0003</a></details>"));
        assertTrue(report, report.contains("a\"b <details class=\"specimen-codes\" style=\"display:inline\"><summary>1 specimen</summary>zztest0009</details>"));
        assertTrue(report, report.contains("<b>Myrmicinae Foo</b>: foo bar <details class=\"specimen-codes\""));
        assertTrue(report, report.contains("Myrmicinae Ma01 <details class=\"specimen-codes\" style=\"display:inline\"><summary>1 specimen</summary>"));
        assertTrue("addCode does not change the line's total", report.contains("Morpho Genera (containing non-alphabetic characters) <font color=green>(uploaded)</font></b> (total:1)"));
        for (String message : m.getMessages()) {
            if (message.contains("Specimen code with characters")) {
                assertFalse("a section that already gives the code gets no list: " + message, message.contains("specimen-codes"));
            }
        }
    }

    /** At most MAX_REPORT_CODES codes in a whole report; later lines give the count only. */
    public void testReportLimit() {
        MessageMgr m = new MessageMgr();
        int lines = MessageMgr.MAX_REPORT_CODES / SpecimenCodes.MAX_CODES + 5;
        for (int l = 0; l < lines; l++) {
            for (int i = 0; i < SpecimenCodes.MAX_CODES + 10; i++) {
                m.setCurrentCode("c" + l + "x" + i);
                m.addToMessages(MessageMgr.unrecognizedInvalidSpecies, String.format("name%03d", l));
            }
        }
        m.setCurrentCode(null);
        m.compileMessages(group());
        String report = String.join("\n", m.getMessages());
        assertEquals(MessageMgr.MAX_REPORT_CODES, report.split("/specimen.do\\?name=", -1).length - 1);
        assertEquals(lines, report.split("<summary>210 specimens</summary>", -1).length - 1);
        assertEquals(5, report.split(SpecimenCodes.NOT_LISTED, -1).length - 1);
        assertTrue("the report stays small: " + report.length(), report.length() < MessageMgr.MAX_REPORT_CODES * 120);
    }
}

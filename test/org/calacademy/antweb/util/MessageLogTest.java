package test.org.calacademy.antweb.util;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.util.AntwebSystem;
import org.calacademy.antweb.util.LogMgr;
import org.calacademy.antweb.util.LogSizeCheck;
import org.calacademy.antweb.util.MessagePageLog;
import org.calacademy.antweb.util.PartsReport;
import org.calacademy.antweb.util.QueryManager;
import org.calacademy.antweb.home.SpecimenDb;

/**
 * Log growth (2026-10-22): messages.txt reached 11 GB in 11 days on staging (lines of ~13 MB: whole
 * admin reports written as the "message" of a page) and 5.4 million lines on production (mostly the
 * same "You must log in" message to bots).
 *
 * MessagePageLog: a short record per message page (tags stripped, one line, cut at 300 characters
 * with the full length, no _csrf or password values), repeats counted per day. LogMgr.capEntry: no
 * log entry over 20,000 characters. The log viewer shows at most 2,000 characters a line. Named
 * queries and the parts report show a bounded number of rows. LogSizeCheck: files over the limit.
 */
public class MessageLogTest extends TestCase {

    public void testShortRecordStripsTagsAndNewlines() {
        String m = "<h2>Sanitize stored page text</h2>\n<p>Preview only;\r\n nothing\thas changed.</p>";
        assertEquals("Sanitize stored page text Preview only; nothing has changed.", MessagePageLog.shortMessage(m));
        assertFalse(MessagePageLog.shortMessage(m).contains("\n"));
    }

    public void testLongMessageIsCutWithItsLength() {
        StringBuilder b = new StringBuilder("<table>");
        for (int i = 0; i < 100000; i++) b.append("<tr><td>casent").append(i).append("</td><td>p</td></tr>");
        String m = b.append("</table>").toString();
        String s = MessagePageLog.shortMessage(m);
        assertTrue(s, s.length() < MessagePageLog.SHOWN + 60);
        assertTrue(s, s.endsWith("... [cut, " + String.format("%,d", m.length()) + " characters]"));
        assertTrue(s, s.startsWith("casent0 p casent1 p"));
        assertFalse(s.contains("<td>"));
        // Short messages are kept whole.
        assertEquals("action not found:x", MessagePageLog.shortMessage("action not found:x"));
        assertEquals("null", MessagePageLog.shortMessage(null));
    }

    public void testTargetHasNoTokens() {
        String t = MessagePageLog.cleanTarget("https://www.antweb.org/sanitizeDescriptions.do?_csrf=abc123&confirm=yes");
        assertEquals("https://www.antweb.org/sanitizeDescriptions.do?confirm=yes", t);
        t = MessagePageLog.cleanTarget("https://www.antweb.org/x.do?a=1&password=secret&token=tok9&b=2");
        assertFalse(t, t.contains("secret") || t.contains("tok9"));
        assertTrue(t, t.contains("a=1") && t.contains("b=2"));
        assertEquals("", MessagePageLog.cleanTarget(null));
        StringBuilder longUrl = new StringBuilder("https://www.antweb.org/query.do?action=");
        for (int i = 0; i < 2000; i++) longUrl.append('z');
        t = MessagePageLog.cleanTarget(longUrl.toString());
        assertTrue(t, t.length() < MessagePageLog.TARGET_MAX + 40 && t.contains("[cut, "));
    }

    public void testRecordHasNoCsrfAnywhere() {
        String m = "Saved. <form><input type=\"hidden\" name=\"_csrf\" value=\"SECRETTOKEN\"></form> <a href=\"x.do?_csrf=SECRETTOKEN\">x</a>";
        String r = MessagePageLog.record(m, "https://www.antweb.org/y.do?_csrf=SECRETTOKEN");
        assertFalse(r, r.contains("SECRETTOKEN"));
        assertTrue(r, r.startsWith("Saved."));
    }

    public void testRepeatsCountedPerDay() {
        MessagePageLog.Counter c = new MessagePageLog.Counter();
        List<String> all = new ArrayList<>();
        for (int i = 0; i < 1000; i++) all.addAll(c.add("2026-10-22", "You must log in", "line " + i));
        // 3 lines, then a running count at 100 and 1,000.
        assertEquals(all.toString(), MessagePageLog.PER_DAY + 2, all.size());
        assertEquals("line 2", all.get(2));
        assertEquals("2026-10-22 - count so far: shown 100 times today: You must log in", all.get(3));
        assertTrue(all.get(4), all.get(4).contains("shown 1,000 times today"));
        assertEquals(1, c.add("2026-10-22", "Another message", "other").size());
        List<String> next = c.add("2026-10-23", "You must log in", "next day");
        assertEquals(next.toString(), 2, next.size());
        assertTrue(next.get(0), next.get(0).startsWith("2026-10-22 - summary: shown 1,000 times"));
        assertTrue(next.get(0).endsWith("You must log in"));
        assertEquals("next day", next.get(1));
    }

    public void testDistinctMessagesBounded() {
        MessagePageLog.Counter c = new MessagePageLog.Counter();
        int written = 0;
        for (int i = 0; i < MessagePageLog.MAX_KEYS + 500; i++) written += c.add("d", "m" + i, "l").size();
        assertEquals(MessagePageLog.MAX_KEYS, written);
        List<String> s = c.summary();
        assertTrue(s.toString(), s.get(s.size() - 1).contains("500 more message pages not logged"));
        assertEquals(20000, MessagePageLog.MAX_KEYS);
    }

    public void testLogEntryCap() {
        assertEquals("short", LogMgr.capEntry("short"));
        assertNull(LogMgr.capEntry(null));
        char[] big = new char[5_000_000];
        Arrays.fill(big, 'x');
        String c = LogMgr.capEntry(new String(big));
        assertTrue(c.length() < LogMgr.MAX_ENTRY + 50);
        assertTrue(c, c.endsWith("... [cut, 5,000,000 characters]"));
        char[] exact = new char[LogMgr.MAX_ENTRY];
        assertEquals(LogMgr.MAX_ENTRY, LogMgr.capEntry(new String(exact)).length());
    }

    public void testLogViewerLineCap() {
        char[] big = new char[13_000_000];
        Arrays.fill(big, 'y');
        String v = AntwebSystem.viewLine(new String(big));
        assertTrue(v.length() < AntwebSystem.VIEW_LINE + 50);
        assertTrue(v, v.endsWith("[cut, 13,000,000 characters]"));
        assertEquals("ok", AntwebSystem.viewLine("ok"));
    }

    public void testNamedQueryRowLimit() {
        assertEquals("", QueryManager.notShownNote(5000, 5000, false));
        assertTrue(QueryManager.notShownNote(5000, 5001, true).contains("Showing the first 5,000 rows</b> (there are more)"));
        assertTrue(QueryManager.limitAtDatabase("imageData"));
        assertFalse(QueryManager.limitAtDatabase("bioregionSpeciesListWithRangeData"));
        assertFalse(QueryManager.limitAtDatabase("checkIntegrity"));
        String n = QueryManager.notShownNote(5000, 234567, false);
        assertTrue(n, n.contains("Showing the first 5,000 of 234,567 rows"));
        assertEquals(5000, QueryManager.MAX_ROWS_SHOWN);
        assertEquals(5000, QueryManager.maxRowsShown("imageData"));
        assertEquals(Integer.MAX_VALUE, QueryManager.maxRowsShown("bioregionSpeciesListWithRangeData"));
    }

    public void testPartsReportLimit() {
        List<SpecimenDb.PartGroup> groups = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            SpecimenDb.PartGroup g = new SpecimenDb.PartGroup();
            g.base = "casent" + (1000000 + i);
            g.parts.add(new SpecimenDb.PartRow(g.base, "a b", 1, "California Academy of Sciences"));
            g.parts.add(new SpecimenDb.PartRow(g.base + "-d01", "a c", 1, "California Academy of Sciences"));
            groups.add(g);
        }
        String page = PartsReport.html(groups, null, "", 10);
        assertTrue(page, page.contains("Showing the first 10 of 30 listed specimens."));
        assertTrue(page, page.contains("(30 specimens, first 10 shown)"));
        assertTrue(page.contains("casent1000009-d01"));
        assertFalse(page.contains("casent1000010-d01"));
        assertFalse(PartsReport.html(groups, null, "").contains("Showing the first"));
    }

    public void testLogSizeCheck() throws Exception {
        File dir = Files.createTempDirectory("logsize").toFile();
        File big = new File(dir, "messages.txt");
        try (RandomAccessFile f = new RandomAccessFile(big, "rw")) { f.setLength(3L * 1024 * 1024); }
        try (RandomAccessFile f = new RandomAccessFile(new File(dir, "small.log"), "rw")) { f.setLength(1024); }
        List<String> p = LogSizeCheck.problems(Arrays.asList(dir, new File(dir, "missing")), 2L * 1024 * 1024, dir, 100);
        assertEquals(p.toString(), 1, p.size());
        assertTrue(p.get(0), p.get(0).contains("messages.txt is 3 MB"));
        assertNull(LogSizeCheck.alert(new ArrayList<>()));
        assertTrue(LogSizeCheck.alert(p).startsWith("Log size check: "));
        // A disk limit of -1% is always exceeded: the disk line is there.
        p = LogSizeCheck.problems(new ArrayList<>(), Long.MAX_VALUE, dir, -1);
        assertEquals(1, p.size());
        assertTrue(p.get(0), p.get(0).contains("% full (over -1%)"));
    }
}

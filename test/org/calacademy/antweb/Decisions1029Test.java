/*
 * Decisions1029Test: Brian Fisher's decisions of 2026-10-29 that need no database: upload date ranges and month/day
 * guesses, the default status of place lists, and the mode of the blank-name-safe cleanups.
 */
package test.org.calacademy.antweb;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import junit.framework.TestCase;

import org.calacademy.antweb.Group;
import org.calacademy.antweb.Project;
import org.calacademy.antweb.StatusSet;
import org.calacademy.antweb.home.BlankNameCleanup;
import org.calacademy.antweb.upload.AntwebUpload;
import org.calacademy.antweb.upload.MessageMgr;
import org.calacademy.antweb.upload.UploadReportPage;
import org.calacademy.antweb.util.DateUtil;

/**
 * 1. Upload dates: a range ("8-11 Feb 2010", with an en dash too) is saved as its first day; the end date column keeps
 *    the last day; a date like 05/04/2013 is still read as month/day and its line is listed in the report's Notes under
 *    "Date read as month/day (please check)" (not counted in the email's summary).
 * 2. Place lists open with every counted status (the place's Species number) and do not carry a status chosen on an
 *    earlier page; a status in the address still works.
 * 4. The blank-name-safe cleanups preview first (compute.log), then delete; etc/cleanup.properties overrides.
 */
public class Decisions1029Test extends TestCase {

    // ------------------------------------------------------------------ 1. upload dates

    public void testRangeIsSavedAsItsFirstDay() {
        String[][] cases = {
            {"8-11 Feb 2010", "2010-02-08"}, {"8–11 Feb 2010", "2010-02-08"}, {"8 - 11 Feb 2010", "2010-02-08"},
            {"8 Feb - 11 Feb 2010", "2010-02-08"}, {"1 Feb - Mar 2010", "2010-02-01"}, {"11-14.ii.2007", "2007-02-11"},
            {"Jan-Mar 2010", "2010-01-01"}, {"2010-2011", "2010-01-01"}, {"2010-02-08 - 2010-02-11", "2010-02-08"},
        };
        for (String[] c : cases) assertEquals(c[0], c[1], DateUtil.getConstructDateStr(c[0]));
        // the first day would come after the last: the last day, as before
        assertEquals("2010-01-02", DateUtil.getConstructDateStr("30 Dec - 2 Jan 2010"));
        // not ranges: unchanged
        assertEquals("1999-07-21", DateUtil.getConstructDateStr("21-Jul-99"));
        assertEquals("2011-05-01", DateUtil.getConstructDateStr("2011-05"));
        assertNull(DateUtil.getConstructDateStr("8-11 Feb 2222"));
    }

    public void testEndDateColumnKeepsTheLastDay() {
        assertEquals("2010-02-11", DateUtil.getConstructEndDateStr("8-11 Feb 2010"));
        assertEquals("2010-02-11", DateUtil.getConstructEndDateStr("8–11 Feb 2010"));
        assertEquals("2007-02-14", DateUtil.getConstructEndDateStr("11-14.ii.2007"));
        assertEquals("2010-03-01", DateUtil.getConstructEndDateStr("1 Feb - Mar 2010"));
        assertEquals("2011-05-02", DateUtil.getConstructEndDateStr("2011-05-02"));
        assertNull(DateUtil.getConstructEndDateStr("2011-02-30"));
    }

    public void testMonthDayGuessIsDetected() {
        assertTrue(DateUtil.isAmbiguousMonthDay("05/04/2013"));
        assertEquals("2013-05-04", DateUtil.getConstructDateStr("05/04/2013"));   // still month/day
        assertTrue(DateUtil.isAmbiguousMonthDay("5/4/13"));
        assertTrue(DateUtil.isAmbiguousMonthDay(" 11/08/2011 "));
        assertFalse(DateUtil.isAmbiguousMonthDay("19/10/2013"));   // day first: the month cannot be 19
        assertFalse(DateUtil.isAmbiguousMonthDay("10/19/2013"));
        assertFalse(DateUtil.isAmbiguousMonthDay("05/05/2013"));   // the same either way
        assertFalse(DateUtil.isAmbiguousMonthDay("13/13/2013"));
        assertFalse(DateUtil.isAmbiguousMonthDay("2013-05-04"));
        assertFalse(DateUtil.isAmbiguousMonthDay("5 Apr 2013"));
        assertFalse(DateUtil.isAmbiguousMonthDay(null));
        assertEquals("Date collected start: 05/04/2013 read as 2013-05-04 (month/day)",
            AntwebUpload.monthDayGuessRow("Date collected start", " 05/04/2013", "2013-05-04"));
        assertNull(AntwebUpload.monthDayGuessRow("Date collected start", "19/10/2013", "2013-10-19"));
    }

    public void testMonthDayGuessIsANoteOfTheReport() {
        MessageMgr m = new MessageMgr();
        m.setCurrentLine(12);
        m.setCurrentCode("CASENT0123456");
        m.addToMessages(MessageMgr.dateReadAsMonthDay, AntwebUpload.monthDayGuessRow("Date collected start", "05/04/2013", "2013-05-04"));
        m.setCurrentLine(13);
        m.setCurrentCode("CASENT0123457");
        m.addToMessages(MessageMgr.dateReadAsMonthDay, AntwebUpload.monthDayGuessRow("Date collected start", "05/04/2013", "2013-05-04"));
        m.setCurrentLine(0);
        m.setCurrentCode(null);
        assertNull(MessageMgr.attention(MessageMgr.dateReadAsMonthDay, MessageMgr.getMessageDisplay(MessageMgr.dateReadAsMonthDay)));
        assertEquals(0, m.getCorrectedCount());
        assertEquals(0, m.getNotLoadedCount());
        assertEquals(2, m.getNoteCount());
        Group group = new Group(9999);
        group.setName("ZZ Test Group");
        m.compileMessages(group);
        MessageMgr.ReportSection sec = null;
        for (MessageMgr.ReportSection s : m.reportSections()) if (MessageMgr.dateReadAsMonthDay.equals(s.key)) sec = s;
        assertNotNull(sec);
        assertEquals(MessageMgr.NOTE, sec.kind);
        assertTrue(sec.headingHtml, sec.headingHtml.contains(MessageMgr.DATE_READ_AS_MONTH_DAY_HEADING));
        assertEquals(1, sec.rows.size());
        MessageMgr.ReportRow row = sec.rows.get(0);
        assertEquals("Date collected start: 05/04/2013 read as 2013-05-04 (month/day)", row.valueHtml);
        assertEquals(2, row.lineCount);
        assertEquals(12, row.lines.get(0)[0]);
        assertTrue(row.codesHtml, row.codesHtml.contains("CASENT0123456") && row.codesHtml.contains("CASENT0123457"));
        UploadReportPage.Facts f = new UploadReportPage.Facts();
        f.title = "test";
        f.parsed = 2;
        f.processed = 2;
        f.insertedSpecimens = 2;
        f.recordCount = 2;
        String page = UploadReportPage.render(f, m);
        int notes = page.indexOf("id=\"rep-note\"");
        int section = page.indexOf("data-key=\"" + MessageMgr.dateReadAsMonthDay + "\"");
        assertTrue(page, notes > 0 && section > notes);
        assertTrue(page, page.contains("Loaded with the date read as month/day."));
        assertTrue(page, page.contains(">Nothing in the report needs your attention.</p>"));
    }

    // ------------------------------------------------------------------ 2. list status

    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, Map<String, Object> values) {
        return (T) Proxy.newProxyInstance(Decisions1029Test.class.getClassLoader(), new Class<?>[] {type}, (p, method, args) -> {
            switch (method.getName()) {
                case "getParameter": case "getAttribute": return values.get((String) args[0]);
                case "setAttribute": values.put((String) args[0], args[1]); return null;
                case "getSession": return values.get("session");
                default: return null;
            }
        });
    }

    public void testListsOpenWithTheStatusesOfTheSpeciesNumber() {
        Map<String, Object> session = new HashMap<>();
        session.put("statusSet", StatusSet.VALID_EXTANT);   // chosen on an earlier page
        Map<String, Object> params = new HashMap<>();
        params.put("session", proxy(HttpSession.class, session));
        HttpServletRequest request = proxy(HttpServletRequest.class, params);
        assertEquals(StatusSet.ALL, StatusSet.getStatusSet(request, null));
        assertEquals(StatusSet.ALL, StatusSet.getStatusSet(null, request, null));
        assertEquals(StatusSet.ALL, StatusSet.getStatusSet(" ", request, null));
        for (String place : new String[] {"Madagascar", "California Academy of Sciences", "Malagasy", "madants", Project.ALLANTWEBANTS}) {
            assertEquals(place, StatusSet.ALL, StatusSet.defaultStatusSet(place));
        }
        assertEquals(StatusSet.VALID_WITH_FOSSIL, StatusSet.defaultStatusSet(Project.WORLDANTS));
        assertEquals(StatusSet.VALID_WITH_FOSSIL, StatusSet.defaultStatusSet(Project.FOSSILANTS));
        // a status in the address still works, and is not kept for the next page
        params.put("statusSet", StatusSet.VALID_EXTANT);
        assertEquals(StatusSet.VALID_EXTANT, StatusSet.getStatusSet(request, null));
        params.remove("statusSet");
        session.clear();
        assertEquals(StatusSet.ALL, StatusSet.getStatusSet(request, null));
        assertTrue(session.isEmpty());
    }

    // ------------------------------------------------------------------ 4. blank-name-safe cleanups

    public void testBlankNameCleanupPreviewsFirst() {
        assertEquals(BlankNameCleanup.Mode.PREVIEW, BlankNameCleanup.mode(null, List.of()));
        assertEquals(BlankNameCleanup.Mode.PREVIEW, BlankNameCleanup.mode(null, null));
        assertEquals(BlankNameCleanup.Mode.PREVIEW, BlankNameCleanup.mode(null, List.of("2026-10-29 05:30:00   Set 2 done")));
        assertEquals(BlankNameCleanup.Mode.ON, BlankNameCleanup.mode(null, List.of("2026-10-30 05:31:00     " + BlankNameCleanup.PREVIEW_LOG + ": ...")));
        assertEquals(BlankNameCleanup.Mode.PREVIEW, BlankNameCleanup.mode("preview", List.of(BlankNameCleanup.PREVIEW_LOG)));
        assertEquals(BlankNameCleanup.Mode.ON, BlankNameCleanup.mode(" On ", List.of()));
    }

    public void testBlankNameCleanupRulesUseNotExists() {
        String[] safe = {BlankNameCleanup.speciesWhere(true), BlankNameCleanup.generaWhere(true), BlankNameCleanup.indetWhere(true),
            BlankNameCleanup.orphanedWhere(true), BlankNameCleanup.morphoNoSpecimen(true)};
        for (String w : safe) {
            assertFalse(w, w.toLowerCase().matches(".*not in \\(select [a-z_, ]+ from specimen.*"));
            assertTrue(w, w.contains("not exists (select 1 from specimen s where"));
            assertTrue(w, w.contains("<> ''"));
        }
        String[] old = {BlankNameCleanup.speciesWhere(false), BlankNameCleanup.generaWhere(false), BlankNameCleanup.indetWhere(false),
            BlankNameCleanup.orphanedWhere(false), BlankNameCleanup.morphoNoSpecimen(false)};
        for (String w : old) assertTrue(w, w.contains("not in (select"));
    }
}

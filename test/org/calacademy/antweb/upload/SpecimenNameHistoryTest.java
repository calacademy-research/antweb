package test.org.calacademy.antweb.upload;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;

import org.calacademy.antweb.Taxon;
import org.calacademy.antweb.curate.NameUpdatesAction;
import org.calacademy.antweb.upload.NameUpdateReason;
import org.calacademy.antweb.upload.SharedNameReport;
import org.calacademy.antweb.upload.SharedNames;
import org.calacademy.antweb.upload.SpecimenNameHistory;
import org.calacademy.antweb.upload.SpecimenNameHistory.Event;
import org.calacademy.antweb.upload.SpecimenNameUpdate;
import org.calacademy.antweb.upload.UploadNameReasons;
import org.calacademy.antweb.util.Html;

/**
 * The specimen page's "Name History" section (SpecimenNameHistory.render), without a database: nothing
 * when there is no history, only applied moves (never report-only rows), newest first as given, the same
 * line as the Name updates page, the uploaded name with the upload report's reason, the shared-name hold
 * in the Name updates page's words, previous determinations, and every value escaped. The query is
 * SpecimenNameHistoryDbTest.
 */
public class SpecimenNameHistoryTest extends TestCase {

    static final String WAS = "myrmicinaewasmannia auropunctata";
    static final String ALLO = "myrmicinaeallomerus auropunctatus";
    static final String OLDER = "myrmicinaeochetomyrmex auropunctatus";
    static final String REASON = "combination in Allomerus (Longino & Branstetter 2026)";

    static Event ev(String day, String mode, String uploaded, String oldName, String newName, String status, String reason, Integer id) {
        return new Event(Timestamp.valueOf(day + " 05:31:07"), mode, uploaded, oldName, newName, status, reason, id);
    }

    static List<Event> list(Event... events) {
        List<Event> l = new ArrayList<>();
        for (Event e : events) l.add(e);
        return l;
    }

    public void testNothingWithoutHistory() {
        assertEquals("", SpecimenNameHistory.render(ALLO, null, new ArrayList<>(), new ArrayList<>(), null, null));
        assertEquals("", SpecimenNameHistory.render(ALLO, ALLO, null, null, null, null));   // uploaded under the name it is filed under
        assertEquals("", SpecimenNameHistory.render(ALLO, "", null, null, null, null));
    }

    public void testReportOnlyRowsAreNotShown() {
        List<Event> events = list(
            ev("2026-10-21", SpecimenNameUpdate.WOULD_CHANGE, WAS, WAS, ALLO, "obsolete combination", REASON, 451279),
            ev("2026-10-21", SpecimenNameUpdate.KEPT_VALID, WAS, ALLO, OLDER, "valid", null, null));
        assertEquals("", SpecimenNameHistory.render(ALLO, null, null, events, null, null));
    }

    public void testAppliedMoveIsTheNameUpdatesPageLine() {
        String html = SpecimenNameHistory.render(ALLO, null, null,
            list(ev("2026-10-22", SpecimenNameUpdate.CHANGED, WAS, WAS, ALLO, "obsolete combination", REASON, 451279)), null, null);
        assertTrue(html, html.contains("<h3>" + SpecimenNameHistory.HEADING + ": </h3>"));
        assertTrue(html, html.contains("<li><b>Name update 2026-10-22: </b></li>"));
        String line = NameUpdatesAction.nameLine(WAS, ALLO) + UploadNameReasons.reasonPart(REASON, 451279, WAS, WAS, ALLO);
        assertTrue(html, html.contains(line));
        // Each name links to its AntWeb page, the reference to AntCat.
        assertTrue(html, html.contains("/description.do?taxonName=" + "myrmicinaewasmannia+auropunctata"));
        assertTrue(html, html.contains("/description.do?taxonName=" + "myrmicinaeallomerus+auropunctatus"));
        assertTrue(html, html.contains("https://www.antcat.org/catalog/451279"));
        assertTrue(html, html.contains(SpecimenNameHistory.NIGHTLY));
    }

    public void testNewestFirstAsGiven() {
        String html = SpecimenNameHistory.render(ALLO, null, null, list(
            ev("2026-10-22", SpecimenNameUpdate.CHANGED, OLDER, WAS, ALLO, "obsolete combination", REASON, 451279),
            ev("2026-10-21", SpecimenNameUpdate.CHANGED, OLDER, OLDER, WAS, "synonym", null, null)), null, null);
        int newer = html.indexOf("Name update 2026-10-22");
        int older = html.indexOf("Name update 2026-10-21");
        assertTrue(html, newer > 0 && older > newer);
        // A row recorded without a reason shows the AntCat status, as the Name updates page does.
        assertTrue(html, html.contains("(AntCat: synonym)"));
    }

    public void testUploadedNamePointsToTheMoveThatMadeIt() {
        AtomicInteger asked = new AtomicInteger();
        UploadNameReasons reasons = new UploadNameReasons((o, n) -> { asked.incrementAndGet(); return new NameUpdateReason("x", null); });
        String html = SpecimenNameHistory.render(ALLO, WAS, null,
            list(ev("2026-10-22", SpecimenNameUpdate.CHANGED, WAS, WAS, ALLO, "obsolete combination", REASON, 451279)), null, reasons);
        assertTrue(html, html.contains("<li><b>Filed under: </b></li>"));
        assertTrue(html, html.contains(Taxon.makeDescriptionLink(ALLO) + " (uploaded as " + Taxon.makeDescriptionLink(WAS) + ")"
            + ": see the name update of 2026-10-22 below"));
        assertEquals("the move's reason is on its own line, not worked out again", 0, asked.get());
        assertTrue(html, html.indexOf("Filed under") < html.indexOf("Name update 2026-10-22"));
    }

    public void testUploadedNameHasTheUploadReportReason() {
        // Moved at upload (no nightly row): the reason of the upload report's "Recognized invalid species" line.
        UploadNameReasons reasons = new UploadNameReasons((o, n) -> {
            assertEquals(WAS, o);
            assertEquals(ALLO, n);
            return new NameUpdateReason(REASON, 451279);
        });
        String html = SpecimenNameHistory.render(ALLO, WAS, null, new ArrayList<>(), null, reasons);
        assertTrue(html, html.contains(" (uploaded as " + Taxon.makeDescriptionLink(WAS) + ")"
            + UploadNameReasons.reasonPart(new NameUpdateReason(REASON, 451279), WAS, WAS, ALLO)));
        // No reason source (or it fails): the names alone.
        String plain = SpecimenNameHistory.render(ALLO, WAS, null, null, null, null);
        assertTrue(plain, plain.contains(" (uploaded as " + Taxon.makeDescriptionLink(WAS) + ")</li>"));
        UploadNameReasons broken = new UploadNameReasons((o, n) -> { throw new IllegalStateException("no history"); });
        String fallback = SpecimenNameHistory.render(ALLO, WAS, null, null, null, broken);
        assertTrue(fallback, fallback.contains(" (uploaded as " + Taxon.makeDescriptionLink(WAS) + ")</li>"));
    }

    public void testSharedNameHoldWhileStillFiledThere() {
        String sauteri = "ponerinaepachycondyla sauteri";
        Map<String, List<String>> m = new HashMap<>();
        m.put("Pachycondyla sauteri", List.of("Cryptopone sauteri", "Ectomomyrmex sauteri"));
        SharedNames shared = SharedNames.of(m);
        String stored = "name is shared by two taxa; please confirm the identification: Cryptopone sauteri or Ectomomyrmex sauteri";
        Event held = ev("2026-10-22", SpecimenNameUpdate.SHARED, sauteri, sauteri, "", "obsolete combination", stored, null);
        String html = SpecimenNameHistory.render(sauteri, null, null, list(held), shared, null);
        assertTrue(html, html.contains("<li><b>" + Html.esc(SharedNameReport.HEADING) + ": </b></li>"));
        assertTrue(html, html.contains(SharedNameReport.line(sauteri, sauteri, shared, stored)));
        assertTrue(html, html.contains(Html.esc(SharedNameReport.EXPLANATION)));
        // Filed under another name since (a later upload): the hold no longer applies.
        assertEquals("", SpecimenNameHistory.render("ponerinaecryptopone sauteri", null, null, list(held), shared, null));
    }

    public void testPreviousDeterminationsKeepTheirMarkup() {
        String html = SpecimenNameHistory.render(ALLO, null,
            List.of("Nylanderia Emery, 1906: US_CA01 by Ward on 2025-12-10", "Nylanderia: US_CA01 by Fisher on 2000-11-10"), null, null, null);
        // The pattern of tests/e2e/test_previous_determinations.py.
        Matcher mm = Pattern.compile("<b>Previous determination: </b></li>\\s*<li>(.*?)</li>", Pattern.DOTALL).matcher(html);
        assertTrue(html, mm.find());
        assertEquals("Nylanderia Emery, 1906: US_CA01 by Ward on 2025-12-10<br>Nylanderia: US_CA01 by Fisher on 2000-11-10", mm.group(1));
        assertTrue(html, html.contains("<ul class=\"previous_determinations\">"));
    }

    public void testEverythingEscaped() {
        String evil = "<script>alert(1)</script>";
        String html = SpecimenNameHistory.render(ALLO, "myrmicinae\"><img src=x onerror=alert(1)>", List.of(evil, "a & b"),
            list(ev("2026-10-22", SpecimenNameUpdate.CHANGED, WAS, WAS + "<b>", ALLO, "<i>status</i>", null, null),
                 ev("2026-10-21", SpecimenNameUpdate.CHANGED, WAS, OLDER, WAS, null, "reason " + evil, 5)),
            null, null);
        assertFalse(html, html.contains("<script>"));
        assertFalse(html, html.contains("<img"));
        assertFalse(html, html.contains("<i>status</i>"));
        assertFalse(html, html.contains("auropunctata<b>"));
        assertTrue(html, html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertTrue(html, html.contains("a &amp; b"));
        assertTrue(html, html.contains("(AntCat: &lt;i&gt;status&lt;/i&gt;)"));
    }
}

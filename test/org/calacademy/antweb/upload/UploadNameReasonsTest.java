package test.org.calacademy.antweb.upload;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.TestCase;

import org.calacademy.antweb.Taxon;
import org.calacademy.antweb.curate.NameUpdatesAction;
import org.calacademy.antweb.upload.CurrentValidNameRule;
import org.calacademy.antweb.upload.NameUpdateReason;
import org.calacademy.antweb.upload.SpecimenNameUpdate;
import org.calacademy.antweb.upload.SpecimenNameUpdate.Outcome;
import org.calacademy.antweb.upload.SpecimenNameUpdateDb;
import org.calacademy.antweb.upload.UploadNameReasons;

/**
 * Follow-ups of the nightly name update (Brian Fisher, 2026-10-20), without a database:
 *  1. the specimen upload report's "Recognized invalid species" lines carry the same reason as the Name
 *     updates page ("old -> new: synonym: junior synonym of NEW (Author year)", the reference linked to
 *     AntCat), worked out once per pair, and fall back to the plain "old -> new" when it cannot be read;
 *  2. the Name updates page's collapsed list "Not moved: filed under a name AntCat calls valid", and the
 *     decision behind it (a specimen already where its uploaded name points is not listed).
 * The database half is SpecimenNameUpdateDbTest. Run with -Dantweb.test=<the test/ folder>.
 */
public class UploadNameReasonsTest extends TestCase {

    static final String OHIO = "myrmicinaestrumigenys ohioensis";
    static final String PILI = "myrmicinaestrumigenys pilinasis";

    /** Strumigenys ohioensis -> S. pilinasis from the real AntCat histories (fixtures/antcat-history.tsv). */
    static NameUpdateReason ohioensisReason() throws Exception {
        return NameUpdateReason.of(null, AntcatHistoryTest.info("Strumigenys ohioensis"), AntcatHistoryTest.info("Strumigenys pilinasis"));
    }

    static String plain(String oldName, String newName) {
        return Taxon.makeDescriptionLink(oldName) + " -> " + Taxon.makeDescriptionLink(newName);
    }

    public void testReportLineHasTheReasonLinkedToAntCat() throws Exception {
        NameUpdateReason r = ohioensisReason();
        assertEquals("synonym: junior synonym of Strumigenys pilinasis (Booher 2019)", r.text);
        UploadNameReasons reasons = new UploadNameReasons((o, n) -> r);
        String line = reasons.line(plain(OHIO, PILI), OHIO, PILI);
        assertTrue(line, line.startsWith(plain(OHIO, PILI) + ": <span class=\"name-update-reason\">synonym: junior synonym of "
            + "<i>Strumigenys pilinasis</i> (<a href=\"https://www.antcat.org/catalog/" + r.antcatId + "\">Booher 2019</a>)</span>"));
        assertFalse("no bold: the report's sections start at <b>", line.contains("<b>"));
    }

    /** Same pair, same text: the report line's reason is the Name updates page's, from the same code. */
    public void testSameReasonAsTheNameUpdatesPage() throws Exception {
        NameUpdateReason r = ohioensisReason();
        String reportLine = new UploadNameReasons((o, n) -> r).line(plain(OHIO, PILI), OHIO, PILI);

        SpecimenNameUpdateDb.Run run = new SpecimenNameUpdateDb.Run();
        run.runDate = new Timestamp(System.currentTimeMillis());
        run.mode = SpecimenNameUpdate.CHANGED;
        run.specimens = 1;
        SpecimenNameUpdateDb.Line line = new SpecimenNameUpdateDb.Line();
        line.runDate = run.runDate;
        line.mode = run.mode;
        line.groupId = 1;
        line.oldName = OHIO;
        line.newName = PILI;
        line.uploadedName = OHIO;
        line.reason = r.text;
        line.antcatId = r.antcatId;
        line.count = 1;
        line.codes.add("casent0000001");
        String page = NameUpdatesAction.linesHtml(List.of(run), List.of(line), "https://www.antweb.org");
        assertTrue(page, page.contains(reportLine));
    }

    public void testOncePerPair() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        NameUpdateReason r = ohioensisReason();
        UploadNameReasons reasons = new UploadNameReasons((o, n) -> { calls.incrementAndGet(); return r; });
        String first = reasons.line(plain(OHIO, PILI), OHIO, PILI);
        for (int i = 0; i < 50; i++) assertEquals(first, reasons.line(plain(OHIO, PILI), OHIO, PILI));
        assertEquals(1, calls.get());
        reasons.line("a -> b", "myrmicinaestrumigenys other", PILI);
        assertEquals("another pair is worked out once more", 2, calls.get());
    }

    public void testPlainLineWhenTheReasonCannotBeRead() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        UploadNameReasons broken = new UploadNameReasons((o, n) -> { calls.incrementAndGet(); throw new IllegalStateException("history not read"); });
        assertEquals(plain(OHIO, PILI), broken.line(plain(OHIO, PILI), OHIO, PILI));
        assertEquals("after one failure the rest of the report is plain, without trying again",
            "x -> y", broken.line("x -> y", "formicinaecamponotus x", "formicinaecamponotus y"));
        assertEquals(1, calls.get());

        UploadNameReasons none = new UploadNameReasons((o, n) -> null);
        assertEquals("no reason", plain(OHIO, PILI), none.line(plain(OHIO, PILI), OHIO, PILI));
        assertEquals("[empty string] -> y", none.line("[empty string] -> y", "", PILI));
        assertNull(none.line(null, OHIO, PILI));
        assertEquals("", UploadNameReasons.reasonPart((NameUpdateReason) null, OHIO, OHIO, PILI));
    }

    // ------------------------------------------------------------ 2. kept on a valid name

    /** Filed under a valid name the uploaded name no longer points to: listed. Already where it points: not. */
    public void testCurrentValidOnlyWhenTheUploadedNamePointsElsewhere() throws Exception {
        String old = SpecimenNameUpdateTest.OLD;   // synonym of NEW
        String now = SpecimenNameUpdateTest.NEW;
        CurrentValidNameRule.Result rule = SpecimenNameUpdateTest.rule(old);
        assertEquals(Outcome.CURRENT_VALID, SpecimenNameUpdate.decide(old, "formicinaedinomyrmex gigas", n -> false, rule, false, true).outcome);
        assertEquals("moved at upload: already right, not listed", Outcome.UNCHANGED,
            SpecimenNameUpdate.decide(old, now, n -> false, rule, false, true).outcome);
    }

    static SpecimenNameUpdateDb.Line kept(int group, String current, String uploaded, String pointsTo, String... codes) {
        SpecimenNameUpdateDb.Line l = new SpecimenNameUpdateDb.Line();
        l.mode = SpecimenNameUpdate.KEPT_VALID;
        l.groupId = group;
        l.oldName = current;
        l.uploadedName = uploaded;
        l.newName = pointsTo;
        for (String c : codes) l.codes.add(c);
        l.count = codes.length;
        return l;
    }

    public void testKeptValidSection() {
        String dino = "formicinaedinomyrmex gigas";
        String gigas = "formicinaecamponotus gigas";
        String cruentatus = "formicinaecamponotus cruentatus";
        List<SpecimenNameUpdateDb.Line> lines = new ArrayList<>();
        lines.add(kept(1, dino, gigas, cruentatus, "casent0000001", "casent0000002"));
        lines.add(kept(7, dino, gigas, cruentatus, "jtl0000001"));
        String html = NameUpdatesAction.keptValidHtml(lines, "https://www.antweb.org");
        assertTrue("collapsed", html.startsWith("<details") && html.endsWith("</details>") && !html.contains("<details open"));
        assertTrue(html, html.contains(">Not moved: filed under a name AntCat calls valid (2 names)</summary>"));
        assertTrue("one plain explanation line", html.contains("<p>" + NameUpdatesAction.KEPT_VALID_HELP.replace("'", "&#39;") + "</p>")
            || html.contains("<p>" + NameUpdatesAction.KEPT_VALID_HELP + "</p>"));
        assertTrue(html, html.contains("<li class=\"name-update-kept-valid\">" + Taxon.makeDescriptionLink(dino)
            + " (uploaded as " + Taxon.makeDescriptionLink(gigas) + ", which AntCat now gives as " + Taxon.makeDescriptionLink(cruentatus) + ")"));
        assertTrue("specimen count", html.contains("<summary>2 specimens</summary>"));
        assertTrue(html.contains("<summary>1 specimen</summary>"));
        assertTrue("per group", html.indexOf("<h4>") != html.lastIndexOf("<h4>"));
        assertTrue(html.contains("/specimen.do?name=jtl0000001"));
        assertTrue("one name", NameUpdatesAction.keptValidHtml(lines.subList(0, 1), "").contains("(1 name)</summary>"));
        assertEquals("nothing kept: no section", "", NameUpdatesAction.keptValidHtml(new ArrayList<>(), ""));
    }
}

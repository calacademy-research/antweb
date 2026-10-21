package test.org.calacademy.antweb.upload;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.Group;
import org.calacademy.antweb.Status;
import org.calacademy.antweb.Taxon;
import org.calacademy.antweb.curate.NameUpdatesAction;
import org.calacademy.antweb.upload.CurrentValidNameRule;
import org.calacademy.antweb.upload.MessageMgr;
import org.calacademy.antweb.upload.SharedNameReport;
import org.calacademy.antweb.upload.SharedNames;
import org.calacademy.antweb.upload.SpecimenNameUpdate;
import org.calacademy.antweb.upload.SpecimenNameUpdateDb;

/**
 * Names of two or more AntCat taxa in the specimen upload report and on the Name updates page (Brian
 * Fisher, 2026-10-20: "the two reports should share code so that if you fix one it fixes the other").
 * Both callers go through SharedNameReport: the same detection, the same words, the same links. Without a
 * database; the database half is SpecimenNameUpdateDbTest.testUploadAndPageGiveTheSameSharedNameLine.
 * Rows of the AntCat export of 2026-10-20 (fixtures/antcat-shared-names.tsv). Run with -Dantweb.test=<test/>.
 */
public class SharedNameReportTest extends TestCase {

    static final String SAUTERI = "ponerinaepachycondyla sauteri";

    /** The rule as the upload decides it for Pachycondyla sauteri: the taxon table holds one of its two taxa. */
    static CurrentValidNameRule.Result sauteriRule() throws Exception {
        return CurrentValidNameRule.decide(SAUTERI, new CurrentValidNameRule.Lookup() {
            public CurrentValidNameRule.TaxonRow getTaxon(String n) {
                if (SAUTERI.equals(n)) return new CurrentValidNameRule.TaxonRow(Status.OBSOLETE_COMBINATION, "ponerinaecryptopone sauteri");
                if ("ponerinaecryptopone sauteri".equals(n)) return new CurrentValidNameRule.TaxonRow(Status.VALID, null);
                return null;
            }
            public String getCurrentValidTaxonName(String n) { return "ponerinaecryptopone sauteri".equals(n) ? n : null; }
            public boolean isHomonym(String n) { return false; }
        });
    }

    /** The page's text for one held line, without the specimen codes it adds after it. */
    static String pageLine(String html) {
        String li = html.substring(html.indexOf("<li class=\"name-update-shared\">") + "<li class=\"name-update-shared\">".length());
        return li.substring(0, li.indexOf(" <details"));
    }

    static SpecimenNameUpdateDb.Line heldLine(String name, String reason) {
        SpecimenNameUpdateDb.Line line = new SpecimenNameUpdateDb.Line();
        line.runDate = Timestamp.valueOf("2026-10-20 05:20:00");
        line.mode = SpecimenNameUpdate.SHARED;
        line.groupId = 1;
        line.oldName = name;
        line.uploadedName = name;
        line.newName = "";
        line.reason = reason;
        line.count = 1;
        line.codes.add("casent0009");
        return line;
    }

    public void testUploadKeepsASharedNameAndSaysWhy() throws Exception {
        SharedNames shared = SharedNamesTest.fixture();
        CurrentValidNameRule.Result rule = sauteriRule();
        assertEquals("without the check the upload would move it to the one taxon it holds", CurrentValidNameRule.Kind.REPLACE, rule.kind);
        String line = SharedNameReport.uploadLine(SAUTERI, rule, shared);
        assertEquals(Taxon.makeDescriptionLink(SAUTERI)
            + ": <span class=\"name-update-reason\">name is shared by two taxa; please confirm the identification: Cryptopone sauteri or Ectomomyrmex sauteri</span>"
            + ". <span class=\"shared-name-taxa\">AntCat: "
            + "<a href=\"https://www.antcat.org/catalog/509406\"><i>Pachycondyla sauteri</i> Wheeler, 1906</a>, obsolete combination, now <i>Cryptopone sauteri</i>; "
            + "<a href=\"https://www.antcat.org/catalog/509563\"><i>Pachycondyla sauteri</i> Forel, 1912</a>, obsolete combination, now <i>Ectomomyrmex sauteri</i></span>",
            line);
        assertFalse("no bold: the report's sections start at <b>", line.contains("<b>"));

        // Not held: the name is not shared, or the rule keeps the name anyway.
        assertNull(SharedNameReport.uploadLine(SAUTERI, rule, SharedNames.none()));
        assertNull(SharedNameReport.uploadLine(SAUTERI, rule, null));
        CurrentValidNameRule.Result keep = CurrentValidNameRule.decide("formicinaeformica picea", new CurrentValidNameRule.Lookup() {
            public CurrentValidNameRule.TaxonRow getTaxon(String n) { return new CurrentValidNameRule.TaxonRow(Status.VALID, null); }
            public String getCurrentValidTaxonName(String n) { return null; }
            public boolean isHomonym(String n) { return false; }
        });
        assertNull("a valid name that stays is not listed (as on the page)", SharedNameReport.uploadLine("formicinaeformica picea", keep, shared));
    }

    /** Brian's requirement: the same name gives the same text in the upload report and on nameUpdates.do. */
    public void testUploadReportAndPageGiveTheSameText() throws Exception {
        SharedNames shared = SharedNamesTest.fixture();
        String upload = SharedNameReport.uploadLine(SAUTERI, sauteriRule(), shared);

        List<SpecimenNameUpdateDb.Line> lines = new ArrayList<>();
        lines.add(heldLine(SAUTERI, SharedNames.reason(shared.candidatesOf("Pachycondyla sauteri"))));
        String page = NameUpdatesAction.sharedHtml(lines, "https://www.antweb.org", shared);
        assertEquals(upload, pageLine(page));
        assertTrue(page, page.contains("<h3 style=\"" ) && page.contains(SharedNameReport.HEADING + "</h3>"));
        assertTrue(page, page.contains("<p>" + SharedNameReport.EXPLANATION + "</p>"));
        assertTrue(page, page.contains("/specimen.do?name=casent0009\">casent0009</a>"));

        // The upload report: the heading and the line, specimens counted.
        MessageMgr mgr = new MessageMgr();
        mgr.addToMessages(MessageMgr.sharedNameKept, upload);
        mgr.addToMessages(MessageMgr.sharedNameKept, upload);
        Group group = new Group();
        group.setId(1);
        group.setName("CAS");
        mgr.compileMessages(group);
        String report = String.join("\n", mgr.getMessages());
        assertTrue(report, report.contains("<b>" + SharedNameReport.UPLOAD_HEADING + " <font color=green>(uploaded)</font></b> (total:2): </b><br>&nbsp;&nbsp;" + upload));
        assertFalse("the old heading is gone", report.contains("Taxon names recognized as Homonyms"));
    }

    /** The page, when the name is no longer shared (or the export cannot be read): the reason the run recorded, no AntCat list. */
    public void testPageFallsBackToTheRecordedReason() throws Exception {
        List<SpecimenNameUpdateDb.Line> lines = new ArrayList<>();
        lines.add(heldLine(SAUTERI, "name is shared by two taxa; please confirm the identification: A or B"));
        String text = pageLine(NameUpdatesAction.sharedHtml(lines, "", SharedNames.none()));
        assertEquals(Taxon.makeDescriptionLink(SAUTERI) + ": <span class=\"name-update-reason\">name is shared by two taxa; please confirm the identification: A or B</span>", text);
        assertEquals("the old two-argument call", text, pageLine(NameUpdatesAction.sharedHtml(lines, "")));

        SpecimenNameUpdateDb.Line moved = heldLine("ponerinaecryptopone sauteri", null);
        moved.uploadedName = SAUTERI;
        lines.clear();
        lines.add(moved);
        String withUploaded = pageLine(NameUpdatesAction.sharedHtml(lines, "", SharedNamesTest.fixture()));
        assertTrue(withUploaded, withUploaded.startsWith(Taxon.makeDescriptionLink("ponerinaecryptopone sauteri") + " (uploaded as <i>Pachycondyla sauteri</i>): "));
        assertTrue("found through the uploaded name", withUploaded.contains("/catalog/509563"));
    }

    /** What the old "Taxon names recognized as Homonyms" section was for: a name AntCat has only as a junior homonym. */
    public void testNameAntCatHasOnlyAsAHomonym() throws Exception {
        SharedNames shared = SharedNamesTest.fixture();
        String name = "myrmicinaepheidole zzhomonym";
        assertNull("one taxon: not shared", shared.candidatesOf("Pheidole zzhomonym"));
        assertEquals(Taxon.makeDescriptionLink(name) + ". <span class=\"shared-name-taxa\">AntCat: "
            + "<a href=\"https://www.antcat.org/catalog/514900\"><i>Pheidole zzhomonym</i> Smith, 1900</a>, homonym</span>",
            SharedNameReport.homonymOnlyLine(name, shared));
        assertNull("not in AntCat", SharedNameReport.homonymOnlyLine("myrmicinaepheidole zzz", shared));
        assertNull("a valid name has a taxon", SharedNameReport.homonymOnlyLine("ponerinaecryptopone sauteri", shared));
        assertNull(SharedNameReport.homonymOnlyLine(name, null));
    }

    /** The nightly step finds shared names through the same method (filed name first, then uploaded name). */
    public void testSameDetectionAsTheNightlyStep() throws Exception {
        SharedNames shared = SharedNamesTest.fixture();
        assertEquals(List.of("Cryptopone sauteri", "Ectomomyrmex sauteri"), SharedNameReport.candidates(SAUTERI, null, shared));
        assertEquals(List.of("Cryptopone sauteri", "Ectomomyrmex sauteri"), SharedNameReport.candidates("ponerinaecryptopone sauteri", SAUTERI, shared));
        assertNull(SharedNameReport.candidates("ponerinaecryptopone sauteri", "ponerinaecryptopone sauteri", shared));
        SpecimenNameUpdate.Decision d = SpecimenNameUpdate.decide(SAUTERI, SAUTERI, n -> false, sauteriRule(), false, false,
            SharedNameReport.candidates(SAUTERI, SAUTERI, shared));
        assertEquals(SpecimenNameUpdate.Outcome.SHARED_NAME, d.outcome);
    }

    /**
     * "Homonym" (the homonym table, a placeholder) and "name shared by two or more AntCat taxa" are one category
     * (Brian Fisher, 2026-10-20): the upload and the nightly step ask SharedNameReport.involved() and word it alike.
     */
    public void testHomonymTableIsTheSameCategory() throws Exception {
        CurrentValidNameRule.Result rule = sauteriRule();
        // Only the homonym table knows (no export): the upload keeps the name, the nightly step holds it, same reason.
        String upload = SharedNameReport.uploadLine(SAUTERI, rule, true, null, SharedNames.none());
        assertEquals(Taxon.makeDescriptionLink(SAUTERI)
            + ": <span class=\"name-update-reason\">name is shared by two or more AntCat taxa; please confirm the identification</span>", upload);
        SpecimenNameUpdate.Decision d = SpecimenNameUpdate.decide(SAUTERI, SAUTERI, n -> false, rule, true, false, SharedNames.none());
        assertEquals(SpecimenNameUpdate.Outcome.SHARED_NAME, d.outcome);
        List<SpecimenNameUpdateDb.Line> lines = new ArrayList<>();
        lines.add(heldLine(SAUTERI, d.reason));
        assertEquals(upload, pageLine(NameUpdatesAction.sharedHtml(lines, "", SharedNames.none())));
        // A valid name the rule points to that is a placeholder for a homonym: held at upload as well.
        assertNotNull(SharedNameReport.uploadLine(SAUTERI, rule, false, n -> n.equals("ponerinaecryptopone sauteri"), SharedNames.none()));
        assertNull("nothing shared", SharedNameReport.uploadLine(SAUTERI, rule, false, n -> false, SharedNames.none()));
    }
}

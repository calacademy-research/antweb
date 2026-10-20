package test.org.calacademy.antweb.upload;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import junit.framework.TestCase;

import org.calacademy.antweb.Status;
import org.calacademy.antweb.Taxon;
import org.calacademy.antweb.curate.NameUpdatesAction;
import org.calacademy.antweb.upload.CurrentValidNameRule;
import org.calacademy.antweb.upload.CurrentValidNameRule.Kind;
import org.calacademy.antweb.upload.SpecimenNameUpdate;
import org.calacademy.antweb.upload.SpecimenNameUpdate.Outcome;
import org.calacademy.antweb.upload.SpecimenNameUpdateDb;

/**
 * The nightly name update's decisions, without a database (2026-10):
 *  1. the upload's name rule (CurrentValidNameRule, taken out of SpecimenUploadProcess) against a small
 *     fake taxon table: synonym -> valid, obsolete combination, current valid name missing or a homonym,
 *     names AntCat does not know, morphospecies and indets;
 *  2. what the nightly step does with the rule's answer for the specimens of one group: move,
 *     move back (resurrected name), or leave alone (homonym, placeholder, morpho/indet, ...);
 *  3. the maxChanges cap and the settings file (no file = off);
 *  4. the compute.log line and the page line (same "old -> new" links as the upload report).
 * The database half (the queries, the update, the table) is SpecimenNameUpdateDbTest (MySQL 8.4 in CI).
 */
public class SpecimenNameUpdateTest extends TestCase {

    static final String OLD = "formicinaetapinoma indicum";          // synonym, valid name exists
    static final String NEW = "dolichoderinaeravavy indicus";
    static final String OBS = "myrmicinaepheidole oldcomb";          // obsolete combination
    static final String OBS_NEW = "myrmicinaepheidole newcomb";
    static final String NO_TARGET = "ponerinaehypoponera lost";      // synonym whose valid name has no taxon row
    static final String TO_HOMONYM = "myrmicinaecarebara tohom";     // synonym whose valid name is a homonym
    static final String HOM_NAME = "myrmicinaecarebara silvestrii";
    static final String SAME = "ponerinaeponera same";                // current valid name is itself
    static final String HOMONYM = "myrmicinaesolenopsis pygmaea";    // status homonym
    static final String UNAVAILABLE = "formicinaecamponotus unav";
    static final String UNKNOWN = "formicinaecamponotus zztestunknown";
    static final String MORPHO = "formicinaecamponotus mg01";
    static final String INDET = "formicinaecamponotus (indet)";

    /** A fake taxon table: name -> {status, current_valid_name}. */
    static final Map<String, String[]> TAXA = new HashMap<>();
    static {
        TAXA.put(OLD, new String[] {Status.SYNONYM, NEW});
        TAXA.put(NEW, new String[] {Status.VALID, null});
        TAXA.put(OBS, new String[] {Status.OBSOLETE_COMBINATION, OBS_NEW});
        TAXA.put(OBS_NEW, new String[] {Status.VALID, null});
        TAXA.put(NO_TARGET, new String[] {Status.SYNONYM, "ponerinaehypoponera missing"});
        TAXA.put(TO_HOMONYM, new String[] {Status.SYNONYM, HOM_NAME});
        TAXA.put(SAME, new String[] {Status.SYNONYM, SAME});
        TAXA.put(HOMONYM, new String[] {Status.HOMONYM, null});
        TAXA.put(UNAVAILABLE, new String[] {Status.UNAVAILABLE, null});
        TAXA.put("formicinaecamponotus nocvn", new String[] {Status.SYNONYM, null});
        TAXA.put("formicinaecamponotus unrec", new String[] {Status.UNRECOGNIZED, null});
    }

    static final CurrentValidNameRule.Lookup LOOKUP = new CurrentValidNameRule.Lookup() {
        public CurrentValidNameRule.TaxonRow getTaxon(String name) {
            String[] t = TAXA.get(name);
            return t == null ? null : new CurrentValidNameRule.TaxonRow(t[0], t[1]);
        }
        public String getCurrentValidTaxonName(String name) {
            return TAXA.containsKey(name.toLowerCase()) ? name.toLowerCase() : null;
        }
        public boolean isHomonym(String name) {
            return HOM_NAME.equals(name);
        }
    };

    static CurrentValidNameRule.Result rule(String name) throws Exception {
        return CurrentValidNameRule.decide(name, LOOKUP);
    }

    // ------------------------------------------------------------ 1. the upload's rule

    public void testSynonymGoesToTheValidName() throws Exception {
        CurrentValidNameRule.Result r = rule(OLD);
        assertEquals(Kind.REPLACE, r.kind);
        assertEquals(NEW, r.taxonName);
        assertEquals(NEW, r.validTaxonName);
        assertEquals("the uploaded name is kept", OLD, r.originalTaxonName);
        assertEquals(Status.VALID, r.status);
        assertEquals(Status.SYNONYM, r.uploadedStatus);
    }

    public void testObsoleteCombinationGoesToTheValidName() throws Exception {
        CurrentValidNameRule.Result r = rule(OBS);
        assertEquals(Kind.REPLACE, r.kind);
        assertEquals(OBS_NEW, r.validTaxonName);
    }

    public void testTargetsThatCannotBeUsed() throws Exception {
        CurrentValidNameRule.Result r = rule(NO_TARGET);
        assertEquals(Kind.TARGET_MISSING, r.kind);
        assertEquals("as before: status stays synonym", Status.SYNONYM, r.status);
        r = rule(TO_HOMONYM);
        assertEquals(Kind.TARGET_IS_HOMONYM, r.kind);
        assertEquals("the upload files it under a placeholder", Status.UNRECOGNIZED, r.status);
        assertEquals(Kind.TARGET_SAME, rule(SAME).kind);
        assertEquals(Kind.NO_CURRENT_VALID_NAME, rule("formicinaecamponotus nocvn").kind);
    }

    public void testNamesThatAreKept() throws Exception {
        assertEquals(Kind.KEEP, rule(NEW).kind);
        assertEquals(Status.VALID, rule(NEW).status);
        assertEquals(Kind.KEEP, rule(HOMONYM).kind);
        assertEquals(Kind.KEEP, rule(UNAVAILABLE).kind);
        assertEquals(Kind.NOT_IN_ANTCAT, rule(UNKNOWN).kind);
        assertEquals(Status.UNRECOGNIZED, rule(UNKNOWN).status);
        assertEquals(Kind.NOT_IN_ANTCAT, rule("formicinaecamponotus unrec").kind);
        assertEquals(Kind.MORPHO, rule(MORPHO).kind);
        assertEquals(Status.MORPHOTAXON, rule(MORPHO).status);
        assertEquals(Kind.INDET, rule(INDET).kind);
        assertEquals(Status.INDETERMINED, rule(INDET).status);
        for (String n : new String[] {NEW, HOMONYM, UNKNOWN, MORPHO, INDET}) {
            assertNull(n, rule(n).originalTaxonName);
            assertEquals(n, rule(n).taxonName);
        }
    }

    // ------------------------------------------------------------ 2. the nightly decision

    /** placeholder: the current name is a homonym placeholder taxon. */
    static SpecimenNameUpdate.Decision nightly(String uploaded, String current, boolean placeholder) throws Exception {
        return SpecimenNameUpdate.decide(uploaded, current, n -> placeholder && n.equals(current), rule(uploaded));
    }

    public void testSynonymAfterUploadIsMoved() throws Exception {
        // Uploaded as OLD while it was valid; AntCat has since made it a synonym of NEW.
        SpecimenNameUpdate.Decision d = nightly(OLD, OLD, false);
        assertEquals(Outcome.CHANGE, d.outcome);
        assertEquals(NEW, d.newName);
        assertEquals("Uploaded As keeps the curator's name", OLD, d.newOriginalName);
    }

    public void testAlreadyOnTheValidNameIsUnchanged() throws Exception {
        assertEquals(Outcome.UNCHANGED, nightly(OLD, NEW, false).outcome);
        assertEquals(Outcome.UNCHANGED, nightly(NEW, NEW, false).outcome);
    }

    public void testResurrectedNameMovesBack() throws Exception {
        // Uploaded as NEW when NEW was a synonym of something else; NEW is valid again.
        SpecimenNameUpdate.Decision d = nightly(NEW, "dolichoderinaeravavy other", false);
        assertEquals(Outcome.CHANGE, d.outcome);
        assertEquals(NEW, d.newName);
        assertNull("back on the uploaded name: no Uploaded As", d.newOriginalName);
    }

    public void testHomonymsAreSkipped() throws Exception {
        assertEquals(Outcome.HOMONYM, nightly(TO_HOMONYM, TO_HOMONYM, false).outcome);
        assertEquals(Outcome.HOMONYM, nightly(HOMONYM, "myrmicinaesolenopsis other", false).outcome);
        assertEquals("specimens on a homonym placeholder stay", Outcome.PLACEHOLDER, nightly(OLD, HOM_NAME, true).outcome);
        // Once the upload has created the placeholder, the rule finds it as the current valid taxon:
        // the nightly step still does not move specimens there.
        TAXA.put("myrmicinaecarebara viaph", new String[] {Status.SYNONYM, "myrmicinaecarebara placeholder"});
        TAXA.put("myrmicinaecarebara placeholder", new String[] {Status.UNRECOGNIZED, null});
        try {
            assertEquals(Kind.REPLACE, rule("myrmicinaecarebara viaph").kind);
            SpecimenNameUpdate.Decision d = SpecimenNameUpdate.decide("myrmicinaecarebara viaph", "myrmicinaecarebara viaph",
                n -> n.equals("myrmicinaecarebara placeholder"), rule("myrmicinaecarebara viaph"));
            assertEquals(Outcome.HOMONYM, d.outcome);
        } finally {
            TAXA.remove("myrmicinaecarebara viaph");
            TAXA.remove("myrmicinaecarebara placeholder");
        }
    }

    public void testMorphosAndIndetsAreSkipped() throws Exception {
        assertEquals(Outcome.MORPHO_OR_INDET, nightly(MORPHO, MORPHO, false).outcome);
        assertEquals(Outcome.MORPHO_OR_INDET, nightly(INDET, INDET, false).outcome);
        assertEquals("a morpho filed under it is not touched either", Outcome.MORPHO_OR_INDET, nightly(OLD, MORPHO, false).outcome);
        assertEquals(Outcome.MORPHO_OR_INDET, nightly(MORPHO, "formicinaecamponotus maculatus", false).outcome);
    }

    public void testOtherCasesAreLeftAlone() throws Exception {
        assertEquals(Outcome.NO_TARGET, nightly(NO_TARGET, NO_TARGET, false).outcome);
        assertEquals(Outcome.NO_TARGET, nightly(SAME, SAME, false).outcome);
        assertEquals(Outcome.NO_TARGET, nightly("formicinaecamponotus nocvn", "formicinaecamponotus nocvn", false).outcome);
        assertEquals(Outcome.NOT_IN_ANTCAT, nightly(UNKNOWN, NEW, false).outcome);
        assertEquals(Outcome.OTHER_STATUS, nightly(UNAVAILABLE, NEW, false).outcome);
    }

    // ------------------------------------------------------------ 3. cap and settings

    public void testMaxChanges() {
        assertFalse(SpecimenNameUpdate.overCap(50000, 50000));
        assertTrue(SpecimenNameUpdate.overCap(50001, 50000));
        assertFalse(SpecimenNameUpdate.overCap(0, 1));
        assertEquals(50000, SpecimenNameUpdate.DEFAULT_MAX_CHANGES);
    }

    static SpecimenNameUpdate.Settings settings(String text) throws Exception {
        Properties p = new Properties();
        p.load(new java.io.StringReader(text));
        return SpecimenNameUpdate.parseSettings(p);
    }

    public void testSettings() throws Exception {
        File none = new File("/nonexistent/name-update.properties");
        assertEquals("no file: off", SpecimenNameUpdate.OFF, SpecimenNameUpdate.readSettings(none).mode);
        assertEquals(SpecimenNameUpdate.REPORT, settings("mode=report").mode);
        assertEquals(SpecimenNameUpdate.APPLY, settings("mode = Apply \n").mode);
        assertEquals(SpecimenNameUpdate.OFF, settings("mode=off").mode);
        assertEquals("unknown value: off", SpecimenNameUpdate.OFF, settings("mode=yes").mode);
        assertNotNull(settings("mode=yes").note);
        assertEquals("no mode: off", SpecimenNameUpdate.OFF, settings("maxChanges=10").mode);
        assertEquals(50000, settings("mode=report").maxChanges);
        assertEquals(5000, settings("mode=apply\nmaxChanges=5000").maxChanges);
        assertEquals("comment after the value", SpecimenNameUpdate.REPORT, settings("mode=report        # off | report | apply").mode);
        assertEquals(5000, settings("maxChanges=5000    # cap").maxChanges);
        assertEquals("bad number: default", 50000, settings("maxChanges=lots").maxChanges);
        assertEquals("zero: default", 50000, settings("maxChanges=0").maxChanges);

        File f = File.createTempFile("name-update", ".properties");
        f.deleteOnExit();
        Files.write(f.toPath(), "mode=report\nmaxChanges=1234\n".getBytes(StandardCharsets.UTF_8));
        SpecimenNameUpdate.Settings s = SpecimenNameUpdate.readSettings(f);
        assertEquals(SpecimenNameUpdate.REPORT, s.mode);
        assertEquals(1234, s.maxChanges);
        assertEquals("/antweb/deploy/etc/name-update.properties", SpecimenNameUpdate.CONFIG);
    }

    // ------------------------------------------------------------ 4. log line and page

    public void testComputeLogLine() {
        SpecimenNameUpdate.Summary s = new SpecimenNameUpdate.Summary();
        s.rowMode = SpecimenNameUpdate.WOULD_CHANGE;
        s.specimens = 1234;
        s.names = 3;
        s.byGroup.put(1, 1200);
        s.byGroup.put(7, 34);
        s.skipped.put(Outcome.HOMONYM, 2);
        String line = s.line();
        assertTrue(line, line.startsWith("name update: 1,234 specimens in 2 groups (report: would change)"));
        assertTrue(line, line.contains("2 homonym"));

        s.rowMode = SpecimenNameUpdate.CHANGED;
        assertTrue(s.line(), s.line().startsWith("name update: 1,234 specimens in 2 groups (apply: changed)"));

        s.rowMode = SpecimenNameUpdate.WOULD_CHANGE;
        s.capped = true;
        s.maxChanges = 1000;
        assertTrue(s.line(), s.line().contains("apply refused: above maxChanges=1000, nothing changed"));

        SpecimenNameUpdate.Summary off = new SpecimenNameUpdate.Summary();
        off.note = "off";
        assertEquals("name update: off", off.line());
    }

    public void testPageLineIsTheUploadReportLine() {
        assertEquals(Taxon.makeDescriptionLink(OLD) + " -> " + Taxon.makeDescriptionLink(NEW), NameUpdatesAction.nameLine(OLD, NEW));

        SpecimenNameUpdateDb.Run run = new SpecimenNameUpdateDb.Run();
        run.runDate = Timestamp.valueOf("2026-10-20 05:20:00");
        run.mode = SpecimenNameUpdate.WOULD_CHANGE;
        run.specimens = 2;
        SpecimenNameUpdateDb.Line line = new SpecimenNameUpdateDb.Line();
        line.runDate = run.runDate;
        line.mode = run.mode;
        line.groupId = 1;
        line.oldName = OLD;
        line.newName = NEW;
        line.antcatStatus = Status.SYNONYM;
        line.count = 2;
        line.codes.add("casent0001");
        line.codes.add("x<script>");
        List<SpecimenNameUpdateDb.Run> runs = new ArrayList<>();
        runs.add(run);
        List<SpecimenNameUpdateDb.Line> lines = new ArrayList<>();
        lines.add(line);
        String html = NameUpdatesAction.linesHtml(runs, lines, "https://www.antweb.org");
        assertTrue(html, html.contains("2026-10-20 05:20: Would change (report only)"));
        assertTrue(html, html.contains(NameUpdatesAction.nameLine(OLD, NEW)));
        assertTrue(html, html.contains("(AntCat: synonym)"));
        assertTrue(html, html.contains("/specimen.do?name=casent0001\">casent0001</a>"));
        assertFalse("codes are escaped", html.contains("<script>"));
        assertTrue(NameUpdatesAction.linesHtml(new ArrayList<>(), new ArrayList<>(), "").contains("No name updates"));
        assertTrue("every report explains itself", NameUpdatesAction.HELP.contains("report-help"));
    }
}

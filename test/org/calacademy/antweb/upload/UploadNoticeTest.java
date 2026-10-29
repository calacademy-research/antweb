package test.org.calacademy.antweb.upload;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Properties;
import java.util.TimeZone;

import junit.framework.TestCase;
import org.calacademy.antweb.upload.MessageMgr;
import org.calacademy.antweb.upload.UploadNotice;
import org.calacademy.antweb.upload.UploadNotice.Facts;
import org.calacademy.antweb.upload.UploadNotice.Settings;

/** The "upload complete" email to curators: when it is sent, and what it says. */
public class UploadNoticeTest extends TestCase {

    private static Settings prod() { return Settings.from(new Properties(), true); }
    private static Settings staging() { return Settings.from(new Properties(), false); }
    private static Settings with(boolean live, String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) p.setProperty(kv[i], kv[i + 1]);
        return Settings.from(p, live);
    }

    public void testDefaults() {
        assertTrue(prod().enabled);
        assertTrue(prod().excludedGroups.contains(UploadNotice.CAS_GROUP_ID));
        assertTrue(prod().onlyDomains.isEmpty());
        assertFalse(staging().enabled);
        assertEquals(UploadNotice.NON_PROD_DEFAULT_DOMAINS, staging().onlyDomains);
    }

    public void testSwitch() {
        assertFalse(with(true, "enabled", "off").enabled);
        assertTrue(with(false, "enabled", "on").enabled);
        assertTrue(with(false, "enabled", "true").enabled);
        // Staging stays limited to our own domains even when turned on.
        assertEquals(UploadNotice.NON_PROD_DEFAULT_DOMAINS, with(false, "enabled", "on").onlyDomains);
        assertTrue(with(true, "excludeGroups", "").excludedGroups.isEmpty());
        assertTrue(with(true, "excludeGroups", "1, 16").excludedGroups.contains(16));
    }

    public void testSendsToAHumanCuratorInProduction() {
        assertNull(UploadNotice.skipReason(prod(), true, "curator@fieldmuseum.org", 2, 2));
    }

    public void testAutomaticUploadsAreNotMailed() {
        assertNotNull(UploadNotice.skipReason(prod(), false, "curator@fieldmuseum.org", 2, 2));
    }

    public void testCasIsNotMailed() {
        assertNotNull(UploadNotice.skipReason(prod(), true, "bfisher@calacademy.org", 1, 1));
        // An admin in another group uploading as a CAS curator, and the other way round.
        assertNotNull(UploadNotice.skipReason(prod(), true, "someone@antweb.org", 5, 1));
        assertNotNull(UploadNotice.skipReason(prod(), true, "someone@antweb.org", 1, 5));
        // Unless CAS is taken off the list.
        assertNull(UploadNotice.skipReason(with(true, "excludeGroups", ""), true, "bfisher@calacademy.org", 1, 1));
    }

    public void testNoOrBadEmail() {
        assertNotNull(UploadNotice.skipReason(prod(), true, null, 2));
        assertNotNull(UploadNotice.skipReason(prod(), true, "  ", 2));
        assertNotNull(UploadNotice.skipReason(prod(), true, "nobody", 2));
        assertNotNull(UploadNotice.skipReason(prod(), true, "a@b.org, c@d.org", 2));
    }

    public void testOffAndStaging() {
        assertNotNull(UploadNotice.skipReason(with(true, "enabled", "off"), true, "curator@fieldmuseum.org", 2));
        assertNotNull(UploadNotice.skipReason(staging(), true, "someone@calacademy.org", 2));
        Settings on = with(false, "enabled", "on");
        assertNotNull(UploadNotice.skipReason(on, true, "curator@fieldmuseum.org", 2));
        assertNull(UploadNotice.skipReason(on, true, "someone@calacademy.org", 2));
        assertNull(UploadNotice.skipReason(on, true, "Someone@Antweb.org", 2));
        assertNotNull(UploadNotice.skipReason(on, true, "someone@notantweb.org", 2));
    }

    private static Facts specimenFacts() {
        Facts f = new Facts();
        f.fileName = "FMNH_specimens.txt";
        f.fileType = "AntWeb specimen file";
        f.groupName = "Field Museum";
        GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone("America/Los_Angeles"));
        c.clear();
        c.set(2026, Calendar.OCTOBER, 18, 14, 5);
        f.when = c.getTime();
        return f;
    }

    public void testSpecimenComplete() {
        Facts f = specimenFacts();
        f.complete = true;
        f.uploadId = 4321;
        f.specimensLoaded = 12345;
        f.redFlagged = 12;
        f.reportUrl = "https://www.antweb.org/web/log/specimen/20261018-14:01:07-specimenUpload.html";
        assertEquals("AntWeb: your specimen upload is complete (12,345 specimens)", UploadNotice.subject(f));
        assertEquals(
            "Your specimen upload to AntWeb is complete.\n\n"
            + "File: FMNH_specimens.txt\n"
            + "Type: AntWeb specimen file\n"
            + "Group: Field Museum\n"
            + "Finished: 18 Oct 2026, 2:05 PM PDT\n"
            + "Upload ID: 4321\n"
            + "Specimens loaded: 12,345\n"
            + "Red-flagged specimens: 12\n\n"
            + "Upload report, with any problems found in your data:\n"
            + "https://www.antweb.org/web/log/specimen/20261018-14:01:07-specimenUpload.html\n\n"
            + "Replies to this message go to antweb@antweb.org.\n",
            UploadNotice.body(f));
        f.specimensLoaded = 1;
        assertEquals("AntWeb: your specimen upload is complete (1 specimen)", UploadNotice.subject(f));
    }

    public void testSpecimenFailed() {
        Facts f = specimenFacts();
        f.failure = UploadNotice.failureText("Specimen File must be a <b>tab-delimited</b> file.");
        f.uploadPageUrl = "https://www.antweb.org/curate.do";
        assertEquals("AntWeb: your specimen upload did not complete", UploadNotice.subject(f));
        String body = UploadNotice.body(f);
        assertTrue(body, body.startsWith("Your specimen upload to AntWeb did not complete.\n\n"));
        assertTrue(body, body.contains("Stopped: 18 Oct 2026, 2:05 PM PDT\n"));
        assertTrue(body, body.contains("Message: Specimen File must be a tab-delimited file.\n"));
        assertTrue(body, body.contains("No upload report was written for this upload.\n"));
        assertTrue(body, body.contains("You can upload the file again at https://www.antweb.org/curate.do\n"));
        assertFalse(body, body.contains("Specimens loaded"));
    }

    public void testImagesComplete() {
        Facts f = new Facts();
        f.images = true;
        f.complete = true;
        f.imagesSaved = 25;
        f.groupName = "Field Museum";
        f.when = specimenFacts().when;
        f.reportUrl = "https://www.antweb.org/imageUploadReport.do?id=812";
        f.reportNeedsLogin = true;
        assertEquals("AntWeb: your image upload is complete (25 images)", UploadNotice.subject(f));
        assertEquals(
            "Your image upload to AntWeb is complete.\n\n"
            + "Group: Field Museum\n"
            + "Finished: 18 Oct 2026, 2:05 PM PDT\n"
            + "Images saved: 25\n\n"
            + "Upload report (log in to AntWeb to see it):\n"
            + "https://www.antweb.org/imageUploadReport.do?id=812\n\n"
            + "The report lists the images that were saved.\n\n"
            + "Replies to this message go to antweb@antweb.org.\n",
            UploadNotice.body(f));
    }

    public void testImagesCompleteWithFilesNotSaved() {
        Facts f = new Facts();
        f.images = true;
        f.complete = true;
        f.imagesSaved = 22;
        f.imagesNotSaved = 3;
        f.groupName = "Field Museum";
        f.when = specimenFacts().when;
        f.reportUrl = "https://www.antweb.org/imageUploadReport.do?id=812";
        f.reportNeedsLogin = true;
        assertEquals("AntWeb: your image upload is complete (22 images)", UploadNotice.subject(f));
        assertEquals(
            "Your image upload to AntWeb is complete.\n\n"
            + "Group: Field Museum\n"
            + "Finished: 18 Oct 2026, 2:05 PM PDT\n"
            + "Images saved: 22\n"
            + "Images not saved: 3\n\n"
            + "Upload report (log in to AntWeb to see it):\n"
            + "https://www.antweb.org/imageUploadReport.do?id=812\n\n"
            + "The report lists the images that were saved, and under \"Not saved\" each file that was not saved and why.\n\n"
            + "Replies to this message go to antweb@antweb.org.\n",
            UploadNotice.body(f));
        // None refused: the line says 0 and the report sentence is the short one.
        f.imagesNotSaved = 0;
        String body = UploadNotice.body(f);
        assertTrue(body, body.contains("Images not saved: 0\n"));
        assertTrue(body, body.contains("The report lists the images that were saved.\n"));
        // Specimen uploads never get the line.
        Facts s = specimenFacts();
        s.complete = true;
        s.imagesNotSaved = 4;
        assertFalse(UploadNotice.body(s).contains("not saved"));
    }

    public void testNoticeLimitIsPerAddressNotPerSubject() {
        org.calacademy.antweb.util.Emailer.resetThrottle();
        long t = 1_000_000L;
        for (int i = 0; i < 20; i++) assertNull(org.calacademy.antweb.util.Emailer.throttleNotice("a@x.org", t));
        assertNotNull(org.calacademy.antweb.util.Emailer.throttleNotice("A@x.org", t));
        assertNull(org.calacademy.antweb.util.Emailer.throttleNotice("b@x.org", t));
        // A new day.
        assertNull(org.calacademy.antweb.util.Emailer.throttleNotice("a@x.org", t + 25L * 60 * 60 * 1000));
        org.calacademy.antweb.util.Emailer.resetThrottle();
    }

    public void testCrashesGetTheFixedSentence() {
        assertEquals("AntWeb hit an unexpected problem (case 12) and made no changes. Please reply to this email so we can look into it.",
            UploadNotice.failureText("Error. Case number:12. No changes made. e:org.calacademy.antweb.AntwebException: No records processed"));
        assertEquals("AntWeb hit an unexpected problem and made no changes. Please reply to this email so we can look into it.",
            UploadNotice.failureText("Could not save. e:java.io.IOException: No space left on device"));
        assertEquals("AntWeb hit an unexpected problem and made no changes. Please reply to this email so we can look into it.",
            UploadNotice.failureText("java.lang.NullPointerException at org.calacademy.antweb.Foo.bar(Foo.java:12)"));
        // Ordinary validation messages are passed on.
        assertEquals("Specimen File must be in the most current format.",
            UploadNotice.failureText("Specimen File must be in the most current format."));
        assertEquals("occurrence.txt should be uploaded as TaxonWorks.",
            UploadNotice.failureText("occurrence.txt should be uploaded as TaxonWorks."));
        assertNull(UploadNotice.failureText(null));
        // The whole email never shows the exception.
        Facts f = specimenFacts();
        f.failure = UploadNotice.failureText("Error. Case number:12. No changes made. e:org.calacademy.antweb.AntwebException: x");
        String body = UploadNotice.body(f);
        assertTrue(body, body.contains("Message: AntWeb hit an unexpected problem (case 12) and made no changes. Please reply to this email so we can look into it.\n"));
        assertFalse(body, body.contains("Exception"));
    }

    public void testPlain() {
        assertNull(UploadNotice.plain(null));
        assertEquals("Error. Case number:7. No changes made. e:java.io.IOException: x < y",
            UploadNotice.plain("Error. Case number:7. No changes made. e:java.io.IOException: x &lt; y"));
        assertTrue(UploadNotice.plain("x".repeat(900)).length() <= 500);
    }

    // ---- the summary of what in the report needs attention (2026-10-29) ----

    private static Facts counts(int corrected, int notLoaded, int red) {
        Facts f = specimenFacts();
        f.complete = true;
        f.corrected = corrected;
        f.notLoaded = notLoaded;
        f.redFlagged = red;
        return f;
    }

    public void testAttentionSummarySentences() {
        assertEquals("Nothing in the report needs your attention.", UploadNotice.attentionSummary(counts(0, 0, 0)));
        assertEquals("1 line was corrected; see the report. Please fix it in your own data before your next upload.",
            UploadNotice.attentionSummary(counts(1, 0, 0)));
        assertEquals("2 lines were not loaded; see the report. Please fix them in your own data before your next upload.",
            UploadNotice.attentionSummary(counts(0, 2, 0)));
        assertEquals("2 lines were corrected and 1 line was not loaded; see the report. Please fix them in your own data before your next upload.",
            UploadNotice.attentionSummary(counts(2, 1, 0)));
        assertEquals("1,200 lines were corrected, 3 lines were not loaded and 1 specimen was red-flagged; see the report."
            + " Please fix them in your own data before your next upload.",
            UploadNotice.attentionSummary(counts(1200, 3, 1)));
        // Not known: no summary (image uploads, uploads that did not complete, counts not filled).
        assertNull(UploadNotice.attentionSummary(counts(-1, -1, 0)));
        Facts failed = counts(1, 1, 0);
        failed.complete = false;
        assertNull(UploadNotice.attentionSummary(failed));
        Facts images = counts(1, 1, 0);
        images.images = true;
        assertNull(UploadNotice.attentionSummary(images));
        for (Facts f : new Facts[] {counts(0, 0, 0), counts(3, 4, 5)}) {
            assertFalse(UploadNotice.attentionSummary(f).toLowerCase().contains("ignore"));
        }
    }

    /** SAM: one Ocymyrmex line corrected (species indet with a subspecies). The whole email. */
    public void testSummaryInEmailOneCorrection() {
        Facts f = counts(1, 0, 0);
        f.fileName = "SAM_specimens.txt";
        f.groupName = "Iziko South African Museum";
        f.uploadId = 5012;
        f.specimensLoaded = 1840;
        f.reportUrl = "https://www.antweb.org/web/log/specimen/20261029-10:12:03-specimenUpload.html";
        f.when = specimenFacts().when;
        assertEquals(
            "Your specimen upload to AntWeb is complete.\n\n"
            + "File: SAM_specimens.txt\n"
            + "Type: AntWeb specimen file\n"
            + "Group: Iziko South African Museum\n"
            + "Finished: 18 Oct 2026, 2:05 PM PDT\n"
            + "Upload ID: 5012\n"
            + "Specimens loaded: 1,840\n"
            + "Red-flagged specimens: 0\n\n"
            + "1 line was corrected; see the report. Please fix it in your own data before your next upload.\n\n"
            + "Upload report, with any problems found in your data:\n"
            + "https://www.antweb.org/web/log/specimen/20261029-10:12:03-specimenUpload.html\n\n"
            + "Replies to this message go to antweb@antweb.org.\n",
            UploadNotice.body(f));
        // No report written: no summary either.
        f.reportUrl = null;
        assertFalse(UploadNotice.body(f).contains("corrected"));
    }

    /** EcoFoG: 2 lines not loaded (no code), 7 adm1 names not recognised (a note: not counted). The whole email. */
    public void testSummaryInEmailLinesNotLoaded() {
        Facts f = counts(0, 2, 0);
        f.fileName = "EcoFoG_specimens.txt";
        f.groupName = "EcoFoG";
        f.uploadId = 5013;
        f.specimensLoaded = 9312;
        f.reportUrl = "https://www.antweb.org/web/log/specimen/20261029-11:40:51-specimenUpload.html";
        f.when = specimenFacts().when;
        assertEquals(
            "Your specimen upload to AntWeb is complete.\n\n"
            + "File: EcoFoG_specimens.txt\n"
            + "Type: AntWeb specimen file\n"
            + "Group: EcoFoG\n"
            + "Finished: 18 Oct 2026, 2:05 PM PDT\n"
            + "Upload ID: 5013\n"
            + "Specimens loaded: 9,312\n"
            + "Red-flagged specimens: 0\n\n"
            + "2 lines were not loaded; see the report. Please fix them in your own data before your next upload.\n\n"
            + "Upload report, with any problems found in your data:\n"
            + "https://www.antweb.org/web/log/specimen/20261029-11:40:51-specimenUpload.html\n\n"
            + "Replies to this message go to antweb@antweb.org.\n",
            UploadNotice.body(f));
    }

    /** The counts come from the report's sections: (not uploaded), the corrections, nothing for notes. */
    public void testCountsFromReportSections() {
        MessageMgr m = new MessageMgr();
        assertEquals(0, m.getNotLoadedCount());
        assertEquals(0, m.getCorrectedCount());
        // EcoFoG: 2 lines without a code, 7 adm1 names not recognised (a note).
        m.addToMessages(MessageMgr.codeNotFound, "line:12 code:");
        m.addToMessages(MessageMgr.codeNotFound, "line:40 code:");
        for (int i = 0; i < 7; i++) m.addToMessages(MessageMgr.notValidAdm1, "Guyane adm1 " + i);
        assertEquals(2, m.getNotLoadedCount());
        assertEquals(0, m.getCorrectedCount());
        // SAM: one line, species indet with a subspecies.
        m.setCurrentCode("SAM-ENT-011940");
        m.addToMessages(MessageMgr.speciesIndetSubspeciesIgnored, "line 7, SAM-ENT-011940: Ocymyrmex indet karooicus -> ...");
        m.addToMessages(MessageMgr.codeNotFoundInLine, "line:9");
        m.addToMapMessages(MessageMgr.invalidSubfamilyForGenusReplaced, "Ponerinae Ocymyrmex", "Ocymyrmex");
        m.addToMessages(MessageMgr.correctedBioregion, "Using Afrotropical for country:South Africa");  // filled in: a note
        m.addToMessages(MessageMgr.identifiedOnlyToGenus, "Ocymyrmex (indet)");                       // a note
        assertEquals(3, m.getNotLoadedCount());
        assertEquals(2, m.getCorrectedCount());
    }

    public void testEverySectionHasAKind() {
        java.util.Map<String, String> kinds = new MessageMgr().attentionByKey();
        for (String key : MessageMgr.CORRECTED) assertEquals(key, "corrected", kinds.get(key));
        assertEquals("notLoaded", kinds.get(MessageMgr.duplicateEntries));
        assertEquals("notLoaded", kinds.get(MessageMgr.invalidSpecimenCode));
        assertEquals("notLoaded", kinds.get(MessageMgr.codeNotFoundInLine));
        assertNull(kinds.get(MessageMgr.notValidAdm1));
        assertNull(kinds.get(MessageMgr.countryNotFound));
        assertNull(kinds.get(MessageMgr.correctedBioregion));
        assertNull(kinds.get(MessageMgr.sharedNameKept));
        // A section is never both: no correction section says "(not uploaded)".
        assertNull(MessageMgr.attention("x", "<b>Something <font color=green>(uploaded)</font></b>"));
        assertEquals("notLoaded", MessageMgr.attention("x", "<b>Something <font color=red>(not uploaded)</font></b>"));
    }
}

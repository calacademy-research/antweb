package test.org.calacademy.antweb.upload;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Properties;
import java.util.TimeZone;

import junit.framework.TestCase;
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
        f.failure = UploadNotice.plain("Specimen File must be a <b>tab-delimited</b> file.");
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
        f.batchImages = true;
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
            + "The report lists the images that were saved. Files the Image Uploader refused were listed on the upload page and are not in the report.\n\n"
            + "Replies to this message go to antweb@antweb.org.\n",
            UploadNotice.body(f));
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

    public void testPlain() {
        assertNull(UploadNotice.plain(null));
        assertEquals("Error. Case number:7. No changes made. e:java.io.IOException: x < y",
            UploadNotice.plain("Error. Case number:7. No changes made. e:java.io.IOException: x &lt; y"));
        assertTrue(UploadNotice.plain("x".repeat(900)).length() <= 500);
    }
}

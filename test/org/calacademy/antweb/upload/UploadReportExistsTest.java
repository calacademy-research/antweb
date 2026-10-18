package test.org.calacademy.antweb.upload;

import java.io.File;
import java.nio.file.Files;
import java.util.Calendar;
import java.util.GregorianCalendar;

import junit.framework.TestCase;
import org.calacademy.antweb.upload.Upload;

/** Group pages link an upload report only while the file is on the server. */
public class UploadReportExistsTest extends TestCase {

    public void testReportPath() {
        Upload u = new Upload();
        assertNull(u.getReportPath());
        u.setCreated(new GregorianCalendar(2022, Calendar.FEBRUARY, 24).getTime());
        u.setLogFileName("20220224-11:49:08-specimen2.html");
        assertEquals("web/log/specimen/20220224-11:49:08-specimen2.html", u.getReportPath());
        u.setCreated(new GregorianCalendar(2018, Calendar.MAY, 6).getTime());
        assertEquals("web/log/upload/20220224-11:49:08-specimen2.html", u.getReportPath());
        u.setLogFileName("../../etc/passwd");
        assertNull(u.getReportPath());
    }

    public void testExistsIsCached() throws Exception {
        File root = Files.createTempDirectory("docroot").toFile();
        String docRoot = root.getPath() + "/";
        String path = "web/log/specimen/r1.html";
        long t = 1_000_000L;

        assertFalse(Upload.reportExists(docRoot, path, t));
        assertFalse(Upload.reportExists(docRoot, null, t));
        assertFalse(Upload.reportExists(null, path, t));

        File f = new File(root, path);
        f.getParentFile().mkdirs();
        Files.writeString(f.toPath(), "report");
        // Still the cached answer within the hour, then re-checked.
        assertFalse(Upload.reportExists(docRoot, path, t + 60_000));
        assertTrue(Upload.reportExists(docRoot, path, t + 61L * 60 * 1000));

        // A folder of that name is not a report.
        assertFalse(Upload.reportExists(docRoot, "web/log/specimen", t));
    }
}

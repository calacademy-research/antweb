package test.org.calacademy.antweb.home;

import java.util.List;

import junit.framework.TestCase;
import org.calacademy.antweb.home.ImageUploadRefusedDb;
import org.calacademy.antweb.home.ImageUploadRefusedDb.Refused;

/** The Image Uploader's "Not saved" list: what the page sends is cleaned and limited before it is stored. */
public class ImageUploadRefusedDbTest extends TestCase {

    public void testParse() {
        List<Refused> l = ImageUploadRefusedDb.parse(
            "CASENT0001_D_1.JPG\tlarger than 95 MB\n"
            + "\n"
            + "casent0002_d_1.jpg\tfailed: network error (after 4 tries)\r\n"
            + "CASENT0001_D_1.JPG\tsecond reason is dropped\n"
            + "NOREASON.JPG\n"
            + "\tno name\n",
            ImageUploadRefusedDb.BROWSER);
        assertEquals(3, l.size());
        assertEquals("CASENT0001_D_1.JPG", l.get(0).fileName);
        assertEquals("larger than 95 MB", l.get(0).reason);
        assertEquals(ImageUploadRefusedDb.BROWSER, l.get(0).source);
        assertEquals("casent0002_d_1.jpg", l.get(1).fileName);
        assertEquals("failed: network error (after 4 tries)", l.get(1).reason);
        assertEquals("NOREASON.JPG", l.get(2).fileName);
        assertEquals("not sent", l.get(2).reason);
        assertTrue(ImageUploadRefusedDb.parse(null, "browser").isEmpty());
        assertTrue(ImageUploadRefusedDb.parse("", "browser").isEmpty());
    }

    public void testNamesAreCleaned() {
        assertEquals("y.jpg", ImageUploadRefusedDb.cleanName("C:\\x\\y.jpg"));
        assertEquals("y.jpg", ImageUploadRefusedDb.cleanName("../../etc/y.jpg"));
        assertEquals("ab.jpg", ImageUploadRefusedDb.cleanName("a\u0000b\u202e.jpg"));
        assertNull(ImageUploadRefusedDb.cleanName("   "));
        assertNull(ImageUploadRefusedDb.cleanName("folder/"));
        assertEquals(ImageUploadRefusedDb.MAX_NAME, ImageUploadRefusedDb.cleanName("x".repeat(5000)).length());
        // HTML is kept as text here; the report escapes it.
        assertEquals("<b>x</b>.jpg", ImageUploadRefusedDb.cleanName("<b>x</b>.jpg"));
    }

    public void testReasonsAreCleaned() {
        assertEquals("a b c", ImageUploadRefusedDb.cleanReason("a\tb\r\n  c"));
        assertEquals("not sent", ImageUploadRefusedDb.cleanReason(null));
        assertEquals("not sent", ImageUploadRefusedDb.cleanReason(" \u0007 "));
        String longReason = ImageUploadRefusedDb.cleanReason("r".repeat(10_000));
        assertEquals(ImageUploadRefusedDb.MAX_REASON, longReason.length());
        assertTrue(longReason.endsWith("..."));
    }

    public void testCountIsCapped() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < ImageUploadRefusedDb.MAX_PER_UPLOAD + 500; i++) b.append("F").append(i).append(".JPG\tx\n");
        assertEquals(ImageUploadRefusedDb.MAX_PER_UPLOAD, ImageUploadRefusedDb.parse(b.toString(), "browser").size());
        // Many blank lines do not make it read without end.
        String blanks = "\n".repeat(1_000_000) + "LAST.JPG\tx";
        assertTrue(ImageUploadRefusedDb.parse(blanks, "browser").isEmpty());
    }
}

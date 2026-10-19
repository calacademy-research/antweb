package test.org.calacademy.antweb.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import org.calacademy.antweb.util.Exif;
import org.calacademy.antweb.util.ExifText;
import org.calacademy.antweb.util.Html;

/**
 * EXIF on the image page (bigPicture, admin section): text in Latin-1 or UTF-8 reads
 * correctly, binary fields are summarized, values are escaped once.
 * The JPEGs are built here byte by byte, so the test needs no image files.
 */
public class ExifTextTest extends TestCase {

    private static final byte[] LATIN1_ARTIST = "José Pérez Núñez".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] UTF8_MODEL = "Prüfgerät <München>".getBytes(StandardCharsets.UTF_8);

    // --- decoding ---------------------------------------------------------------

    public void testLatin1TextDecodes() {
        String raw = new String(LATIN1_ARTIST, StandardCharsets.ISO_8859_1) + "\0";
        assertEquals("José Pérez Núñez", ExifText.decodeText(raw));
    }

    public void testUtf8TextDecodes() {
        String raw = new String("André Åström".getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1) + "\0\0";
        assertEquals("André Åström", ExifText.decodeText(raw));
    }

    public void testOldCommaEntityAndControlCharacters() {
        assertEquals("California Academy of Sciences, 2000-2009",
            ExifText.decodeText("California Academy of Sciences&#44; 2000-2009\0garbage"));
        assertEquals("a b", ExifText.decodeText("a\tb\u0001"));
    }

    public void testUserCommentCharacterCodes() {
        assertEquals("Attribution-ShareAlike", ExifText.decodeUserComment("ASCII\0\0\0Attribution-ShareAlike\0", false));
        assertEquals("", ExifText.decodeUserComment("ASCII\0\0\0", false));
        byte[] u = "François".getBytes(StandardCharsets.UTF_16BE);
        String raw = "UNICODE\0" + new String(u, StandardCharsets.ISO_8859_1);
        assertEquals("François", ExifText.decodeUserComment(raw, true));
    }

    // --- whole files ---------------------------------------------------------------

    public void testLittleEndianFile() throws Exception {
        Exif exif = Exif.fromBytes(jpeg(false));
        assertTrue(exif.isFound());
        assertEquals("José Pérez Núñez", exif.getArtist());
        assertEquals("California Academy of Sciences, 2000-2009", exif.getCopyright());
        assertEquals("02/21/2006", exif.getDate());

        assertRow(exif, "Artist", "José Pérez Núñez");
        assertRow(exif, "Camera model", "Prüfgerät <München>");
        assertRow(exif, "Lens", "Leica Z16 APO");
        assertRow(exif, "Exposure time", "1/250 s");
        assertRow(exif, "Aperture", "f/8");
        assertRow(exif, "ISO", "100");
        assertRow(exif, "Date taken", "2006-02-21 09:09:50");
        assertRow(exif, "Comment", "Attribution-ShareAlike Creative Commons License");
        assertRow(exif, "Camera maker's notes", "binary data, 40 bytes (not shown)");
        assertClean(exif);
    }

    public void testBigEndianFileFromDisk() throws Exception {
        File f = File.createTempFile("exif", ".jpg");
        try {
            Files.write(f.toPath(), jpeg(true));
            Exif exif = new Exif(f.getPath());
            assertTrue(exif.isFound());
            assertEquals("José Pérez Núñez", exif.getArtist());
            assertRow(exif, "Exposure time", "1/250 s");
            assertClean(exif);
        } finally {
            f.delete();
        }
    }

    public void testEscapedOnce() throws Exception {
        Exif exif = Exif.fromBytes(jpeg(false));
        String model = null;
        for (ExifText.Row r : exif.getRows()) if (r.label.equals("Camera model")) model = Html.esc(r.value);
        assertEquals("Prüfgerät &lt;München&gt;", model);
    }

    public void testNoExif() throws Exception {
        byte[] plain = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xDA, 0, 2, 1, 2, 3};
        Exif exif = Exif.fromBytes(plain);
        assertTrue(exif.getRows().isEmpty());
        assertEquals("", exif.getArtist());
    }

    // --- helpers ------------------------------------------------------------------

    private static void assertRow(Exif exif, String label, String value) {
        for (ExifText.Row r : exif.getRows()) {
            if (r.label.equals(label)) { assertEquals(label, value, r.value); return; }
        }
        fail("no row " + label + " in " + exif.getRows());
    }

    private static void assertClean(Exif exif) {
        for (ExifText.Row r : exif.getRows()) {
            String s = r.label + r.value;
            assertEquals("replacement character in " + r, -1, s.indexOf('�'));
            for (char c : s.toCharArray()) assertTrue("control character in " + r, c >= 0x20);
        }
    }

    /** A JPEG with an EXIF block (and no image data after the start of scan). */
    private static byte[] jpeg(boolean bigEndian) {
        Tiff t = new Tiff(bigEndian);
        List<int[]> ifd0 = new ArrayList<>();
        List<Object[]> e0 = new ArrayList<>();
        e0.add(new Object[]{0x013B, 2, z(LATIN1_ARTIST)});                                   // Artist, Latin-1
        e0.add(new Object[]{0x8298, 2, z("California Academy of Sciences&#44; 2000-2009".getBytes(StandardCharsets.US_ASCII))});
        e0.add(new Object[]{0x0110, 2, z(UTF8_MODEL)});                                      // Model, UTF-8
        e0.add(new Object[]{0x0132, 2, z("2006:02:21 09:09:50".getBytes(StandardCharsets.US_ASCII))});
        List<Object[]> e1 = new ArrayList<>();
        e1.add(new Object[]{0x829A, 5, new long[]{1, 250}});                                 // ExposureTime
        e1.add(new Object[]{0x829D, 5, new long[]{8, 1}});                                   // FNumber
        e1.add(new Object[]{0x8827, 3, 100});                                                // ISO
        e1.add(new Object[]{0x9003, 2, z("2006:02:21 09:09:50".getBytes(StandardCharsets.US_ASCII))});
        byte[] maker = new byte[40];
        for (int i = 0; i < maker.length; i++) maker[i] = (byte) (i * 7);
        e1.add(new Object[]{0x927C, 7, maker});                                              // MakerNote
        e1.add(new Object[]{0x9286, 7, cat("ASCII\0\0\0".getBytes(StandardCharsets.US_ASCII),
            "Attribution-ShareAlike Creative Commons License".getBytes(StandardCharsets.US_ASCII))});
        e1.add(new Object[]{0xA434, 2, z("Leica Z16 APO".getBytes(StandardCharsets.US_ASCII))});
        byte[] tiff = t.build(e0, e1);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF); out.write(0xD8);
        byte[] app1 = cat("Exif\0\0".getBytes(StandardCharsets.US_ASCII), tiff);
        out.write(0xFF); out.write(0xE1);
        int len = app1.length + 2;
        out.write(len >> 8); out.write(len & 0xFF);
        out.write(app1, 0, app1.length);
        out.write(0xFF); out.write(0xDA);
        out.write(0); out.write(8); out.write(1); out.write(2); out.write(3);
        return out.toByteArray();
    }

    private static byte[] z(byte[] b) { return cat(b, new byte[]{0}); }

    private static byte[] cat(byte[] a, byte[] b) {
        byte[] c = new byte[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

    /** Minimal TIFF writer: IFD0 with a pointer to an Exif IFD. Types: 2 ASCII, 3 SHORT, 5 RATIONAL, 7 UNDEFINED. */
    private static final class Tiff {
        final boolean be;
        final ByteArrayOutputStream data = new ByteArrayOutputStream();
        Tiff(boolean be) { this.be = be; }

        byte[] build(List<Object[]> ifd0, List<Object[]> exif) {
            int ifd0Size = 2 + 12 * (ifd0.size() + 1) + 4;
            int exifSize = 2 + 12 * exif.size() + 4;
            int ifd0At = 8, exifAt = ifd0At + ifd0Size, dataAt = exifAt + exifSize;
            byte[] a = ifd(ifd0, dataAt, exifAt);
            byte[] b = ifd(exif, dataAt, -1);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(be ? 'M' : 'I'); out.write(be ? 'M' : 'I');
            put(out, 0x2A, 2); put(out, ifd0At, 4);
            out.write(a, 0, a.length);
            out.write(b, 0, b.length);
            byte[] d = data.toByteArray();
            out.write(d, 0, d.length);
            return out.toByteArray();
        }

        private byte[] ifd(List<Object[]> entries, int dataAt, int exifPointer) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            put(out, entries.size() + (exifPointer >= 0 ? 1 : 0), 2);
            for (Object[] e : entries) {
                int tag = (Integer) e[0], type = (Integer) e[1];
                put(out, tag, 2); put(out, type, 2);
                byte[] value;
                int count;
                if (type == 3) {
                    ByteArrayOutputStream v = new ByteArrayOutputStream();
                    put(v, (Integer) e[2], 2); put(v, 0, 2);
                    value = v.toByteArray(); count = 1;
                } else if (type == 5) {
                    long[] r = (long[]) e[2];
                    ByteArrayOutputStream v = new ByteArrayOutputStream();
                    put(v, (int) r[0], 4); put(v, (int) r[1], 4);
                    value = v.toByteArray(); count = 1;
                } else {
                    value = (byte[]) e[2]; count = value.length;
                }
                put(out, count, 4);
                int size = type == 3 ? 2 : type == 5 ? 8 : count;
                if (size <= 4) {
                    byte[] four = new byte[4];
                    System.arraycopy(value, 0, four, 0, Math.min(4, value.length));
                    out.write(four, 0, 4);
                } else {
                    put(out, dataAt + data.size(), 4);
                    data.write(value, 0, value.length);
                    if (data.size() % 2 == 1) data.write(0);
                }
            }
            if (exifPointer >= 0) {
                put(out, 0x8769, 2); put(out, 4, 2); put(out, 1, 4); put(out, exifPointer, 4);
            }
            put(out, 0, 4);
            return out.toByteArray();
        }

        private void put(ByteArrayOutputStream out, int v, int n) {
            for (int i = 0; i < n; i++) {
                int shift = be ? 8 * (n - 1 - i) : 8 * i;
                out.write((v >> shift) & 0xFF);
            }
        }
    }
}

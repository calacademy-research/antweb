package org.calacademy.antweb.upload;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Takes one expected file out of an uploaded zip archive, in Java (no unzip command).
 *
 * The archive is refused, and nothing is written, when it has a symbolic link, an absolute
 * file name or one with "..", more than MAX_ENTRIES entries, more than MAX_TOTAL_BYTES of
 * uncompressed content, or not exactly one entry the caller wants. Only that entry is
 * written, to the caller's output file. Directory entries and the files macOS adds
 * (__MACOSX/, ._name, .DS_Store) are ignored.
 *
 * Refusal messages say what is wrong with the archive; they never quote its content.
 *
 * Used by SpecimenUploader (curator .txt in a zip) and Uploader.copyAndUnzipFile()
 * (data.tsv of a TaxonWorks archive, occurrence.txt of a GBIF archive).
 */
public final class SafeUnzip {

    /**
     * Uncompressed size cap. The largest uploads are CAS TaxonWorks archives (about 265,000
     * records, a data.tsv of a few hundred MB); the form takes at most 200 MB compressed
     * (struts-config.xml maxFileSize). 2 GB leaves a wide margin and keeps a zip bomb from
     * filling the disk.
     */
    public static final long MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024;

    /** A specimen zip has one file, a Darwin Core archive a handful. */
    public static final int MAX_ENTRIES = 1000;

    private static final int MAX_CENTRAL_DIRECTORY = 64 * 1024 * 1024;

    /** The archive is refused; the message can be shown to the uploader. */
    public static class Refused extends Exception {
        public Refused(String message) {
            super(message);
        }
    }

    /** Which entry (by its name in the archive, '/' separated) is the one wanted. */
    public interface Picker {
        boolean wanted(String name);
    }

    /** Curator specimen uploads: the one .txt file, at any depth. */
    public static final Picker ONE_TXT = name -> baseName(name).toLowerCase(Locale.ROOT).endsWith(".txt");

    /** TaxonWorks / GBIF archives: the named file at the top level of the archive. */
    public static Picker named(String topLevelName) {
        return name -> name.equals(topLevelName);
    }

    private SafeUnzip() {
    }

    public static String extract(File zip, Picker picker, String what, File out) throws Refused, IOException {
        return extract(zip, picker, what, out, MAX_TOTAL_BYTES, MAX_ENTRIES);
    }

    /**
     * Writes the one wanted entry of zip to out and returns its name in the archive.
     * what names the wanted file in messages, for example ".txt file" or "data.tsv file".
     */
    public static String extract(File zip, Picker picker, String what, File out, long maxTotalBytes, int maxEntries)
            throws Refused, IOException {
        if (zip == null || !zip.isFile()) throw new Refused("The uploaded zip file was not received.");

        List<Boolean> links = symlinkFlags(zip, maxEntries);

        try (ZipFile zf = open(zip)) {
            List<ZipEntry> wanted = new ArrayList<>();
            long declared = 0;
            int i = 0;
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (i >= maxEntries) throw new Refused("The zip file has more than " + maxEntries + " entries.");
                if (i >= links.size()) throw new Refused("The zip file could not be read.");
                if (links.get(i)) throw new Refused("The zip file contains a symbolic link, which is not allowed.");
                ++i;
                String name = e.getName().replace('\\', '/');
                if (!isSafeName(name)) {
                    throw new Refused("The zip file contains a file name that is not allowed (an absolute path or \"..\").");
                }
                if (e.getSize() > 0) declared += e.getSize();
                if (declared > maxTotalBytes) throw new Refused(tooLarge(maxTotalBytes));
                if (e.isDirectory() || name.endsWith("/") || isJunk(name)) continue;
                if (picker.wanted(name)) wanted.add(e);
            }
            if (i != links.size()) throw new Refused("The zip file could not be read.");
            if (wanted.isEmpty()) throw new Refused("The zip file has no " + what + ".");
            if (wanted.size() > 1) throw new Refused("The zip file must contain only one " + what + ".");

            ZipEntry entry = wanted.get(0);
            File part = new File(out.getPath() + ".part");
            long written = 0;
            boolean ok = false;
            try (InputStream in = zf.getInputStream(entry); OutputStream os = Files.newOutputStream(part.toPath())) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    written += n;
                    if (written > maxTotalBytes) throw new Refused(tooLarge(maxTotalBytes));
                    os.write(buf, 0, n);
                }
                ok = true;
            } catch (ZipException ze) {
                throw new Refused("The zip file could not be read.");
            } finally {
                if (!ok) Files.deleteIfExists(part.toPath());
            }
            Files.move(part.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return entry.getName();
        } catch (ZipException ze) {
            throw new Refused("The zip file could not be read.");
        }
    }

    private static String tooLarge(long max) {
        return "The zip file is too large when unpacked (more than " + (max / (1024 * 1024)) + " MB).";
    }

    /** Names in UTF-8 as most tools write them; older Windows tools use code page 437. */
    private static ZipFile open(File zip) throws IOException {
        try {
            return new ZipFile(zip, StandardCharsets.UTF_8);
        } catch (ZipException e) {
            return new ZipFile(zip, Charset.forName("IBM437"));
        }
    }

    /** Relative, no "..", no drive letter, no NUL. name uses '/' as separator. */
    static boolean isSafeName(String name) {
        if (name == null || name.isEmpty()) return false;
        if (name.indexOf('\0') >= 0) return false;
        if (name.startsWith("/")) return false;
        if (name.length() >= 2 && name.charAt(1) == ':' && Character.isLetter(name.charAt(0))) return false;
        for (String part : name.split("/")) {
            if (part.equals("..")) return false;
        }
        return true;
    }

    static boolean isJunk(String name) {
        for (String part : name.split("/")) {
            if (part.equals("__MACOSX")) return true;
        }
        String base = baseName(name);
        return base.startsWith("._") || base.equals(".DS_Store");
    }

    static String baseName(String name) {
        int i = name.lastIndexOf('/');
        return i < 0 ? name : name.substring(i + 1);
    }

    /**
     * One flag per central directory entry, in archive order: true for a Unix symbolic link
     * (made on Unix, file type 0120000 in the external attributes). java.util.zip has no
     * public accessor for these attributes, so the central directory is read here.
     */
    static List<Boolean> symlinkFlags(File zip, int maxEntries) throws IOException, Refused {
        try (RandomAccessFile raf = new RandomAccessFile(zip, "r")) {
            long len = raf.length();
            if (len < 22) throw new Refused("The uploaded file is not a zip file.");
            int tail = (int) Math.min(len, 22 + 65535 + 20);
            byte[] buf = new byte[tail];
            raf.seek(len - tail);
            raf.readFully(buf);
            int eocd = -1;
            for (int p = tail - 22; p >= 0; p--) {
                if (le32(buf, p) == 0x06054b50L) {
                    eocd = p;
                    break;
                }
            }
            if (eocd < 0) throw new Refused("The uploaded file is not a zip file.");
            long eocdPos = len - tail + eocd;
            long entries = le16(buf, eocd + 10);
            long cdSize = le32(buf, eocd + 12);
            long cdOff = le32(buf, eocd + 16);
            if (entries == 0xFFFF || cdSize == 0xFFFFFFFFL || cdOff == 0xFFFFFFFFL) {
                int loc = eocd - 20;
                if (loc < 0 || le32(buf, loc) != 0x07064b50L) throw new Refused("The zip file could not be read.");
                long z64 = le64(buf, loc + 8);
                if (z64 < 0 || z64 + 56 > len) throw new Refused("The zip file could not be read.");
                byte[] z = new byte[56];
                raf.seek(z64);
                raf.readFully(z);
                if (le32(z, 0) != 0x06064b50L) throw new Refused("The zip file could not be read.");
                entries = le64(z, 32);
                cdSize = le64(z, 40);
                cdOff = le64(z, 48);
                eocdPos = z64;
            }
            if (entries > maxEntries) throw new Refused("The zip file has more than " + maxEntries + " entries.");
            if (cdSize < 0 || cdSize > MAX_CENTRAL_DIRECTORY || cdSize > eocdPos) throw new Refused("The zip file could not be read.");
            // Data prepended to the archive shifts every offset: the central directory ends where the end record starts.
            long start = cdOff;
            if (start < 0 || start + cdSize > len || !isCentralHeader(raf, start)) start = eocdPos - cdSize;
            byte[] cd = new byte[(int) cdSize];
            raf.seek(start);
            raf.readFully(cd);
            List<Boolean> flags = new ArrayList<>();
            int p = 0;
            while (p + 46 <= cd.length && le32(cd, p) == 0x02014b50L) {
                int madeBy = (int) le16(cd, p + 4);
                int nameLen = (int) le16(cd, p + 28);
                int extraLen = (int) le16(cd, p + 30);
                int commentLen = (int) le16(cd, p + 32);
                long ext = le32(cd, p + 38);
                int mode = (int) (ext >>> 16);
                flags.add((madeBy >> 8) == 3 && (mode & 0170000) == 0120000);
                if (flags.size() > maxEntries) throw new Refused("The zip file has more than " + maxEntries + " entries.");
                p += 46 + nameLen + extraLen + commentLen;
            }
            if (flags.size() != entries) throw new Refused("The zip file could not be read.");
            return flags;
        }
    }

    private static boolean isCentralHeader(RandomAccessFile raf, long pos) throws IOException {
        byte[] b = new byte[4];
        raf.seek(pos);
        raf.readFully(b);
        return le32(b, 0) == 0x02014b50L;
    }

    private static long le16(byte[] b, int p) {
        return (b[p] & 0xff) | ((b[p + 1] & 0xff) << 8);
    }

    private static long le32(byte[] b, int p) {
        return le16(b, p) | (le16(b, p + 2) << 16);
    }

    private static long le64(byte[] b, int p) {
        return le32(b, p) | (le32(b, p + 4) << 32);
    }
}

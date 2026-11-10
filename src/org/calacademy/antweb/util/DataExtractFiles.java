package org.calacademy.antweb.util;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Where the data extracts of dataExtract.do live on the server, and the only code that finds, lists, serves and
 * removes them.
 *
 * Folder: [data root]/log/extracts/[yyyy-mm-dd]-[job number]/ (in the container /usr/local/antweb/log/extracts,
 * the host's logbak/extracts). That is the folder of the "data" logs, which is never served: Caddy answers 404 for
 * /log/ and nothing links there, and it is on the host, so a rebuilt container keeps the extracts. Not under
 * web/log/ (parts of it are public) and not at the top of the data root (the data root is the web root, and Caddy
 * lets *.txt through).
 *
 * A folder holds the files DataExtract writes and request.json (who asked, the note, the filters), which is never
 * offered for download. A file is found by job number and a name from the fixed list DOWNLOADS only: no part of a
 * path comes from a request. Files are offered only once manifest.json is there (the extract finished).
 *
 * Extracts older than KEEP_DAYS days (by the date in the folder name) are removed: after each daily update
 * (Scheduler) and whenever the page is opened.
 *
 * Tests: test/org/calacademy/antweb/util/DataExtractTest.java.
 */
public final class DataExtractFiles {

    private static final Log s_log = LogFactory.getLog(DataExtractFiles.class);

    private DataExtractFiles() {}

    public static final int KEEP_DAYS = 30;

    /** Who asked for the extract and why; never downloadable. */
    public static final String REQUEST = "request.json";

    /** Every file of an extract in one zip file, made when it is asked for (not a file in the folder). */
    public static final String ZIP = "all.zip";

    /** The only file names a download may name (ZIP besides), in the order the page lists them. */
    public static final List<String> DOWNLOADS = List.of(DataExtract.SPECIMENS, DataExtract.GENUS_SUMMARY,
        DataExtract.BIOREGION_LIST, DataExtract.MANIFEST_TXT, DataExtract.MANIFEST_JSON);

    static final Pattern FOLDER = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})-(\\d{1,9})");

    /** The folder all extracts are in (see the class comment for why there). */
    public static File root() {
        return new File(AntwebProps.getDataRoot() + "log/extracts");
    }

    /** One extract folder, as the page lists it. */
    public static final class Entry {
        public File folder;
        public int jobId;
        public LocalDate day;
        public int loginId;
        public String loginName = "";
        public String note = "";
        public List<String> countries = new ArrayList<>();
        public boolean islands;
        public String bioregion = "";
        /** From manifest.json; null while the extract has not finished (or did not). */
        public Long specimens;
        public Boolean verified;
        /** The downloadable files that exist, in DOWNLOADS order; empty until the extract has finished. */
        public List<String> files = new ArrayList<>();
        public long bytes;

        public boolean isFinished() { return verified != null; }
    }

    /** Makes the folder of a new extract. */
    public static File create(File root, LocalDate day, int jobId) throws IOException {
        if (jobId <= 0) throw new IOException("no job number for the extract folder");
        File folder = new File(root, day + "-" + jobId);
        Files.createDirectories(folder.toPath());
        return folder;
    }

    private static boolean isRealDirectory(File f) {
        return Files.isDirectory(f.toPath(), LinkOption.NOFOLLOW_LINKS);
    }

    private static boolean isRealFile(File f) {
        return Files.isRegularFile(f.toPath(), LinkOption.NOFOLLOW_LINKS);
    }

    /** The folder of a job's extract, or null. Found by listing the root: the job number is compared, never used as a path. */
    public static File find(File root, int jobId) {
        if (jobId <= 0) return null;
        File[] folders = root.listFiles();
        if (folders == null) return null;
        for (File f : folders) {
            Matcher m = FOLDER.matcher(f.getName());
            if (m.matches() && Integer.parseInt(m.group(2)) == jobId && isRealDirectory(f)) return f;
        }
        return null;
    }

    /**
     * A downloadable file of a finished extract, or null: the name must be one of DOWNLOADS, the extract must have
     * its manifest, and the file must be a plain file in the job's folder.
     */
    public static File file(File root, int jobId, String name) {
        if (name == null || !DOWNLOADS.contains(name)) return null;
        File folder = find(root, jobId);
        if (folder == null || !isRealFile(new File(folder, DataExtract.MANIFEST_JSON))) return null;
        File file = new File(folder, DOWNLOADS.get(DOWNLOADS.indexOf(name)));
        return isRealFile(file) ? file : null;
    }

    /** The downloadable files of a finished extract that exist, in DOWNLOADS order; empty when it has not finished. */
    public static List<File> files(File root, int jobId) {
        List<File> out = new ArrayList<>();
        for (String name : DOWNLOADS) {
            File f = file(root, jobId, name);
            if (f != null) out.add(f);
        }
        return out;
    }

    /** The name a browser saves a download under: antweb-extract-2026-11-10-123-specimens.csv.gz. */
    public static String downloadName(File folder, String name) {
        return "antweb-extract-" + folder.getName() + (ZIP.equals(name) ? ".zip" : "-" + name);
    }

    public static String contentType(String name) {
        if (name.endsWith(".gz")) return "application/gzip";
        if (name.endsWith(".zip")) return "application/zip";
        if (name.endsWith(".csv")) return "text/csv; charset=UTF-8";
        if (name.endsWith(".json")) return "application/json; charset=UTF-8";
        return "text/plain; charset=UTF-8";
    }

    /** Every file of a finished extract as one zip (stored, not compressed again: the large file is gzip already). */
    public static void writeZip(List<File> files, OutputStream out) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zip.setLevel(java.util.zip.Deflater.NO_COMPRESSION);
            for (File f : files) {
                zip.putNextEntry(new ZipEntry(f.getName()));
                Files.copy(f.toPath(), zip);
                zip.closeEntry();
            }
        }
    }

    // ------------------------------------------------------------------ request.json

    /** Records who asked for the extract, the note and the filters, in the folder (not downloadable). */
    public static void writeRequest(File folder, int jobId, int loginId, String loginName, String note,
                                    DataExtract.Request request) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("job", jobId);
        m.put("login_id", loginId);
        m.put("login_name", loginName == null ? "" : loginName);
        m.put("note", note == null ? "" : note);
        m.put("countries", request.countries);
        m.put("islands", request.islands);
        m.put("bioregion", request.bioregion);
        try (Writer w = Files.newBufferedWriter(new File(folder, REQUEST).toPath(), StandardCharsets.UTF_8)) {
            w.write(DataExtract.json(m));
        }
    }

    private static JsonObject readJson(File file) {
        if (!isRealFile(file)) return null;
        try {
            JsonElement e = JsonParser.parseString(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (IOException | RuntimeException e) {
            s_log.warn("readJson() " + file + " e:" + e);
            return null;
        }
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || !e.isJsonPrimitive() ? "" : e.getAsString();
    }

    private static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        JsonElement e = o.get(key);
        if (e != null && e.isJsonArray()) {
            for (JsonElement x : (JsonArray) e) if (x.isJsonPrimitive()) out.add(x.getAsString());
        }
        return out;
    }

    /** What the folder says about its extract (request.json, manifest.json, the files there). Never throws. */
    public static Entry read(File folder) {
        Matcher m = FOLDER.matcher(folder.getName());
        if (!m.matches() || !isRealDirectory(folder)) return null;
        Entry e = new Entry();
        e.folder = folder;
        e.jobId = Integer.parseInt(m.group(2));
        try {
            e.day = LocalDate.parse(m.group(1));
        } catch (DateTimeParseException x) {
            return null;
        }
        JsonObject request = readJson(new File(folder, REQUEST));
        if (request != null) {
            try {
                e.loginId = request.has("login_id") ? request.get("login_id").getAsInt() : 0;
                e.loginName = string(request, "login_name");
                e.note = string(request, "note");
                e.countries = strings(request, "countries");
                e.islands = request.has("islands") && request.get("islands").getAsBoolean();
                e.bioregion = string(request, "bioregion");
            } catch (RuntimeException x) {
                s_log.warn("read() " + folder + " request.json e:" + x);
            }
        }
        JsonObject manifest = readJson(new File(folder, DataExtract.MANIFEST_JSON));
        if (manifest != null) {
            try {
                e.specimens = manifest.get("specimens").getAsLong();
                e.verified = manifest.get("verified").getAsBoolean();
                if (e.countries.isEmpty()) e.countries = strings(manifest, "countries");
            } catch (RuntimeException x) {
                s_log.warn("read() " + folder + " manifest.json e:" + x);
                e.specimens = null;
                e.verified = null;
            }
        }
        if (e.isFinished()) {
            for (String name : DOWNLOADS) {
                File f = new File(folder, name);
                if (isRealFile(f)) {
                    e.files.add(name);
                    e.bytes += f.length();
                }
            }
        }
        return e;
    }

    /** The extracts on the server, newest first (by job number). */
    public static List<Entry> list(File root) {
        List<Entry> out = new ArrayList<>();
        File[] folders = root.listFiles();
        if (folders == null) return out;
        for (File f : folders) {
            Entry e = read(f);
            if (e != null) out.add(e);
        }
        out.sort((a, b) -> Integer.compare(b.jobId, a.jobId));
        return out;
    }

    // ------------------------------------------------------------------ removal

    /**
     * Removes an extract folder: the plain files in it, then the folder. Never goes into sub-folders and never
     * follows a link. False when something is left.
     */
    public static boolean delete(File folder) {
        if (!FOLDER.matcher(folder.getName()).matches() || !isRealDirectory(folder)) return false;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                Path p = f.toPath();
                try {
                    if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(p)) Files.delete(p);
                } catch (IOException e) {
                    s_log.warn("delete() " + f + " e:" + e);
                }
            }
        }
        try {
            Files.delete(folder.toPath());
            return true;
        } catch (IOException e) {
            s_log.warn("delete() " + folder + " not removed e:" + e);
            return false;
        }
    }

    /** Removes the data files of an extract that did not finish; request.json stays, so the page still lists it. */
    public static void deleteData(File folder) {
        if (!FOLDER.matcher(folder.getName()).matches() || !isRealDirectory(folder)) return;
        for (String name : DOWNLOADS) {
            try {
                Files.deleteIfExists(new File(folder, name).toPath());
            } catch (IOException e) {
                s_log.warn("deleteData() " + name + " e:" + e);
            }
        }
    }

    /** Removes the extracts whose folder date is more than days days before today; returns how many. */
    public static int removeOlderThan(File root, int days, LocalDate today) {
        int removed = 0;
        File[] folders = root.listFiles();
        if (folders == null) return 0;
        LocalDate oldest = today.minusDays(days);
        for (File f : folders) {
            Matcher m = FOLDER.matcher(f.getName());
            if (!m.matches()) continue;
            try {
                if (LocalDate.parse(m.group(1)).isBefore(oldest) && delete(f)) removed++;
            } catch (DateTimeParseException e) {
                // not a date: not one of ours
            }
        }
        return removed;
    }

    /** The daily removal, as one line for compute.log. Never throws. */
    public static String removeOld() {
        try {
            int n = removeOlderThan(root(), KEEP_DAYS, LocalDate.now());
            return "data extracts older than " + KEEP_DAYS + " days removed: " + n;
        } catch (RuntimeException e) {
            s_log.warn("removeOld() e:" + e);
            return "data extracts not checked for removal e:" + e;
        }
    }
}

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
 * Folder: [Tomcat folder]/logs/extracts/[yyyy-mm-dd]-[job number]/ (in the container
 * /usr/local/tomcat/logs/extracts, the host's logs/extracts in the project folder). It is OUTSIDE the web
 * application's folder (/usr/local/antweb, Tomcat's docBase), so no address reaches it, whatever Caddy lets
 * through and also for a request that comes to Tomcat directly; it is on the host (docker-compose.yml mounts
 * ./logs there), so a rebuilt container keeps the extracts, and antweb-fix-perms gives it to the user tomcat.
 * Not logs/web (that is the web application's web/log) and nowhere under the data root (the data root is the web
 * root). bin/clearDisk.py, which tidies Tomcat's logs, never walks it.
 *
 * A folder holds the files DataExtract writes and request.json (who asked, the note, the filters), which is never
 * offered for download. A file is found by job number and a name from the fixed list DOWNLOADS only: no part of a
 * path comes from a request. Files are offered only once manifest.json is there and can be read (the extract
 * finished; it is written last, in one step). An extract that is not verified is never offered as a zip, and its
 * files are saved under a name that says NOT-VERIFIED.
 *
 * Extracts older than KEEP_DAYS days (by the date in the folder name) are removed, and the half-written files of
 * an extract the server stopped in the middle of: after each daily update (Scheduler) and whenever the page is
 * opened. All extracts together may use MAX_TOTAL_BYTES; a new one is not started above that.
 *
 * Tests: test/org/calacademy/antweb/util/DataExtractTest.java.
 */
public final class DataExtractFiles {

    private static final Log s_log = LogFactory.getLog(DataExtractFiles.class);

    private DataExtractFiles() {}

    public static final int KEEP_DAYS = 30;

    /** All extracts on the server together: above this no new extract is started (delete one first). */
    public static final long MAX_TOTAL_BYTES = 5L * 1024 * 1024 * 1024;

    /** Who asked for the extract and why; never downloadable. */
    public static final String REQUEST = "request.json";

    /** Every file of an extract in one zip file, made when it is asked for (not a file in the folder). */
    public static final String ZIP = "all.zip";

    /** The only file names a download may name (ZIP besides), in the order the page lists them. */
    public static final List<String> DOWNLOADS = List.of(DataExtract.SPECIMENS, DataExtract.SPECIMENS_NO_COUNTRY, DataExtract.GENUS_SUMMARY,
        DataExtract.BIOREGION_LIST, DataExtract.MANIFEST_TXT, DataExtract.MANIFEST_JSON);

    static final Pattern FOLDER = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})-(\\d{1,9})");

    /** The folder all extracts are in: beside Tomcat's logs, outside the web application (see the class comment). */
    public static File root() {
        return new File(AntwebProps.getTomcatDir() + "logs/extracts");
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
        /** True: also the specimens that are not ants. */
        public boolean allFamilies;
        /** True: with the file of the specimens that have no country. */
        public boolean noCountry;
        public String bioregion = "";
        /** From manifest.json; null while the extract has not finished (or did not). */
        public Long specimens;
        public Boolean verified;
        /** From manifest.json: requested countries AntWeb has no specimen for (a warning on the page). */
        public List<String> emptyCountries = new ArrayList<>();
        /** The downloadable files that exist, in DOWNLOADS order; empty until the extract has finished. */
        public List<String> files = new ArrayList<>();
        public long bytes;

        public boolean isFinished() { return verified != null; }
    }

    /**
     * Makes the folder of a new extract. A folder that already has this job number (job numbers start again when
     * staging's database is refreshed from production) is an old extract and is removed first; if it cannot be
     * removed the new extract does not start, so a download never finds the old files under the new number.
     */
    public static File create(File root, LocalDate day, int jobId) throws IOException {
        if (jobId <= 0) throw new IOException("no job number for the extract folder");
        for (File old : folders(root, jobId)) {
            if (!delete(old)) throw new IOException("an older extract folder with job number " + jobId + " could not be removed: " + old.getName());
        }
        File folder = new File(root, day + "-" + jobId);
        Files.createDirectories(folder.toPath());
        return folder;
    }

    /** Every extract folder with this job number (normally one), newest date first. */
    static List<File> folders(File root, int jobId) {
        List<File> out = new ArrayList<>();
        if (jobId <= 0) return out;
        File[] all = root.listFiles();
        if (all == null) return out;
        for (File f : all) {
            Matcher m = FOLDER.matcher(f.getName());
            if (m.matches() && Integer.parseInt(m.group(2)) == jobId && isRealDirectory(f)) out.add(f);
        }
        out.sort((a, b) -> b.getName().compareTo(a.getName()));   // yyyy-mm-dd-N with the same N: by date
        return out;
    }

    private static boolean isRealDirectory(File f) {
        return Files.isDirectory(f.toPath(), LinkOption.NOFOLLOW_LINKS);
    }

    private static boolean isRealFile(File f) {
        return Files.isRegularFile(f.toPath(), LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * The folder of a job's extract, or null. Found by listing the root: the job number is compared, never used as
     * a path. Should two folders have the number, the newest date is the one.
     */
    public static File find(File root, int jobId) {
        List<File> folders = folders(root, jobId);
        return folders.isEmpty() ? null : folders.get(0);
    }

    /**
     * A downloadable file of a finished extract, or null: the name must be one of DOWNLOADS, the extract must have
     * a manifest that can be read (specimens and verified in it), and the file must be a plain file in the job's
     * folder.
     */
    public static File file(File root, int jobId, String name) {
        if (name == null || !DOWNLOADS.contains(name)) return null;
        File folder = find(root, jobId);
        if (folder == null) return null;
        Entry entry = read(folder);
        if (entry == null || !entry.isFinished()) return null;
        File file = new File(folder, DOWNLOADS.get(DOWNLOADS.indexOf(name)));
        return isRealFile(file) ? file : null;
    }

    /** True when the job's extract finished and its manifest says verified: only then is there a zip to send. */
    public static boolean isVerified(File root, int jobId) {
        File folder = find(root, jobId);
        Entry entry = folder == null ? null : read(folder);
        return entry != null && Boolean.TRUE.equals(entry.verified);
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

    /**
     * The name a browser saves a download under: antweb-extract-2026-11-10-123-specimens.csv.gz; for an extract
     * that is not verified antweb-extract-NOT-VERIFIED-2026-11-10-123-specimens.csv.gz, so the file says so
     * wherever it ends up.
     */
    public static String downloadName(File folder, String name, boolean verified) {
        return "antweb-extract-" + (verified ? "" : "NOT-VERIFIED-") + folder.getName() + (ZIP.equals(name) ? ".zip" : "-" + name);
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
        m.put("all_families", request.allFamilies);
        m.put("no_country", request.noCountry);
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
                e.allFamilies = request.has("all_families") && request.get("all_families").getAsBoolean();
                e.noCountry = request.has("no_country") && request.get("no_country").getAsBoolean();
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
                e.emptyCountries = strings(manifest, "countries_without_specimens");
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
        List<String> names = new ArrayList<>(DOWNLOADS);
        for (String name : DOWNLOADS) names.add(name + DataExtract.PART);
        for (String name : names) {
            try {
                Files.deleteIfExists(new File(folder, name).toPath());
            } catch (IOException e) {
                s_log.warn("deleteData() " + name + " e:" + e);
            }
        }
    }

    /**
     * Removes the half-written files of extracts that did not finish (the server stopped in the middle); returns
     * how many folders were tidied. active says whether a job number is waiting or running now: asked right
     * before deleting, and the folder is read again after that, so an extract that finishes at this moment keeps
     * its files.
     */
    public static int removeUnfinished(File root, java.util.function.IntPredicate active) {
        int tidied = 0;
        for (Entry e : list(root)) {
            if (e.isFinished() || active.test(e.jobId)) continue;
            Entry now = read(e.folder);
            if (now == null || now.isFinished()) continue;
            String[] before = e.folder.list();
            deleteData(e.folder);
            String[] after = e.folder.list();
            if (before != null && after != null && after.length < before.length) tidied++;
        }
        return tidied;
    }

    /** The bytes all extract folders use (every file in them, also half-written ones). */
    public static long totalBytes(File root) {
        long total = 0;
        File[] folders = root.listFiles();
        if (folders == null) return 0;
        for (File folder : folders) {
            if (!FOLDER.matcher(folder.getName()).matches() || !isRealDirectory(folder)) continue;
            File[] files = folder.listFiles();
            if (files == null) continue;
            for (File f : files) if (isRealFile(f)) total += f.length();
        }
        return total;
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
}

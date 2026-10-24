package org.calacademy.antweb.upload;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.Curator;
import org.calacademy.antweb.home.LoginDb;
import org.calacademy.antweb.util.AntwebMgr;
import org.calacademy.antweb.util.AntwebProps;
import org.calacademy.antweb.util.DateUtil;
import org.calacademy.antweb.util.LogMgr;

/**
 * Nightly TaxonWorks -> AntWeb specimen upload (GitHub issue #27).
 *
 * Fetches the CAS project's "complete" Darwin Core archive from TaxonWorks and
 * uploads it exactly as an administrator would with the TaxonWorks option on the
 * specimen upload page. Run from the nightly scheduler (set1), or by an
 * administrator: /utilData.do?action=taxonWorksNightly
 *
 * Settings: etc/taxonworks.properties on the server (not in git; the antweb
 * container sees it as /antweb/deploy/etc/taxonworks.properties):
 *   url=        the project's "Public download link" from TaxonWorks
 *               (Project -> Darwin core export); it contains the project token
 *   curatorId=  AntWeb login the upload is made as (its group owns the records)
 *   minRatio=   optional, default 0.95: skip the upload if the archive has fewer
 *               than this fraction of the group's current specimen records
 * Without the file the job does nothing.
 *
 * The job skips (and logs why) when TaxonWorks is still building the archive,
 * when the archive is identical to the last one uploaded, or when it would
 * shrink the group's records by more than the guard allows.
 */
public final class TaxonWorksNightly {

    private static final Log s_log = LogFactory.getLog(TaxonWorksNightly.class);
    static final String CONFIG = "/antweb/deploy/etc/taxonworks.properties";
    private static final String LOG = "taxonworksNightly.log";

    private TaxonWorksNightly() {}

    private static final java.util.concurrent.atomic.AtomicBoolean s_running = new java.util.concurrent.atomic.AtomicBoolean(false);

    /** Minutes between checks while waiting for TaxonWorks to build a fresh archive. */
    static final int WAIT_STEP_MINUTES = 3;

    public static String run(Connection connection) {
        return run(connection, 0);
    }

    /**
     * TaxonWorks builds a new archive at most once every 24 hours, and only when the
     * download link is requested; the request that starts a build still receives the
     * previous archive. So the nightly run (waitMinutes > 0) keeps checking every few
     * minutes, up to waitMinutes, while the archive is unchanged, still being built, or
     * built before this run's request (since 2026-10-24), and uploads the fresh one as soon
     * as it is there; at the last check it uploads an older archive rather than nothing. An admin run (waitMinutes 0)
     * checks once.
     */
    public static String run(Connection connection, int waitMinutes) {
        String result;
        // One run at a time: a second request (a browser retry, say) would read the
        // files the first run is still writing.
        if (!s_running.compareAndSet(false, true)) {
            result = "skipped: already running";
        } else {
            long start = System.currentTimeMillis();
            int checks = 0;
            try {
                while (true) {
                    checks++;
                    // The last check of the wait (or an admin run) uploads an older archive rather than nothing.
                    long waitedBefore = (System.currentTimeMillis() - start) / 60000;
                    boolean lastCheck = waitMinutes == 0 || waitedBefore + WAIT_STEP_MINUTES > waitMinutes;
                    result = runOnce(connection, start, lastCheck);
                    long waited = (System.currentTimeMillis() - start) / 60000;
                    if (!isWaitingForTaxonWorks(result) || waited + WAIT_STEP_MINUTES > waitMinutes) break;
                    if (checks == 1) {
                        LogMgr.appendLog(LOG, DateUtil.getFormatDateTimeStr() + " " + result
                            + "; waiting for TaxonWorks to build a new archive (checking every "
                            + WAIT_STEP_MINUTES + " min, up to " + waitMinutes + " min)");
                    }
                    Thread.sleep(WAIT_STEP_MINUTES * 60000L);
                }
                if (checks > 1) {
                    result += " (after " + checks + " checks, "
                        + (System.currentTimeMillis() - start) / 60000 + " min)";
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                result = "FAILED: interrupted while waiting for TaxonWorks";
            } catch (Exception e) {
                // Only the exception type in the logs: its text can include the download URL (with its token).
                result = "FAILED: " + e.getClass().getSimpleName();
                s_log.error("run() " + result + " (details in the server log)", e);
            } finally {
                s_running.set(false);
            }
        }
        LogMgr.appendLog(LOG, DateUtil.getFormatDateTimeStr() + " " + result);
        LogMgr.appendLog("admin.log", DateUtil.getFormatDateTimeStr() + " taxonWorksNightly: " + result);
        s_log.warn("run() " + result);
        return result;
    }

    private static String runOnce(Connection connection, long requestStart, boolean acceptOlderArchive) throws Exception {
        Properties props = new Properties();
        File config = new File(CONFIG);
        if (!config.isFile()) return "skipped: no " + CONFIG;
        try (InputStream in = new FileInputStream(config)) {
            props.load(in);
        }
        String url = props.getProperty("url", "").trim();
        int curatorId = Integer.parseInt(props.getProperty("curatorId", "0").trim());
        double minRatio = Double.parseDouble(props.getProperty("minRatio", "0.95").trim());
        if (url.isEmpty() || curatorId == 0) return "skipped: url or curatorId missing in " + CONFIG;

        Curator curator = new LoginDb(connection).getCurator(curatorId);
        if (curator == null || curator.getGroup() == null) return "skipped: no curator/group for curatorId " + curatorId;

        // 1. Fetch the archive.
        Path workDir = Paths.get(AntwebProps.getWorkingDir());
        Files.createDirectories(workDir);
        Path zip = workDir.resolve("taxonworksNightly.zip");
        Path tmp = workDir.resolve("taxonworksNightly.zip.part");
        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMinutes(60))
            .header("User-Agent", "AntWeb nightly TaxonWorks job (https://www.antweb.org)")
            .GET().build();
        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(tmp));
        if (response.statusCode() != 200 || !isZip(tmp)) {
            String body = "";
            try { body = Files.readString(tmp, StandardCharsets.UTF_8); } catch (IOException ignore) { }
            if (body.length() > 200) body = body.substring(0, 200);
            Files.deleteIfExists(tmp);
            return "skipped: TaxonWorks answered HTTP " + response.statusCode() + " " + body.replaceAll("\\s+", " ");
        }
        Files.move(tmp, zip, StandardCopyOption.REPLACE_EXISTING);

        // 2. Same archive as last time?
        String sha = sha256(zip);
        Path shaFile = workDir.resolve("taxonworksNightly.sha256");
        if (Files.exists(shaFile) && sha.equals(Files.readString(shaFile).trim())) {
            return "skipped: archive unchanged since the last upload";
        }

        // 2b. Built before this run's request? TaxonWorks answers the request that starts a build with the archive it
        // built the day before (2026-10-24: the 05:00 run uploaded the archive built 2026-10-23 05:01 Pacific, so a
        // name fixed in TaxonWorks on 10-23 arrived only on 10-25). Wait for the archive this request starts.
        java.time.LocalDateTime builtUtc = archiveBuiltUtc(zip);
        if (!acceptOlderArchive && isOlderArchive(builtUtc, requestStart)) {
            return "skipped: archive built " + builtUtc + " UTC, before this run's request (TaxonWorks builds the new one now)";
        }
        String builtNote = builtUtc == null ? "" : "; archive built " + builtUtc + " UTC"
            + (isOlderArchive(builtUtc, requestStart) ? " (older than this run: TaxonWorks did not build a new one in time)" : "");

        // 3. Unzip and transform, then check the record count.
        int groupId = curator.getGroup().getId();
        Path specimenFile = TaxonWorksUploader.prepareFromArchive(zip, curator.getGroup());
        long newCount = countRecords(specimenFile);
        java.util.List<String> skipped = TaxonWorksUploader.getLastSkipped();
        Path skippedFile = workDir.resolve("taxonworksSkipped.txt");
        Files.write(skippedFile, skipped, StandardCharsets.UTF_8);
        String skippedNote = skipped.isEmpty() ? "" : "; " + skipped.size() + " malformed records left out (fix in TaxonWorks): "
            + String.join(", ", skipped.subList(0, Math.min(10, skipped.size()))) + (skipped.size() > 10 ? ", ..." : "");
        long currentCount = countGroupSpecimens(connection, groupId);
        if (currentCount > 0 && newCount < minRatio * currentCount) {
            return "SKIPPED BY GUARD: archive has " + newCount + " records, group " + groupId + " has "
                + currentCount + " specimens now (minimum " + Math.round(minRatio * 100) + "%). Nothing changed.";
        }

        // 4. Upload, as the manual TaxonWorks upload does.
        if (UploadAction.getIsInUploadProcess() != null) {
            return "skipped: another upload is running (" + UploadAction.getIsInUploadProcess() + ")";
        }
        boolean autoCommit = connection.getAutoCommit();
        UploadAction.setIsInUploadProcess("TaxonWorks nightly:" + curator.getName());
        try {
            connection.setAutoCommit(false);
            AntwebMgr.incrementSpecimenUploadId(connection);
            UploadDetails details = new TaxonWorksUploader(connection).uploadPreparedFile(specimenFile, curator);
            if (details.getForwardPage() != null) {
                connection.rollback();
                return "FAILED: " + details.getMessage();
            }
            UploadAction.postProcessSpecimenUpload(connection, curator, details);
            UploadAction.specimenPostProcessGlobal(connection);
            details.setUploadSource("Nightly TaxonWorks job (automatic)");
            details.finish(curator, null, connection);
            connection.commit();
            Files.writeString(shaFile, sha);
            return "uploaded " + newCount + " records for group " + groupId + " (was " + currentCount
                + " specimens) in " + details.getExecTime() + builtNote + skippedNote;
        } catch (Exception e) {
            connection.rollback();
            throw e;
        } finally {
            UploadAction.setIsInUploadProcess(null);
            connection.setAutoCommit(autoCommit);
        }
    }

    /** True when the archive is the one already uploaded, older than this run's request, or still being built (HTTP 422). */
    static boolean isWaitingForTaxonWorks(String result) {
        return result != null && (result.startsWith("skipped: archive unchanged")
            || result.startsWith("skipped: archive built ")
            || result.startsWith("skipped: TaxonWorks answered HTTP 422"));
    }

    /**
     * The newest file time in the archive, read as UTC (TaxonWorks writes its times in UTC: the archive of
     * 2026-10-23 05:01 Pacific has 12:01). null when it cannot be read.
     */
    static java.time.LocalDateTime archiveBuiltUtc(Path zip) {
        java.time.LocalDateTime newest = null;
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(zip.toFile())) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> e = z.entries();
            while (e.hasMoreElements()) {
                java.time.LocalDateTime t = e.nextElement().getTimeLocal();
                if (t != null && (newest == null || t.isAfter(newest))) newest = t;
            }
        } catch (IOException ex) {
            s_log.warn("archiveBuiltUtc() e:" + ex);
        }
        return newest;
    }

    /**
     * True when the archive was built more than 12 hours before this run's request: the day-old archive that
     * TaxonWorks hands out while it builds the new one. 12 hours leaves room for a wrong time zone in either direction.
     */
    static boolean isOlderArchive(java.time.LocalDateTime builtUtc, long requestStartMillis) {
        if (builtUtc == null) return false;
        long built = builtUtc.toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
        return built < requestStartMillis - 12L * 3600 * 1000;
    }

    private static boolean isZip(Path file) throws IOException {
        if (Files.size(file) < 4) return false;
        try (InputStream in = Files.newInputStream(file)) {
            byte[] b = in.readNBytes(2);
            return b[0] == 'P' && b[1] == 'K';
        }
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return HexFormat.of().formatHex(md.digest());
    }

    /** Data lines in the transformed specimen file (header excluded). */
    private static long countRecords(Path file) throws IOException {
        try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return Math.max(0, lines.filter(l -> !l.isBlank()).count() - 1);
        }
    }

    private static long countGroupSpecimens(Connection connection, int groupId) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("select count(*) from specimen where access_group = ?")) {
            stmt.setInt(1, groupId);
            try (ResultSet rset = stmt.executeQuery()) {
                return rset.next() ? rset.getLong(1) : 0;
            }
        }
    }
}

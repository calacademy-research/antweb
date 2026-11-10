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
 *
 * Early request (2026-11-10, Brian Fisher: a faster daily update; production 11-10 waited about 18 of the upload's 40
 * minutes for a fresh archive): TaxonWorks builds an archive only when the download link is requested and the archive
 * on offer is older than the project's maximum age (TaxonWorks, Project, Darwin core export preferences: "max age" in
 * days, counted from the request that started the last build). So the link is also requested EARLY_REQUEST_MINUTES
 * before the daily update (04:35; earlyRequest, scheduled by SessionRequestFilter.runTask): a request only, no upload,
 * no database work, one line in compute.log. When that request starts a build, the upload at about 05:06 finds an
 * archive built that morning and loads it at its first check. When it does not (maximum age of one day: the last build
 * was requested less than 24 hours before), or when it fails, the upload requests and waits as before. Off without the
 * settings file (staging, development). See docs/flows/specimen-upload.md section 5.
 */
public final class TaxonWorksNightly {

    private static final Log s_log = LogFactory.getLog(TaxonWorksNightly.class);
    static final String CONFIG = "/antweb/deploy/etc/taxonworks.properties";
    private static final String LOG = "taxonworksNightly.log";

    private TaxonWorksNightly() {}

    private static final java.util.concurrent.atomic.AtomicBoolean s_running = new java.util.concurrent.atomic.AtomicBoolean(false);

    /** Minutes between checks while waiting for TaxonWorks to build a fresh archive. */
    static final int WAIT_STEP_MINUTES = 3;

    /** Text with every http(s) URL replaced by &lt;url&gt; (a download URL can carry the project token). */
    static String withoutUrls(String text) {
        if (text == null) return "";
        return text.replaceAll("(?i)https?://\\S+", "<url>").replaceAll("(?i)(token=)\\S+", "$1***");
    }

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
    /**
     * What to do after a check: "stop" (done, or that was the last check), "wait" (check again after
     * WAIT_STEP_MINUTES) or "final" (the wait is over but that check did not accept an older archive:
     * one more check that does).
     */
    static String afterCheck(boolean waitingForTaxonWorks, boolean wasLastCheck, long waitedMinutes, int waitMinutes) {
        if (!waitingForTaxonWorks || wasLastCheck) return "stop";
        if (waitedMinutes + WAIT_STEP_MINUTES > waitMinutes) return "final";
        return "wait";
    }

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
                    String next = afterCheck(isWaitingForTaxonWorks(result), lastCheck, waited, waitMinutes);
                    if ("final".equals(next)) {
                        // The wait ran out during a check that did not accept an older archive (a slow download):
                        // one more check that does, so the day is not left without an upload.
                        checks++;
                        result = runOnce(connection, start, true);
                        break;
                    }
                    if ("stop".equals(next)) break;
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
                // Server log: the message without any URL (a URL can carry the token), and where it happened.
                StackTraceElement[] at = e.getStackTrace();
                s_log.error("run() " + result + ": " + withoutUrls(e.getMessage()) + (at.length > 0 ? " at " + at[0] : ""));
            } finally {
                s_running.set(false);
            }
        }
        LogMgr.appendLog(LOG, DateUtil.getFormatDateTimeStr() + " " + result);
        LogMgr.appendLog("admin.log", DateUtil.getFormatDateTimeStr() + " taxonWorksNightly: " + result);
        // An older archive was uploaded (TaxonWorks did not build a new one in time): today's TaxonWorks edits are
        // missing until tomorrow. An admin alert and an email, so it is seen the same morning (2026-10-24).
        if (isOlderArchiveUploaded(result)) {
            String alert = "TaxonWorks nightly: " + result + ". Edits made in TaxonWorks since that build arrive with the next run.";
            try {
                org.calacademy.antweb.util.AdminAlertMgr.addIfFresh(alert, "TaxonWorks nightly:", "INTERVAL 1 DAY", connection);
                org.calacademy.antweb.util.Emailer.sendMail(org.calacademy.antweb.util.AntwebUtil.getDevEmail(),
                    "AntWeb: the CAS upload used an older TaxonWorks archive", alert);
            } catch (Exception e) {
                s_log.warn("run() alert e:" + e);
            }
        }
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

        // 3 and 4 hold the upload flag (claimed, not checked then set): a manual upload of the same group writes the same
        // working files (specimenTW<group>.txt), and only one upload may run at a time. Released only if still ours.
        String lockName = "TaxonWorks nightly:" + curator.getName();
        if (!UploadAction.claimUploadProcess(lockName)) {
            return "skipped: another upload is running (" + UploadAction.getIsInUploadProcess() + ")";
        }
        try {
            return prepareAndUpload(connection, curator, zip, workDir, minRatio, sha, shaFile, builtNote);
        } finally {
            UploadAction.releaseUploadProcess(lockName);
        }
    }

    /** Steps 3 and 4 of runOnce(); the caller holds the upload flag. */
    private static String prepareAndUpload(Connection connection, Curator curator, Path zip, Path workDir, double minRatio,
            String sha, Path shaFile, String builtNote) throws Exception {
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
        boolean autoCommit = connection.getAutoCommit();
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
            connection.setAutoCommit(autoCommit);
        }
    }

    // ------------------------------------------------------------------ the early request (2026-11-10)

    /** The archive is requested this many minutes before the daily update's launch hour (05:00: at 04:35). */
    public static final int EARLY_REQUEST_MINUTES = 25;

    /** The early request gives up after this long (the daily update then requests the archive itself, as before). */
    static final int EARLY_REQUEST_TIMEOUT_MINUTES = 10;

    /** compute.log: the line of the early request begins with this. */
    public static final String EARLY_LOG = "TaxonWorks early request";

    /**
     * The next early request: EARLY_REQUEST_MINUTES before launchHour:00, strictly after now, by the clock of now's time
     * zone (the JVM's: Pacific), so also on the days the clocks change.
     */
    public static java.util.Date nextEarlyRequestTime(java.util.Calendar now, int launchHour) {
        java.util.Calendar c = (java.util.Calendar) now.clone();
        int minuteOfDay = launchHour * 60 - EARLY_REQUEST_MINUTES;
        c.set(java.util.Calendar.HOUR_OF_DAY, minuteOfDay / 60);
        c.set(java.util.Calendar.MINUTE, minuteOfDay % 60);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        if (!c.after(now)) c.add(java.util.Calendar.DATE, 1);
        return c.getTime();
    }

    /**
     * Whether to send the early request: "off" without the settings file or its url (staging, development: nothing is
     * requested and nothing logged), "busy" while the upload job itself runs (an administrator's run: it has requested
     * the archive), else "request".
     */
    public static String earlyRequestDecision(boolean settingsFile, String url, boolean uploadRunning) {
        if (!settingsFile || url == null || url.trim().isEmpty()) return "off";
        if (uploadRunning) return "busy";
        return "request";
    }

    /**
     * The compute.log line of an early request. status: TaxonWorks' answer (0: none); builtUtc: when the archive it sent
     * was built (null: not an archive, or not read); error: the exception's class when the request failed, else null.
     * Never the URL (it carries the project token).
     */
    public static String earlyRequestLine(int status, java.time.LocalDateTime builtUtc, long nowMillis, String error) {
        String asBefore = "; the daily update requests the archive itself, as before";
        if (error != null) return EARLY_LOG + ": FAILED (" + error + ")" + asBefore;
        if (status == 422) return EARLY_LOG + ": HTTP 422, TaxonWorks is building the archive";
        if (status != 200) return EARLY_LOG + ": HTTP " + status + asBefore;
        if (builtUtc == null) return EARLY_LOG + ": HTTP 200, the answer was not a readable archive" + asBefore;
        long ageMinutes = (nowMillis - builtUtc.toInstant(java.time.ZoneOffset.UTC).toEpochMilli()) / 60000;
        String age = ageMinutes < 0 ? "" : " (" + String.format(java.util.Locale.US, "%.1f", ageMinutes / 60.0) + " hours ago)";
        return EARLY_LOG + ": HTTP 200, the archive on offer was built " + builtUtc + " UTC" + age
            + "; TaxonWorks builds a new one now if that is over the project's maximum age, for the upload of the daily update";
    }

    /**
     * Requests the archive from TaxonWorks so that it starts building a fresh one: a GET of the download link, the
     * answer saved to a file of its own in the working directory, read for its build time and deleted. No upload, no
     * database, none of the upload job's files. Returns the compute.log line, or null when off (no settings file or url).
     * Never throws: a failure is the line's text, and the daily update requests the archive itself, as before.
     */
    public static String earlyRequest() {
        try {
            File config = new File(CONFIG);
            String url = null;
            if (config.isFile()) {
                Properties props = new Properties();
                try (InputStream in = new FileInputStream(config)) {
                    props.load(in);
                }
                url = props.getProperty("url", "").trim();
            }
            String decision = earlyRequestDecision(config.isFile(), url, s_running.get());
            if ("off".equals(decision)) return null;
            if ("busy".equals(decision)) return EARLY_LOG + ": not sent, the TaxonWorks upload is running";
            return earlyRequest(url, Paths.get(AntwebProps.getWorkingDir()), EARLY_REQUEST_TIMEOUT_MINUTES * 60);
        } catch (Exception | LinkageError e) {
            return earlyRequestLine(0, null, System.currentTimeMillis(), e.getClass().getSimpleName());
        }
    }

    /** The request itself (the tests give a local address and a short timeout). Never throws. */
    public static String earlyRequest(String url, Path workDir, int timeoutSeconds) {
        Path tmp = null;
        try {
            Files.createDirectories(workDir);
            tmp = workDir.resolve("taxonworksEarlyRequest.zip.part");
            HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(Math.min(30, timeoutSeconds)))
                .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("User-Agent", "AntWeb nightly TaxonWorks job (https://www.antweb.org)")
                .GET().build();
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(tmp));
            java.time.LocalDateTime built = response.statusCode() == 200 && isZip(tmp) ? archiveBuiltUtc(tmp) : null;
            return earlyRequestLine(response.statusCode(), built, System.currentTimeMillis(), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return earlyRequestLine(0, null, System.currentTimeMillis(), "InterruptedException");
        } catch (Exception e) {
            // only the exception's class: its text can include the download URL (with its token)
            return earlyRequestLine(0, null, System.currentTimeMillis(), e.getClass().getSimpleName());
        } finally {
            try {
                if (tmp != null) Files.deleteIfExists(tmp);
            } catch (IOException ignore) { }
        }
    }

    /**
     * Schedules the early request for the next EARLY_REQUEST_MINUTES before launchHour, and after each one the next
     * (as the daily update's own timer: by the clock, not every 24 hours). The line goes to compute.log; off: no line.
     */
    public static void scheduleEarlyRequest(final java.util.Timer timer, final int launchHour) {
        java.util.Date next = nextEarlyRequestTime(java.util.Calendar.getInstance(), launchHour);
        timer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                try {
                    String line = earlyRequest();
                    if (line != null) LogMgr.appendLog("compute.log", "  " + line, true);
                } catch (Throwable t) {
                    s_log.warn("early request e:" + t.getClass().getSimpleName());
                } finally {
                    try {
                        scheduleEarlyRequest(timer, launchHour);
                    } catch (RuntimeException e) {
                        s_log.warn("early request: could not schedule the next one e:" + e);
                    }
                }
            }
        }, next);
    }

    /** True when the archive is the one already uploaded, older than this run's request, or still being built (HTTP 422). */
    static boolean isWaitingForTaxonWorks(String result) {
        return result != null && (result.startsWith("skipped: archive unchanged")
            || result.startsWith("skipped: archive built ")
            || result.startsWith("skipped: TaxonWorks answered HTTP 422"));
    }

    /** True when the run uploaded an archive built before its request (the last check of the wait accepted it). */
    static boolean isOlderArchiveUploaded(String result) {
        return result != null && result.startsWith("uploaded ") && result.contains("(older than this run");
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

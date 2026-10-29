package org.calacademy.antweb.upload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.calacademy.antweb.util.Html;

/**
 * The upload report page: the HTML file written at upload time into web/log/specimen/ (and web/log/upload/,
 * web/log/worldants/), linked from the upload-complete email, the upload status page and the group's upload list.
 *
 * Brian Fisher, 2026-10-29: "I really dislike how our specimen reports look"; the report takes the tool pages' look
 * (web/common/tools.css, docs/guides/page-style.md) and reads on a phone:
 *   1. a summary box: file, group, curator, upload id, date and the line counts (read, loaded, not loaded,
 *      corrected, red-flagged, notes), with the email's own sentence (UploadNotice.attentionSummary);
 *   2. the sections that found something, in groups: "Not loaded (fix these)", "Corrected by AntWeb (please fix in
 *      your data)", "Red-flagged" and "Notes". Which group a section is in is MessageMgr's rule, the same one that
 *      counts the email's summary (MessageMgr.attention, MessageMgr.reportSections); there is no second copy here;
 *   3. each row: the lines of the file (linked to the line as uploaded, logMgr.do?action=uploadLog, the specimen
 *      page's curator link), what the file said, what AntWeb did, and the specimens; long sections show their first
 *      rows and the rest under "Show all" (details/summary, no script);
 *   4. the checks that found nothing in one collapsed line, "N other checks found nothing";
 *   5. the upload's details and statistics in small tables at the bottom.
 *
 * Old reports are static files and stay as they are; the file names do not change. Every value written here is
 * escaped; the rows' values (MessageMgr) are HTML escaped where each message is added (they carry links).
 * Tested in UploadReportPageTest (the summary's counts equal the email's) and tests/e2e.
 */
public final class UploadReportPage {

    private UploadReportPage() {}

    /** Rows shown before "Show all" in a long section. */
    public static final int SHOWN_ROWS = 10;
    /** A section with more rows than this is collapsed after SHOWN_ROWS. */
    public static final int COLLAPSE_AFTER = 15;
    /** Line links shown in a row before "+N more". */
    public static final int SHOWN_LINES = 3;

    /** The report's group titles, in order (tests read them). */
    public static final Map<String, String> GROUP_TITLES = new LinkedHashMap<>();
    static {
        GROUP_TITLES.put(MessageMgr.NOT_LOADED, "Not loaded (fix these)");
        GROUP_TITLES.put(MessageMgr.CORRECTED_KIND, "Corrected by AntWeb (please fix in your data)");
        GROUP_TITLES.put(MessageMgr.RED_FLAG, "Red-flagged (loaded, hidden from most pages until fixed)");
        GROUP_TITLES.put(MessageMgr.NOTE, "Notes");
    }

    private static final Map<String, String> GROUP_LEADS = new LinkedHashMap<>();
    static {
        GROUP_LEADS.put(MessageMgr.NOT_LOADED, "These lines of your file are not in AntWeb. Fix them in your data and upload again.");
        GROUP_LEADS.put(MessageMgr.CORRECTED_KIND, "AntWeb changed what these lines said and loaded them. Please make the same change in your own data, so the next upload does not need it.");
        GROUP_LEADS.put(MessageMgr.RED_FLAG, "These specimens were loaded with a red flag: until the problem is fixed they do not appear in most of AntWeb.");
        GROUP_LEADS.put(MessageMgr.NOTE, "Loaded. Worth a look: names AntCat does not know, places AntWeb does not recognise, and similar.");
    }

    /** What AntWeb did with the lines of a section, when the heading does not say it plainly. */
    private static final Map<String, String> ACTIONS = new LinkedHashMap<>();
    static {
        ACTIONS.put(MessageMgr.duplicateEntries, "Not loaded: AntWeb already has a specimen with this code.");
        ACTIONS.put(MessageMgr.codeNotFoundInLine, "Not loaded: the line has no specimen code.");
        ACTIONS.put(MessageMgr.codeNotFound, "Not loaded.");
        ACTIONS.put(MessageMgr.recognizedInvalidSpecies, "Filed under the current valid name from AntCat.");
        ACTIONS.put(MessageMgr.synonymGenusReplaced, "Filed under the current valid genus from AntCat.");
        ACTIONS.put(MessageMgr.preferredSubfamilyForGenusReplaced, "Filed under AntCat's subfamily for the genus.");
        ACTIONS.put(MessageMgr.invalidSubfamilyForGenusReplaced, "Filed under AntCat's subfamily for the genus.");
        ACTIONS.put(MessageMgr.specimenIdSpacesRemoved, "Spaces removed from the code; loaded.");
        ACTIONS.put(MessageMgr.codeQuoteReplaced, "Each double quote replaced by -; loaded.");
        ACTIONS.put(MessageMgr.invalidDateCollectedStart, "Loaded without this date.");
        ACTIONS.put(MessageMgr.invalidDateCollectedEnd, "Loaded without this date.");
        ACTIONS.put(MessageMgr.invalidDateDetermined, "Loaded without this date.");
        ACTIONS.put(MessageMgr.subspeciesIndetDropped, "Loaded under genus and species; the subspecies value dropped.");
        ACTIONS.put(MessageMgr.speciesIndetSubspeciesIgnored, "Loaded as <Genus> (indet); the subspecies value ignored.");
        ACTIONS.put(MessageMgr.unrecognizedColumn, "Column ignored.");
        ACTIONS.put(MessageMgr.correctedBioregion, "Loaded with the bioregion of the country (or adm1).");
        ACTIONS.put(MessageMgr.identifiedOnlyToGenus, "Loaded as <Genus> (indet).");
        ACTIONS.put(MessageMgr.identifiedOnlyToSubfamily, "Loaded as (Subfamily) (indet).");
        ACTIONS.put(MessageMgr.currentValidNameNotInAntweb, "Loaded under the uploaded name.");
    }

    /** Everything the page shows besides MessageMgr's sections. Strings ending in Html are HTML already escaped. */
    public static final class Facts {
        public String domainApp = "";
        /** The tool pages' stylesheet (common/tools.css). */
        public String cssHref;
        public String title;
        public String operation;
        /** A message for the whole upload (UploadDetails.getMessage), HTML; null when none. */
        public String warningHtml;
        public int uploadId;
        public String date;
        public String encoding;
        public String version;
        public String logFileHtml;
        public String uploadedFileHtml;
        /** The uploaded file's name in web/upload/ (logMgr.do?action=uploadLog&file=); null: lines are not linked. */
        public String uploadFileName;
        public String uploadedBy;
        public String groupHtml;
        public String curatorHtml;
        public String adminHtml;
        public String loginId;
        public int recordCount;
        /** Parsed, processed, inserted: -1 when the upload is not a specimen upload. */
        public int parsed = -1;
        public int processed = -1;
        public int insertedSpecimens = -1;
        public String museumsHtml;
        public String worldantsLinksHtml;
        public String execTime;
        public int insertedSpecies;
        public int updatedSpecies;
        /** The project statistics tables (species list uploads), HTML; null when none. */
        public String statisticsHtml;
        /** Worldants uploads: their error list, HTML each; null otherwise. */
        public List<String> errorsHtml;
        /** False for Worldants reports: no sections, no list of checks (their errors only). */
        public boolean showChecks = true;
        /** The counts rule of the report (UploadDetails.COUNTS_RULE). */
        public String countsRule;
    }

    /** The counts of the summary box: the email's counts (UploadNotice.Facts) plus notes and lines read. */
    public static UploadNotice.Facts noticeFacts(MessageMgr m, int insertedSpecimens) {
        UploadNotice.Facts f = new UploadNotice.Facts();
        f.complete = true;
        f.specimensLoaded = insertedSpecimens;
        f.redFlagged = m.getRedFlagCount();
        f.notLoaded = m.getNotLoadedCount();
        f.corrected = m.getCorrectedCount();
        return f;
    }

    public static String render(Facts f, MessageMgr m) {
        String app = f.domainApp == null ? "" : f.domainApp;
        boolean specimen = f.parsed >= 0;
        List<MessageMgr.ReportSection> sections = f.showChecks ? m.reportSections() : new ArrayList<>();
        UploadNotice.Facts counts = noticeFacts(m, f.insertedSpecimens);
        int notes = m.getNoteCount();

        StringBuilder b = new StringBuilder(32768);
        b.append("<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"utf-8\"/>")
         .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>")
         .append("<title>").append(Html.esc(f.title)).append("</title>")
         .append("<link rel='shortcut icon' href='").append(Html.escAttr(app)).append("/image/favicon.ico' />");
        if (f.cssHref != null) b.append("<link href=\"").append(Html.escAttr(f.cssHref)).append("\" rel=\"stylesheet\" type=\"text/css\">");
        b.append("<style>").append(CSS).append("</style></head>\n<body class=\"rep-body\">\n");
        b.append("<div class=\"aw-tools aw-report\" id=\"upload-report\">\n");
        b.append("<p class=\"aw-crumbs\"><a href=\"").append(Html.escAttr(app)).append("\">Home</a> | <a href=\"")
         .append(Html.escAttr(app)).append("/curate.do\">Curator Tools</a></p>\n");
        b.append("<h1 class=\"aw-title\">Upload Report</h1>\n");
        if (f.countsRule != null) b.append("<span id=\"report-counts-rule\" hidden>").append(Html.esc(f.countsRule)).append("</span>");
        if (f.warningHtml != null && !f.warningHtml.isEmpty()) b.append("<div class=\"aw-msg\" id=\"rep-warning\">").append(f.warningHtml).append("</div>\n");
        String stop = MessageMgr.getStopMessage();
        if (stop != null && !stop.isEmpty()) b.append("<div class=\"aw-msg\" id=\"rep-stop\">").append(stop).append("</div>\n");

        summary(b, f, counts, notes, specimen);

        // The sections that found something, in their groups.
        for (Map.Entry<String, String> g : GROUP_TITLES.entrySet()) {
            List<MessageMgr.ReportSection> in = new ArrayList<>();
            int lines = 0;
            for (MessageMgr.ReportSection s : sections) {
                if (g.getKey().equals(s.kind)) { in.add(s); lines += s.count; }
            }
            if (in.isEmpty()) continue;
            b.append("<div class=\"rep-group rep-").append(g.getKey()).append("\" id=\"rep-").append(g.getKey()).append("\">\n");
            b.append("<h2 class=\"rep-group-title\">").append(Html.esc(g.getValue()))
             .append(" <span class=\"rep-pill\">").append(plural(lines, "line")).append("</span></h2>\n");
            b.append("<p class=\"aw-lead\">").append(Html.esc(GROUP_LEADS.get(g.getKey()))).append("</p>\n");
            for (MessageMgr.ReportSection s : in) section(b, s, f, app);
            b.append("</div>\n");
        }

        if (f.errorsHtml != null && !f.errorsHtml.isEmpty()) {
            b.append("<div class=\"aw-card\" id=\"rep-errors\"><h2>Errors</h2><ol class=\"rep-errors\">");
            for (String e : f.errorsHtml) b.append("<li>").append(e).append("</li>");
            b.append("</ol></div>\n");
        }

        // The checks that found nothing: one line.
        List<String> passed = f.showChecks ? m.passedHeadings() : new ArrayList<>();
        if (!passed.isEmpty()) {
            b.append("<details class=\"aw-card rep-passed\" id=\"rep-passed\"><summary>")
             .append(sections.isEmpty() ? "All " + passed.size() + " checks found nothing" : passed.size() + " other " + (passed.size() == 1 ? "check" : "checks") + " found nothing")
             .append("</summary><ul>");
            for (String h : passed) b.append("<li>").append(headingText(h)).append("</li>");
            b.append("</ul></details>\n");
        }

        details(b, f, counts, specimen, app);

        b.append("</div>\n</body></html>\n");
        return b.toString();
    }

    private static void summary(StringBuilder b, Facts f, UploadNotice.Facts c, int notes, boolean specimen) {
        b.append("<div class=\"aw-card rep-summary\" id=\"rep-summary\">\n");
        b.append("<table class=\"aw-kv rep-facts\">");
        kv(b, "File", f.uploadedFileHtml);
        kv(b, "Group", f.groupHtml);
        kv(b, "Curator", f.curatorHtml);
        if (f.adminHtml != null) kv(b, "Admin", f.adminHtml);
        kv(b, "Upload ID", f.uploadId > 0 ? "<a href=\"" + Html.escAttr(f.domainApp) + "/uploadReport.do?uploadId=" + f.uploadId + "\">" + f.uploadId + "</a>" : null);
        kv(b, "Date", Html.esc(f.date));
        b.append("</table>\n");

        b.append("<div class=\"rep-tiles\">");
        tile(b, "read", "Lines read", f.recordCount, false);
        if (specimen) {
            tile(b, "loaded", "Loaded", Math.max(f.insertedSpecimens, 0), false);
            tile(b, "notLoaded", "Not loaded", c.notLoaded, c.notLoaded > 0);
            tile(b, "corrected", "Corrected", c.corrected, c.corrected > 0);
            tile(b, "redFlagged", "Red-flagged", c.redFlagged, c.redFlagged > 0);
        } else {
            tile(b, "notLoaded", "Not loaded", c.notLoaded, c.notLoaded > 0);
            tile(b, "corrected", "Corrected", c.corrected, c.corrected > 0);
        }
        tile(b, "notes", "Notes", notes, false);
        b.append("</div>\n");

        String sentence = UploadNotice.attentionSummary(c);
        if (sentence != null) b.append("<p class=\"rep-sentence\" id=\"rep-sentence\">").append(Html.esc(sentence)).append("</p>\n");
        if (specimen && f.insertedSpecimens >= 0) {
            int unlisted = f.recordCount - f.insertedSpecimens - c.notLoaded;
            if (unlisted > 0) {
                b.append("<p class=\"aw-note\" id=\"rep-unlisted\">").append(plural(unlisted, "other line"))
                 .append(unlisted == 1 ? " was" : " were").append(" read but not loaded and ")
                 .append(unlisted == 1 ? "is" : "are").append(" not listed below (for example an empty line, or a line the upload could not read).</p>\n");
            }
        }
        b.append("</div>\n");
    }

    private static void tile(StringBuilder b, String id, String label, int n, boolean alert) {
        b.append("<div class=\"rep-tile rep-tile-").append(id).append(alert ? " rep-alert" : "").append("\" id=\"rep-count-").append(id)
         .append("\" data-count=\"").append(n).append("\"><span class=\"rep-n\">").append(String.format("%,d", n))
         .append("</span><span class=\"rep-l\">").append(Html.esc(label)).append("</span></div>");
    }

    private static void kv(StringBuilder b, String label, String valueHtml) {
        if (valueHtml == null || valueHtml.isEmpty()) return;
        b.append("<tr><th scope=\"row\">").append(Html.esc(label)).append("</th><td>").append(valueHtml).append("</td></tr>");
    }

    private static void section(StringBuilder b, MessageMgr.ReportSection s, Facts f, String app) {
        boolean anyLines = false, anyCodes = false, anyAction = false;
        List<String[]> split = new ArrayList<>();
        for (MessageMgr.ReportRow r : s.rows) {
            if (!r.lines.isEmpty()) anyLines = true;
            if (r.codesHtml != null && !r.codesHtml.isEmpty()) anyCodes = true;
            String[] parts = splitArrow(r.valueHtml);
            if (parts[1] != null || r.detailHtml != null) anyAction = true;
            split.add(parts);
        }
        anyLines = anyLines && f.uploadFileName != null;

        b.append("<section class=\"aw-card rep-sec\" id=\"sec-").append(Html.escAttr(s.key)).append("\" data-key=\"").append(Html.escAttr(s.key))
         .append("\" data-kind=\"").append(s.kind).append("\" data-total=\"").append(s.count).append("\" data-rows=\"").append(s.rows.size()).append("\">\n");
        b.append("<h3 class=\"rep-sec-title\">").append(headingText(s.headingHtml))
         .append(" <span class=\"rep-pill\">").append(plural(s.count, "line")).append("</span>");
        if (s.detailHref != null) b.append(" <a class=\"rep-detail\" href=\"").append(Html.escAttr(s.detailHref)).append("\">details</a>");
        if (s.listHref != null) b.append(" <a class=\"rep-detail\" href=\"").append(Html.escAttr(s.listHref)).append("\">list</a>");
        b.append("</h3>\n");
        String action = ACTIONS.get(s.key);
        if (action == null) action = defaultAction(s);
        if (action != null) b.append("<p class=\"rep-did\"><span>AntWeb:</span> ").append(Html.esc(action)).append("</p>\n");

        if (s.rows.isEmpty()) {
            b.append("</section>\n");
            return;
        }
        int n = s.rows.size();
        int shown = n > COLLAPSE_AFTER ? SHOWN_ROWS : n;
        table(b, s, split, 0, shown, anyLines, anyAction, anyCodes, f, app);
        if (shown < n) {
            b.append("<details class=\"rep-more\"><summary>Show all ").append(String.format("%,d", n)).append(" rows</summary>\n");
            table(b, s, split, shown, n, anyLines, anyAction, anyCodes, f, app);
            b.append("</details>\n");
        }
        b.append("</section>\n");
    }

    private static void table(StringBuilder b, MessageMgr.ReportSection s, List<String[]> split, int from, int to,
                              boolean anyLines, boolean anyAction, boolean anyCodes, Facts f, String app) {
        b.append("<div class=\"aw-table-wrap\"><table class=\"rep-rows\"><thead><tr>");
        if (anyLines) b.append("<th class=\"rep-c-line\">Line</th>");
        b.append("<th>In your file</th>");
        if (anyAction) b.append("<th>AntWeb</th>");
        if (anyCodes) b.append("<th>Specimens</th>");
        b.append("</tr></thead><tbody>\n");
        for (int i = from; i < to; i++) {
            MessageMgr.ReportRow r = s.rows.get(i);
            String[] parts = split.get(i);
            b.append("<tr>");
            if (anyLines) b.append("<td class=\"rep-c-line\" data-label=\"Line\">").append(lineLinks(r, f, app)).append("</td>");
            String written = anyLines && !r.lines.isEmpty() ? withoutLineNumber(parts[0]) : parts[0];
            if (written == null || written.isEmpty()) written = "&mdash;";
            b.append("<td class=\"rep-c-file\" data-label=\"In your file\">").append(written).append("</td>");
            if (anyAction) {
                String did = parts[1] != null ? parts[1] : r.detailHtml != null ? r.detailHtml : "";
                b.append("<td class=\"rep-c-did\" data-label=\"AntWeb\">").append(did).append("</td>");
            }
            if (anyCodes) b.append("<td class=\"rep-c-codes\" data-label=\"Specimens\">").append(r.codesHtml == null ? "" : r.codesHtml).append("</td>");
            b.append("</tr>\n");
        }
        b.append("</tbody></table></div>\n");
    }

    /** The row's lines, each linked to the line as uploaded; past SHOWN_LINES the rest under "+N more". */
    static String lineLinks(MessageMgr.ReportRow r, Facts f, String app) {
        if (r.lines.isEmpty() || f.uploadFileName == null) return "";
        StringBuilder b = new StringBuilder();
        String base = app + "/logMgr.do?action=uploadLog&file=" + Html.url(f.uploadFileName) + "&line=";
        int i = 0;
        for (int[] line : r.lines) {
            if (i == SHOWN_LINES) b.append("<details class=\"rep-lines\"><summary>+").append(String.format("%,d", r.lineCount - SHOWN_LINES)).append(" more</summary>");
            if (i > 0 && i != SHOWN_LINES) b.append(", ");
            b.append("<a href=\"").append(Html.escAttr(base + line[0])).append("\" title=\"This line as uploaded\">").append(line[1]).append("</a>");
            ++i;
        }
        if (r.lineCount > r.lines.size()) b.append(" ... and ").append(String.format("%,d", r.lineCount - r.lines.size())).append(" more");
        if (i > SHOWN_LINES) b.append("</details>");
        return b.toString();
    }

    /** A value that starts with its line number ("line:926 code:x"), without it when the row has a Line column;
     *  "(no specimen code)" for a line whose code was empty. */
    static String withoutLineNumber(String valueHtml) {
        if (valueHtml == null) return "";
        String v = valueHtml.replaceFirst("^(?i)line:?\\s*\\d+[\\s,]*", "");
        if (v.equals(valueHtml)) return valueHtml;
        if (v.isEmpty() || v.trim().equalsIgnoreCase("code:")) return "(no specimen code)";
        return v;
    }

    /** "old -> new" (or "old -&gt; new") as {old, new}; {value, null} when the value has no arrow. */
    static String[] splitArrow(String valueHtml) {
        if (valueHtml == null) return new String[] {"", null};
        for (String arrow : new String[] {" -> ", " -&gt; "}) {
            int i = valueHtml.indexOf(arrow);
            if (i >= 0) return new String[] {valueHtml.substring(0, i), valueHtml.substring(i + arrow.length())};
        }
        return new String[] {valueHtml, null};
    }

    private static String defaultAction(MessageMgr.ReportSection s) {
        String h = s.headingHtml == null ? "" : s.headingHtml;
        if (MessageMgr.NOT_LOADED.equals(s.kind)) return "Not loaded.";
        if (MessageMgr.RED_FLAG.equals(s.kind)) return "Loaded with a red flag.";
        if (MessageMgr.CORRECTED_KIND.equals(s.kind)) return "Corrected and loaded.";
        if (h.contains("(uploaded)")) return "Loaded.";
        return null;
    }

    /** A MessageMgr heading as the section's title: its words and links, without the old bold and font tags. */
    static String headingText(String headingHtml) {
        if (headingHtml == null) return "";
        return headingHtml.replaceAll("(?i)</?(b|font)(\\s[^>]*)?>", "").replaceAll("\\s+", " ").trim();
    }

    private static void details(StringBuilder b, Facts f, UploadNotice.Facts c, boolean specimen, String app) {
        b.append("<div class=\"aw-card rep-details\" id=\"rep-details\"><h2>Upload details</h2>\n<table class=\"aw-kv\">");
        kv(b, "Operation", Html.esc(f.operation));
        kv(b, "Upload ID", f.uploadId > 0 ? "<a href=\"" + Html.escAttr(app) + "/uploadReport.do?uploadId=" + f.uploadId + "\">" + f.uploadId + "</a>" : null);
        kv(b, "This Log File", f.logFileHtml);
        kv(b, "Uploaded File", f.uploadedFileHtml);
        kv(b, "Uploaded by", Html.esc(f.uploadedBy));
        kv(b, "Group Id", f.groupHtml);
        kv(b, "Curator", f.curatorHtml);
        kv(b, "Admin", f.adminHtml);
        kv(b, "Login Id", Html.esc(f.loginId));
        kv(b, "Date", Html.esc(f.date));
        kv(b, "Encoding", Html.esc(f.encoding));
        kv(b, "Antweb version", Html.esc(f.version));
        kv(b, "Record Count", String.valueOf(f.recordCount));
        if (specimen) {
            kv(b, "Parsed", String.valueOf(f.parsed));
            kv(b, "Processed", String.valueOf(f.processed));
            kv(b, "Museums", f.museumsHtml);
            kv(b, "Inserted Specimens", String.valueOf(Math.max(f.insertedSpecimens, 0)));
            kv(b, "Red Flagged Specimens", String.valueOf(c.redFlagged));
            if (f.uploadId > 0) {
                kv(b, "Advanced Search", "<a href=\"" + Html.escAttr(app + "/advancedSearch.do?searchMethod=advancedSearch&advanced=true&uploadId=" + f.uploadId)
                    + "\">This Upload</a> <span class=\"aw-note\">(may be changed by later uploads; red-flagged specimens are not included)</span>");
            }
        }
        if (f.insertedSpecies > 0) kv(b, "Inserted Species", String.valueOf(f.insertedSpecies));
        if (f.updatedSpecies > 0) kv(b, "Updated Species", String.valueOf(f.updatedSpecies));
        kv(b, "Worldants", f.worldantsLinksHtml);
        kv(b, "Exec Time", Html.esc(f.execTime));
        b.append("</table>\n");
        if (f.statisticsHtml != null && !f.statisticsHtml.isEmpty()) {
            b.append("<h3>Statistics</h3>\n<div class=\"rep-stats\">").append(f.statisticsHtml).append("</div>\n");
        }
        b.append("</div>\n");
    }

    static String plural(int n, String word) {
        return String.format("%,d", n) + " " + word + (n == 1 ? "" : "s");
    }

    /*
     * The report's own rules, inline so that the file reads the same on any later version of tools.css; every rule
     * is scoped to .aw-report. The page also loads tools.css (the tool pages' cards, tables and type).
     */
    static final String CSS =
        "body.rep-body{margin:0;background:#fff;}"
        + ".aw-report{max-width:1100px;margin:0 auto;padding:12px 16px 24px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','Helvetica Neue',Helvetica,Arial,sans-serif;"
        + "font-size:14px;line-height:1.5;color:#2d3338;box-sizing:border-box;}"
        + ".aw-report *{box-sizing:border-box;}"
        + ".aw-report a{color:#1f63c6;}"
        + ".aw-report .rep-summary .rep-facts{margin:0 0 12px 0;}"
        + ".aw-report .rep-tiles{display:grid;grid-template-columns:repeat(auto-fit,minmax(110px,1fr));gap:8px;margin:0 0 12px 0;}"
        + ".aw-report .rep-tile{background:#fff;border:1px solid #e1e5ea;border-radius:8px;padding:8px 12px;}"
        + ".aw-report .rep-n{display:block;font-size:22px;font-weight:650;line-height:1.2;font-variant-numeric:tabular-nums;color:#1f2529;}"
        + ".aw-report .rep-l{display:block;font-size:12px;color:#66707a;text-transform:uppercase;letter-spacing:.04em;font-weight:600;}"
        + ".aw-report .rep-tile-notLoaded.rep-alert,.aw-report .rep-tile-redFlagged.rep-alert{background:#fdecea;border-color:#f3c6bd;}"
        + ".aw-report .rep-tile-notLoaded.rep-alert .rep-n,.aw-report .rep-tile-redFlagged.rep-alert .rep-n{color:#a0281a;}"
        + ".aw-report .rep-tile-corrected.rep-alert{background:#fff8eb;border-color:#f0d9a8;}"
        + ".aw-report .rep-tile-corrected.rep-alert .rep-n{color:#8a5300;}"
        + ".aw-report .rep-sentence{font-weight:600;margin:0 0 4px 0;}"
        + ".aw-report .rep-group{margin:22px 0 0 0;}"
        + ".aw-report h2.rep-group-title{font-size:18px;margin:0 0 4px 0;padding-left:10px;border-left:4px solid #8a96a3;}"
        + ".aw-report .rep-notLoaded h2.rep-group-title{border-left-color:#c0392b;}"
        + ".aw-report .rep-corrected h2.rep-group-title{border-left-color:#d97706;}"
        + ".aw-report .rep-redFlag h2.rep-group-title{border-left-color:#e11d48;}"
        + ".aw-report .rep-group > .aw-lead{margin:0 0 10px 14px;color:#5b6570;}"
        + ".aw-report .rep-pill{display:inline-block;vertical-align:2px;margin-left:6px;padding:1px 9px;border-radius:999px;background:#e8edf5;"
        + "color:#44546a;font-size:12px;font-weight:650;white-space:nowrap;}"
        + ".aw-report .rep-notLoaded .rep-pill{background:#fdecea;color:#a0281a;}"
        + ".aw-report .rep-corrected .rep-pill{background:#fff1d6;color:#8a5300;}"
        + ".aw-report .rep-redFlag .rep-pill{background:#ffe4e9;color:#9f1239;}"
        + ".aw-report h3.rep-sec-title{font-size:15px;font-weight:650;color:#1f2529;text-transform:none;letter-spacing:0;margin:0 0 4px 0;}"
        + ".aw-report .rep-detail{font-size:13px;font-weight:600;margin-left:4px;}"
        + ".aw-report .rep-did{margin:0 0 8px 0;color:#4a545e;font-size:13px;}"
        + ".aw-report .rep-did span{font-weight:650;color:#3a434b;}"
        + ".aw-report .rep-sec{padding:12px 16px 10px;}"
        + ".aw-report table.rep-rows{width:100%;}"
        + ".aw-report table.rep-rows td{overflow-wrap:anywhere;}"
        + ".aw-report td.rep-c-line,.aw-report th.rep-c-line{white-space:nowrap;width:1%;font-variant-numeric:tabular-nums;}"
        + ".aw-report td.rep-c-line details{display:inline;}"
        + ".aw-report td.rep-c-line details[open]{display:block;white-space:normal;max-width:16em;}"
        + ".aw-report .rep-more{margin:4px 0 0 0;}"
        + ".aw-report .rep-more > summary,.aw-report .rep-passed > summary{font-weight:650;color:#1f63c6;}"
        + ".aw-report .rep-more > summary{margin:0 0 8px 0;}"
        + ".aw-report .rep-passed{margin-top:22px;}"
        + ".aw-report .rep-passed ul{margin:10px 0 0 0;columns:2 22em;column-gap:24px;}"
        + ".aw-report .rep-passed li{break-inside:avoid;color:#4a545e;}"
        + ".aw-report details.specimen-codes{display:inline;}"
        + ".aw-report details.specimen-codes[open]{display:block;}"
        + ".aw-report .rep-stats{overflow-x:auto;}"
        + ".aw-report .rep-errors li{margin:0 0 6px 0;}"
        + "@media (max-width:760px){"
        + ".aw-report{padding:8px 12px 20px;font-size:15px;}"
        + ".aw-report .rep-tiles{grid-template-columns:repeat(3,1fr);}"
        + ".aw-report .rep-n{font-size:19px;}"
        + ".aw-report table.rep-rows,.aw-report table.rep-rows tbody,.aw-report table.rep-rows tr,.aw-report table.rep-rows td{display:block;width:100%;}"
        + ".aw-report table.rep-rows thead{display:none;}"
        + ".aw-report table.rep-rows tr{border-bottom:1px solid #e6e9ee;padding:6px 0;}"
        + ".aw-report table.rep-rows td{border:0;padding:2px 4px;}"
        + ".aw-report table.rep-rows td::before{content:attr(data-label);display:inline-block;width:8.5em;vertical-align:top;color:#66707a;font-size:11px;font-weight:650;"
        + "text-transform:uppercase;letter-spacing:.03em;}"
        + ".aw-report td.rep-c-line{white-space:normal;}"
        + ".aw-report .rep-passed ul{columns:1;}"
        + "}";
}

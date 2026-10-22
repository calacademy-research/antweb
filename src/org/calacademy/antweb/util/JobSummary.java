package org.calacademy.antweb.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plain-text results for the background jobs of the nightly sets started by hand (Scheduler), and a safe
 * display of results stored before this existed.
 *
 * Until 2026-10-22 a set started from the Technical page stored, as its result, the whole page that
 * utilData.do?action=setN answered with (AntWeb header, search boxes, the line "set2 - {operation:dataCleanup}
 * ... in 9.73 mins.", the site footer), and jobStatus.do showed that page inside the job page (Brian's
 * screenshot of data job 186). Now the job keeps one sentence, e.g. "Nightly set 2 finished in 9.73 minutes:
 * data clean-up, place species lists from specimens, list names brought up to date, place counts, place list
 * fixes." Tested in test/org/calacademy/antweb/util/JobSummaryTest.java and tests/e2e/test_tool_pages_style.py.
 */
public final class JobSummary {

    private JobSummary() {}

    /** The operations of the nightly sets (UtilDataAction set1 to set5) in plain words. */
    static final Map<String, String> OPERATIONS = new LinkedHashMap<>();
    static {
        OPERATIONS.put("worldantsFetchAndReload", "AntCat names imported");
        OPERATIONS.put("taxonFinish", "taxon clean-up");
        OPERATIONS.put("dataCleanup", "data clean-up");
        OPERATIONS.put("generateGeolocaleTaxaFromSpecimens", "place species lists from specimens");
        OPERATIONS.put("updateTaxonSets", "list names brought up to date");
        OPERATIONS.put("geolocaleCountCrawl", "place counts");
        OPERATIONS.put("geolocaleTaxonFix", "place list fixes");
        OPERATIONS.put("projectCountCrawl", "project counts");
        OPERATIONS.put("populateMuseum", "museum lists and counts");
        OPERATIONS.put("populateBioregion", "bioregion lists and counts");
        OPERATIONS.put("crawlForType", "type specimens");
        OPERATIONS.put("imageCountCrawl", "image counts");
        OPERATIONS.put("placeImageCounts", "place image counts");
        OPERATIONS.put("deleteConflictedDefaultImages", "conflicting default images removed");
        OPERATIONS.put("genObjectMaps", "maps");
        OPERATIONS.put("cleanupSpeciesListProxyRecords", "species list clean-up");
        OPERATIONS.put("verifyProjTaxon", "project list check");
    }

    private static final Pattern SCRIPT_STYLE = Pattern.compile("(?is)<(script|style)\\b.*?</\\1\\s*>");
    private static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern TAG = Pattern.compile("(?s)<[^>]*>");
    private static final Pattern SPACE = Pattern.compile("\\s+");
    /** One set's line in the page utilData.do answered with: "set2 - {operation:a} {operation:b} in 9.73 mins." */
    private static final Pattern SET_LINE = Pattern.compile("\\bset([1-5]) -\\s*(.*?)\\s+in\\s+([0-9]+(?:\\.[0-9]+)?)\\s*mins?\\b");
    private static final Pattern OPERATION = Pattern.compile("\\{operation:([A-Za-z0-9_]+)[^}]*}");
    private static final Pattern SCHEDULER = Pattern.compile("^Scheduler \\w+:(\\d+) completed in ([0-9]+(?:\\.[0-9]+)?) mins?");
    /** Marks of a whole page (layout, header or footer) inside a stored result. */
    private static final Pattern FULL_PAGE = Pattern.compile("(?i)<!DOCTYPE|<html\\b|<body\\b|<head\\b|id=\"admin_layout\"|class=\"wide_left\"|site_header|<footer\\b");

    /** Text of an HTML fragment: no scripts, styles, comments or tags; common entities decoded; spaces collapsed. Not escaped. */
    static String text(String html) {
        if (html == null) return "";
        String t = SCRIPT_STYLE.matcher(html).replaceAll(" ");
        t = COMMENT.matcher(t).replaceAll(" ");
        t = TAG.matcher(t).replaceAll(" ");
        t = t.replace("&nbsp;", " ").replace("&#160;", " ").replace("&middot;", "\u00b7").replace("&lt;", "<").replace("&gt;", ">")
             .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
        return SPACE.matcher(t).replaceAll(" ").trim();
    }

    /** "{operation:dataCleanup} {operation:geolocaleCountCrawl}" -> "data clean-up, place counts". */
    static String operations(String setText) {
        List<String> parts = new ArrayList<>();
        Matcher m = OPERATION.matcher(setText);
        int last = 0;
        while (m.find()) {
            addOther(parts, setText.substring(last, m.start()));
            parts.add(words(m.group(1)));
            last = m.end();
        }
        addOther(parts, setText.substring(last));
        return String.join(", ", parts);
    }

    private static void addOther(List<String> parts, String other) {
        String o = other.replace("{", " ").replace("}", " ").replace("null", " ");
        o = SPACE.matcher(o).replaceAll(" ").trim();
        if (o.startsWith("-")) o = o.substring(1).trim();
        if (!o.isEmpty()) parts.add(o);
    }

    /** Plain words for an operation name; an unknown one is split at its capitals (fooBarCrawl -> foo bar crawl). */
    static String words(String operation) {
        String known = OPERATIONS.get(operation);
        if (known != null) return known;
        return operation.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase();
    }

    /**
     * The result of a nightly run started by hand, from what Scheduler.doAction returned ("Scheduler run:2 completed
     * in 9.80 mins.  output:" followed by the pages of utilData.do). Plain text, not escaped.
     */
    public static String scheduler(String doActionResult) {
        if (doActionResult == null) return "";
        int out = doActionResult.indexOf(" output:");
        String head = out < 0 ? doActionResult : doActionResult.substring(0, out);
        String output = out < 0 ? doActionResult : doActionResult.substring(out + " output:".length());
        String sets = sets(text(output));
        if (sets != null) return sets;
        Matcher h = SCHEDULER.matcher(text(head));
        if (h.find()) {
            String which = "0".equals(h.group(1)) ? "Nightly sets 1 to 5" : "Nightly set " + h.group(1);
            return which + " finished in " + h.group(2) + " minutes. Details in compute.log (Log Manager).";
        }
        return text(head);
    }

    /** The set lines of a text in plain sentences ("Nightly set 2 finished in 9.73 minutes: data clean-up, ..."); null if none. */
    static String sets(String text) {
        List<String> sets = new ArrayList<>();
        Matcher s = SET_LINE.matcher(text == null ? "" : text);
        while (s.find()) {
            String ops = operations(s.group(2));
            sets.add("Nightly set " + s.group(1) + " finished in " + s.group(3) + " minutes" + (ops.isEmpty() ? "." : ": " + ops + "."));
        }
        return sets.isEmpty() ? null : String.join(" ", sets);
    }

    /**
     * The result of a data job (UtilDataAction.runDataJob): a set's "{operation:...}" line in plain words, anything
     * else unchanged.
     */
    public static String dataJob(String message) {
        if (message == null || !message.contains("{operation:")) return message;
        String sets = sets(text(message));
        return sets == null ? message : Html.esc(sets);
    }

    /** True when a stored result holds a whole page (a layout, its header or footer), not a short result. */
    public static boolean isFullPage(String result) {
        return result != null && FULL_PAGE.matcher(result).find();
    }

    /**
     * A stored data-job result ready for the job page: AntWeb's own short HTML as it is; a result that holds a
     * whole page (jobs stored before 2026-10-22) as escaped plain text without the page's header and footer.
     */
    public static String display(String result) {
        if (result == null) return "";
        if (!isFullPage(result)) return dataJob(result);
        String plain = result.startsWith("Scheduler ") ? scheduler(result) : messageText(result);
        if (plain.isEmpty()) plain = "Finished. Details in compute.log (Log Manager).";
        return Html.esc(plain);
    }

    /** The message of a message page (common/message-body.jsp: div.wide_left), without the site around it. */
    static String messageText(String page) {
        int i = page.indexOf("class=\"wide_left\"");
        if (i < 0) return "";
        int start = page.indexOf('>', i);
        int end = page.indexOf("</div>", start < 0 ? i : start);
        if (start < 0 || end < 0) return "";
        return text(page.substring(start + 1, end));
    }
}

package org.calacademy.antweb.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The specimens of one report line: "N specimens", a collapsed list (details/summary) of their codes,
 * each linked to its specimen page. One markup for the Name updates page (nameUpdates.do) and every
 * section of the specimen upload report (MessageMgr), so the two look the same (Brian Fisher, 2026-10-21:
 * "can you add that feature to every specimen report").
 *
 * The list is capped: the first MAX_CODES codes, then "... and N more". A collector (add) keeps only those
 * codes and counts the rest, so a line of a 265,000-record upload holds at most MAX_CODES strings.
 */
public final class SpecimenCodes {

    /** Codes listed per line; the rest are counted ("and N more"). */
    public static final int MAX_CODES = 200;

    /** The list's text when no code is listed (the report's limit was reached). */
    public static final String NOT_LISTED = "Codes not listed: this report already lists the most it shows. Search by name to see them.";

    private int count = 0;
    private final List<String> codes = new ArrayList<>();
    private final Set<String> listed = new HashSet<>();
    private String last;

    /**
     * Adds a specimen code. A code already listed, or the same code twice in a row (one line of the file
     * reported twice), is not counted again; null and blank codes are ignored.
     */
    public void add(String code) {
        if (code == null || code.isBlank()) return;
        if (code.equals(last) || listed.contains(code)) return;
        last = code;
        ++count;
        if (codes.size() < MAX_CODES) {
            codes.add(code);
            listed.add(code);
        }
    }

    public int count() {
        return count;
    }

    public List<String> codes() {
        return Collections.unmodifiableList(codes);
    }

    /** This line's markup (see html(int, List, String, boolean)). */
    public String html(String domainApp, boolean link) {
        return html(count, codes, domainApp, link);
    }

    /**
     * " <details class="specimen-codes"><summary>N specimens</summary>code, code, ... and M more</details>",
     * or "" when count is 0. codes: the first codes (at most MAX_CODES are shown); count: all specimens of the
     * line. link: each code links to specimen.do (false for lines that were not uploaded: plain codes).
     * Codes are escaped; an empty list (the report's limit reached) says NOT_LISTED.
     */
    public static String html(int count, List<String> codes, String domainApp, boolean link) {
        if (count <= 0) return "";
        StringBuilder b = new StringBuilder(64 + Math.min(codes.size(), MAX_CODES) * 80);
        b.append(" <details class=\"specimen-codes\" style=\"display:inline\"><summary>")
         .append(String.format("%,d", count)).append(count == 1 ? " specimen" : " specimens").append("</summary>");
        int shown = 0;
        for (String code : codes) {
            if (shown == MAX_CODES) break;
            if (shown++ > 0) b.append(", ");
            if (link) {
                b.append("<a href=\"").append(domainApp == null ? "" : domainApp).append("/specimen.do?name=")
                 .append(Html.url(code)).append("\">").append(Html.esc(code)).append("</a>");
            } else {
                b.append(Html.esc(code));
            }
        }
        if (shown == 0) {
            b.append(NOT_LISTED);
        } else if (count > shown) {
            b.append(" ... and ").append(String.format("%,d", count - shown)).append(" more");
        }
        return b.append("</details>").toString();
    }
}

package org.calacademy.antweb.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.text.StringEscapeUtils;

/**
 * A small word/line diff for the page-text History page (fieldHistory.do).
 *
 * readable(a, b): both HTML texts are turned into plain text (paragraphs, line breaks and list
 * items become new lines; images and videos become [image: ...] / [video: ...]); the result marks
 * words only in a with del and words only in b with ins. source(a, b) does the same on the HTML
 * source, with each tag as one token.
 *
 * Method: common start and end are skipped, then a longest-common-subsequence table on the
 * remaining words. If that table would be too large (very long, very different texts), the
 * comparison falls back to whole lines, and then to "all of a removed, all of b added". So the
 * cost is bounded (at most MAX_CELLS table cells, 2 bytes each).
 *
 * The output is HTML with all text escaped; new lines are written as br (readable) or kept for a
 * pre block (source).
 */
public final class TextDiff {

    private TextDiff() {}

    static final int MAX_CELLS = 4_000_000;

    public enum Kind { SAME, DEL, INS }

    public record Op(Kind kind, String text) { }

    private static final Pattern WORDS = Pattern.compile("\\n|[^\\S\\n]+|[^\\s]+");
    private static final Pattern SOURCE_TOKENS = Pattern.compile("<[^>]*>|\\n|[^\\S\\n]+|[^\\s<]+|<");

    private static final Pattern BR = Pattern.compile("(?i)<br\\b[^>]*>");
    private static final Pattern BLOCK = Pattern.compile("(?i)</?(p|div|li|ul|ol|h[1-6]|tr|table|thead|tbody|blockquote|pre|dl|dt|dd|center|hr)\\b[^>]*>");
    private static final Pattern CELL = Pattern.compile("(?i)</t[dh]\\s*>");
    private static final Pattern IMG = Pattern.compile("(?i)<img\\b[^>]*?\\bsrc\\s*=\\s*[\"']?([^\"'\\s>]+)[^>]*>");
    private static final Pattern IFRAME = Pattern.compile("(?i)<iframe\\b[^>]*?\\bsrc\\s*=\\s*[\"']?([^\"'\\s>]+)[^>]*>");
    private static final Pattern TAG = Pattern.compile("<[^>]*>");

    /** The text a reader sees, one paragraph or line per line. */
    public static String htmlToText(String html) {
        if (html == null) return "";
        String s = html.replace("\r\n", "\n").replace('\r', '\n').replace('\n', ' ');
        s = IMG.matcher(s).replaceAll(m -> Matcher.quoteReplacement(" [image: " + m.group(1) + "] "));
        s = IFRAME.matcher(s).replaceAll(m -> Matcher.quoteReplacement(" [video: " + m.group(1) + "] "));
        s = BR.matcher(s).replaceAll("\n");
        s = BLOCK.matcher(s).replaceAll("\n");
        s = CELL.matcher(s).replaceAll(" | ");
        s = TAG.matcher(s).replaceAll("");
        s = StringEscapeUtils.unescapeHtml4(s).replace('\u00a0', ' ');
        StringBuilder out = new StringBuilder();
        int blank = 0;
        for (String line : s.split("\n", -1)) {
            String t = line.replaceAll("[ \\t\\x0B\\f]+", " ").trim();
            if (t.isEmpty()) {
                if (out.length() > 0) blank++;
                continue;
            }
            if (out.length() > 0) out.append(blank > 0 ? "\n\n" : "\n");
            out.append(t);
            blank = 0;
        }
        return out.toString();
    }

    /** Plain-text comparison of two HTML texts, as HTML (br for new lines). */
    public static String readable(String fromHtml, String toHtml) {
        return render(diff(tokens(htmlToText(fromHtml), WORDS), tokens(htmlToText(toHtml), WORDS)), true);
    }

    /** Work allowed for the comparisons on one page (table cells). */
    public static final long PAGE_BUDGET = 20_000_000L;

    /**
     * As readable(a, b), but charged to budget[0] (table cells, at most MAX_CELLS per comparison).
     * Returns null, without comparing, when the budget is used up: the page then offers the
     * comparison of that version on its own.
     */
    public static String readable(String fromHtml, String toHtml, long[] budget) {
        List<String> a = tokens(htmlToText(fromHtml), WORDS);
        List<String> b = tokens(htmlToText(toHtml), WORDS);
        long cost = Math.min((long) (a.size() + 1) * (b.size() + 1), MAX_CELLS);
        if (budget != null) {
            if (budget[0] < cost) return null;
            budget[0] -= cost;
        }
        return render(diff(a, b), true);
    }

    /** Comparison of the HTML source, as HTML for a pre block. */
    public static String source(String fromHtml, String toHtml) {
        String a = fromHtml == null ? "" : fromHtml.replace("\r\n", "\n").replace('\r', '\n');
        String b = toHtml == null ? "" : toHtml.replace("\r\n", "\n").replace('\r', '\n');
        return render(diff(tokens(a, SOURCE_TOKENS), tokens(b, SOURCE_TOKENS)), false);
    }

    /** True if the readable texts are the same. */
    public static boolean sameText(String fromHtml, String toHtml) {
        return htmlToText(fromHtml).equals(htmlToText(toHtml));
    }

    static List<String> tokens(String s, Pattern p) {
        List<String> list = new ArrayList<>();
        Matcher m = p.matcher(s);
        while (m.find()) list.add(m.group());
        return list;
    }

    /** The edit script from a to b. */
    public static List<Op> diff(List<String> a, List<String> b) {
        List<Op> ops = new ArrayList<>();
        int n = a.size(), m = b.size();
        int pre = 0;
        while (pre < n && pre < m && a.get(pre).equals(b.get(pre))) pre++;
        int suf = 0;
        while (suf < n - pre && suf < m - pre && a.get(n - 1 - suf).equals(b.get(m - 1 - suf))) suf++;
        for (int i = 0; i < pre; i++) ops.add(new Op(Kind.SAME, a.get(i)));
        List<String> ma = a.subList(pre, n - suf);
        List<String> mb = b.subList(pre, m - suf);
        if ((long) (ma.size() + 1) * (mb.size() + 1) <= MAX_CELLS) {
            lcs(ma, mb, ops);
        } else {
            List<String> la = lines(ma), lb = lines(mb);
            if ((long) (la.size() + 1) * (lb.size() + 1) <= MAX_CELLS) {
                lcs(la, lb, ops);
            } else {
                for (String t : ma) ops.add(new Op(Kind.DEL, t));
                for (String t : mb) ops.add(new Op(Kind.INS, t));
            }
        }
        for (int i = n - suf; i < n; i++) ops.add(new Op(Kind.SAME, a.get(i)));
        return ops;
    }

    /** Tokens joined into lines (each line keeps its new line). */
    private static List<String> lines(List<String> tokens) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String t : tokens) {
            cur.append(t);
            if ("\n".equals(t)) { lines.add(cur.toString()); cur.setLength(0); }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    private static void lcs(List<String> a, List<String> b, List<Op> ops) {
        int n = a.size(), m = b.size();
        int w = m + 1;
        // len[i*w+j] = length of the longest common subsequence of a[i..] and b[j..]. At most
        // min(n, m) <= sqrt(MAX_CELLS) = 2000, so a short is enough.
        short[] len = new short[(n + 1) * w];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (a.get(i).equals(b.get(j))) len[i * w + j] = (short) (len[(i + 1) * w + j + 1] + 1);
                else len[i * w + j] = (short) Math.max(len[(i + 1) * w + j], len[i * w + j + 1]);
            }
        }
        int i = 0, j = 0;
        while (i < n && j < m) {
            if (a.get(i).equals(b.get(j))) { ops.add(new Op(Kind.SAME, a.get(i))); i++; j++; }
            else if (len[(i + 1) * w + j] >= len[i * w + j + 1]) { ops.add(new Op(Kind.DEL, a.get(i))); i++; }
            else { ops.add(new Op(Kind.INS, b.get(j))); j++; }
        }
        while (i < n) ops.add(new Op(Kind.DEL, a.get(i++)));
        while (j < m) ops.add(new Op(Kind.INS, b.get(j++)));
    }

    /**
     * HTML for an edit script. A run of whitespace between two changes of the same kind is
     * included in the change, so "old words" reads as one change, not two.
     */
    static String render(List<Op> ops, boolean brLines) {
        StringBuilder out = new StringBuilder();
        Kind open = null;
        for (int k = 0; k < ops.size(); k++) {
            Op op = ops.get(k);
            Kind kind = op.kind();
            if (kind == Kind.SAME && open != null && op.text().isBlank() && !op.text().contains("\n")
                && k + 1 < ops.size() && ops.get(k + 1).kind() == open) {
                kind = open;   // glue
            }
            if (kind != open) {
                if (open != null) out.append(open == Kind.DEL ? "</del>" : "</ins>");
                if (kind != Kind.SAME) out.append(kind == Kind.DEL ? "<del class=\"fh-del\">" : "<ins class=\"fh-ins\">");
                open = kind == Kind.SAME ? null : kind;
            }
            String esc = Html.esc(op.text());
            if (brLines) esc = esc.replace("\n", kind == Kind.SAME ? "<br>" : "&para;<br>");
            out.append(esc);
        }
        if (open != null) out.append(open == Kind.DEL ? "</del>" : "</ins>");
        return out.toString();
    }

    /** True if the script has any change. */
    public static boolean changed(List<Op> ops) {
        for (Op op : ops) if (op.kind() != Kind.SAME) return true;
        return false;
    }
}

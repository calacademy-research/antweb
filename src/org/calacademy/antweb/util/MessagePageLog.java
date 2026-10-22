package org.calacademy.antweb.util;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The record of message pages in the data log messages.txt (log/ of the project folder on the
 * server: /root/antweb/logbak, in the container /usr/local/antweb/log).
 *
 * Why (2026-10-22): every message page wrote its whole message to messages.txt. Admin tools that
 * return a whole HTML report as the message (named queries such as imageData, tens of thousands of
 * table rows) wrote lines of ~13 MB: staging's messages.txt reached 11 GB in 11 days. On production
 * the file held 5.4 million lines, most of them the same "You must log in" message shown to bots.
 *
 * Now each message page writes a short record: date, the first SHOWN characters of the message as
 * plain text (tags removed, white space collapsed, with "[cut, N characters]" when longer), and
 * the page address without the form token (_csrf) or password and token values. The visitor still
 * sees the whole message: only the log is short.
 *
 * Repeats are counted, not written: each distinct message is written at most PER_DAY times a day;
 * when the day changes, one summary line per repeated message gives the count of the day before.
 * Messages are told apart by their first KEY_LENGTH characters. At most MAX_KEYS distinct messages
 * are tracked a day (worst case about 3 x 20,000 lines); messages beyond that are only counted.
 */
public final class MessagePageLog {

    public static final String FILE = "messages.txt";
    public static final int SHOWN = 300;
    public static final int TARGET_MAX = 500;
    public static final int PER_DAY = 3;
    public static final int MAX_KEYS = 20000;
    static final int KEY_LENGTH = 120;

    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern SPACE = Pattern.compile("\\s+");
    // Values never kept in a log: the form token, passwords, reset and other tokens.
    private static final Pattern SECRET_PARAM = Pattern.compile(
        "(?i)([?&](?:_csrf|password|passwd|pwd|newpassword|token|resettoken|key|secret)=)[^&#\\s]*");

    private MessagePageLog() {}

    /** Write the short record of the message page (or count it). Never throws. */
    public static void log(HttpServletRequest request, String message) {
        if (message == null) return;
        try {
            String target = request == null ? null : HttpUtil.getTarget(request);
            String line = DateUtil.getFormatDateTimeStr(new java.util.Date()) + " - " + record(message, target);
            String key = shortMessage(message);
            if (key.length() > KEY_LENGTH) key = key.substring(0, KEY_LENGTH);
            for (String l : COUNTER.add(LocalDate.now().toString(), key, line)) {
                LogMgr.appendDataLog(FILE, l);
            }
        } catch (RuntimeException e) {
            // A log line must never break the page.
        }
    }

    /** The message part of the record and the page address: "short message target". */
    public static String record(String message, String target) {
        String t = cleanTarget(target);
        return shortMessage(message) + (t.isEmpty() ? "" : " " + t);
    }

    /** The message as plain text on one line, at most SHOWN characters, with the full length if cut. */
    public static String shortMessage(String message) {
        if (message == null) return "null";
        String text = TAG.matcher(message).replaceAll(" ");
        text = SPACE.matcher(text).replaceAll(" ").trim();
        text = SECRET_PARAM.matcher(text).replaceAll("$1...");
        if (text.length() <= SHOWN) return text;
        return text.substring(0, SHOWN) + "... [cut, " + String.format("%,d", message.length()) + " characters]";
    }

    /** The page address without secret values, on one line, at most TARGET_MAX characters. */
    public static String cleanTarget(String target) {
        if (target == null) return "";
        String t = Csrf.stripParam(SPACE.matcher(target).replaceAll(""));
        t = SECRET_PARAM.matcher(t).replaceAll("$1...");
        if (t.length() > TARGET_MAX) t = t.substring(0, TARGET_MAX) + "... [cut, " + String.format("%,d", target.length()) + " characters]";
        return t;
    }

    static final Counter COUNTER = new Counter();

    /** Counts each distinct message per day; says which lines to write. */
    public static final class Counter {
        private String day;
        private final Map<String, int[]> counts = new LinkedHashMap<>();
        private int untracked;

        /** The lines to write for this message: the day-before summary (once), then the line if still due. */
        public synchronized List<String> add(String today, String key, String line) {
            List<String> out = new ArrayList<>();
            if (!today.equals(day)) {
                if (day != null) out.addAll(summary());
                day = today;
                counts.clear();
                untracked = 0;
            }
            int[] n = counts.get(key);
            if (n == null) {
                if (counts.size() >= MAX_KEYS) {
                    untracked++;
                    return out;
                }
                n = new int[1];
                counts.put(key, n);
            }
            n[0]++;
            if (n[0] <= PER_DAY) out.add(line);
            // A running count at 100, 1,000, 10,000 ... so a restart (counts are in memory) never
            // loses the record of a message shown very often.
            else if (isPowerOfTen(n[0]) && n[0] >= 100) {
                out.add(today + " - count so far: shown " + String.format("%,d", n[0]) + " times today: " + key);
            }
            return out;
        }

        static boolean isPowerOfTen(int n) {
            while (n >= 10 && n % 10 == 0) n /= 10;
            return n == 1;
        }

        /** Summary lines for the current day (messages shown more than PER_DAY times, and the untracked ones). */
        public synchronized List<String> summary() {
            List<String> out = new ArrayList<>();
            for (Map.Entry<String, int[]> e : counts.entrySet()) {
                int n = e.getValue()[0];
                if (n > PER_DAY) {
                    out.add(day + " - summary: shown " + String.format("%,d", n) + " times (first " + PER_DAY
                        + " logged): " + e.getKey());
                }
            }
            if (untracked > 0) {
                out.add(day + " - summary: " + String.format("%,d", untracked) + " more message pages not logged (over "
                    + MAX_KEYS + " different messages that day)");
            }
            return out;
        }
    }
}

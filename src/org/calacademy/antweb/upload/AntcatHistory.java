package org.calacademy.antweb.upload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads AntCat's taxonomic history of one taxon (2026-10): the "taxonomic history html" column of the
 * AntCat export, stored by the nightly AntCat import in description_edit (title 'taxonomichistory').
 *
 * The history is a list of entries, one per line (a div in the HTML), each a heading and the
 * references that support it, for example
 *
 *   Junior synonym of Crematogaster laeviuscula: Morgan &amp; Mackay, 2017 PDF: 197
 *   Status as species: Hunt &amp; Snelling, 1975 PDF: 21; ...; Ward &amp; Blaimer, 2022 PDF: 917.
 *   Combination in Holcoponera Mayr, 1887: Emery, 1911e PDF: 40; Camacho et al., 2022 ... PDF: 11.
 *
 * A reference is "Author(s), year[letter]" (what follows, the DOI, "PDF" and the page, is dropped).
 * The nightly name update (SpecimenNameUpdate) uses it to say why a specimen moved and on whose
 * authority. Plain text with the entries run together (tags already stripped) is read too. Pure:
 * tested in AntcatHistoryTest with real histories from the export.
 */
public final class AntcatHistory {

    public static final String JUNIOR_SYNONYM_OF = "Junior synonym of";
    public static final String COMBINATION_IN = "Combination in";
    public static final String STATUS_AS_SPECIES = "Status as species";

    /** Headings that start an entry: used to split text whose entries are not on lines of their own. */
    private static final String[] HEADINGS = {
        "Junior synonym of", "Senior synonym of", "Combination in", "Status as species", "Status as subspecies",
        "Subspecies of", "Revived from synonymy", "Lectotype designation", "Neotype designation", "As unavailable",
        "Unnecessary replacement name for", "Replacement name", "Unidentifiable taxon", "Unidentifiable to genus",
        "Material of the unavailable", "Material referred to", "Incertae sedis in", "Included under", "Nomen nudum"
    };
    private static final Pattern SPLIT;
    static {
        StringBuilder b = new StringBuilder("(?<=[.\\d)\\]])\\s+(?=(?:");
        for (int i = 0; i < HEADINGS.length; i++) {
            if (i > 0) b.append('|');
            b.append(Pattern.quote(HEADINGS[i]));
        }
        SPLIT = Pattern.compile(b.append(")\\b)").toString());
    }
    private static final Pattern BLOCK_TAG = Pattern.compile("<\\s*/?\\s*(?:div|p|br|li|ul|ol|tr|h\\d)\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern ENTRY = Pattern.compile("^([A-Z][^:]*?)\\s*:\\s*(.*)$");
    private static final Pattern REF = Pattern.compile("^(\\D+?,\\s*\\d{4}[a-z]?)(?![0-9a-z])");

    /** One entry: heading (e.g. "Junior synonym of"), subject (e.g. "Crematogaster laeviuscula"; may be empty), references. */
    public static final class Entry {
        public final String heading;
        public final String subject;
        public final List<String> refs;
        Entry(String heading, String subject, List<String> refs) {
            this.heading = heading;
            this.subject = subject;
            this.refs = Collections.unmodifiableList(refs);
        }
        public String toString() { return heading + (subject.isEmpty() ? "" : " " + subject) + ": " + String.join("; ", refs); }
    }

    private final List<Entry> entries;

    private AntcatHistory(List<Entry> entries) {
        this.entries = Collections.unmodifiableList(entries);
    }

    public List<Entry> getEntries() {
        return entries;
    }

    /** Reads the stored history (HTML or plain text). Null or empty gives an empty history. */
    public static AntcatHistory parse(String history) {
        List<Entry> entries = new ArrayList<>();
        if (history == null || history.isBlank()) return new AntcatHistory(entries);
        String text = history;
        int start = text.indexOf("Taxonomic history");
        if (start >= 0) text = text.substring(start + "Taxonomic history".length());
        text = BLOCK_TAG.matcher(text).replaceAll("\n");
        text = TAG.matcher(text).replaceAll("");
        text = unescape(text);
        for (String line : text.split("\n")) {
            line = normalize(line);
            if (line.isEmpty()) continue;
            for (String part : SPLIT.split(line)) {
                Entry e = entry(part.trim());
                if (e != null) entries.add(e);
            }
        }
        return new AntcatHistory(entries);
    }

    private static Entry entry(String text) {
        Matcher m = ENTRY.matcher(text);
        if (!m.matches()) return null;
        String head = m.group(1).trim();
        String heading = null;
        for (String h : HEADINGS) {
            if (head.equals(h) || head.startsWith(h + " ")) {
                heading = h;
                break;
            }
        }
        if (heading == null) return null;
        String subject = head.substring(heading.length()).trim();
        List<String> refs = new ArrayList<>();
        for (String r : m.group(2).split(";")) {
            Matcher rm = REF.matcher(r.trim());
            if (rm.find()) refs.add(rm.group(1).replaceAll("\\s+", " ").trim());
        }
        return new Entry(heading, subject, refs);
    }

    /**
     * The reference of the latest "Junior synonym of <seniorName>" entry: its first reference, the
     * publication that made the synonymy (those after it confirm it). Null when there is none.
     */
    public String juniorSynonymRef(String seniorName) {
        Entry latest = null;
        String want = nameKey(seniorName);
        for (Entry e : entries) {
            if (JUNIOR_SYNONYM_OF.equals(e.heading) && nameKey(e.subject).equals(want)) latest = e;
        }
        return latest == null || latest.refs.isEmpty() ? null : latest.refs.get(0);
    }

    /**
     * The reference of the latest "Combination in <genus>" entry (any subgenus): its last reference,
     * the publication that put the species in that genus most recently (e.g. Camacho et al., 2022 for
     * Holcoponera, restored as a genus that year). Null when there is none.
     */
    public String combinationRef(String genus) {
        if (genus == null) return null;
        Entry latest = null;
        String want = genus.trim().toLowerCase(Locale.ROOT);
        for (Entry e : entries) {
            if (!COMBINATION_IN.equals(e.heading)) continue;
            String first = e.subject.split("[\\s(]", 2)[0].toLowerCase(Locale.ROOT);
            if (first.equals(want)) latest = e;
        }
        return latest == null || latest.refs.isEmpty() ? null : latest.refs.get(latest.refs.size() - 1);
    }

    /** The latest reference of "Status as species" (or "Revived from synonymy" when AntCat uses it). Null when there is none. */
    public String statusAsSpeciesRef() {
        String ref = null;
        for (Entry e : entries) {
            if ((STATUS_AS_SPECIES.equals(e.heading) || "Revived from synonymy".equals(e.heading)) && !e.refs.isEmpty()) {
                ref = e.refs.get(e.refs.size() - 1);
            }
        }
        return ref;
    }

    /** "Morgan & Mackay, 2017" -> "Morgan & Mackay 2017". */
    public static String shortRef(String ref) {
        if (ref == null) return null;
        return ref.replaceAll(",\\s*(\\d{4}[a-z]?)$", " $1");
    }

    /** Compares names without subgenus, case or extra spaces. */
    static String nameKey(String name) {
        if (name == null) return "";
        return name.replaceAll("\\([^)]*\\)", " ").replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static String normalize(String s) {
        s = s.replace(' ', ' ').replaceAll("\\s+", " ").trim();
        return s.replaceAll(" ([:,.;])", "$1");
    }

    /** The character references AntCat's HTML uses (named, decimal, hex). */
    static String unescape(String s) {
        if (s.indexOf('&') < 0) return s;
        Matcher m = Pattern.compile("&(#\\d+|#[xX][0-9a-fA-F]+|[a-zA-Z]+);").matcher(s);
        StringBuilder b = new StringBuilder();
        while (m.find()) {
            String ent = m.group(1);
            String rep;
            try {
                if (ent.startsWith("#x") || ent.startsWith("#X")) rep = new String(Character.toChars(Integer.parseInt(ent.substring(2), 16)));
                else if (ent.startsWith("#")) rep = new String(Character.toChars(Integer.parseInt(ent.substring(1))));
                else {
                    switch (ent) {
                        case "amp": rep = "&"; break;
                        case "lt": rep = "<"; break;
                        case "gt": rep = ">"; break;
                        case "quot": rep = "\""; break;
                        case "apos": rep = "'"; break;
                        case "nbsp": rep = " "; break;
                        default: rep = m.group(0);
                    }
                }
            } catch (IllegalArgumentException e) {
                rep = m.group(0);
            }
            m.appendReplacement(b, Matcher.quoteReplacement(rep));
        }
        m.appendTail(b);
        return b.toString();
    }
}

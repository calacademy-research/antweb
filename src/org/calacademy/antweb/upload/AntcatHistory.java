package org.calacademy.antweb.upload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
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
    public static final String SENIOR_SYNONYM_OF = "Senior synonym of";
    public static final String SUBSPECIES_OF = "Subspecies of";
    public static final String REVIVED = "Revived from synonymy";
    public static final String RAISED = "Raised to species";
    public static final String UNNECESSARY_REPLACEMENT = "Unnecessary replacement name for";

    /** Headings that start an entry: used to split text whose entries are not on lines of their own. */
    private static final String[] HEADINGS = {
        "Junior synonym of", "Senior synonym of", "Combination in", "Status as species", "Status as subspecies",
        "Subspecies of", "Revived from synonymy", "Raised to species", "Lectotype designation", "Neotype designation", "As unavailable",
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
    /** "Unnecessary (second) replacement name for", "Unnecessary third replacement name for" -> UNNECESSARY_REPLACEMENT. */
    private static final Pattern UNNECESSARY_VARIANTS = Pattern.compile("^Unnecessary (?:\\(?[a-z]+\\)? )?replacement name for\\b");
    private static final Pattern NO_REFS = Pattern.compile("^(Unnecessary replacement name for|Senior synonym of|Junior synonym of) ([^:]+?)\\.?$");
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
        text = UNNECESSARY_VARIANTS.matcher(text).replaceFirst(UNNECESSARY_REPLACEMENT);
        Matcher m = ENTRY.matcher(text);
        if (!m.matches()) {
            // Lines without references: "Unnecessary replacement name for Lioponera reticulata (Clark, 1926).",
            // "Senior synonym of Lioponera lata (Brown, 1975) (unnecessary replacement name)."
            Matcher n = NO_REFS.matcher(text);
            if (!n.matches()) return null;
            return new Entry(n.group(1), n.group(2).replace("\u2020", "").trim(), new ArrayList<>());
        }
        String head = m.group(1).trim();
        String heading = null;
        for (String h : HEADINGS) {
            if (head.equals(h) || head.startsWith(h + " ")) {
                heading = h;
                break;
            }
        }
        if (heading == null) return null;
        String subject = head.substring(heading.length()).replace("\u2020", "").trim();
        List<String> refs = new ArrayList<>();
        for (String r : m.group(2).split(";")) {
            Matcher rm = REF.matcher(r.trim());
            if (rm.find()) refs.add(rm.group(1).replaceAll("\\s+", " ").trim());
        }
        return new Entry(heading, subject, refs);
    }

    // ------------------------------------------------------------ the acts (Brian Fisher, 2026-10-20)
    //
    // Each reference of an entry is an act dated by its year (and year letter: 2025a before 2025b).
    // AntCat's entries are not always in date order (21 of 565 rows checked were not), nor are the
    // references inside one entry, so the acts are sorted by date and the order of the text is used
    // only to break exact ties. The act cited for a current state is the earliest act of that kind
    // after the most recent contrary act (actOf).

    /** One reference of an entry: "Author(s), year[letter]", dated. */
    public static final class Ref {
        public final String text;
        final int year;
        final String letter;
        final int order;
        Ref(String text, int order) {
            this.text = text;
            this.order = order;
            Matcher m = YEAR.matcher(text);
            boolean dated = m.find();
            this.year = dated ? Integer.parseInt(m.group(1)) : 0;
            this.letter = dated ? m.group(2) : "";
        }
        /** Compares the dates only (year, then letter: 2025 before 2025a before 2025b). */
        int compareDate(Ref o) {
            if (year != o.year) return Integer.compare(year, o.year);
            return letter.compareTo(o.letter);
        }
        public String toString() { return text; }
    }
    private static final Pattern YEAR = Pattern.compile("(\\d{4})([a-z]?)$");
    private static final Comparator<Ref> BY_DATE = (a, b) -> {
        int c = a.compareDate(b);
        return c != 0 ? c : Integer.compare(a.order, b.order);
    };

    /** The references of the entries that p accepts, sorted by date. */
    public List<Ref> refs(Predicate<Entry> p) {
        List<Ref> out = new ArrayList<>();
        int order = 0;
        for (Entry e : entries) {
            for (String r : e.refs) {
                order++;
                if (p.test(e)) out.add(new Ref(r, order));
            }
        }
        out.sort(BY_DATE);
        return out;
    }

    /**
     * The act that made the current state: the earliest of current dated after the most recent of
     * contrary that comes before the latest of current. Without such a contrary act, the earliest of
     * current. Null when current is empty, or when a contrary act is dated after the latest of current
     * (then the act that made the current state is not in the history). Example (Dorymyrmex medeis, revived from synonymy with
     * D. smithi): synonymy Snelling 1995a; status as species Deyrup 2003, Deyrup 2017 => Deyrup 2003.
     */
    public static String actOf(List<Ref> current, List<Ref> contrary) {
        return actOf(current, contrary, true);
    }

    /** stillCurrent false: a past state (the synonymy of a name now valid again), whatever came after it. */
    static String actOf(List<Ref> current, List<Ref> contrary, boolean stillCurrent) {
        if (current.isEmpty()) return null;
        Ref latest = current.get(current.size() - 1);
        // The only references for the current state predate a contrary one (Lioponera incontenta: synonymy
        // Clark 1924b, listed as a species 1985-2009): AntCat's history does not record the act. None is cited.
        if (stillCurrent && !contrary.isEmpty() && contrary.get(contrary.size() - 1).compareDate(latest) > 0) return null;
        Ref lastContrary = null;
        for (Ref c : contrary) if (c.compareDate(latest) < 0) lastContrary = c;   // sorted: the last one wins
        for (Ref r : current) {
            if (lastContrary == null || r.compareDate(lastContrary) > 0) return r.text;
        }
        return latest.text;
    }

    /** Whether the history says the name is an unnecessary replacement name for an accepted senior. */
    public boolean isUnnecessaryReplacementFor(Predicate<String> senior) {
        for (Entry e : entries) if (UNNECESSARY_REPLACEMENT.equals(e.heading) && senior.test(e.subject)) return true;
        return false;
    }

    private static final Pattern AUTHOR_YEAR = Pattern.compile("\\(([^()]*?, \\d{4}[a-z]?)\\)");

    /**
     * In a senior's history, "Senior synonym of <junior> (Author, year) (unnecessary replacement name)":
     * the author and year of the replacement name ("Brown, 1975"), "" when the entry has none, or null
     * when there is no such entry for an accepted junior.
     */
    public String unnecessaryReplacementAuthor(Predicate<String> junior) {
        for (Entry e : entries) {
            if (!SENIOR_SYNONYM_OF.equals(e.heading)) continue;
            String lower = e.subject.toLowerCase(Locale.ROOT);
            if (!lower.contains("unnecessary") || !lower.contains("replacement name") || !junior.test(e.subject)) continue;
            Matcher m = AUTHOR_YEAR.matcher(e.subject);
            return m.find() ? m.group(1).replaceAll("\\s+", " ").trim() : "";
        }
        return null;
    }

    /** The earliest reference of the entries that p accepts, or null. */
    public String earliest(Predicate<Entry> p) {
        List<Ref> r = refs(p);
        return r.isEmpty() ? null : r.get(0).text;
    }

    static boolean isRevival(Entry e) {
        return STATUS_AS_SPECIES.equals(e.heading) || REVIVED.equals(e.heading) || RAISED.equals(e.heading);
    }

    /**
     * The synonymy with a senior name: the first "Junior synonym of" an accepted senior after the last
     * act that treated the taxon as valid (status as species, or subspecies of a species) before the
     * latest such synonymy.
     */
    public String juniorSynonymRef(Predicate<String> senior) {
        return juniorSynonymRef(senior, true);
    }

    /** stillCurrent false: the synonymy of a name that is now valid again (later revivals do not void it). */
    public String juniorSynonymRef(Predicate<String> senior, boolean stillCurrent) {
        return actOf(refs(e -> JUNIOR_SYNONYM_OF.equals(e.heading) && senior.test(e.subject)),
            refs(e -> isRevival(e) || SUBSPECIES_OF.equals(e.heading)), stillCurrent);
    }

    /** juniorSynonymRef for one senior name (subgenus, case and spaces do not matter). */
    public String juniorSynonymRef(String seniorName) {
        String want = nameKey(seniorName);
        return juniorSynonymRef(s -> nameKey(s).equals(want));
    }

    /** The earliest reference of a "Senior synonym of" an accepted junior name (the senior's history). */
    public String seniorSynonymRef(Predicate<String> junior) {
        return earliest(e -> SENIOR_SYNONYM_OF.equals(e.heading) && junior.test(e.subject));
    }

    /**
     * The revival from synonymy with an accepted senior: the first status as species after the last
     * synonymy with it. Without such a synonymy, the first status as species after the last synonymy.
     */
    public String revivalRef(Predicate<String> formerSenior) {
        List<Ref> syn = refs(e -> JUNIOR_SYNONYM_OF.equals(e.heading) && formerSenior.test(e.subject));
        if (syn.isEmpty()) syn = refs(e -> JUNIOR_SYNONYM_OF.equals(e.heading));
        return actOf(refs(AntcatHistory::isRevival), syn);
    }

    /** Raised to species: the first status as species after the last "Subspecies of". */
    public String raisedRef() {
        return actOf(refs(AntcatHistory::isRevival), refs(e -> SUBSPECIES_OF.equals(e.heading)));
    }

    /** Placed as subspecies: the first "Subspecies of" an accepted species after the last status as species. */
    public String subspeciesRef(Predicate<String> species) {
        return actOf(refs(e -> SUBSPECIES_OF.equals(e.heading) && species.test(e.subject)), refs(AntcatHistory::isRevival));
    }

    /**
     * The combination in a genus (any subgenus): the first "Combination in <genus>" after the last
     * combination in another genus (Holcoponera striatula: Camacho et al. 2022, not Emery 1911e).
     */
    public String combinationRef(String genus) {
        if (genus == null) return null;
        String want = genus.trim().toLowerCase(Locale.ROOT);
        return actOf(refs(e -> COMBINATION_IN.equals(e.heading) && genusOf(e.subject).equals(want)),
            refs(e -> COMBINATION_IN.equals(e.heading) && !genusOf(e.subject).equals(want)));
    }

    static String genusOf(String subject) {
        return subject.replace("\u2020", "").trim().split("[\\s(]", 2)[0].toLowerCase(Locale.ROOT);
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

package org.calacademy.antweb.upload;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.Status;
import org.calacademy.antweb.Taxon;

/**
 * Names that belong to two or more AntCat taxa with different valid names (Brian Fisher, 2026-10-20),
 * e.g. Pachycondyla sauteri: Wheeler, 1906 (now Cryptopone sauteri) and Forel, 1912 (now Ectomomyrmex
 * sauteri). From the name alone the nightly name update cannot tell which one a curator meant, so it
 * does not move those specimens; nameUpdates.do lists them under "Needs the curator's decision" with
 * the candidates ("Cryptopone sauteri or Ectomomyrmex sauteri").
 *
 * Read from the AntCat export the nightly AntCat import loaded that night
 * (web/speciesList/world/worldants_speciesList.txt): every row of it, because the import keeps only the
 * first taxon of a name in the taxon table and, for a second obsolete combination (the Pachycondyla
 * sauteri case), stores nothing at all; only a second synonym or homonym goes to the homonym table.
 * When the file cannot be read, the homonym table is used instead (SpecimenNameUpdate.sharedNames).
 *
 * Each row gives a candidate: its current valid name; for a valid row, its own name; for a row without
 * one (a junior homonym, an unidentifiable taxon), its name, author and status; an unavailable name
 * without a valid name is not a taxon and gives none. A name is shared when
 * its rows give two or more different candidates. Keys and candidates are display names (genus,
 * species, subspecies; no subgenus), compared without case. Pure: tested in SharedNamesTest.
 *
 * Each shared name also keeps its AntCat taxa (AntcatTaxon: AntCat id, author and year, status, valid
 * name), and so does a name AntCat has but AntWeb does not file specimens under (only a junior homonym,
 * an excluded status): the upload report and nameUpdates.do list them with links to AntCat
 * (SharedNameReport). load() is the one way both read it: the nightly name update, every specimen
 * upload and the page.
 */
public final class SharedNames {

    private static final Log s_log = LogFactory.getLog(SharedNames.class);

    public static final String EXPORT_FILE = "speciesList/world/worldants_speciesList.txt";

    /** One AntCat taxon of a name (one row of the export). antcatId 0 when not known (homonym table). */
    public static final class AntcatTaxon {
        public final int antcatId;
        /** Display name as AntCat spells this taxon (Pachycondyla sauteri). */
        public final String name;
        /** "Wheeler, 1906", tags removed; may be "". */
        public final String authorDate;
        public final String status;
        /** The valid name it stands for (its own name when valid), or null. */
        public final String validName;

        public AntcatTaxon(int antcatId, String name, String authorDate, String status, String validName) {
            this.antcatId = antcatId;
            this.name = name;
            this.authorDate = authorDate == null ? "" : authorDate;
            this.status = status == null ? "" : status;
            this.validName = validName;
        }

        public String toString() {
            return name + (authorDate.isEmpty() ? "" : " " + authorDate) + " (" + status + (validName != null && !validName.equals(name) ? ", now " + validName : "") + ")";
        }
    }

    private final Map<String, List<String>> candidates;
    private final Map<String, List<AntcatTaxon>> taxa;

    private SharedNames(Map<String, List<String>> candidates) {
        this(candidates, new HashMap<>());
    }

    private SharedNames(Map<String, List<String>> candidates, Map<String, List<AntcatTaxon>> taxa) {
        this.candidates = candidates;
        this.taxa = taxa;
    }

    public static SharedNames none() {
        return new SharedNames(new HashMap<>());
    }

    /**
     * The AntCat taxa of a shared name, or of a name AntCat has without any taxon AntWeb files specimens
     * under (no valid row and no valid name: a junior homonym, an excluded status); otherwise null.
     */
    public List<AntcatTaxon> taxaOf(String displayName) {
        if (displayName == null) return null;
        return taxa.get(key(displayName));
    }

    /** The candidates when the name is shared, otherwise null. name: a display name (Pachycondyla sauteri). */
    public List<String> candidatesOf(String displayName) {
        if (displayName == null) return null;
        return candidates.get(key(displayName));
    }

    public int size() {
        return candidates.size();
    }

    static String key(String name) {
        return name.replaceAll("\\([^)]*\\)", " ").replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    /** "Cryptopone sauteri or Ectomomyrmex sauteri"; three or more: "A, B or C". */
    public static String join(List<String> names) {
        if (names.size() <= 1) return names.isEmpty() ? "" : names.get(0);
        return String.join(", ", names.subList(0, names.size() - 1)) + " or " + names.get(names.size() - 1);
    }

    /** "name is shared by two taxa; please confirm the identification: Cryptopone sauteri or Ectomomyrmex sauteri". */
    public static String reason(List<String> names) {
        String n = names.size() == 2 ? "two" : names.size() == 3 ? "three" : String.valueOf(names.size());
        return "name is shared by " + n + " taxa; please confirm the identification: " + join(names);
    }

    public static SharedNames fromExport(File file) throws IOException {
        try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return fromExport(r);
        }
    }

    /** The AntCat export for AntWeb (tab separated, first line the column names). */
    public static SharedNames fromExport(Reader reader) throws IOException {
        BufferedReader in = reader instanceof BufferedReader b ? b : new BufferedReader(reader);
        String header = in.readLine();
        if (header == null) return none();
        List<String> cols = List.of(header.split("\t", -1));
        int genus = cols.indexOf("genus"), species = cols.indexOf("species"), subspecies = cols.indexOf("subspecies");
        int status = cols.indexOf("status"), valid = cols.indexOf("current valid name"), author = cols.indexOf("author date");
        int id = cols.indexOf("antcat id");
        if (genus < 0 || species < 0 || status < 0 || valid < 0) throw new IOException("not an AntCat export: " + header);
        Map<String, Set<String>> all = new HashMap<>();
        Map<String, Integer> rows = new HashMap<>();
        Map<String, List<AntcatTaxon>> rowTaxa = new HashMap<>();
        String line;
        while ((line = in.readLine()) != null) {
            String[] f = line.split("\t", -1);
            if (f.length <= Math.max(Math.max(genus, species), Math.max(status, valid))) continue;
            if (f[species].isEmpty()) continue;   // genus and higher ranks
            String name = f[genus] + " " + f[species] + (subspecies >= 0 && subspecies < f.length && !f[subspecies].isEmpty() ? " " + f[subspecies] : "");
            String k = key(name);
            rows.merge(k, 1, Integer::sum);
            String authorDate = author >= 0 && author < f.length ? f[author] : "";
            String c = candidate(name, f[status], f[valid], authorDate);
            if (c != null) all.computeIfAbsent(k, x -> new LinkedHashSet<>()).add(c);
            int antcatId = 0;
            if (id >= 0 && id < f.length) {
                try { antcatId = Integer.parseInt(f[id].trim()); } catch (NumberFormatException e) { antcatId = 0; }
            }
            rowTaxa.computeIfAbsent(k, x -> new ArrayList<>(2)).add(
                new AntcatTaxon(antcatId, name, cleanAuthor(authorDate), f[status], validName(name, f[status], f[valid])));
        }
        Map<String, List<String>> shared = new HashMap<>();
        Map<String, List<AntcatTaxon>> keptTaxa = new HashMap<>();
        for (Map.Entry<String, List<AntcatTaxon>> e : rowTaxa.entrySet()) {
            Set<String> c = all.get(e.getKey());
            if (rows.get(e.getKey()) > 1 && c != null && c.size() > 1) {
                List<String> sorted = new ArrayList<>(c);
                Collections.sort(sorted);
                shared.put(e.getKey(), sorted);
                keptTaxa.put(e.getKey(), sortTaxa(e.getValue()));
            } else if (noTaxonForSpecimens(e.getValue())) {
                keptTaxa.put(e.getKey(), sortTaxa(e.getValue()));
            }
        }
        return new SharedNames(shared, keptTaxa);
    }

    /** No row is valid and none has a valid name: AntWeb has no taxon to file the name's specimens under. */
    private static boolean noTaxonForSpecimens(List<AntcatTaxon> rows) {
        for (AntcatTaxon t : rows) if (t.validName != null) return false;
        return true;
    }

    private static List<AntcatTaxon> sortTaxa(List<AntcatTaxon> rows) {
        List<AntcatTaxon> sorted = new ArrayList<>(rows);
        sorted.sort((a, b) -> {
            int y = year(a.authorDate).compareTo(year(b.authorDate));
            return y != 0 ? y : Integer.compare(a.antcatId, b.antcatId);
        });
        return sorted;
    }

    /** "1906" from "Wheeler, 1906" (for the order: oldest first); "9999" when there is none. */
    static String year(String authorDate) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(1[5-9]|20)\\d\\d").matcher(authorDate == null ? "" : authorDate);
        String y = "9999";
        while (m.find()) y = m.group();
        return y;
    }

    static String cleanAuthor(String authorDate) {
        return authorDate == null ? "" : authorDate.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
    }

    /** The valid name a row stands for: its own name when valid, its current valid name, otherwise null. */
    static String validName(String name, String status, String currentValid) {
        if ("valid".equals(status)) return name;
        String v = currentValid == null ? "" : currentValid.trim();
        if (v.isEmpty()) return null;
        String[] p = v.split("\\s+", 2);
        boolean subfamilyFirst = p.length == 2 && !p[1].isEmpty() && Character.isUpperCase(p[1].charAt(0));
        return subfamilyFirst ? p[1] : v;
    }

    /** One row's candidate. currentValid is "Subfamily Genus species" in the export. */
    static String candidate(String name, String status, String currentValid, String authorDate) {
        if ("valid".equals(status)) return name;
        String v = currentValid == null ? "" : currentValid.trim();
        if (!v.isEmpty()) {
            String[] p = v.split("\\s+", 2);
            boolean subfamilyFirst = p.length == 2 && !p[1].isEmpty() && Character.isUpperCase(p[1].charAt(0));   // "Ponerinae Cryptopone sauteri", "incertae_sedis Genus species"
            return subfamilyFirst ? p[1] : v;
        }
        if (status != null && status.startsWith("unavailable")) return null;   // a name, not a taxon (Protanilla wallacei Hölldobler & Wilson, 1990)
        String a = authorDate == null ? "" : authorDate.replaceAll("<[^>]*>", "").trim();
        return name + (a.isEmpty() ? "" : " " + a) + " (" + status + ")";
    }

    /** From the homonym table: name -> its candidates and AntCat taxa, built by load(). */
    static SharedNames of(Map<String, List<String>> byDisplayName, Map<String, List<AntcatTaxon>> taxaByDisplayName) {
        SharedNames s = of(byDisplayName);
        for (Map.Entry<String, List<AntcatTaxon>> e : taxaByDisplayName.entrySet()) {
            if (s.candidates.containsKey(key(e.getKey()))) s.taxa.put(key(e.getKey()), sortTaxa(e.getValue()));
        }
        return s;
    }

    // ---------------------------------------------------------------- reading it, the same way everywhere

    private static File s_cachedFile;
    private static long s_cachedModified = -1;
    private static long s_cachedLength = -1;
    private static SharedNames s_cached;

    /**
     * The names shared by two or more AntCat taxa, as the nightly name update, the specimen upload and
     * nameUpdates.do all read them: from the AntCat export the nightly import loaded
     * (web/speciesList/world/worldants_speciesList.txt), which has every taxon; read again only when the
     * file changes. When it cannot be read, from the homonym table (the second taxon of a name the import
     * stored there; it misses two obsolete combinations of one name, which the import does not store).
     */
    public static SharedNames load(Connection connection) {
        File file = null;
        try {
            file = new File(org.calacademy.antweb.util.AntwebProps.getWebDir() + EXPORT_FILE);
        } catch (RuntimeException e) {
            s_log.warn("load() no web directory. e:" + e);
        }
        if (file != null && file.isFile()) {
            synchronized (SharedNames.class) {
                if (s_cached != null && file.equals(s_cachedFile) && file.lastModified() == s_cachedModified && file.length() == s_cachedLength) {
                    return s_cached;
                }
                try {
                    long modified = file.lastModified(), length = file.length();
                    SharedNames s = fromExport(file);
                    s_cachedFile = file;
                    s_cachedModified = modified;
                    s_cachedLength = length;
                    s_cached = s;
                    return s;
                } catch (IOException | RuntimeException e) {
                    s_log.warn("load() could not read " + file + ", using the homonym table. e:" + e);
                }
            }
        }
        return fromHomonymTable(connection);
    }

    /** The fallback of load(): the homonym table, with the taxon row of the same name. */
    public static SharedNames fromHomonymTable(Connection connection) {
        Map<String, List<String>> byName = new HashMap<>();
        Map<String, List<AntcatTaxon>> taxaByName = new HashMap<>();
        if (connection == null) return none();
        String query = "select h.taxon_name, h.author_date, t.status, t.current_valid_name, t.antcat_id, t.author_date"
            + " from homonym h left join taxon t on t.taxon_name = h.taxon_name";
        try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(query)) {
            while (r.next()) {
                String name = Taxon.getPrettyTaxonName(r.getString(1));
                List<String> c = byName.computeIfAbsent(name, k -> new ArrayList<>());
                List<AntcatTaxon> t = taxaByName.computeIfAbsent(name, k -> new ArrayList<>());
                String status = r.getString(3);
                String cvn = r.getString(4);
                if (status != null) {
                    String valid = Status.VALID.equals(status) ? name : cvn != null && !cvn.isEmpty() ? Taxon.getPrettyTaxonName(cvn) : null;
                    c.add(valid != null ? valid : name + " (" + status + ")");
                    boolean known = false;
                    for (AntcatTaxon x : t) if (x.authorDate.equals(cleanAuthor(r.getString(6)))) known = true;
                    if (!known) t.add(new AntcatTaxon(r.getInt(5), name, cleanAuthor(r.getString(6)), status, valid));
                }
                String author = cleanAuthor(r.getString(2));
                c.add(name + (author.isEmpty() ? "" : " " + author) + " (homonym)");
                t.add(new AntcatTaxon(0, name, author, Status.HOMONYM, null));
            }
        } catch (SQLException e) {
            s_log.warn("fromHomonymTable() homonym table not read. e:" + e);
        }
        return of(byName, taxaByName);
    }

    /** From the homonym table: name -> its candidates, built by the caller. */
    public static SharedNames of(Map<String, List<String>> byDisplayName) {
        Map<String, List<String>> m = new HashMap<>();
        for (Map.Entry<String, List<String>> e : byDisplayName.entrySet()) {
            List<String> c = new ArrayList<>(new LinkedHashSet<>(e.getValue()));
            if (c.size() > 1) {
                Collections.sort(c);
                m.put(key(e.getKey()), c);
            }
        }
        return new SharedNames(m);
    }
}

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
 */
public final class SharedNames {

    public static final String EXPORT_FILE = "speciesList/world/worldants_speciesList.txt";

    private final Map<String, List<String>> candidates;

    private SharedNames(Map<String, List<String>> candidates) {
        this.candidates = candidates;
    }

    public static SharedNames none() {
        return new SharedNames(new HashMap<>());
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
        if (genus < 0 || species < 0 || status < 0 || valid < 0) throw new IOException("not an AntCat export: " + header);
        Map<String, Set<String>> all = new HashMap<>();
        Map<String, Integer> rows = new HashMap<>();
        String line;
        while ((line = in.readLine()) != null) {
            String[] f = line.split("\t", -1);
            if (f.length <= Math.max(Math.max(genus, species), Math.max(status, valid))) continue;
            if (f[species].isEmpty()) continue;   // genus and higher ranks
            String name = f[genus] + " " + f[species] + (subspecies >= 0 && subspecies < f.length && !f[subspecies].isEmpty() ? " " + f[subspecies] : "");
            String k = key(name);
            rows.merge(k, 1, Integer::sum);
            String c = candidate(name, f[status], f[valid], author >= 0 && author < f.length ? f[author] : "");
            if (c != null) all.computeIfAbsent(k, x -> new LinkedHashSet<>()).add(c);
        }
        Map<String, List<String>> shared = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : all.entrySet()) {
            if (rows.get(e.getKey()) > 1 && e.getValue().size() > 1) {
                List<String> c = new ArrayList<>(e.getValue());
                Collections.sort(c);
                shared.put(e.getKey(), c);
            }
        }
        return new SharedNames(shared);
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

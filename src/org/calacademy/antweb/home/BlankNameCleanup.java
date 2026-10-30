/*
 * BlankNameCleanup: the cleanups that delete names and list rows no specimen uses any more, written so that
 * specimens with a blank (NULL or empty) taxon name, subfamily or genus do not stop them, with a preview first.
 * Brian Fisher, 2026-10-29. See docs/flows/nightly.md (set 2) and docs/runbook.md ("Specimens with an empty name").
 */
package org.calacademy.antweb.home;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.LogMgr;

/**
 * Six cleanups delete names (taxon rows) and list rows (proj_taxon, geolocale_taxon) that no specimen uses any more:
 *
 *   speciesWithoutSpecimen     nightly set 2 (dataCleanup): species and subspecies not in AntCat's list (worldants)
 *                              without a specimen (TaxonDb.deleteSpeciesWithoutSpecimenOrAntcatSource)
 *   generaWithoutSpecimen      nightly set 2: genera not in AntCat's list without a specimen of that subfamily and
 *                              genus (TaxonDb.deleteGeneraWithoutSpecimenOrAntcatSource)
 *   indetWithoutSpecimen       after each specimen upload: indetermined and unrecognized names from specimen uploads
 *                              that no specimen uses (TaxonDb.removeIndetWithoutSpecimen)
 *   orphanedSpecies            after each specimen upload: the group's names from its uploads that no specimen uses,
 *                              fewer than 100 (OrphansDb.deleteOrphanedSpeciesFromSource)
 *   projectMorphos, placeMorphos  nightly set 2: morphospecies without a specimen on project and place lists (not the
 *                              curators' rows; ProjTaxonDb / GeolocaleTaxonDb.deleteUncuratedMorphosWithoutSpecimen)
 *
 * Each was written "name not in (select taxon_name from specimen)" (genera: "(subfamily, genus) not in (select
 * subfamily, genus from specimen)"). NOT IN over a list that holds a NULL is never true, so as soon as one specimen in
 * AntWeb has no taxon name (or no subfamily or genus) the cleanup silently deleted nothing at all (triage 2026-10-27,
 * docs/runbook.md). The blank-safe rule (BLANK_SAFE_*): NOT EXISTS, and blank names excluded explicitly on both sides:
 * a specimen with a blank name protects nothing, and a name row that is itself blank is never deleted here.
 *
 * The blank-safe rule deletes the backlog that piled up while the old rule did nothing, so it starts with a preview
 * (Brian Fisher, 2026-10-29): mode() is PREVIEW until compute.log holds a preview line (PREVIEW_LOG) from an earlier
 * run, ON after that. In PREVIEW every cleanup runs its old rule (exactly what it did before), and nightly set 2
 * writes to compute.log how many rows each blank-safe rule would delete, with examples (preview()). The next run
 * deletes them. etc/cleanup.properties on the server (blankNameCleanup=preview or on) overrides: preview keeps the
 * old rules (and writes the preview every night); on starts the blank-safe rules at once.
 */
public final class BlankNameCleanup {

    private static final Log s_log = LogFactory.getLog(BlankNameCleanup.class);

    private BlankNameCleanup() {}

    /** The line nightly set 2 writes to compute.log in PREVIEW; the next run, seeing it, deletes (ON). */
    public static final String PREVIEW_LOG = "blank-name-safe cleanup preview (2026-10-29)";

    /** The server's switch (gitignored, in etc/ of the project folder; the container sees it here). */
    static final String CONFIG = "/antweb/deploy/etc/cleanup.properties";

    public enum Mode { PREVIEW, ON }

    /** Examples per cleanup in the preview. */
    public static final int EXAMPLES = 10;

    // ------------------------------------------------------------------ the rules

    /** No specimen with a non-blank name is filed under the outer row's name (the outer table given by its name). */
    static String noSpecimen(String outer) {
        return "not exists (select 1 from specimen s where s.taxon_name = " + outer + ".taxon_name)";
    }

    static String notBlank(String column) {
        return column + " is not null and " + column + " <> ''";
    }

    static final String NOT_IN_ANTCAT = "not exists (select 1 from proj_taxon p where p.project_name = 'worldants' and p.taxon_name = taxon.taxon_name)";

    /** speciesWithoutSpecimen, old rule. */
    public static final String OLD_SPECIES_WHERE = "where taxon_name not in (select taxon_name from proj_taxon where project_name = 'worldants') "
        + " and taxon_name not in (select taxon_name from specimen) "
        + " and taxarank in ('species', 'subspecies')";
    /** speciesWithoutSpecimen, blank-safe. */
    public static final String BLANK_SAFE_SPECIES_WHERE = "where taxarank in ('species', 'subspecies') and " + notBlank("taxon.taxon_name")
        + " and " + NOT_IN_ANTCAT + " and " + noSpecimen("taxon");

    /** generaWithoutSpecimen, old rule. */
    public static final String OLD_GENERA_WHERE = "where taxon_name not in (select taxon_name from proj_taxon where project_name = 'worldants') "
        + " and (subfamily, genus) not in (select subfamily, genus from specimen) "
        + " and taxarank in ('genus')";
    /** generaWithoutSpecimen, blank-safe: a genus row without a subfamily or genus is left alone, as the old rule left it. */
    public static final String BLANK_SAFE_GENERA_WHERE = "where taxarank = 'genus' and " + notBlank("taxon.taxon_name")
        + " and " + notBlank("taxon.subfamily") + " and " + notBlank("taxon.genus") + " and " + NOT_IN_ANTCAT
        + " and not exists (select 1 from specimen s where s.subfamily = taxon.subfamily and s.genus = taxon.genus)";

    /** indetWithoutSpecimen, old rule. */
    public static final String OLD_INDET_WHERE = "where source like 'specimen%' and status in ('indetermined', 'unrecognized') and taxon_name not in (select taxon_name from specimen)";
    /** indetWithoutSpecimen, blank-safe. */
    public static final String BLANK_SAFE_INDET_WHERE = "where source like 'specimen%' and status in ('indetermined', 'unrecognized') and "
        + notBlank("taxon.taxon_name") + " and " + noSpecimen("taxon");

    /** orphanedSpecies (the select; OrphansDb adds rank, source and order), old rule. */
    public static final String OLD_ORPHANED_WHERE = " where taxon_name not in (select taxon_name from specimen)";
    /** orphanedSpecies, blank-safe. */
    public static final String BLANK_SAFE_ORPHANED_WHERE = " where " + notBlank("taxon.taxon_name") + " and " + noSpecimen("taxon");

    /** projectMorphos and placeMorphos: the morphospecies without a specimen (t: taxon), old rule. */
    public static final String OLD_MORPHO_NO_SPECIMEN = "t.taxon_name not in (select taxon_name from specimen)";
    /** projectMorphos and placeMorphos, blank-safe. */
    public static final String BLANK_SAFE_MORPHO_NO_SPECIMEN = notBlank("t.taxon_name") + " and " + noSpecimen("t");

    public static String speciesWhere(boolean blankSafe) { return blankSafe ? BLANK_SAFE_SPECIES_WHERE : OLD_SPECIES_WHERE; }
    public static String generaWhere(boolean blankSafe) { return blankSafe ? BLANK_SAFE_GENERA_WHERE : OLD_GENERA_WHERE; }
    public static String indetWhere(boolean blankSafe) { return blankSafe ? BLANK_SAFE_INDET_WHERE : OLD_INDET_WHERE; }
    public static String orphanedWhere(boolean blankSafe) { return blankSafe ? BLANK_SAFE_ORPHANED_WHERE : OLD_ORPHANED_WHERE; }
    public static String morphoNoSpecimen(boolean blankSafe) { return blankSafe ? BLANK_SAFE_MORPHO_NO_SPECIMEN : OLD_MORPHO_NO_SPECIMEN; }

    // ------------------------------------------------------------------ the mode

    /** The mode from the switch's value (preview, on, or null/other: automatic) and the lines of compute.log. */
    public static Mode mode(String setting, List<String> computeLog) {
        String v = setting == null ? "" : setting.trim().toLowerCase();
        if (v.equals("preview") || v.equals("off")) return Mode.PREVIEW;
        if (v.equals("on")) return Mode.ON;
        if (computeLog != null) {
            for (String line : computeLog) if (line.contains(PREVIEW_LOG)) return Mode.ON;
        }
        return Mode.PREVIEW;
    }

    private static Mode s_mode = null;
    private static long s_loaded = 0;

    /** The mode in effect (re-read within a minute; compute.log grows by one line a night, read once a minute at most). */
    public static synchronized Mode mode() {
        long now = System.currentTimeMillis();
        if (s_mode == null || now - s_loaded > 60_000) {
            s_loaded = now;
            s_mode = mode(setting(), computeLog());
        }
        return s_mode;
    }

    /** True when the cleanups use the blank-safe rules. */
    public static boolean isOn() {
        try {
            return mode() == Mode.ON;
        } catch (RuntimeException e) {
            s_log.warn("isOn() e:" + e);
            return false;
        }
    }

    static String setting() {
        File f = new File(CONFIG);
        if (!f.isFile()) return null;
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(f)) {
            p.load(in);
        } catch (Exception e) {
            s_log.warn("setting() could not read " + CONFIG + " e:" + e);
            return null;
        }
        return p.getProperty("blankNameCleanup");
    }

    static List<String> computeLog() {
        try {
            return Files.readAllLines(Paths.get(LogMgr.getWebLogRoot() + "compute.log"), StandardCharsets.ISO_8859_1);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // ------------------------------------------------------------------ the preview

    /** One cleanup in the preview: its name, the table, the rows its old rule and its blank-safe rule delete now. */
    public static final class Count {
        public final String name;
        public final long oldRule;
        public final long blankSafe;
        public final List<String> examples;
        Count(String name, long oldRule, long blankSafe, List<String> examples) {
            this.name = name; this.oldRule = oldRule; this.blankSafe = blankSafe; this.examples = examples;
        }
        public String line() {
            return name + ": old rule " + oldRule + ", blank-safe rule " + blankSafe
                + (examples.isEmpty() ? "" : " (e.g. " + String.join(", ", examples) + ")");
        }
    }

    static long count(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            return r.next() ? r.getLong(1) : 0;
        }
    }

    static List<String> names(Connection c, String sql) throws SQLException {
        List<String> list = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            while (r.next()) list.add(r.getString(1));
        }
        return list;
    }

    static Count taxonCount(Connection c, String name, String oldWhere, String newWhere) throws SQLException {
        return new Count(name, count(c, "select count(*) from taxon " + oldWhere), count(c, "select count(*) from taxon " + newWhere),
            names(c, "select taxon_name from taxon " + newWhere + " order by taxon_name limit " + EXAMPLES));
    }

    static String morphoRows(String table, String alias, String noSpecimen, String curatorSources) {
        return "from " + table + " " + alias + " join taxon t on t.taxon_name = " + alias + ".taxon_name"
            + " where t.taxarank in ('species', 'subspecies') and t.status = 'morphotaxon' and " + noSpecimen
            + (curatorSources == null ? " and " + alias + ".source = 'specimen'" : " and " + alias + ".source not in " + curatorSources);
    }

    static Count morphoCount(Connection c, String name, String table, String curatorSources) throws SQLException {
        String a = "x";
        return new Count(name, count(c, "select count(*) " + morphoRows(table, a, OLD_MORPHO_NO_SPECIMEN, curatorSources)),
            count(c, "select count(*) " + morphoRows(table, a, BLANK_SAFE_MORPHO_NO_SPECIMEN, curatorSources)),
            names(c, "select distinct t.taxon_name " + morphoRows(table, a, BLANK_SAFE_MORPHO_NO_SPECIMEN, curatorSources)
                + " order by t.taxon_name limit " + EXAMPLES));
    }

    /** What each cleanup deletes now by its old and its blank-safe rule (reads only). */
    public static List<Count> counts(Connection c, String curatorSources) throws SQLException {
        List<Count> list = new ArrayList<>();
        list.add(taxonCount(c, "speciesWithoutSpecimen", OLD_SPECIES_WHERE, BLANK_SAFE_SPECIES_WHERE));
        list.add(taxonCount(c, "generaWithoutSpecimen", OLD_GENERA_WHERE, BLANK_SAFE_GENERA_WHERE));
        list.add(taxonCount(c, "indetWithoutSpecimen", OLD_INDET_WHERE, BLANK_SAFE_INDET_WHERE));
        String orphan = " and taxarank in ('species', 'subspecies') and source like '%specimen%'";
        list.add(taxonCount(c, "orphanedSpecies (all groups; an upload deletes its group's when fewer than 100)",
            OLD_ORPHANED_WHERE + orphan, BLANK_SAFE_ORPHANED_WHERE + orphan));
        list.add(morphoCount(c, "projectMorphos", "proj_taxon", curatorSources));
        list.add(morphoCount(c, "placeMorphos", "geolocale_taxon", null));
        return list;
    }

    /** Specimens with a blank (NULL or empty) taxon name, subfamily or genus: the rows that stopped the old rules. */
    public static String blankSpecimens(Connection c) throws SQLException {
        return "specimens with a blank name " + count(c, "select count(*) from specimen where taxon_name is null or taxon_name = ''")
            + ", blank subfamily or genus " + count(c, "select count(*) from specimen where subfamily is null or subfamily = '' or genus is null or genus = ''");
    }

    /**
     * When blankSafe is false (PREVIEW): writes to compute.log the preview line (PREVIEW_LOG; the next run deletes) and
     * one line per cleanup with what its old and blank-safe rules delete now, and returns a short message. When true:
     * nothing (returns ""). Reads only. Called at the start of nightly set 2 (dataCleanup), which decides the mode once
     * (isOn()) for its whole run, so the line written here takes effect from the next run on.
     */
    public static String preview(Connection c, String curatorSources, boolean blankSafe) throws SQLException {
        if (blankSafe) return "";
        List<String> lines = previewLines(c, curatorSources);
        for (String line : lines) LogMgr.appendLog("compute.log", "  " + line, true);
        return lines.get(0) + " ";
    }

    /** The preview's lines (previewLines(c, ...).get(0) holds PREVIEW_LOG). */
    public static List<String> previewLines(Connection c, String curatorSources) throws SQLException {
        List<String> lines = new ArrayList<>();
        lines.add(PREVIEW_LOG + ": nothing deleted by the blank-safe rules tonight; the next run deletes what they find ("
            + blankSpecimens(c) + "; etc/cleanup.properties blankNameCleanup=preview keeps the old rules)");
        for (Count n : counts(c, curatorSources)) lines.add("  " + n.line());
        return lines;
    }
}

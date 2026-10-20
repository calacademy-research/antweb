package org.calacademy.antweb.upload;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.Group;
import org.calacademy.antweb.Status;
import org.calacademy.antweb.Taxon;
import org.calacademy.antweb.home.CuratorTaxonSetDb;
import org.calacademy.antweb.util.GroupMgr;
import org.calacademy.antweb.util.LogMgr;
import org.calacademy.antweb.util.TaxonPropMgr;

/**
 * Nightly name update (2026-10, approved by Brian Fisher 10-18): existing specimens of every group
 * follow AntCat name changes without a new upload.
 *
 * A specimen gets its name when its file is uploaded: if the uploaded name is then a synonym (or
 * obsolete combination, misspelling...) it is filed under the current valid name and the uploaded
 * name is kept as original_taxon_name ("Uploaded As"). Before this step nothing re-checked that later:
 * when AntCat synonymized a name after the upload, the specimen stayed on the old name and dropped
 * out of both species pages until the group uploaded again.
 *
 * Each night, after the AntCat import and the TaxonWorks upload (set 1), this step applies the
 * upload's own rule (CurrentValidNameRule, the decision of SpecimenUploadProcess.setStatusAndCurrentValidName)
 * to the uploaded name of every specimen, and moves the specimen if the answer differs from the
 * name it is filed under now. Each specimen moved (or, in report mode, that would move) is a row in
 * specimen_name_update, shown on the Name updates page (nameUpdates.do). Sets 2 to 5 then rebuild
 * the lists, counts and maps from the new names.
 *
 * Each row also records why (Brian Fisher, 2026-10-20): the taxonomic reason with AntCat's reference,
 * read from AntCat's taxonomic history as the AntCat import stored it that night (description_edit,
 * title taxonomichistory), and the AntCat id of the taxon whose history gave it (NameUpdateReason,
 * AntcatHistory). This is the specimen's taxonomic history.
 *
 * Left alone: morphospecies and indets; specimens filed under a homonym placeholder taxon; names
 * whose current valid name is a homonym or has no taxon row (the step never creates taxa); uploaded
 * names AntCat does not know; names with another non-valid status (unavailable, homonym, ...).
 *
 * Switch: etc/name-update.properties (mode=off|report|apply, maxChanges); no file = off.
 * Docs: docs/flows/antcat-to-antweb.md, docs/flows/nightly.md. Table: db/upgrade/8.113/2026-10-20-specimen-name-update.sql.
 */
public class SpecimenNameUpdate {

    private static final Log s_log = LogFactory.getLog(SpecimenNameUpdate.class);

    public static final String CONFIG = "/antweb/deploy/etc/name-update.properties";

    public static final String OFF = "off";
    public static final String REPORT = "report";
    public static final String APPLY = "apply";
    public static final int DEFAULT_MAX_CHANGES = 50000;

    /** specimen_name_update.mode values. */
    public static final String CHANGED = "changed";
    public static final String WOULD_CHANGE = "would_change";

    public static final String PLACEHOLDER_INSERT_METHOD = "homononymMirroringTaxon";

    // ---------------------------------------------------------------- settings

    public static final class Settings {
        public final String mode;
        public final int maxChanges;
        public final String note;   // why the mode is off, for the log
        public Settings(String mode, int maxChanges, String note) {
            this.mode = mode;
            this.maxChanges = maxChanges;
            this.note = note;
        }
        public String toString() { return "mode=" + mode + " maxChanges=" + maxChanges + (note != null ? " (" + note + ")" : ""); }
    }

    public static Settings readSettings() {
        return readSettings(new File(CONFIG));
    }

    /** No file: off. Read at each run, so a change needs no restart. */
    public static Settings readSettings(File file) {
        if (file == null || !file.isFile()) return new Settings(OFF, DEFAULT_MAX_CHANGES, "no etc/name-update.properties");
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            s_log.error("readSettings() could not read " + file + " e:" + e);
            return new Settings(OFF, DEFAULT_MAX_CHANGES, "could not read " + file.getName());
        }
        return parseSettings(p);
    }

    /** mode: off, report or apply (anything else: off). maxChanges: a positive number, default 50000. A # starts a comment. */
    public static Settings parseSettings(Properties p) {
        String mode = value(p.getProperty("mode"));
        String note = null;
        if (mode == null || mode.isEmpty()) {
            mode = OFF;
            note = "no mode in the settings file";
        } else {
            mode = mode.toLowerCase();
            if (!OFF.equals(mode) && !REPORT.equals(mode) && !APPLY.equals(mode)) {
                note = "unknown mode '" + mode + "'";
                mode = OFF;
            }
        }
        int max = DEFAULT_MAX_CHANGES;
        String m = value(p.getProperty("maxChanges"));
        if (m != null && !m.isEmpty()) {
            try {
                int v = Integer.parseInt(m.replace(",", "").replace("_", ""));
                if (v > 0) max = v;
            } catch (NumberFormatException e) {
                // keep the default
            }
        }
        return new Settings(mode, max, note);
    }

    private static String value(String v) {
        if (v == null) return null;
        int hash = v.indexOf('#');
        if (hash >= 0) v = v.substring(0, hash);
        return v.trim();
    }

    // ---------------------------------------------------------------- the decision for one name

    public enum Outcome {
        CHANGE("moved"),
        UNCHANGED("already right"),
        MORPHO_OR_INDET("morphospecies or indet"),
        PLACEHOLDER("filed under a homonym placeholder"),
        HOMONYM("homonym"),
        CURRENT_VALID("filed under a name AntCat still calls valid"),
        NOT_IN_ANTCAT("uploaded name not in AntCat"),
        NO_TARGET("current valid name has no taxon row"),
        OTHER_STATUS("other status");

        public final String label;
        Outcome(String label) { this.label = label; }
    }

    public static final class Decision {
        public final Outcome outcome;
        public final String newName;            // CHANGE only
        public final String newOriginalName;    // CHANGE only: uploaded name, or null when the specimen goes back to it
        Decision(Outcome outcome, String newName, String newOriginalName) {
            this.outcome = outcome;
            this.newName = newName;
            this.newOriginalName = newOriginalName;
        }
        static Decision of(Outcome outcome) { return new Decision(outcome, null, null); }
        public String toString() { return outcome + (newName != null ? " -> " + newName : ""); }
    }

    /**
     * What happens to the specimens of one group uploaded as uploadedName and filed under currentName.
     * rule is CurrentValidNameRule.decide(uploadedName); isPlaceholder tells the homonym placeholder
     * taxa (insert_method homononymMirroringTaxon) apart. Pure: tested in SpecimenNameUpdateTest.
     */
    public static Decision decide(String uploadedName, String currentName, java.util.function.Predicate<String> isPlaceholder, CurrentValidNameRule.Result rule) {
        return decide(uploadedName, currentName, isPlaceholder, rule, false, false);
    }

    public static Decision decide(String uploadedName, String currentName, java.util.function.Predicate<String> isPlaceholder, CurrentValidNameRule.Result rule, boolean uploadedIsHomonym) {
        return decide(uploadedName, currentName, isPlaceholder, rule, uploadedIsHomonym, false);
    }

    /**
     * uploadedIsHomonym: the uploaded name belongs to more than one AntCat taxon (it is in the homonym
     * table). The name alone cannot tell which one the curator meant, so the nightly step leaves the
     * specimen where the upload filed it. 2026-10-20: specimens uploaded as "Camponotus gigas" were filed
     * under Dinomyrmex gigas (Camponotus gigas Latreille, 1802); the taxon row of that name is the other
     * Camponotus gigas (Leach, 1825), a synonym of Camponotus cruentatus, and the preview would have moved
     * them there (Brian: both species are valid).
     */
    /**
     * currentIsValid: AntCat calls the name the specimen is filed under valid. Brian Fisher, 2026-10-20: a
     * specimen filed under a valid name never moves to another name through its uploaded name's synonymy
     * (the AntCat import keeps only the first of two taxa with the same name, so the uploaded name's row can
     * be the wrong one, e.g. Camponotus gigas Leach instead of Camponotus gigas Latreille). The one move
     * still made from a valid name: the uploaded name itself is valid again (resurrected from synonymy),
     * and the specimen goes back to the name the curator gave.
     */
    public static Decision decide(String uploadedName, String currentName, java.util.function.Predicate<String> isPlaceholder, CurrentValidNameRule.Result rule, boolean uploadedIsHomonym, boolean currentIsValid) {
        if (Taxon.isMorphoOrIndet(uploadedName) || Taxon.isMorphoOrIndet(currentName)) return Decision.of(Outcome.MORPHO_OR_INDET);
        if (uploadedIsHomonym && !uploadedName.equals(currentName)) return Decision.of(Outcome.HOMONYM);
        if (currentIsValid && rule.kind == CurrentValidNameRule.Kind.REPLACE) return Decision.of(Outcome.CURRENT_VALID);
        if (isPlaceholder.test(currentName)) return Decision.of(Outcome.PLACEHOLDER);
        String newName;
        switch (rule.kind) {
            case REPLACE:
                // The upload files a name whose current valid name is a homonym under a placeholder
                // taxon; once that exists, the rule finds it. The nightly step never moves specimens there.
                if (isPlaceholder.test(rule.validTaxonName)) return Decision.of(Outcome.HOMONYM);
                newName = rule.validTaxonName;
                break;
            case KEEP:
                // A name that is valid again (resurrected): the specimen goes back to the uploaded name.
                if (Status.VALID.equals(rule.status)) {
                    newName = uploadedName;
                    break;
                }
                return Decision.of(Status.HOMONYM.equals(rule.status) ? Outcome.HOMONYM : Outcome.OTHER_STATUS);
            case TARGET_IS_HOMONYM:
                return Decision.of(Outcome.HOMONYM);
            case NOT_IN_ANTCAT:
                return Decision.of(Outcome.NOT_IN_ANTCAT);
            case INDET:
            case MORPHO:
                return Decision.of(Outcome.MORPHO_OR_INDET);
            default:   // NO_CURRENT_VALID_NAME, TARGET_MISSING, TARGET_SAME
                return Decision.of(Outcome.NO_TARGET);
        }
        if (newName == null || newName.equals(currentName)) return Decision.of(Outcome.UNCHANGED);
        return new Decision(Outcome.CHANGE, newName, newName.equals(uploadedName) ? null : uploadedName);
    }

    /** Apply mode changes nothing when more specimens than maxChanges would move in one run. */
    public static boolean overCap(int specimens, int maxChanges) {
        return specimens > maxChanges;
    }

    // ---------------------------------------------------------------- one run

    /** The specimens of one (group, uploaded name, current name) that move to one new name. */
    public static final class Change {
        public final int groupId;
        public final String uploadedName;
        public final String oldName;
        public final String newName;
        public final String newOriginalName;
        public final String antcatStatus;
        public final NameUpdateReason reason;
        public final List<String[]> specimens = new ArrayList<>();   // code, bioregion
        Change(int groupId, String uploadedName, String oldName, String newName, String newOriginalName, String antcatStatus) {
            this(groupId, uploadedName, oldName, newName, newOriginalName, antcatStatus, null);
        }
        Change(int groupId, String uploadedName, String oldName, String newName, String newOriginalName, String antcatStatus, NameUpdateReason reason) {
            this.groupId = groupId;
            this.uploadedName = uploadedName;
            this.oldName = oldName;
            this.newName = newName;
            this.newOriginalName = newOriginalName;
            this.antcatStatus = antcatStatus;
            this.reason = reason;
        }
    }

    public static final class Summary {
        public String requestedMode;
        public String rowMode;          // CHANGED, WOULD_CHANGE, or null when nothing ran
        public int specimens;
        public int names;
        public final Map<Integer, Integer> byGroup = new TreeMap<>();
        public final Map<Outcome, Integer> skipped = new LinkedHashMap<>();
        public boolean capped;
        public int maxChanges;
        public int parkedRenamed;
        public long millis;
        public String note;             // skipped / failed

        public String line() {
            StringBuilder b = new StringBuilder("name update: ");
            if (rowMode == null) {
                return b.append(note == null ? "nothing done" : note).toString();
            }
            String modeLabel = CHANGED.equals(rowMode) ? "apply: changed" : (capped ? "apply refused: above maxChanges=" + maxChanges + ", nothing changed" : "report: would change");
            b.append(String.format("%,d", specimens)).append(" specimens in ").append(byGroup.size()).append(" groups (").append(modeLabel).append(")");
            if (!byGroup.isEmpty()) {
                b.append(" [");
                int i = 0;
                for (Map.Entry<Integer, Integer> e : byGroup.entrySet()) {
                    if (i++ > 0) b.append(", ");
                    if (i > 12) { b.append("..."); break; }
                    b.append(groupLabel(e.getKey())).append(" ").append(e.getValue());
                }
                b.append("]");
            }
            b.append(", ").append(names).append(" names");
            int skippedTotal = 0;
            StringBuilder s = new StringBuilder();
            for (Map.Entry<Outcome, Integer> e : skipped.entrySet()) {
                if (e.getKey() == Outcome.UNCHANGED || e.getValue() == 0) continue;
                skippedTotal += e.getValue();
                if (s.length() > 0) s.append(", ");
                s.append(e.getValue()).append(" ").append(e.getKey().label);
            }
            if (skippedTotal > 0) b.append("; left alone (names): ").append(s);
            if (parkedRenamed > 0) b.append("; parked list entries moved: ").append(parkedRenamed);
            b.append("; ").append(String.format("%.1f", millis / 60000.0)).append(" min. See nameUpdates.do.");
            if (note != null) b.append(" ").append(note);
            return b.toString();
        }
    }

    static String groupLabel(int groupId) {
        try {
            Group g = GroupMgr.getGroup(groupId);
            if (g != null && g.getAbbrev() != null && !g.getAbbrev().isEmpty()) return g.getAbbrev();
            if (g != null && g.getName() != null) return g.getName();
        } catch (RuntimeException e) {
            // GroupMgr not loaded (tests)
        }
        return "group " + groupId;
    }

    /**
     * Runs the step once, unless a specimen upload is running (then it does nothing). While it runs,
     * uploads are refused ("nightly name update"). mode REPORT records what would change; APPLY
     * changes the specimens (or, above maxChanges, records them as would_change and changes nothing).
     * The caller commits.
     */
    public static Summary runLocked(Connection connection, String mode, int maxChanges) throws SQLException {
        synchronized (UploadAction.class) {
            if (UploadAction.isInUploadProcess()) {
                Summary s = new Summary();
                s.requestedMode = mode;
                s.note = "skipped: an upload is running (" + UploadAction.getIsInUploadProcess() + ")";
                return s;
            }
            UploadAction.setIsInUploadProcess("nightly name update");
        }
        try {
            return run(connection, mode, maxChanges);
        } finally {
            UploadAction.setIsInUploadProcess(null);
        }
    }

    /** Candidates: names that can change. Everything else is filed under its uploaded name and that name is not a synonym etc. */
    static String candidatesSql() {
        return "select s.access_group, coalesce(nullif(s.original_taxon_name, ''), s.taxon_name) uploaded, s.taxon_name, count(*) n"
            + " from specimen s"
            + " left join taxon tu on tu.taxon_name = coalesce(nullif(s.original_taxon_name, ''), s.taxon_name)"
            + " where (s.original_taxon_name is not null and s.original_taxon_name != '' and s.original_taxon_name != s.taxon_name)"
            + "    or tu.status in " + Status.usesCurrentValidNameSqlList()
            + " group by s.access_group, uploaded, s.taxon_name"
            + " order by s.access_group, uploaded, s.taxon_name";
    }

    static final String SPECIMENS_SQL = "select code, bioregion from specimen"
        + " where access_group = ? and taxon_name = ? and coalesce(nullif(original_taxon_name, ''), taxon_name) = ?";

    static final String UPDATE_SQL = "update specimen set taxon_name = ?, original_taxon_name = ?, status = ?,"
        + " family = ?, subfamily = ?, tribe = ?, genus = ?, subgenus = ?, species = ?, subspecies = ?"
        + " where access_group = ? and taxon_name = ? and coalesce(nullif(original_taxon_name, ''), taxon_name) = ?";

    public static Summary run(Connection connection, String mode, int maxChanges) throws SQLException {
        long start = System.currentTimeMillis();
        Summary summary = new Summary();
        summary.requestedMode = mode;
        summary.maxChanges = maxChanges;
        if (!REPORT.equals(mode) && !APPLY.equals(mode)) {
            summary.note = "off";
            return summary;
        }
        new SpecimenNameUpdateDb(connection).ensureSchema();
        for (Outcome o : Outcome.values()) summary.skipped.put(o, 0);

        Timestamp runDate;
        try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery("select now()")) {
            r.next();
            runDate = r.getTimestamp(1);
        }

        Set<String> placeholders = new HashSet<>();
        try (Statement stmt = connection.createStatement();
             ResultSet r = stmt.executeQuery("select taxon_name from taxon where insert_method = '" + PLACEHOLDER_INSERT_METHOD + "'")) {
            while (r.next()) placeholders.add(r.getString(1));
        }

        CurrentValidNameRule.Lookup lookup = CurrentValidNameRule.plainLookup(connection);
        Map<String, CurrentValidNameRule.Result> ruleCache = new java.util.HashMap<>();
        Map<String, Boolean> homonymCache = new java.util.HashMap<>();
        Map<String, Boolean> currentValidCache = new java.util.HashMap<>();
        Reasons reasons = new Reasons(connection);
        List<Change> changes = new ArrayList<>();
        Set<String> names = new HashSet<>();

        List<Object[]> candidates = new ArrayList<>();
        try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(candidatesSql())) {
            while (r.next()) candidates.add(new Object[] {r.getInt(1), r.getString(2), r.getString(3)});
        }
        for (Object[] c : candidates) {
            int groupId = (Integer) c[0];
            String uploaded = (String) c[1];
            String current = (String) c[2];
            if (uploaded == null || current == null) continue;
            CurrentValidNameRule.Result rule = ruleCache.get(uploaded);
            if (rule == null) {
                rule = CurrentValidNameRule.decide(uploaded, lookup);
                ruleCache.put(uploaded, rule);
            }
            Boolean uploadedHomonym = homonymCache.get(uploaded);
            if (uploadedHomonym == null) {
                uploadedHomonym = lookup.isHomonym(uploaded);
                homonymCache.put(uploaded, uploadedHomonym);
            }
            Boolean currentValid = currentValidCache.get(current);
            if (currentValid == null) {
                CurrentValidNameRule.TaxonRow cur = lookup.getTaxon(current);
                currentValid = cur != null && Status.VALID.equals(cur.status);
                currentValidCache.put(current, currentValid);
            }
            Decision d = decide(uploaded, current, placeholders::contains, rule, uploadedHomonym, currentValid);
            if (d.outcome != Outcome.CHANGE) {
                summary.skipped.merge(d.outcome, 1, Integer::sum);
                continue;
            }
            CurrentValidNameRule.TaxonRow oldRow = lookup.getTaxon(current);
            Change change = new Change(groupId, uploaded, current, d.newName, d.newOriginalName, oldRow == null ? null : oldRow.status,
                reasons.get(uploaded, current, d.newName));
            try (PreparedStatement stmt = connection.prepareStatement(SPECIMENS_SQL)) {
                stmt.setInt(1, groupId);
                stmt.setString(2, current);
                stmt.setString(3, uploaded);
                try (ResultSet r = stmt.executeQuery()) {
                    while (r.next()) change.specimens.add(new String[] {r.getString(1), r.getString(2)});
                }
            }
            if (change.specimens.isEmpty()) continue;
            changes.add(change);
            summary.specimens += change.specimens.size();
            summary.byGroup.merge(groupId, change.specimens.size(), Integer::sum);
            names.add(current + "\t" + d.newName);
        }
        summary.names = names.size();

        boolean apply = APPLY.equals(mode);
        if (apply && overCap(summary.specimens, maxChanges)) {
            summary.capped = true;
            apply = false;
        }
        summary.rowMode = apply ? CHANGED : WOULD_CHANGE;

        SpecimenNameUpdateDb db = new SpecimenNameUpdateDb(connection);
        db.deletePreview();   // the page shows only the latest preview; changed rows are the history
        if (apply) {
            for (Change change : changes) applyChange(connection, change);
        }
        db.insert(runDate, summary.rowMode, changes);
        summary.parkedRenamed = moveParkedListEntries(connection, lookup, apply);

        summary.millis = System.currentTimeMillis() - start;
        if (summary.capped) {
            s_log.warn("run() " + summary.specimens + " specimens would change, above maxChanges=" + maxChanges + ": nothing changed");
        }
        return summary;
    }

    /**
     * The reason of each move, from the taxon table (status, antcat_id) and the taxonomic history the
     * AntCat import stored in description_edit. One per (uploaded, old, new) name; each taxon is read once.
     * If the history cannot be read the move still happens, with the AntCat status as its reason.
     */
    static final class Reasons {
        private final Connection connection;
        private final Map<String, NameUpdateReason> byNames = new java.util.HashMap<>();
        private final Map<String, NameUpdateReason.TaxonInfo> taxa = new java.util.HashMap<>();
        private boolean failed = false;

        Reasons(Connection connection) {
            this.connection = connection;
        }

        NameUpdateReason get(String uploaded, String oldName, String newName) {
            String key = uploaded + "\t" + oldName + "\t" + newName;
            NameUpdateReason r = byNames.get(key);
            if (r == null) {
                r = NameUpdateReason.of(info(uploaded), info(oldName), info(newName));
                byNames.put(key, r);
            }
            return r;
        }

        NameUpdateReason.TaxonInfo info(String taxonName) {
            NameUpdateReason.TaxonInfo info = taxa.get(taxonName);
            if (info != null) return info;
            String status = null;
            Integer antcatId = null;
            String history = null;
            if (!failed) {
                try {
                    try (PreparedStatement stmt = connection.prepareStatement("select status, antcat_id from taxon where taxon_name = ?")) {
                        stmt.setString(1, taxonName);
                        try (ResultSet r = stmt.executeQuery()) {
                            if (r.next()) {
                                status = r.getString(1);
                                int id = r.getInt(2);
                                antcatId = r.wasNull() ? null : id;
                            }
                        }
                    }
                    try (PreparedStatement stmt = connection.prepareStatement(
                            "select content from description_edit where taxon_name = ? and title = 'taxonomichistory' and code is null")) {
                        stmt.setString(1, taxonName);
                        try (ResultSet r = stmt.executeQuery()) {
                            while (r.next()) history = r.getString(1);   // as the taxon page: the last one
                        }
                    }
                } catch (SQLException e) {
                    failed = true;
                    s_log.warn("Reasons.info() taxonomic history not read, reasons without references. e:" + e);
                }
            }
            info = new NameUpdateReason.TaxonInfo(Taxon.getPrettyTaxonName(taxonName), status, antcatId, AntcatHistory.parse(history));
            taxa.put(taxonName, info);
            return info;
        }
    }

    private static void applyChange(Connection connection, Change change) throws SQLException {
        String[] cols = new String[7];
        try (PreparedStatement stmt = connection.prepareStatement(
                "select family, subfamily, tribe, genus, subgenus, species, subspecies from taxon where taxon_name = ?")) {
            stmt.setString(1, change.newName);
            try (ResultSet r = stmt.executeQuery()) {
                if (r.next()) for (int i = 0; i < 7; i++) cols[i] = r.getString(i + 1);
            }
        }
        try (PreparedStatement stmt = connection.prepareStatement(UPDATE_SQL)) {
            stmt.setString(1, change.newName);
            stmt.setString(2, change.newOriginalName);
            stmt.setString(3, Status.VALID);
            for (int i = 0; i < 7; i++) stmt.setString(4 + i, cols[i]);
            stmt.setInt(11, change.groupId);
            stmt.setString(12, change.oldName);
            stmt.setString(13, change.uploadedName);
            stmt.executeUpdate();
        }
        // As the upload does (processLine): introduced or not depends on the name and the bioregion.
        try (PreparedStatement stmt = connection.prepareStatement("update specimen set is_introduced = ? where code = ? and access_group = ?")) {
            int n = 0;
            for (String[] sp : change.specimens) {
                boolean introduced;
                try {
                    introduced = TaxonPropMgr.isIntroduced(change.newName, sp[1]);
                } catch (RuntimeException e) {
                    return;   // taxon properties not loaded: leave the flags as they are
                }
                stmt.setInt(1, introduced ? 1 : 0);
                stmt.setString(2, sp[0]);
                stmt.setInt(3, change.groupId);
                stmt.addBatch();
                if (++n % 500 == 0) stmt.executeBatch();
            }
            stmt.executeBatch();
        }
    }

    /**
     * Curator list entries parked by the cleanups (taxon_set_parked, PR #180) under a name that has
     * since become a synonym etc. are moved to the current valid name, so that the restore puts them
     * back under the name the list shows. (List rows themselves are moved every night by the AntCat
     * import, TaxonSetDb.updateTaxonSetTaxonNames.) Returns the number of parked entries moved (or, in
     * report mode, that would be moved).
     */
    static int moveParkedListEntries(Connection connection, CurrentValidNameRule.Lookup lookup, boolean apply) throws SQLException {
        new CuratorTaxonSetDb(connection).ensureSchema();
        List<String[]> rows = new ArrayList<>();
        String query = "select p.list_type, p.list_key, p.taxon_name, t.current_valid_name from taxon_set_parked p"
            + " join taxon t on t.taxon_name = p.taxon_name"
            + " where p.restored is null and t.status in " + Status.usesCurrentValidNameSqlList()
            + " and t.current_valid_name is not null";
        try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(query)) {
            while (r.next()) rows.add(new String[] {r.getString(1), r.getString(2), r.getString(3), r.getString(4)});
        }
        int moved = 0;
        for (String[] row : rows) {
            String target = lookup.getCurrentValidTaxonName(row[3]);
            if (target == null || target.equals(row[2])) continue;
            ++moved;
            if (!apply) continue;
            boolean exists;
            try (PreparedStatement stmt = connection.prepareStatement(
                    "select 1 from taxon_set_parked where list_type = ? and list_key = ? and taxon_name = ?")) {
                stmt.setString(1, row[0]);
                stmt.setString(2, row[1]);
                stmt.setString(3, target);
                try (ResultSet r = stmt.executeQuery()) { exists = r.next(); }
            }
            String dml = exists
                ? "delete from taxon_set_parked where list_type = ? and list_key = ? and taxon_name = ?"
                : "update taxon_set_parked set taxon_name = ? where list_type = ? and list_key = ? and taxon_name = ?";
            try (PreparedStatement stmt = connection.prepareStatement(dml)) {
                int i = 1;
                if (!exists) stmt.setString(i++, target);
                stmt.setString(i++, row[0]);
                stmt.setString(i++, row[1]);
                stmt.setString(i, row[2]);
                stmt.executeUpdate();
            }
        }
        if (moved > 0 && apply) LogMgr.appendLog("taxonSet.log", "SpecimenNameUpdate parked entries moved to the current valid name:" + moved, true);
        return moved;
    }

    /** One line in compute.log. */
    public static void log(Summary summary) {
        LogMgr.appendLog("compute.log", "  " + summary.line(), true);
    }
}

package org.calacademy.antweb.upload;

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
import java.util.Set;

import org.calacademy.antweb.Status;

/**
 * Table genus_concept_rule (Brian Fisher, 2026-11-03): the old genus concepts approved or rejected on
 * genusConceptRules.do, one row per old genus and bioregion. The nightly name update (third pass) and the specimen
 * upload apply the approved rows (GenusConceptRule). Also the read-only queries that propose new ones
 * (GenusConceptProposals).
 *
 * ensureSchema() creates the table when missing, in the character set and collation of taxon.taxon_name, and when
 * it is empty puts in GenusConceptRule.SEED (Mesoponera in the Afrotropical and Malagasy regions is Xiphopelta,
 * approved by Brian Fisher, 2026-11-03). Undo of an approval: update genus_concept_rule set status = 'rejected'
 * where old_genus = ... and bioregion = ... (specimens already moved stay; specimen_name_update has the moves).
 */
public class GenusConceptRuleDb {

    public static final String APPROVED = "approved";
    public static final String REJECTED = "rejected";

    private final Connection connection;

    public GenusConceptRuleDb(Connection connection) {
        this.connection = connection;
    }

    public static String createTable(String charset, String collation) {
        String defaults = "";
        if (charset != null && charset.matches("[a-z0-9_]+")) defaults += " DEFAULT CHARSET=" + charset;
        if (collation != null && collation.matches("[a-z0-9_]+")) defaults += " COLLATE=" + collation;
        return "CREATE TABLE IF NOT EXISTS genus_concept_rule ("
            + " id int NOT NULL AUTO_INCREMENT,"
            + " old_genus varchar(128) NOT NULL,"
            + " bioregion varchar(64) NOT NULL,"
            + " new_genus varchar(128) NOT NULL,"
            + " status varchar(12) NOT NULL,"
            + " approved_by varchar(128) NULL,"
            + " approved_at datetime NULL,"
            + " note varchar(500) NULL,"
            + " PRIMARY KEY (id),"
            + " UNIQUE KEY by_genus_bioregion (old_genus, bioregion)"
            + ") ENGINE=InnoDB" + defaults;
    }

    private static volatile boolean s_schemaChecked = false;

    /** For the database tests, which drop and re-create the table between tests. */
    public static void resetSchemaCheck() {
        s_schemaChecked = false;
    }

    /**
     * Creates the table only when it is missing, and seeds it only then (review of #321: a CREATE TABLE, even IF NOT
     * EXISTS, commits the caller's open transaction in MySQL, and the nightly name update reads the rules mid-run; and
     * an emptied table must not re-approve the seed rules by itself). Checked once per server start.
     */
    public void ensureSchema() throws SQLException {
        if (s_schemaChecked) return;
        String charset = null;
        String collation = null;
        try (Statement stmt = connection.createStatement()) {
            try (ResultSet r = stmt.executeQuery("select 1 from information_schema.tables where table_schema = database()"
                    + " and table_name = 'genus_concept_rule'")) {
                if (r.next()) {
                    s_schemaChecked = true;
                    return;
                }
            }
            try (ResultSet r = stmt.executeQuery("select character_set_name, collation_name from information_schema.columns"
                    + " where table_schema = database() and table_name = 'taxon' and column_name = 'taxon_name'")) {
                if (r.next()) {
                    charset = r.getString(1);
                    collation = r.getString(2);
                }
            }
            stmt.executeUpdate(createTable(charset, collation));
        }
        // Seeded right after the create; when a seed row fails, the new table is dropped again so the next run
        // creates and seeds it once more, instead of finding it empty and never seeding it (review of #321).
        try {
            for (GenusConceptRule.Rule rule : GenusConceptRule.SEED) {
                for (String bioregion : new java.util.TreeSet<>(rule.bioregions)) {
                    save(rule.oldGenus, bioregion, rule.newGenus, APPROVED, rule.approvedBy, Timestamp.valueOf(rule.approvedOn + " 10:35:00"), rule.note);
                }
            }
        } catch (SQLException | RuntimeException e) {
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("drop table if exists genus_concept_rule");
            } catch (SQLException dropped) {
                e.addSuppressed(dropped);
            }
            throw e;
        }
        s_schemaChecked = true;
    }

    /** One row of the table. */
    public static final class Row {
        public String oldGenus;
        public String bioregion;
        public String newGenus;
        public String status;
        public String approvedBy;
        public Timestamp approvedAt;
        public String note;
    }

    /** All rows, approved first, then by old genus and bioregion. */
    public List<Row> all() throws SQLException {
        ensureSchema();
        List<Row> rows = new ArrayList<>();
        try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(
                "select old_genus, bioregion, new_genus, status, approved_by, approved_at, note from genus_concept_rule"
                + " order by status, old_genus, bioregion")) {
            while (r.next()) {
                Row row = new Row();
                row.oldGenus = r.getString(1);
                row.bioregion = r.getString(2);
                row.newGenus = r.getString(3);
                row.status = r.getString(4);
                row.approvedBy = r.getString(5);
                row.approvedAt = r.getTimestamp(6);
                row.note = r.getString(7);
                rows.add(row);
            }
        }
        return rows;
    }

    /** The approved rules, one per old genus and bioregion (the table is read once by each caller per run or upload). */
    public List<GenusConceptRule.Rule> approved() throws SQLException {
        List<GenusConceptRule.Rule> rules = new ArrayList<>();
        for (Row row : all()) {
            if (!APPROVED.equals(row.status)) continue;
            rules.add(new GenusConceptRule.Rule(row.oldGenus, Set.of(row.bioregion), row.newGenus, row.approvedBy,
                row.approvedAt == null ? "" : row.approvedAt.toString().substring(0, 10), row.note));
        }
        return rules;
    }

    /** The keys (GenusConceptProposals.key) of every approved or rejected (old genus, bioregion). */
    public Set<String> ruledKeys() throws SQLException {
        Set<String> keys = new HashSet<>();
        for (Row row : all()) keys.add(GenusConceptProposals.key(row.oldGenus, row.bioregion));
        return keys;
    }

    /** Approves or rejects (old genus, bioregion); a second decision replaces the first. */
    public void save(String oldGenus, String bioregion, String newGenus, String status, String by, Timestamp at, String note) throws SQLException {
        if (!APPROVED.equals(status) && !REJECTED.equals(status)) throw new IllegalArgumentException("status " + status);
        try (PreparedStatement stmt = connection.prepareStatement("insert into genus_concept_rule"
                + " (old_genus, bioregion, new_genus, status, approved_by, approved_at, note) values (?, ?, ?, ?, ?, ?, ?)"
                + " on duplicate key update new_genus = values(new_genus), status = values(status), approved_by = values(approved_by),"
                + " approved_at = values(approved_at), note = values(note)")) {
            stmt.setString(1, oldGenus);
            stmt.setString(2, bioregion);
            stmt.setString(3, newGenus == null ? "" : newGenus);
            stmt.setString(4, status);
            stmt.setString(5, by);
            stmt.setTimestamp(6, at);
            stmt.setString(7, note == null || note.isBlank() ? null : (note.length() > 500 ? note.substring(0, 500) : note));
            stmt.executeUpdate();
        }
    }

    // ------------------------------------------------------------ the proposals (read only)

    /**
     * The (genus, bioregion) pairs with morphospecies or indet specimens, not red-flagged, with the genus row's
     * status. Morphospecies and indets by specimen.status, as the upload sets it (CurrentValidNameRule).
     */
    public static final String SPECIMENS_SQL = "select substring_index(s.taxon_name, ' ', 1) genus, s.bioregion, s.access_group,"
        + " count(*) n, max(g.status) genus_status"
        + " from specimen s"
        + " left join taxon g on g.taxon_name = substring_index(s.taxon_name, ' ', 1) and g.taxarank = 'genus'"
        + " where s.status in ('" + Status.MORPHOTAXON + "', '" + Status.INDETERMINED + "')"
        + " and (s.flag is null or s.flag != 'red')"
        + " and s.bioregion is not null and s.bioregion != ''"
        + " and s.taxon_name like '% %' and s.taxon_name not like '(%'"
        + " group by genus, s.bioregion, s.access_group";

    /**
     * AntCat's species and subspecies of each genus that now go under another name (Status.usesCurrentValidName),
     * by the bioregion lists their current valid name is on (bioregion_taxon) and the genus of that name, with that
     * genus row's status and rank. Moves within the genus are dropped in Java.
     */
    public static String movedSql() {
        return "select substring_index(t.taxon_name, ' ', 1) old_genus, bt.bioregion_name, substring_index(v.taxon_name, ' ', 1) new_genus,"
            + " count(distinct v.taxon_name) n,"
            + " group_concat(distinct concat(t.taxon_name, '>', v.taxon_name) order by t.taxon_name separator '|') examples,"
            + " max(h.status) new_status, max(h.taxarank) new_rank"
            + " from taxon t"
            + " join taxon v on v.taxon_name = t.current_valid_name and v.status = '" + Status.VALID + "'"
            + " join bioregion_taxon bt on bt.taxon_name = v.taxon_name"
            + " left join taxon h on h.taxon_name = substring_index(v.taxon_name, ' ', 1)"
            + " where t.taxarank in ('species', 'subspecies') and t.status in " + Status.usesCurrentValidNameSqlList()
            + " and t.taxon_name not like '(%'"
            + " group by old_genus, bt.bioregion_name, new_genus";
    }

    /** The valid species and subspecies of each genus on each bioregion's list. */
    public static final String VALID_SQL = "select substring_index(t.taxon_name, ' ', 1) genus, bt.bioregion_name, count(distinct t.taxon_name) n"
        + " from taxon t join bioregion_taxon bt on bt.taxon_name = t.taxon_name"
        + " where t.taxarank in ('species', 'subspecies') and t.status = '" + Status.VALID + "' and t.taxon_name not like '(%'"
        + " group by genus, bt.bioregion_name";

    /** The pairs, with their specimens, moved species and valid species; not classified (GenusConceptProposals.listed). */
    public List<GenusConceptProposals.Pair> loadPairs() throws SQLException {
        Map<String, GenusConceptProposals.Pair> pairs = new LinkedHashMap<>();
        try (Statement stmt = connection.createStatement()) {
            try (ResultSet r = stmt.executeQuery(SPECIMENS_SQL)) {
                while (r.next()) {
                    String genus = r.getString(1);
                    String bioregion = r.getString(2);
                    GenusConceptProposals.Pair p = pairs.computeIfAbsent(GenusConceptProposals.key(genus, bioregion),
                        k -> new GenusConceptProposals.Pair(genus, bioregion));
                    p.specimensByGroup.merge(r.getInt(3), r.getInt(4), Integer::sum);
                    if (r.getString(5) != null) p.genusStatus = r.getString(5);
                }
            }
            try (ResultSet r = stmt.executeQuery(movedSql())) {
                while (r.next()) {
                    GenusConceptProposals.Pair p = pairs.get(GenusConceptProposals.key(r.getString(1), r.getString(2)));
                    String newGenus = r.getString(3);
                    if (p == null || newGenus == null || newGenus.equals(p.genus)) continue;
                    GenusConceptProposals.Moved m = new GenusConceptProposals.Moved(newGenus);
                    m.species = r.getInt(4);
                    String ex = r.getString(5);
                    if (ex != null) for (String e : ex.split("\\|")) if (!e.isEmpty()) m.examples.add(e);
                    m.newGenusStatus = r.getString(6);
                    m.newGenusRank = r.getString(7);
                    p.moved.put(newGenus, m);
                }
            }
            try (ResultSet r = stmt.executeQuery(VALID_SQL)) {
                while (r.next()) {
                    GenusConceptProposals.Pair p = pairs.get(GenusConceptProposals.key(r.getString(1), r.getString(2)));
                    if (p != null) p.validSpecies = r.getInt(3);
                }
            }
        }
        return new ArrayList<>(pairs.values());
    }

    /** loadPairs() classified against the table: the listed pairs, PROPOSE first, each with the names a rule would move. */
    public List<GenusConceptProposals.Pair> proposals() throws SQLException {
        List<GenusConceptProposals.Pair> listed = GenusConceptProposals.listed(loadPairs(), ruledKeys());
        for (GenusConceptProposals.Pair p : listed) loadWillMove(p);
        return listed;
    }

    /** The specimens a rule would move, as SPECIMENS_SQL counts them, by name: {taxon_name, specimens, up to 5 codes}. */
    public static final String WILL_MOVE_SQL = "select s.taxon_name, count(*) n,"
        + " substring_index(group_concat(s.code order by s.code separator ' '), ' ', 5) codes"
        + " from specimen s"
        + " where s.taxon_name like ? escape '!' and s.bioregion = ?"
        + " and s.status in ('" + Status.MORPHOTAXON + "', '" + Status.INDETERMINED + "')"
        + " and (s.flag is null or s.flag != 'red')"
        + " group by s.taxon_name order by n desc, s.taxon_name";

    void loadWillMove(GenusConceptProposals.Pair p) throws SQLException {
        p.willMove.clear();
        String prefix = p.genus.replace("!", "!!").replace("%", "!%").replace("_", "!_") + " %";
        try (PreparedStatement stmt = connection.prepareStatement(WILL_MOVE_SQL)) {
            stmt.setString(1, prefix);
            stmt.setString(2, p.bioregion);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) p.willMove.add(new String[] {r.getString(1), r.getString(2), r.getString(3)});
            }
        }
    }
}

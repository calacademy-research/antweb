package org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * B36 (docs/numbers.md, 2026-10-22): an ant species or subspecies of a counted status, that is its own current valid
 * name and is identified to genus, has its genus as parent: the genus row named by the part of its name before the first
 * space (Taxon.getParentTaxonNameFromName). The nightly crawl counts a list's species and subspecies rows under their
 * parent (CountDb.countGrandChildren) and adds images up the same tree (CountDb.imageCountCrawl), so a name with no
 * parent, or with another genus as parent, is missing from its genus row.
 *
 * repair() runs every night in set 1, after the AntCat import, the TaxonWorks upload and the name update, before sets 2
 * to 5 build and count the lists. It only sets parents, and only to a genus row that exists. It never makes a genus row
 * (Brian Fisher, 2026-10-22):
 *   - names not identified to genus (PlaceTotals.identifiedToGenus: the genus part is indet, (indet), a subfamily in
 *     parentheses, or the whole name is unidentified) are not species and are left alone; no placeholder genus rows;
 *   - a name whose genus has no taxon row is reported, not repaired (missing()): for a valid name that is an AntCat
 *     import problem (the import brings the genus rows; an admin alert says so); for other names it is usually an
 *     upload that lacked the subfamily ('(indet)decamorium decem': Decamorium is myrmicinaedecamorium), a genus that is
 *     not an ant ('(indet)termes (indet)') or text that is not a name. The count check (taxon.parents) lists them too.
 * Names whose genus name is a taxon row of another rank are left alone (the count check reports them). Synonyms and
 * other names that are not their own current valid name keep the parent of their valid name (by design,
 * AntwebUpload.saveTaxon).
 */
public class TaxonParentDb extends AntwebDb {

    private static final Log s_log = LogFactory.getLog(TaxonParentDb.class);

    public TaxonParentDb(Connection connection) {
        super(connection);
    }

    /**
     * The names B36 is about (alias c): ant species and subspecies of a counted status that are their own current valid
     * name and are identified to genus.
     */
    public static final String NAMES = "c.family = 'formicidae' and c.taxarank in ('species', 'subspecies') and c.taxon_name like '% %'"
        + " and c.status in " + PlaceTotals.COUNTABLE
        + " and (c.current_valid_name is null or c.current_valid_name = '' or c.current_valid_name = c.taxon_name)"
        + " and " + PlaceTotals.identifiedToGenus("c");

    /** The genus of a name of NAMES, by its name. */
    public static final String GENUS_BY_NAME = "substring_index(c.taxon_name, ' ', 1)";

    /**
     * Rows NAMES whose parent is not their genus row (read-only): the preview of the repair. genus_row_rank empty = no
     * taxon row of that name (reported, not repaired); a rank other than genus = left alone.
     */
    public static final String PREVIEW_SQL = "select c.taxon_name, c.taxarank, c.status, c.parent_taxon_name, " + GENUS_BY_NAME + " genus_by_name,"
        + " g.taxarank genus_row_rank from taxon c left join taxon g on g.taxon_name = " + GENUS_BY_NAME
        + " where " + NAMES + " and (c.parent_taxon_name is null or c.parent_taxon_name <> " + GENUS_BY_NAME + " or g.taxon_name is null)"
        + " order by c.taxon_name";

    /** The parent of each name of NAMES set to its genus row, where that genus row exists. */
    public static final String UPDATE_PARENTS_SQL = "update taxon c join taxon g on g.taxon_name = " + GENUS_BY_NAME + " and g.taxarank = 'genus'"
        + " set c.parent_taxon_name = g.taxon_name"
        + " where " + NAMES + " and (c.parent_taxon_name is null or c.parent_taxon_name <> g.taxon_name)";

    /**
     * Names of NAMES whose genus by name has no taxon row (reported, never repaired): name, status, and a genus row of
     * the same genus under another subfamily if there is one (the upload lacked the subfamily), else null.
     */
    public static final String MISSING_GENUS_SQL = "select c.taxon_name, c.status,"
        + " (select min(o.taxon_name) from taxon o where o.taxarank = 'genus' and o.family = 'formicidae' and o.genus = c.genus"
        + "   and o.taxon_name <> " + GENUS_BY_NAME + ") other_genus_row"
        + " from taxon c left join taxon g on g.taxon_name = " + GENUS_BY_NAME
        + " where " + NAMES + " and g.taxon_name is null order by c.taxon_name";

    /** What a repair did and found. */
    public static final class Result {
        /** parents set to their genus row */
        public int parents;
        /** names whose genus has no taxon row (not changed), and of those the valid ones (an AntCat import problem) */
        public int missing;
        public int missingValid;
        /** the first few of them, "name (status)" */
        public final java.util.List<String> examples = new java.util.ArrayList<>();
        public final java.util.List<String> validExamples = new java.util.ArrayList<>();

        public boolean changed() { return parents > 0; }

        public String line() {
            String line = "taxon parents (B36): " + parents + " parents set to the genus";
            if (missing > 0) {
                line += "; " + missing + " names whose genus has no taxon row, not changed (" + missingValid + " valid: AntCat import problem;"
                    + " others: subfamily missing in the upload, or not an ant), e.g. " + String.join(", ", examples);
            }
            return line;
        }

        /** The admin alert for valid names whose genus row is missing, or null. */
        public String alert() {
            if (missingValid == 0) return null;
            return MISSING_VALID_ALERT + ": " + missingValid + " (e.g. " + String.join(", ", validExamples)
                + "). The AntCat import should bring the genus row; check the AntCat export and the import log.";
        }
    }

    /** Start of the admin alert (AdminAlertMgr.addIfFresh looks for it). */
    public static final String MISSING_VALID_ALERT = "Valid ant names whose genus row is missing (AntCat import problem)";

    static final int EXAMPLES = 5;

    public Result repair() throws SQLException {
        Result result = new Result();
        try (Statement stmt = getConnection().createStatement()) {
            result.parents = stmt.executeUpdate(UPDATE_PARENTS_SQL);
        }
        missing(result);
        if (result.changed() || result.missing > 0) s_log.info("repair() " + result.line());
        return result;
    }

    /** Fills the missing counts and examples of result (read-only). */
    public void missing(Result result) throws SQLException {
        try (Statement stmt = getConnection().createStatement(); ResultSet r = stmt.executeQuery(MISSING_GENUS_SQL)) {
            while (r.next()) {
                ++result.missing;
                String other = r.getString(3);
                String example = r.getString(1) + " (" + r.getString(2) + (other == null ? "" : "; genus row " + other) + ")";
                if (result.examples.size() < EXAMPLES) result.examples.add(example);
                if ("valid".equals(r.getString(2))) {
                    ++result.missingValid;
                    if (result.validExamples.size() < EXAMPLES) result.validExamples.add(example);
                }
            }
        }
    }
}

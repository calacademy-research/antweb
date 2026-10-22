package org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * B36 (docs/numbers.md, 2026-10-22): an ant species or subspecies of a counted status, that is its own current valid
 * name, has its genus as parent: the genus row named by the part of its name before the first space
 * (Taxon.getParentTaxonNameFromName). The nightly crawl counts a list's species and subspecies rows under their parent
 * (CountDb.countGrandChildren) and adds images up the same tree (CountDb.imageCountCrawl), so a name with no parent, or
 * with another genus as parent, is missing from its genus row.
 *
 * Uploads set the parent from the name since 2026-10-22 (AntwebUpload.saveTaxon). Before, the parent was left empty when
 * TaxonMgr did not know the genus (a genus new in tonight's AntCat import, a morphogenus or an indet genus such as
 * "(indet)decamorium"), and the genus row was not made when its own parent could not be read from its name.
 *
 * repair() runs every night in set 1, after the AntCat import, the TaxonWorks upload and the name update, before sets 2
 * to 5 build and count the lists: (1) a genus row is made, as a placeholder (status unrecognized, insert_method
 * parentFix, like AntwebUpload.addMissingGenus), for each such name whose genus has no taxon row at all; (2) the parent
 * of each such name is set to its genus row. Names whose genus name is a taxon row of another rank are left alone (the
 * count check reports them). Synonyms and other names that are not their own current valid name keep the parent of their
 * valid name (by design, AntwebUpload.saveTaxon).
 */
public class TaxonParentDb extends AntwebDb {

    private static final Log s_log = LogFactory.getLog(TaxonParentDb.class);

    public TaxonParentDb(Connection connection) {
        super(connection);
    }

    /** The names B36 is about (alias c): ant species and subspecies of a counted status that are their own current valid name. */
    public static final String NAMES = "c.family = 'formicidae' and c.taxarank in ('species', 'subspecies') and c.taxon_name like '% %'"
        + " and c.status in " + PlaceTotals.COUNTABLE
        + " and (c.current_valid_name is null or c.current_valid_name = '' or c.current_valid_name = c.taxon_name)";

    /** The genus of a name of NAMES, by its name. */
    public static final String GENUS_BY_NAME = "substring_index(c.taxon_name, ' ', 1)";

    /** Rows NAMES whose parent is not their genus by name (read-only; the preview of the repair). */
    public static final String PREVIEW_SQL = "select c.taxon_name, c.taxarank, c.status, c.parent_taxon_name, " + GENUS_BY_NAME + " genus_by_name,"
        + " g.taxarank genus_row_rank from taxon c left join taxon g on g.taxon_name = " + GENUS_BY_NAME
        + " where " + NAMES + " and (c.parent_taxon_name is null or c.parent_taxon_name <> " + GENUS_BY_NAME + " or g.taxon_name is null)"
        + " order by c.taxon_name";

    /**
     * (1) A placeholder genus row for each genus by name that has no taxon row: family, subfamily and genus from one of
     * its names, parent its subfamily row when there is one.
     */
    public static final String INSERT_GENERA_SQL = "insert into taxon (taxon_name, taxarank, family, subfamily, genus, status, parent_taxon_name,"
        + " source, insert_method)"
        + " select x.genus_name, 'genus', 'formicidae', x.subfamily, x.genus, 'unrecognized', sf.taxon_name, 'parentFix', 'parentFix'"
        + " from (select " + GENUS_BY_NAME + " genus_name, min(c.subfamily) subfamily, min(c.genus) genus from taxon c"
        + "   left join taxon g on g.taxon_name = " + GENUS_BY_NAME
        + "   where " + NAMES + " and g.taxon_name is null group by " + GENUS_BY_NAME + ") x"
        + " left join taxon sf on sf.taxon_name = x.subfamily and sf.taxarank = 'subfamily'";

    /** (2) The parent of each name of NAMES set to its genus row. */
    public static final String UPDATE_PARENTS_SQL = "update taxon c join taxon g on g.taxon_name = " + GENUS_BY_NAME + " and g.taxarank = 'genus'"
        + " set c.parent_taxon_name = g.taxon_name"
        + " where " + NAMES + " and (c.parent_taxon_name is null or c.parent_taxon_name <> g.taxon_name)";

    /** The two counts of a repair: genus rows made, parents set. */
    public static final class Result {
        public int genera;
        public int parents;

        public boolean changed() { return genera > 0 || parents > 0; }

        public String line() {
            return "taxon parents (B36): " + genera + " genus rows made, " + parents + " parents set to the genus";
        }
    }

    public Result repair() throws SQLException {
        Result result = new Result();
        try (Statement stmt = getConnection().createStatement()) {
            result.genera = stmt.executeUpdate(INSERT_GENERA_SQL);
            result.parents = stmt.executeUpdate(UPDATE_PARENTS_SQL);
        }
        if (result.changed()) s_log.info("repair() " + result.line());
        return result;
    }
}

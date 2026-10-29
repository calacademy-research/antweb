package org.calacademy.antweb.util;

import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;


/**
 * Named queries that take a parameter (query.do?name=...&param=...): the species list with range data and the
 * range summary of a place (geolocale) or a bioregion, linked from the place and bioregion species list pages.
 * The place or bioregion name is quoted into the SQL (sqlText). Run by QueryManager.runQueryWithParam. Tests:
 * QueriesReviewTest, tests/e2e/test_named_queries.py.
 */
public abstract class QueriesWithParams {

    private static final Log s_log = LogFactory.getLog(QueriesWithParams.class);

    public static NamedQuery getNamedQueryWithParam(String queryName, String param) {

        if ("geolocaleSpeciesListWithRangeData".equals(queryName)) {
            return getGeolocaleSpeciesListWithRangeData(param); // param is a geolocaleName
        }
        if ("geolocaleSpeciesListRangeSummary".equals(queryName)) {
            return getGeolocaleSpeciesListRangeSummary(param); // param is a geolocaleName
        }

        if ("bioregionSpeciesListWithRangeData".equals(queryName)) {
            return getBioregionSpeciesListWithRangeData(param); // param is a bioregionName
        }
        if ("bioregionSpeciesListRangeSummary".equals(queryName)) {
            return getBioregionSpeciesListRangeSummary(param); // param is a bioregionName
        }

        //if ("specificSpeciesList".equals(queryName)) {
        //    return getSpecificSpeciesList(param); // param is a bioregionName
        //}

        return null;
    }

    /* Maybe instead of search?
    private static NamedQuery getSpecificSpeciesList(String species) {
        NamedQuery query = null;
        query = new NamedQuery(
                "specificSpeciesList"
                , species
                , FileUtil.makeReportName("SpecificSpeciesList")
                , ""
                , new String[] {"Subfamily", "Genus", "Species", "Subspecies", "Author Date", "Status", "Introduced", "Endemic", "Range"}
                , "select initcap(t.subfamily), initcap(t.genus), t.species, IFNULL(t.subspecies, ''), t.author_date, t.status, gt.is_introduced, gt.is_endemic, "
                + " (select group_concat(' ', g2.name order by name) from geolocale_taxon gt2, geolocale g2 where gt2.geolocale_id = g2.id and g2.georank = '" + georank + "' and gt2.taxon_name = t.taxon_name order by g2.name) "
                + " from taxon t, geolocale_taxon gt, geolocale g where t.taxon_name = gt.taxon_name and gt.geolocale_id = g.id and t.taxarank in ('species', 'subspecies') "
                + " and g.georank = '" + georank + "' and g.name = '" + geolocaleName + "' order by t.subfamily, t.genus, t.species, t.subspecies"
        );

        A.log("getNamedQuery() geolocale:" + geolocale + " query:" + query);

        return query; // end getQueryWithParam()
    }*/

    /**
     * A place name or georank as the inside of an SQL string literal: quotes doubled and backslashes doubled
     * (MySQL reads a backslash as an escape). The builders below are public for the tests; callers from pages
     * go through getNamedQueryWithParam, which first checks that the place exists.
     */
    static String sqlText(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("'", "''");
    }

    private static NamedQuery getGeolocaleSpeciesListWithRangeData(String geolocaleName) {
        NamedQuery query = null;

        Geolocale geolocale = GeolocaleMgr.getGeolocale(geolocaleName);
        if (geolocale == null) {
            s_log.warn("getGeolocaleSpeciesListWithRangeData() geolocale not found:" + geolocaleName);
            return null;
        }
        query = geolocaleSpeciesListWithRangeData(geolocaleName, geolocale.getGeorank());
        s_log.debug("getNamedQuery() geolocale:" + geolocale + " query:" + query);
        return query;
    }

    // The query's name is the name pages link to (query.do?name=...&param=...): the permalink on the result page
    // uses it. Was "speciesListWithRangeData" and "speciesListRangeSummary", names no page could open (2026-10-22).
    public static NamedQuery geolocaleSpeciesListWithRangeData(String geolocaleName, String georank) {
        return new NamedQuery(
                "geolocaleSpeciesListWithRangeData"
                , geolocaleName
                , FileUtil.makeReportName("SpeciesListWithRange" + geolocaleName)
                , "The species and subspecies on this place's species list, with author and year, status, the"
                  + " introduced and endemic flags of the list (1 = yes; worked out for valid names only) and the other"
                  + " places of the same rank whose lists hold the name (Range). Copy it into a spreadsheet."
                , new String[] {"Subfamily", "Genus", "Species", "Subspecies", "Author Date", "Status", "Introduced", "Endemic", "Range"}
                , "select initcap(t.subfamily), initcap(t.genus), t.species, IFNULL(t.subspecies, ''), t.author_date, t.status, gt.is_introduced, gt.is_endemic, "
                  + " (select group_concat(' ', g2.name order by g2.name) from geolocale_taxon gt2, geolocale g2 where gt2.geolocale_id = g2.id and g2.georank = '" + sqlText(georank) + "' and gt2.taxon_name = t.taxon_name) "
                  + " from taxon t, geolocale_taxon gt, geolocale g where t.taxon_name = gt.taxon_name and gt.geolocale_id = g.id and t.taxarank in ('species', 'subspecies') "
                  + " and g.georank = '" + sqlText(georank) + "' and g.name = '" + sqlText(geolocaleName) + "' order by t.subfamily, t.genus, t.species, t.subspecies"
        );
    }


    private static NamedQuery getGeolocaleSpeciesListRangeSummary(String geolocaleName) {
        NamedQuery query = null;

        Geolocale geolocale = GeolocaleMgr.getGeolocale(geolocaleName);
        if (geolocale == null) {
            s_log.warn("getGeolocaleSpeciesListRangeSummary() geolocale not found:" + geolocaleName);
            return null;
        }
        query = geolocaleSpeciesListRangeSummary(geolocaleName, geolocale.getGeorank());
        s_log.debug("getNamedQuery() geolocale:" + geolocale + " query:" + query);
        return query;
    }

    public static NamedQuery geolocaleSpeciesListRangeSummary(String geolocaleName, String georank) {
        return new NamedQuery(
                "geolocaleSpeciesListRangeSummary"
                , geolocaleName
                , FileUtil.makeReportName("SpeciesListRangeSummary" + geolocaleName)
                , "How many species and subspecies on this place's species list have each status, and how many of"
                  + " them are flagged introduced and endemic (1 = yes). The totals of the Species List with Range Data."
                , new String[] {"Names", "Status", "Introduced", "Endemic"}
                , "select count(*), t.status, gt.is_introduced, gt.is_endemic from geolocale g, geolocale_taxon gt, taxon t "
                + " where g.id = gt.geolocale_id and gt.taxon_name = t.taxon_name and g.georank = '" + sqlText(georank) + "' and t.taxarank in ('species', 'subspecies') "
                + " and g.name = '" + sqlText(geolocaleName) + "' group by t.status, gt.is_introduced, gt.is_endemic order by count(*) desc"
        );
    }

    private static NamedQuery getBioregionSpeciesListWithRangeData(String bioregionName) {
        NamedQuery query = null;

        Bioregion bioregion = BioregionMgr.getBioregion(bioregionName);
        if (bioregion == null) {
            s_log.warn("getBioregionSpeciesListWithRangeData() bioregion not found:" + bioregionName);
            return null;
        }
        query = bioregionSpeciesListWithRangeData(bioregionName);
        s_log.debug("getNamedQuery() bioregion:" + bioregion + " query:" + query);
        return query;
    }

    public static NamedQuery bioregionSpeciesListWithRangeData(String bioregionName) {
        return new NamedQuery(
                "bioregionSpeciesListWithRangeData"
                , bioregionName
                , FileUtil.makeReportName("bioregionSpeciesListWithRange" + bioregionName)
                , "The species and subspecies on this bioregion's species list, with author and year, status, the"
                  + " introduced and endemic flags (1 = yes) and every bioregion whose list holds the name (Range)."
                  + " Copy it into a spreadsheet."
                , new String[] {"Subfamily", "Genus", "Species", "Subspecies", "Author Date", "Status", "Introduced", "Endemic", "Range"}
                , "select initcap(t.subfamily), initcap(t.genus), t.species, IFNULL(t.subspecies, ''), t.author_date, t.status, bt.is_introduced, bt.is_endemic"
                + " , (select group_concat(' ', b2.name order by b2.name) from bioregion_taxon bt2, bioregion b2 where bt2.bioregion_name = b2.name and bt2.taxon_name = t.taxon_name)"
                + " from taxon t, bioregion_taxon bt, bioregion b where t.taxon_name = bt.taxon_name and bt.bioregion_name = b.name and t.taxarank in ('species', 'subspecies')"
                + " and b.name = '" + sqlText(bioregionName) + "' order by t.subfamily, t.genus, t.species, t.subspecies"
        );
    }

    private static NamedQuery getBioregionSpeciesListRangeSummary(String bioregionName) {
        NamedQuery query = null;

        Bioregion bioregion = BioregionMgr.getBioregion(bioregionName);
        if (bioregion == null) {
            s_log.warn("getBioregionSpeciesListRangeSummary() bioregion not found:" + bioregionName);
            return null;
        }
        query = bioregionSpeciesListRangeSummary(bioregionName);
        s_log.debug("getNamedQuery() bioregion:" + bioregion + " query:" + query);
        return query;
    }

    public static NamedQuery bioregionSpeciesListRangeSummary(String bioregionName) {
        return new NamedQuery(
                "bioregionSpeciesListRangeSummary"
                , bioregionName
                , FileUtil.makeReportName("BioregionSpeciesListRangeSummary" + bioregionName)
                , "How many species and subspecies on this bioregion's species list have each status, and how many"
                  + " of them are flagged introduced and endemic (1 = yes). The totals of the Species List with Range Data."
                , new String[] {"Names", "Status", "Introduced", "Endemic"}
                , "select count(*), t.status, bt.is_introduced, bt.is_endemic from bioregion b, bioregion_taxon bt, taxon t "
                + " where b.name = bt.bioregion_name and bt.taxon_name = t.taxon_name and t.taxarank in ('species', 'subspecies') "
                + " and b.name = '" + sqlText(bioregionName) + "' group by t.status, bt.is_introduced, bt.is_endemic order by count(*) desc"
        );
    }

}


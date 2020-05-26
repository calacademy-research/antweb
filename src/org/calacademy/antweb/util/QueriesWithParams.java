package org.calacademy.antweb.util;

import java.util.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;


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

    private static NamedQuery getGeolocaleSpeciesListWithRangeData(String geolocaleName) {
        NamedQuery query = null;

        Geolocale geolocale = GeolocaleMgr.getGeolocale(geolocaleName);
        String georank = geolocale.getGeorank();
        query = new NamedQuery(
                "speciesListWithRangeData"
                , geolocaleName
                , FileUtil.makeReportName("SpeciesListWithRange" + geolocaleName)
                , "species list with range data (including is_introduced and is_endemic) for a given geolocale (region, subregion, country or adm1). Introduced and Endemic only calculated for valid taxa."
                , new String[] {"Subfamily", "Genus", "Species", "Subspecies", "Author Date", "Status", "Introduced", "Endemic", "Range"}
                , "select initcap(t.subfamily), initcap(t.genus), t.species, IFNULL(t.subspecies, ''), t.author_date, t.status, gt.is_introduced, gt.is_endemic, "
                  + " (select group_concat(' ', g2.name order by name) from geolocale_taxon gt2, geolocale g2 where gt2.geolocale_id = g2.id and g2.georank = '" + georank + "' and gt2.taxon_name = t.taxon_name order by g2.name) "
                  + " from taxon t, geolocale_taxon gt, geolocale g where t.taxon_name = gt.taxon_name and gt.geolocale_id = g.id and t.taxarank in ('species', 'subspecies') "
                  + " and g.georank = '" + georank + "' and g.name = '" + geolocaleName + "' order by t.subfamily, t.genus, t.species, t.subspecies"
        );

        A.log("getNamedQuery() geolocale:" + geolocale + " query:" + query);

        return query; // end getQueryWithParam()
    }


    private static NamedQuery getGeolocaleSpeciesListRangeSummary(String geolocaleName) {
        NamedQuery query = null;

        Geolocale geolocale = GeolocaleMgr.getGeolocale(geolocaleName);
        if (geolocale == null) {
            s_log.warn("getGeolocaleSpeciesListRangeSummary() geolocale not found:" + geolocaleName);
            return null;
        }
        String georank = geolocale.getGeorank();
        query = new NamedQuery(
                "speciesListRangeSummary"
                , geolocaleName
                , FileUtil.makeReportName("SpeciesListRangeSummary" + geolocaleName)
                , "Species List Range Summary for a given geolocale (region, subregion, country or adm1)."
                , new String[] {"Count", "Status", "Introduced", "Endemic"}
                , "select count(*), t.status, gt.is_introduced, gt.is_endemic from geolocale g, geolocale_taxon gt, taxon t "
                + " where g.id = gt.geolocale_id and gt.taxon_name = t.taxon_name and g.georank = '" + georank + "' and t.taxarank in ('species', 'subspecies') "
                + " and g.name = '" + geolocaleName + "' group by t.status, gt.is_introduced, gt.is_endemic"
        );

        A.log("getNamedQuery() geolocale:" + geolocale + " query:" + query);

        return query; // end getQueryWithParam()
    }

    private static NamedQuery getBioregionSpeciesListWithRangeData(String bioregionName) {
        NamedQuery query = null;

        Bioregion bioregion = BioregionMgr.getBioregion(bioregionName);
        query = new NamedQuery(
                "bioregionSpeciesListWithRangeData"
                , bioregionName
                , FileUtil.makeReportName("bioregionSpeciesListWithRange" + bioregionName)
                , "Bioregion species list with range data (including is_endemic and introduced) for a given bioregion."
                , new String[] {"Subfamily", "Genus", "Species", "Subspecies", "Author Date", "Status", "Introduced", "Endemic", "Range"}
                , "select initcap(t.subfamily), initcap(t.genus), t.species, IFNULL(t.subspecies, ''), t.author_date, t.status, bt.is_introduced, bt.is_endemic"
                + " , (select group_concat(' ', b2.name order by name) from bioregion_taxon bt2, bioregion b2 where bt2.bioregion_name = b2.name and bt2.taxon_name = t.taxon_name order by b2.name)"
                + " from taxon t, bioregion_taxon bt, bioregion b where t.taxon_name = bt.taxon_name and bt.bioregion_name = b.name and t.taxarank in ('species', 'subspecies')"
                + " and b.name = '" + bioregionName + "' order by t.subfamily, t.genus, t.species, t.subspecies"
        );

        A.log("getNamedQuery() bioregion:" + bioregion + " query:" + query);

        return query;
    }

    private static NamedQuery getBioregionSpeciesListRangeSummary(String bioregionName) {
        NamedQuery query = null;

        Bioregion bioregion = BioregionMgr.getBioregion(bioregionName);
        if (bioregion == null) {
            s_log.warn("getBioregionSpeciesListRangeSummary() bioregion not found:" + bioregionName);
            return null;
        }
        query = new NamedQuery(
                "bioregionSpeciesListRangeSummary"
                , bioregionName
                , FileUtil.makeReportName("BioregionSpeciesListRangeSummary" + bioregionName)
                , "Bioregion Species List Range Summary for a given bioregion."
                , new String[] {"Count", "Status", "Introduced", "Endemic"}
                , "select count(*), t.status, bt.is_introduced, bt.is_endemic from bioregion b, bioregion_taxon bt, taxon t "
                + " where b.name = bt.bioregion_name and bt.taxon_name = t.taxon_name and t.taxarank in ('species', 'subspecies') "
                + " and b.name = '" + bioregionName + "' group by t.status, bt.is_introduced, bt.is_endemic"
        );

        A.log("getNamedQuery() bioregion:" + bioregion + " query:" + query);

        return query;
    }

}


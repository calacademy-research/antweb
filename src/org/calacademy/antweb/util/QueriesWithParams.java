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

        if ("speciesListWithRangeData".equals(queryName)) {
            return getSpeciesListWithRangeData(param); // param is a geolocaleName
        }
        if ("speciesListRangeSummary".equals(queryName)) {
            return getSpeciesListRangeSummary(param); // param is a geolocaleName
        }
        return null;
    }

    private static NamedQuery getSpeciesListWithRangeData(String geolocaleName) {
        NamedQuery query = null;

        Geolocale geolocale = GeolocaleMgr.getGeolocale(geolocaleName);
        String georank = geolocale.getGeorank();
        query = new NamedQuery(
                "speciesListWithRangeData"
                , geolocaleName
                , FileUtil.makeReportName("SpeciesListWithRange" + geolocaleName)
                , "species list with range data (including is_introduced and is_endemic) for a given geolocale (region, subregion, country or adm1). Introduced and Endemic only calculated for valid taxa."
                , new String[] {"Subfamily", "Genus", "Species", "Subspecies", "Author Date", "Status", "Introduced", "Endemic", "Range"}
                //, "<th>Subfamily</th><th>Genus</th><th>Species</th><th>Subspecies</th><th>Author Date</th><th>Status</th><th>Introduced</th><th>Endemic</th><th>Range</th>"
                , "select initcap(t.subfamily), initcap(t.genus), t.species, IFNULL(t.subspecies, ''), t.author_date, t.status, gt.is_introduced, gt.is_endemic, "
                  + " (select group_concat(' ', g2.name order by name) from geolocale_taxon gt2, geolocale g2 where gt2.geolocale_id = g2.id and g2.georank = '" + georank + "' and gt2.taxon_name = t.taxon_name order by g2.name) "
                  + " from taxon t, geolocale_taxon gt, geolocale g where t.taxon_name = gt.taxon_name and gt.geolocale_id = g.id and t.rank in ('species', 'subspecies') "
                  + " and g.georank = '" + georank + "' and g.name = '" + geolocaleName + "' order by t.subfamily, t.genus, t.species, t.subspecies"
        );

        A.log("getNamedQuery() geolocale:" + geolocale + " query:" + query);

        return query; // end getQueryWithParam()
    }

    private static NamedQuery getSpeciesListRangeSummary(String geolocaleName) {
        NamedQuery query = null;

        Geolocale geolocale = GeolocaleMgr.getGeolocale(geolocaleName);
        if (geolocale == null) {
            s_log.warn("getSpeciesListRangeSummary() geolocale not found:" + geolocaleName);
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
                + " where g.id = gt.geolocale_id and gt.taxon_name = t.taxon_name and g.georank = '" + georank + "' and t.rank in ('species', 'subspecies') "
                + " and g.name = '" + geolocaleName + "' group by t.status, gt.is_introduced, gt.is_endemic"
        );

        A.log("getNamedQuery() geolocale:" + geolocale + " query:" + query);

        return query; // end getQueryWithParam()
    }

}

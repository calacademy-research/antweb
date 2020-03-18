package org.calacademy.antweb.util;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Date;

import javax.servlet.http.*;
import javax.servlet.*;

import java.sql.*;
import javax.sql.*;
import com.mchange.v2.c3p0.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;


public abstract class QueriesWithParams {

    private static final Log s_log = LogFactory.getLog(QueriesWithParams.class);


    public static NamedQuery getNamedQueryWithParam(String queryName, String stringParam) {

        ArrayList<NamedQuery> queries = new ArrayList<NamedQuery>();

        NamedQuery query = null;

        if ("speciesListWithRangeData".equals(queryName)) {
            query = new NamedQuery(
                    "speciesListWithRangeData"
                    , "species list with range data for given geolocale."
                    , "<th>Subfamily</th><th>Genus</th><th>Species</th><th>Subspecies</th><th>Author Date</th><th>Status</th><th>Introduced</th><th>Endemic</th><th>Range</th>"
                    , "select t.subfamily, t.genus, t.species, IFNULL(t.subspecies, ''), t.author_date, t.status, gt.is_introduced, gt.is_endemic, "
                      + " (select group_concat(g2.name) from geolocale_taxon gt2, geolocale g2 where gt2.geolocale_id = g2.id and g2.georank = 'country' and gt2.taxon_name = t.taxon_name order by g2.name) "
                      + " from taxon t, geolocale_taxon gt, geolocale g where t.taxon_name = gt.taxon_name and gt.geolocale_id = g.id and t.rank in ('species', 'subspecies') and g.georank = 'country' and g.name = '" + stringParam + "' order by t.subfamily, t.genus, t.species, t.subspecies"
            );
        }

        A.log("getNamedQUery() queryName:" + queryName + " stringParam:" + stringParam + " query:" + query);

        return query; // end getQueryWithParam()
    }

}


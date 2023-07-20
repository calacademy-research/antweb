package org.calacademy.antweb.search;

import org.calacademy.antweb.util.*;

import java.util.*;
import java.io.Serializable;
import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
/** Class Search does the standard search for taxa */
public class Search extends GenericSearch implements Serializable {

    private static final Log s_log = LogFactory.getLog(Search.class);

    protected ArrayList<ResultItem> createInitialResults() throws SearchException {
        String theQuery = null;
        String genus = null;
        String species = null;

        if (name == null || name.equals("")) {
            return new ArrayList<>();
        }

        // if this query has more than one term assume the first is the genus and 
        // the rest is the species
        //
        if (name.contains(" ")) {
            //StringTokenizer toke = new StringTokenizer(name, " ");
            //genus = toke.nextToken();
            //species = toke.nextToken();
            int firstSpace = name.indexOf(" ");
            genus = name.substring(0, firstSpace);
            species = name.substring(firstSpace+1);
        }

        theQuery = "select taxon.taxon_name, taxon.subfamily, taxon.genus, taxon.species, sp.type, sp.code, sp.toc, "
            + " count(image.id) as imagecount, taxon.valid, sp.country, sp.adm1, sp.localityname, sp.caste, sp.medium, sp.specimennotes "
            + " from taxon left outer join specimen as sp on taxon.taxon_name = sp.taxon_name left outer join image on "
            + " sp.code = image.image_of_id where  (";

        if (genus != null && species != null) {
            theQuery += getSearchString("taxon.genus", "equals", genus)
                + " and "
                + getSearchString("taxon.species", "equals", species)
                + ") ";
        } else {
            theQuery += getSearchString("taxon.subfamily", searchType, name)
                + " or " + getSearchString("taxon.genus", searchType, name)
                + " or " + getSearchString("taxon.species", searchType, name)
                + " or sp.code = '" + name + "' " + ") ";
        }
        theQuery += " group by taxon.taxon_name, taxon.subfamily, taxon.genus, taxon.species, sp.type, sp.code, taxon.valid, sp.toc, sp.country, sp.adm1, sp.localityname, sp.caste, sp.medium, sp.specimennotes ";

        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = connection.createStatement();
            rset = stmt.executeQuery(theQuery);

//          s_log.info("in search, query is :" + theQuery);
            ArrayList<ResultItem> list = getListFromRset(GenericSearch.SEARCH, rset, null, theQuery);
            return list;
        } catch (SQLException e) {
            s_log.error("createInitialResultSet() theQuery:" + theQuery + " e:" + e);
        } finally {
            DBUtil.close(stmt, rset, this, "createInitialResults()");
        }
        return null;
    }

/* This is code, easy to cut and past, to be used to fix other methods improperly handling.
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = connection.createStatement();
            rset = stmt.executeQuery(theQuery);

        } finally {
            DBUtil.close(stmt, rset, this, "createInitialResults()");
        }
*/
}

package org.calacademy.antweb;

import java.util.*;
import java.io.Serializable;
import java.sql.*;

import org.calacademy.antweb.geolocale.LocalityOverview;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
        
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

/** Class Subfamily keeps track of the information about a specific taxon */
public class Subfamily extends Family implements Serializable {

    private static Log s_log = LogFactory.getLog(Subfamily.class);

    public static boolean isValidAntSubfamily(String subfamily) {
      // for the test, strip any parenthesis...
      subfamily = Formatter.stripString(subfamily, "(");
      subfamily = Formatter.stripString(subfamily, ")");
      
      ArrayList<Taxon> subfamilies = TaxonMgr.getSubfamilies();
      for (Taxon aSubfamily : subfamilies) {
        if (aSubfamily.isValid() && aSubfamily.isAnt() && 
          aSubfamily.getSubfamily().equals(subfamily)
        ) {
          return true;
        }
      }
      return false;
    }

    public String getName() { 
        return getSubfamily(); 
    }
    
    public String getNextRank() {
        return "Genera";
    }


    protected String getThisWhereClause() {
      return getThisWhereClause("");
    }
    protected String getThisWhereClause(String table) {
        if (!"".equals(table)) table = table + ".";    
        String clause = " and " + table + "subfamily = '" + AntFormatter.escapeQuotes(subfamily) + "'" ;
        return clause;
    }

    public void setChildren(Connection connection, Overview overview, StatusSet statusSet, boolean getChildImages, boolean getChildMaps, String caste, boolean global, String subgenus) throws SQLException {

        String fetchChildrenClause = "where 1 = 1";
        if (!global && overview != null) fetchChildrenClause = overview.getFetchChildrenClause();

        // We only use the subgenus clause when rank is genus.

        ArrayList theseChildren = new ArrayList();
        String query;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            query = "select distinct taxon.genus from taxon"
                    + fetchChildrenClause
                    + " and taxon.subfamily = '" + AntFormatter.escapeQuotes(subfamily) + "'"
                    + " and taxon.genus != '' "
                    + " and taxarank = 'genus' "
                    + statusSet.getAndCriteria()
            ;

            //A.log("setChildren(5) overview:" + overview + " query:" + query);

            stmt = connection.createStatement();
            rset = stmt.executeQuery(query);

            String genus = null;
            Taxon child = null;
            String mapParams = null;
            int i = 0;
            while (rset.next()) {
                ++i;

                child = (new TaxonDb(connection)).getGenus(subfamily, rset.getString("genus"));

                if (getChildImages) {
                    child.setImages(connection, overview, caste);
                }// else {
                //    child.setHasImages(overview);
                //}

                if ((getChildMaps) && (i < Taxon.getMaxSafeChildrenCount())) {
                    if (overview instanceof LocalityOverview)
                        child.setMap(new Map(child, (LocalityOverview) overview, connection));
                }

                //A.log("setChildren() overview:" + overview + " child:" + child.getTaxonName() + " + this:" + this);                                                
                child.initTaxonSet(connection, overview);
                child.generateBrowserParams(overview);

                theseChildren.add(child);
            }
        } catch (SQLException e) {
            s_log.warn("setChildren() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, this, "setChildren() overview:" + overview);
        }        this.children = theseChildren;
    }
    
    public String getTaxonomicBrowserParams() {
        return "subfamily=" + this.getSubfamily() + "&rank=subfamily";
    }
    
}

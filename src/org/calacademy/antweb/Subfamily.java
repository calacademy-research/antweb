package org.calacademy.antweb;

import java.util.*;
import java.io.Serializable;
import java.sql.*;

import org.calacademy.antweb.util.*;
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


/* June 25, 2013.  Performance concern.  We have add the second line of the sql query and the rank = subfamily.
   This restricts the number or rows and excludes records.  Without the rank criteria, the
   parent_taxon_id could be the parent of a genera.  Sloppy.  Why loop though all the 
   distinct rows below.  Only need one.  */        

    public void setTaxonomicInfo(String project) throws SQLException {
        s_log.warn("setTaxonomicInfo(project) is deprecated");
        setTaxonomicInfo();
    }
    
    public void setTaxonomicInfo() throws SQLException {
		String theQuery = null;
		theQuery = "select distinct taxon.kingdom_name, taxon.phylum_name, taxon.class_name, taxon.order_name, taxon.family " 
			+ " from taxon " 
			//+ " , proj_taxon "
			 + " where " 
			//+ " taxon.taxon_name = proj_taxon.taxon_name and ";
		 + " subfamily = '" + AntFormatter.escapeQuotes(subfamily) + "'"
		 + " and rank = 'subfamily'";
		 // and status = 'valid' ";

		if (AntwebProps.isDevMode()) {
		  A.log("setTaxonomicInfo(String) family:" + family + " subfamily:" + subfamily + " query:" + theQuery);
		  //AntwebUtil.logStackTrace();
		}
		
		//if ((project != null) && (!(project.equals("")))) {
		//	theQuery = theQuery + " and proj_taxon.project_name = '" + project + "'";
		//}
		
		TaxonDb taxonDb = new TaxonDb(connection);
		taxonDb.setTaxonomicInfo(theQuery, this);
    }

/*
    public void setTaxonomicInfo(int geolocaleId)  throws SQLException {

		String theQuery = null;

		theQuery = "select distinct taxon.kingdom_name, taxon.phylum_name, taxon.class_name, taxon.order_name, taxon.family " 
			+ " from taxon, geolocale_taxon where taxon.taxon_name = geolocale_taxon.taxon_name and ";
		theQuery += " taxon.subfamily='" + AntFormatter.escapeQuotes(subfamily) + "'"
		 + " and taxon.rank = 'subfamily'";
		 // and status = 'valid' ";

		if (AntwebProps.isDevMode()) {
		  s_log.warn("setTaxonomicInfo(int) family:" + family + " subfamily:" + subfamily + " query:" + theQuery);
		  //AntwebUtil.logStackTrace();
		}
		
		if (geolocaleId > 0) {
			theQuery = theQuery + " and geolocale_taxon.geolocale_id = " + geolocaleId;
		}

		GeolocaleTaxonDb taxonSetDb = new GeolocaleTaxonDb(connection);
		taxonSetDb.setTaxonomicInfo(theQuery, this);
    }
*/

    protected String getThisWhereClause() {
      return getThisWhereClause("");
    }
    protected String getThisWhereClause(String table) {
        if (!"".equals(table)) table = table + ".";    
        String clause = " and " + table + "subfamily = '" + AntFormatter.escapeQuotes(subfamily) + "'" ;
        return clause;
    }

    public void setChildren(Overview overview, StatusSet statusSet, boolean getChildImages, boolean getChildMaps, String caste, boolean global, String subgenus) throws SQLException {

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
                + " and rank = 'genus' "
                + statusSet.getAndCriteria()
                ;
            
            A.log("setChildren(5) overview:" + overview + " query:" + query);

            stmt = connection.createStatement();
            rset = stmt.executeQuery(query);

            String genus = null;
            Taxon child = null;
            String mapParams = null;
            int i = 0;
            while (rset.next()) {
                ++i;
                genus = rset.getString("genus");
                child = new Genus();
                child.setSubfamily(subfamily);
                child.setGenus(genus);
                child.setRank("genus");
                child.setConnection(connection);
                child.init(); // added Oct 3, 2012 Mark
                if (getChildImages) { 
                    child.setImages(overview, caste);
                }// else {
                //    child.setHasImages(overview);
                //}
            
                if ((getChildMaps) && (i < Taxon.getMaxSafeChildrenCount())) {
                  if (overview instanceof LocalityOverview) 
                  child.setMap(new Map(child, (LocalityOverview) overview, connection));
                }
                
                //A.log("setChildren() overview:" + overview + " child:" + child.getTaxonName() + " + this:" + this);                                                
                child.initTaxonSet(overview);                
                child.generateBrowserParams(overview);
                
                child.setConnection(null);                
                theseChildren.add(child);                
            }
        } finally {
            DBUtil.close(stmt, rset, this, "setChildren() overview:" + overview);
        }        this.children = theseChildren;
    }
    
    public String getTaxonomicBrowserParams() {
        return "subfamily=" + this.getSubfamily() + "&rank=subfamily";
    }
    
}

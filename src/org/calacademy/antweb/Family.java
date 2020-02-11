
package org.calacademy.antweb;

import java.util.*;
import java.io.Serializable;
import java.sql.*;


import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

/** Class Family keeps track of the information about a specific taxon */
public class Family extends Taxon implements Serializable {

    private static Log s_log = LogFactory.getLog(Family.class);
    
    public static String FORMICIDAE = "formicidae";
    public static String ANT_FAMILY = "formicidae";
    
    public String getName() { 
        return getFamily(); 
    }
        
    public String getNextRank() {
        return "Subfamilies";
    }

    public void setTaxonomicInfo(String project) throws SQLException {
        s_log.warn("setTaxonomicInfo(project) is deprecated");
        setTaxonomicInfo();
    }
    
    public void setTaxonomicInfo() throws SQLException {
        //s_log.warn("setTaxonomicInfo(" + project + ") family:" + getFamily());   
        String theQuery = null;

		theQuery = "select distinct taxon.kingdom_name, taxon.phylum_name, taxon.class_name, taxon.order_name " 
		  + " from taxon"
		  // + ", proj_taxon"
		  + " where " 
		  // + " taxon.taxon_name = proj_taxon.taxon_name and " 
		  + " family='" + AntFormatter.escapeQuotes(family) + "' " 
		  //+ " and status = 'valid' "
		  + " and rank = 'family'"
		  ;

		//if ((project != null) && (!(project.equals("")))) {
		//	theQuery = theQuery + " and proj_taxon.project_name = '" + project + "'";
		//}
                
		TaxonDb taxonDb = new TaxonDb(connection);
		taxonDb.setTaxonomicInfo(theQuery, this);
    }                

    public void setChildren(Overview overview, StatusSet statusSet, boolean getChildImages, boolean getChildMaps, String caste, boolean global) throws SQLException {

        String fetchChildrenClause = "where 1 = 1";
        if (!global && overview != null) fetchChildrenClause = overview.getFetchChildrenClause();
        
        ArrayList theseChildren = new ArrayList();
        Statement stmt = null;
        ResultSet rset = null;
        String theQuery;
        try {
          theQuery =
            "select distinct taxon.subfamily from taxon" // proj_taxon where " 
                + fetchChildrenClause                   
                + " and taxon.subfamily != '' "
                + " and rank = 'subfamily' "
                + " and taxon.family = '" + getFamily() + "'"   // added by Mark to avoid non-ants on formicidae page
                + statusSet.getAndCriteria()
                + " order by taxon.subfamily";

            stmt = DBUtil.getStatement(getConnection(), "setChildren()"); 
            rset = stmt.executeQuery(theQuery);

            String subfamily = null;
            Subfamily child = null;
            String theParams = null;
            
            while (rset.next()) {
                child = new Subfamily();
                child.setRank(Rank.SUBFAMILY);
                child.setSubfamily(rset.getString("subfamily"));
                child.setConnection(connection);
                
                child.init();
                
                if (getChildImages) {
                    A.log("setChildren() setImages(" + overview + ")");   
                    child.setImages(overview, caste);
                }// else {
                //    child.setHasImages(overview);
                //}    
                
                A.log("setChildren(5) overview:" + overview + " child:" + child.getTaxonName() + " getChildMaps:" + getChildMaps + " getChildImages:" + getChildImages);                                
                
                child.initTaxonSet(overview);
                child.generateBrowserParams(overview);
                
                child.setConnection(null);
                theseChildren.add(child);
            }

        } finally {
            DBUtil.close(stmt, rset, this, "setChildren()");
        }
        this.children = theseChildren;
        
        //A.log("setChildren() children.size:" + theseChildren.size() + " theQuery:" + theQuery);
    }
    
    public String getTaxonomicBrowserParams() {
        return "family=" + this.getFamily() + "&rank=family";
    }    

}

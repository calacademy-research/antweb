
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

/** ClassOrder keeps track of the information about a specific taxon */
public class Order extends Taxon implements Serializable {

    private static Log s_log = LogFactory.getLog(Order.class);
    
    public String getNextRank() {
        return "Families";
    }

    public void setTaxonomicInfo(String project) throws SQLException {
        s_log.warn("setTaxonomicInfo(project) is deprecated");
        setTaxonomicInfo();
    }
    
    public void setTaxonomicInfo() throws SQLException {
        setOrderName(name); 
        //s_log.warn("setTaxonomicInfo(" + project + ") family:" + getFamily());   

		String theQuery = "select distinct taxon.kingdom_name, taxon.phylum_name, taxon.class_name " 
		  + " from taxon" 
		  //+ " , proj_taxon" 
		  + " where " 
		  //+ " taxon.taxon_name = proj_taxon.taxon_name and " 
		  + " taxon.order_name='" + AntFormatter.escapeQuotes(name) + "' " 
		  + " and taxon.status = 'valid'";

		//if ((project != null) && (!(project.equals("")))) {
		//	theQuery = theQuery + " and proj_taxon.project_name = '" + project + "'";
		//}
                
		TaxonDb taxonDb = new TaxonDb(connection);
		taxonDb.setTaxonomicInfo(theQuery, this);
    }
    
/*    
    public void setChildren(Project project) throws SQLException {
        setChildren(project, false, false); 
    }

    public void setChildren(Project projectObj, boolean getChildImages, boolean getChildMaps) throws SQLException {

        String project = projectObj.getName();
        
        ArrayList theseChildren = new ArrayList();

        String theQuery;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            if ((project != null) && !(project.equals(""))) {
                theQuery =
                    "select distinct taxon.family from taxon, proj_taxon where taxon.family != '' "
                        + " and taxon.taxon_name = proj_taxon.taxon_name "
                        + " and taxon.status = 'valid'"
                        + " and taxon.order_name = '" + getName() + "'"
                        + " and proj_taxon.project_name = '" + project + "'" 
                        + " order by taxon.family";
            } else {
                theQuery =
                    "select distinct taxon.family from taxon " 
                    + " where taxon.family != '' " 
                    + " and taxon.status = 'valid' " 
                    + " and taxon.order_name = '" + getName() + "'"
                    + " order by taxon.family";
            }
            
            //s_log.info("setChildren() theQuery:" + theQuery);

            stmt = connection.createStatement();
            rset = stmt.executeQuery(theQuery);

            String family = null;
            Family child = null;
            String theParams = null;
            
            while (rset.next()) {
                family = rset.getString("family");
            //    System.out.println("looking at " + family);
                child = new Family();
                child.setFamily(family);
                theParams = "family=" + family + "&rank=family";
                if ((project != null) && (project.length() > 0)) {
                    theParams = theParams + "&project=" + project;
                }
                child.setBrowserParams(theParams);
                
                child.setRank("family");
                child.setConnection(connection);
                
                //if (getChildImages) {
                //    child.setImages(projectObj, false);
                //}
                
                child.setConnection(null);
                theseChildren.add(child);
            }

        } finally {
            DBUtil.close(stmt, rset, this, "setChildren()");
        }
        this.children = theseChildren;
    }
*/
}

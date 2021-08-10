
package org.calacademy.antweb;

import java.io.Serializable;
import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

/** Class Kingdom keeps track of the information about a specific taxon */
public class Kingdom extends Taxon implements Serializable {

    private static Log s_log = LogFactory.getLog(Kingdom.class);
    
    public String getNextRank() {
        return "Phyla";
    }

    public void setTaxonomicInfo(String project) throws SQLException {
        s_log.warn("setTaxonomicInfo(project) is deprecated");
        setTaxonomicInfo();
    }
    
    public void setTaxonomicInfo() throws SQLException {
        setKingdomName(name);    

        A.log("setTaxonomicInfo() family:" + getFamily());   

		String theQuery = "select distinct taxon.kingdom_name " 
		  + " from taxon" 
		 // + , proj_taxon" 
		  + " where " 
		 // + " taxon.taxon_name = proj_taxon.taxon_name and " 
		  + " taxon.kingdom_name='" + AntFormatter.escapeQuotes(name) + "' "
		  + " and taxon.status = 'valid'";

		//if ((project != null) && (!(project.equals("")))) {
		//	theQuery = theQuery + " and proj_taxon.project_name = '" + project + "'";
		//}

		A.log("setTaxonomicInfo() name:" + name + " query:" + theQuery);

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
        Statement stmt = null;
        ResultSet rset = null;
        try {

            String theQuery;

            if ((project != null) && !(project.equals(""))) {
                theQuery =
                    "select distinct taxon.phylum_name from taxon, proj_taxon where taxon.phylum_name != '' "
                        + " and taxon.taxon_name = proj_taxon.taxon_name "
                        + " and taxon.status = 'valid' "
                        + " and taxon.kingdom_name = '" + getName() + "'"
                        + " and proj_taxon.project_name = '" + project + "'" 
                        + " order by taxon.phylum_name";
            } else {
                theQuery =
                    "select distinct taxon.phylum_name from taxon " 
                    + " where taxon.phylum_name != '' " 
                    + " and taxon.status = 'valid' "
                    + " and taxon.kingdom_name = '" + getName() + "'"
                    + " order by taxon.phylum_name";
            }
            //s_log.info("setChildren() theQuery:" + theQuery);

            stmt = connection.createStatement();
            rset = stmt.executeQuery(theQuery);

            String phylumName = null;
            Family child = null;
            String theParams = null;

            while (rset.next()) {
                phylumName = rset.getString("phylum_name");
            //    System.out.println("looking at " + thisName);
                child = new Family();
                child.setFamily(phylumName);
                theParams = "phylum=" + phylumName + "&rank=phylum";
                if ((project != null) && (project.length() > 0)) {
                    theParams = theParams + "&project=" + project;
                }
                child.setBrowserParams(theParams);
                child.setRank("phylum");
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

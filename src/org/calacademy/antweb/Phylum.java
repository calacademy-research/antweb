
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

/** Class Phylum keeps track of the information about a specific taxon */
public class Phylum extends Taxon implements Serializable {

    private static Log s_log = LogFactory.getLog(Phylum.class);
    
    public String getNextRank() {
        return "Classes";
    }

    public void setTaxonomicInfo(String project) throws SQLException {
        s_log.warn("setTaxonomicInfo(project) is deprecated");
        setTaxonomicInfo();
    }
    
    public void setTaxonomicInfo() throws SQLException {
        setPhylumName(name);
        //s_log.warn("setTaxonomicInfo(" + project + ") family:" + getFamily());   

		String theQuery = "select distinct taxon.kingdom_name, taxon.phylum_name " 
		  + " from taxon" 
		  + " , proj_taxon "
		  + " where " 
		  // +taxon.taxon_name = proj_taxon.taxon_name and " 
		  + " taxon.phylum_name='" + AntFormatter.escapeQuotes(name) + "' " 
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
                    "select distinct taxon.class_name from taxon, proj_taxon where taxon.class_name != '' "
                        + " and taxon.taxon_name = proj_taxon.taxon_name "
                        + " and taxon.status = 'valid' "
                        + " and taxon.phylum_name = '" + getName() + "'"
                        + " and proj_taxon.project_name = '" + project + "'" 
                        + " order by taxon.class_name";
            } else {
                theQuery =
                    "select distinct taxon.class_name from taxon " 
                    + " where taxon.class_name != '' " 
                    + " and taxon.status = 'valid' " 
                    + " and taxon.phylum_name = '" + getName() + "'"
                    + " order by taxon.class_name";
            }
            
            //s_log.info("setChildren() theQuery:" + theQuery);

            stmt = connection.createStatement();
            rset = stmt.executeQuery(theQuery);

            String className = null;
            Family child = null;
            String theParams = null;
            
            while (rset.next()) {
                className = rset.getString("class_name");
            //    System.out.println("looking at " + thisName);
                child = new Family();
                child.setClassName(className);
                theParams = "class=" + className + "&rank=class";
                if ((project != null) && (project.length() > 0)) {
                    theParams = theParams + "&project=" + project;
                }
                child.setBrowserParams(theParams);
                child.setRank("class");
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

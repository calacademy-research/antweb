
package org.calacademy.antweb;

import java.util.*;
import java.io.Serializable;
import java.sql.*;


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


    public void setChildren(Connection connection, Overview overview, StatusSet statusSet, boolean getChildImages, boolean getChildMaps, String caste, boolean global, String subgenus)
            throws SQLException, AntwebException {

        String fetchChildrenClause = "where 1 = 1";
        if (!global && overview != null) fetchChildrenClause = overview.getFetchChildrenClause();

        // We only use the subgenus clause when rank is genus.

        ArrayList theseChildren = new ArrayList();
        Statement stmt = null;
        ResultSet rset = null;
        String query = null;
        try {
          query =
            "select distinct taxon.subfamily from taxon" // proj_taxon where " 
                + fetchChildrenClause                   
                + " and taxon.subfamily != '' "
                + " and taxarank = 'subfamily' "
                + " and taxon.family = '" + getFamily() + "'"   // added by Mark to avoid non-ants on formicidae page
                + statusSet.getAndCriteria()
                + " order by taxon.subfamily";

            stmt = DBUtil.getStatement(connection, "setChildren()");
            rset = stmt.executeQuery(query);

            //A.log("setChildren() query:" + theQuery);

            Subfamily child = null;
            String theParams = null;
            
            while (rset.next()) {
                String subfamily = rset.getString("subfamily");
                child = new TaxonDb(connection).getSubfamily(subfamily);

                if (getChildImages) {
                    //A.log("setChildren() setImages(" + overview + ")");
                    child.setImages(connection, overview, caste);
                }
                
                //A.log("setChildren(5) overview:" + overview + " child:" + child.getTaxonName() + " getChildMaps:" + getChildMaps + " getChildImages:" + getChildImages);
                
                child.initTaxonSet(connection, overview);
                child.generateBrowserParams(overview);
                
                theseChildren.add(child);
            }
        } catch (SQLException e) {
            s_log.info("setChildren() query:" + query + " e:" + e);
            throw e;
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

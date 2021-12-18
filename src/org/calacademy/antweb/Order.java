
package org.calacademy.antweb;

import java.io.Serializable;
import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.home.*;

/** ClassOrder keeps track of the information about a specific taxon */
public class Order extends Taxon implements Serializable {

    private static Log s_log = LogFactory.getLog(Order.class);
    
    public String getNextRank() {
        return "Families";
    }

    /*
    public void setTaxonomicInfo(Connection connection) throws SQLException {
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
*/
}

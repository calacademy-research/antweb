
package org.calacademy.antweb;

import java.io.Serializable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/** Class Kingdom keeps track of the information about a specific taxon */
public class Kingdom extends Taxon implements Serializable {

    private static final Log s_log = LogFactory.getLog(Kingdom.class);
    
    public String getNextRank() {
        return "Phyla";
    }

    /*
    public void setTaxonomicInfo(Connection connection) throws SQLException {
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
*/
}

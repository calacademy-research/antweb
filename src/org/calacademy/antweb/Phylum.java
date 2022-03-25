
package org.calacademy.antweb;

import java.io.Serializable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/** Class Phylum keeps track of the information about a specific taxon */
public class Phylum extends Taxon implements Serializable {

    private static final Log s_log = LogFactory.getLog(Phylum.class);
    
    public String getNextRank() {
        return "Classes";
    }

    /*
    public void setTaxonomicInfo(Connection connection) throws SQLException {
        setPhylumName(name);
        //s_log.warn("setTaxonomicInfo(" + project + ") family:" + getFamily());   

		String theQuery = "select distinct taxon.kingdom_name, taxon.phylum_name " 
		  + " from taxon" 
		  + " , proj_taxon "
		  + " where " 
		  // +taxon.taxon_name = proj_taxon.taxon_name and " 
		  + " taxon.phylum_name='" + AntFormatter.escapeQuotes(name) + "' " 
		  + " and taxon.status = 'valid'";

		TaxonDb taxonDb = new TaxonDb(connection);
		taxonDb.setTaxonomicInfo(theQuery, this);
    }
*/
}

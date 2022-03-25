
package org.calacademy.antweb;

import java.io.Serializable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/** Class Class keeps track of the information about a specific taxon */
public class Class extends Taxon implements Serializable {

    private static final Log s_log = LogFactory.getLog(Class.class);
    
    public String getNextRank() {
        return "Orders";
    }

    /*
    public void setTaxonomicInfo(String project) throws SQLException {
        s_log.warn("setTaxonomicInfo(project) is deprecated");
        setTaxonomicInfo();
    }
*/

/*
    public void setTaxonomicInfo(Connection connection) throws SQLException {
        setClassName(name);    

	    String theQuery = "select distinct taxon.kingdom_name, taxon.phylum_name " 
		  + " from taxon" 
		  //+ ", proj_taxon " 
		  + " where " 
		  //+ " taxon.taxon_name = proj_taxon.taxon_name and " 
		  + " taxon.class_name='" + AntFormatter.escapeQuotes(name) + "' " 
		  //+ " and taxon.valid = 1";
		  + " and taxon.status = 'valid'";
	    //if ((project != null) && (!(project.equals("")))) {
		//	theQuery = theQuery + " and proj_taxon.project_name = '" + project + "'";
 	    //}
                
		TaxonDb taxonDb = new TaxonDb(connection);
		taxonDb.setTaxonomicInfo(theQuery, this);
    }
*/
}

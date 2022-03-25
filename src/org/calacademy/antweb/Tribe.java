package org.calacademy.antweb;

import java.io.Serializable;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class Tribe extends Genus implements Serializable {

    private static final Log s_log = LogFactory.getLog(Tribe.class);
    
    public String getNextRank() {
        return "Genus";
    }

    public String getName() { 
        return getTribe(); 
    }
    /*
    public void setTaxonomicInfo(Connection connection) throws SQLException {
        setTribe(name);
        
        String subfamilyClause = "";
        if ((subfamily != null) && (!subfamily.equals("null"))) {
          subfamilyClause = " and taxon.subfamily = '" + AntFormatter.escapeQuotes(subfamily) + "' ";
        }

        String theQuery = null;
        theQuery = "select distinct taxon.taxarank, taxon.kingdom_name, taxon.phylum_name, taxon.class_name, taxon.order_name " 
		  + ", taxon.family, taxon.subfamily from taxon"
		  // + ", proj_taxon "
		  + " where 1 = 1 " 
		  // + " taxon.taxon_name = proj_taxon.taxon_name and "
		  + subfamilyClause
		  + " and taxarank = 'tribe'"
		  + " and taxon.genus='" + AntFormatter.escapeQuotes(genus) + "'"; // and status = "valid"';

		//if ((project != null) && (!(project.equals("")))) {
		//	theQuery = theQuery + " and proj_taxon.project_name = '" + project + "'";
		//}

		TaxonDb taxonDb = new TaxonDb(connection);
		taxonDb.setTaxonomicInfo(theQuery, this);
    }
    */

    public String getTaxonomicBrowserParams() {
        String theParams = "rank=tribe&name=" + this.getName();
        theParams += "&subfamily=" + this.getSubfamily();
        return theParams;
    }
}

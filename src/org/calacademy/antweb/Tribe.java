package org.calacademy.antweb;

import java.util.*;
import java.io.Serializable;
import java.sql.*;
 
import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

public class Tribe extends Genus implements Serializable {

    private static Log s_log = LogFactory.getLog(Tribe.class);
    
    public String getNextRank() {
        return "Genus";
    }

    public String getName() { 
        return getTribe(); 
    }
    
    public void setTaxonomicInfo(String project) throws SQLException {
        s_log.warn("setTaxonomicInfo(project) is deprecated");
        setTaxonomicInfo();
    }
    
    public void setTaxonomicInfo() throws SQLException {
        setTribe(name);
        
        String subfamilyClause = "";
        if ((subfamily != null) && (!subfamily.equals("null"))) {
          subfamilyClause = " and taxon.subfamily = '" + AntFormatter.escapeQuotes(subfamily) + "' ";
        }

        String theQuery = null;
        theQuery = "select distinct taxon.rank, taxon.kingdom_name, taxon.phylum_name, taxon.class_name, taxon.order_name " 
		  + ", taxon.family, taxon.subfamily from taxon"
		  // + ", proj_taxon "
		  + " where 1 = 1 " 
		  // + " taxon.taxon_name = proj_taxon.taxon_name and "
		  + subfamilyClause
		  + " and rank = 'tribe'"
		  + " and taxon.genus='" + AntFormatter.escapeQuotes(genus) + "'"; // and status = "valid"';

		//if ((project != null) && (!(project.equals("")))) {
		//	theQuery = theQuery + " and proj_taxon.project_name = '" + project + "'";
		//}

		TaxonDb taxonDb = new TaxonDb(connection);
		taxonDb.setTaxonomicInfo(theQuery, this);
    }
    
    public String getTaxonomicBrowserParams() {
        String theParams = "rank=tribe&name=" + this.getName();
        theParams += "&subfamily=" + this.getSubfamily();
        return theParams;
    }
}

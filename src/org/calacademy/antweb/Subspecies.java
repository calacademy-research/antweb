package org.calacademy.antweb; 

import java.util.*;
import java.io.Serializable;
import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.home.*;

public final class Subspecies extends Species implements Serializable {

    private static Log s_log = LogFactory.getLog(Subspecies.class);

    public String getNextRank() {
        return "Specimens";
    }

    public String getName() { 
        return getSubspecies(); 
    }

    /*
    public void setTaxonomicInfo(Connection connection) throws SQLException {
        String theQuery = null;
        
		String subfamilyClaus = "";
		if (subfamily != null) subfamilyClaus = " subfamily = '" + AntFormatter.escapeQuotes(subfamily) + "' and ";

		theQuery = "select distinct taxon.kingdom_name, taxon.phylum_name, taxon.class_name, taxon.order_name " 
		  + ", taxon.family, taxon.subfamily, taxon.tribe, taxon.subgenus, taxon.speciesgroup, taxon.species " 
		  + ", taxon.type, taxon.status "
		  + " from taxon " //, proj_taxon " 
		 // + " where taxon.taxon_name = proj_taxon.taxon_name " 
		  + " where " 
		  + subfamilyClaus
		  + " genus ='" + AntFormatter.escapeQuotes(genus) + "'"
		  + " and species ='" + AntFormatter.escapeQuotes(species) + "'" 
		  + " and subspecies ='" + AntFormatter.escapeQuotes(subspecies) + "'" 
		  + " and taxarank = 'subspecies'";

		// theQuery += " and proj_taxon.project_name = '" + project + "'";

		A.log("setTaxonomicInfo() theQuery:" + theQuery);

		TaxonDb taxonDb = new TaxonDb(connection);
		taxonDb.setTaxonomicInfo(theQuery, this);
	}
    */

    public String getSeeAlsoSiblingSubspecies(Connection connection) throws SQLException {
      // Used by SetSeeAlso()    
        String siblingSubspecies = "";
        String query = "select taxon.taxon_name " 
            + " from taxon " 
            + " where taxon.subfamily = '" + getSubfamily() + "'"
            + "   and taxon.genus = '" + getGenus() + "'"
            + "   and taxon.species = '" + getSpecies() + "'"
            + "   and taxon.taxarank = 'species'"
          ;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(connection, "getSeeAlsoSiblingSubspecies()");
            rset = stmt.executeQuery(query);
            
            //s_log.warn("getSiblingSubspecies() query:" + query);
            while (rset.next()) {
              String taxonName = rset.getString("taxon.taxon_name");
              if (!"".equals(siblingSubspecies)) {
                siblingSubspecies += ", ";
              }
              siblingSubspecies += "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + Taxon.displayTaxonName(taxonName) + "</a>";
            }
        } finally {
            DBUtil.close(stmt, rset, this, "getSeeAlsoSiblingSubspecies()");
        }
      return siblingSubspecies;

    }
    

    protected String getThisWhereClause() {
      return getThisWhereClause("");
    }    
    protected String getThisWhereClause(String table) {
        String clause = "";
        if (!"".equals(table)) table = table + ".";
        clause = " and " + table + "genus = '" + AntFormatter.escapeQuotes(genus) + "'" 
            + " and " + table + "species = '" + AntFormatter.escapeQuotes(species) + "'" 
            + " and " + table + "subspecies = '" + AntFormatter.escapeQuotes(subspecies) + "'"; 
        return clause;    
    }

    public void setChildren(Connection connection, Overview overview, StatusSet statusSet, boolean getChildImages, boolean getChildMaps, String caste, String subgenus) throws SQLException {
      // This method does not seem to use project in it's criteria?!  SetChildrenLocalized below does...

        ArrayList theseChildren = new ArrayList();      

        String theQuery =
           "select distinct specimen.code from taxon, specimen " 
                + " where taxon.taxon_name = specimen.taxon_name"
                + getThisWhereClause("taxon")
                + statusSet.getAndCriteria()
                + " and " + Caste.getSpecimenClause(caste)                
                ;

        Statement stmt = null;
        ResultSet rset = null;
        try {
          stmt = DBUtil.getStatement(connection, "setChildren()");
          rset = stmt.executeQuery(theQuery);
          Specimen child = null;
          
          A.log("Subspecies.setChildren(5) overview:" + overview + " getChildImages:" + getChildImages + " query:" + theQuery);

          int i = 0;
          while (rset.next()) {
            ++i;
            child = new Specimen();
            child.setRank(Rank.SPECIMEN);
            child.setSubfamily(subfamily);
            child.setGenus(genus);
            child.setSpecies(species);
            child.setSubspecies(subspecies);
            child.setCode(rset.getString("code"));
            child.setStatus(getStatus());
            if (getChildImages) {
                child.setImages(connection, overview, caste);
            } else {
                child.setHasImages(connection, overview);
            }                        
            if ((getChildMaps) && (i < Taxon.getMaxSafeChildrenCount()) && overview instanceof LocalityOverview) {
                child.setMap(new Map(child, (LocalityOverview) overview, connection));
            }
            child.setTaxonomicInfo(connection);   // is this needed?  Yes, for now.
            child.generateBrowserParams(); 
            // child.init(); // added Oct 3, 2012 Mark.  No, can't.  Specimen has all in setTaxonomicInfo(), bad.            
            child.initTaxonSet(connection, overview);

            // A.log("setChildren() overview:" + overview + " child:" + child.getTaxonName() + " code:" + child.getCode());                                
            theseChildren.add(child);
          }
          
        } catch (Exception e) {
            s_log.error("setChildren(" + overview + ") e:" + e + " query:" + theQuery);
        } finally {
            DBUtil.close(stmt, rset, this, "setChildren()");
        }
        
        this.children = overview.sort(theseChildren);
                
        setChildrenCount(theseChildren.size());
    }
    
    public void setChildrenLocalized(Connection connection, Overview overview) throws SQLException {
    /* Called by FieldGuideAction.execute() in the case of species.
       This method uses project to figure the locality criteria does not seem to use project in it's criteria.
     */
        s_log.info("setting localized children");
        ArrayList theseChildren = new ArrayList();
	
		// This one is differerent from setChildren in that it will pull back subspecies as well.  Correct?
		String query =
			"select distinct specimen.code from taxon, specimen where " 
				+ " and taxon.taxon_name = specimen.taxon_name"
				+ getThisWhereClause()
				//+ " taxon.genus = '" + AntFormatter.escapeQuotes(genus) + "'" 
				//+ " and taxon.species = '" + AntFormatter.escapeQuotes(species) + "'"                     
				//+ " and taxon.subspecies = '" + AntFormatter.escapeQuotes(subspecies) + "'" 
			  //  + ' and status = "valid"'
			  ;

		if (overview instanceof LocalityOverview) {
		  String locality = ((LocalityOverview) overview).getLocality();                    
		  A.log("setChildrenLocalized() locality:" + locality);
		  if ((locality != null) && (locality.length() > 0) && (!locality.equals("null")))  {
			if ("country".equals(locality.substring(0, 7))) {
				locality = "specimen." + locality;
			}
			query += " and " + locality;
		  }
		}
		A.log("setChildrenLocalized() query:" + query);
/*
		String locality = ProjectMgr.getProject(project).getLocality();
		
		if ((locality != null) && (locality.length() > 0) && (!locality.equals("null")))  {
			theQuery += " and " + locality;
		}
*/            
        Statement stmt = null;
        ResultSet rset = null;
        try {
          stmt = connection.createStatement();
          rset = stmt.executeQuery(query);

            Specimen child = null;
            while (rset.next()) {
                child = new Specimen();
                child.setRank(Rank.SPECIMEN);
                child.setSubfamily(subfamily);
                child.setGenus(genus);
                child.setSpecies(species);
                child.setSubspecies(subspecies);
                child.setCode(rset.getString("code"));
                child.setImages(connection, overview);
                child.setTaxonomicInfo(connection);
                child.generateBrowserParams(overview);
                //child.setMap(new Map(child, project, connection));
                theseChildren.add(child);
            }
        } finally {
            DBUtil.close(stmt, rset, this, "setChildrenLocalized()");
        }
        
        this.children = overview.sort(theseChildren);

        setChildrenCount(theseChildren.size());
    }
        
    public String getFullName() {
        StringBuffer fullName = new StringBuffer();
        fullName.append(genus + " ");
        if ((subgenus != null)
            && (!("".equals(subgenus)))
            && (!("null".equals(subgenus)))) {
            fullName.append("(" + subgenus + ") ");
        }
/*        
        if ((speciesGroup != null)
            && (!("".equals(speciesGroup)))
            && (!("null".equals(speciesGroup)))) {
            fullName.append("(" + speciesGroup + ") ");
        }
*/
        fullName.append(species);
        if ((subspecies != null)
            && (!("".equals(subspecies)))
            && (!("null".equals(subspecies)))) {
            fullName.append(" " + subspecies);
        }

        return fullName.toString();
    }

    public String getTaxonomicBrowserParams() {
        String theParams = "genus=" + this.getGenus();
        theParams += "&species=" + this.getSpecies();
        theParams += "&subspecies=" + this.getSubspecies();
        theParams += "&rank=subspecies";
        return theParams;
    }

     /* These two methods are for the automated generation of authority files. */
    public static String getDataHeader() {
      String header = 
        "Subfamily" + "\t" +
        "Tribe" + "\t" +
        "Genus" + "\t" +
        "Subgenus" + "\t" +
        "SpeciesGroup" + "\t" +
        "Species" + "\t" +
        "Subspecies" + "\t";
      return header;
    }    

    public String getData() throws SQLException {
      String data = "";
      String delimiter = "\t";   // ", ";
      
      data += Utility.notBlankValue(getSubfamily()) + delimiter;
      data += delimiter;  // data += Utility.notBlankValue(getTribe()) + delimiter;
      data += Utility.notBlankValue(getGenus()) + delimiter;
      data += Utility.notBlankValue(getSubgenus()) + delimiter;
      data += Utility.notBlankValue(getSpeciesGroup()) + delimiter;
      data += Utility.notBlankValue(getSpecies()) + delimiter;
      data += Utility.notBlankValue(getSubspecies()) + delimiter;
      return data;
    }  

}



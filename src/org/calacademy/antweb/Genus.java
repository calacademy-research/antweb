package org.calacademy.antweb;

import java.util.*;
import java.io.Serializable;
import java.sql.*;
 
import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.home.*;

/** Class Genus keeps track of the information about a specific taxon */
public class Genus extends Subfamily implements Serializable {

    private static Log s_log = LogFactory.getLog(Genus.class);
    
    public String getNextRank() {
        return "Species";
    }
    
    public static boolean isIndet(String genusName) {
      if (genusName != null) {
        if ("(indet)".equals(genusName)) return true;
        if (genusName.contains("(") && genusName.contains(")")) {
          if (Subfamily.isValidAntSubfamily(genusName)) {
            //A.log("isIndet() true genusName:" + genusName);
            return true;
          }
        }
      }
      return false;
    }

    public void filterChildren(String[] goodList) {
        if (children != null) {
            Taxon thisChild;
            List goodArrayList = Arrays.asList(goodList);
            ArrayList newChildren = new ArrayList();
            Iterator iterator = children.iterator();
            while (iterator.hasNext()) {
                thisChild = (Taxon) iterator.next();
                if (goodArrayList.contains(thisChild.getFullName())) {
                    newChildren.add(thisChild);
                }
            }
            this.children = newChildren;
        }
    }

    public String getName() { 
        return getGenus(); 
    }

    
    public void setTaxonomicInfo(String project) throws SQLException {
        s_log.warn("setTaxonomicInfo(project) is deprecated");
        setTaxonomicInfo();
    }
    
    public void setTaxonomicInfo() throws SQLException {        
        String subfamilyClause = "";
        if ((subfamily != null) && (!subfamily.equals("null"))) {
          subfamilyClause = " and taxon.subfamily = '" + AntFormatter.escapeQuotes(subfamily) + "' ";
        }
		String theQuery = null;

		theQuery = "select distinct taxon.taxarank, taxon.kingdom_name, taxon.phylum_name, taxon.class_name, taxon.order_name "
		  + ", taxon.family, taxon.subfamily, taxon.tribe from taxon"
		  // + ", proj_taxon "
		  + " where 1 = 1 " 
		  // + " taxon.taxon_name = proj_taxon.taxon_name and"
		  + subfamilyClause
		  + " and taxarank = 'genus'"
		  + " and taxon.genus='" + AntFormatter.escapeQuotes(genus) + "'";
		 // + " and status = 'valid'";
		
		TaxonDb taxonDb = new TaxonDb(connection);
		taxonDb.setTaxonomicInfo(theQuery, this);
		A.log("setTaxonomicInfo() order:" + this.getOrderName() + " query:" + theQuery);
    }
    
    protected String getThisWhereClause() {
      return getThisWhereClause("");
    }
    protected String getThisWhereClause(String table) {    
        if (!"".equals(table)) table = table + ".";
        String clause = " and " + table + "genus = '" + AntFormatter.escapeQuotes(genus) + "'"; //specimen.
        return clause;    
    }    

    public void setChildren(Overview overview, StatusSet statusSet, boolean getChildImages, boolean getChildMaps, String caste, boolean global, String subgenus) throws SQLException {
    
        // global is not used. Currently only in Species.java.
        String fetchChildrenClause = " where 1 = 1";
        //if (!global) overviewCriteria = overview.getOverviewCriteria();
        if (!global) fetchChildrenClause = overview.getFetchChildrenClause();

        String subgenusClause = "";
        if (subgenus == null || "all".equals(subgenus)) {
            // Do nothing. Default.
        } else if ("none".equals(subgenus)) {
            subgenusClause = " and subgenus is null ";
        } else {
            subgenusClause = " and subgenus = '" + subgenus + "' ";
        }
        A.log("subgenus clause added:" + subgenusClause);

        long now = (new GregorianCalendar()).getTimeInMillis();
        
        ArrayList theseChildren = new ArrayList();

        Statement stmt = null;
        ResultSet rset = null;
        try {
            String query = null;

            query = "select distinct taxon.species, taxon.subgenus, taxon.speciesgroup, taxon.subspecies " //, taxon.fossil " 
                        + ", taxon.taxarank "
                        + "from taxon"
                        + fetchChildrenClause   
                        + " and taxon.subfamily = '" + AntFormatter.escapeQuotes(getSubfamily()) + "'"
                        + " and taxon.genus = '" + AntFormatter.escapeQuotes(genus) + "'" 
                        + " and taxon.species != '' "
                        + " and (taxarank = 'species' || taxarank = 'subspecies')"
                        + subgenusClause
                        + statusSet.getAndCriteria()
                        ;
//            if (!"default".equals(project))                        
//              query += " and proj_taxon.project_name = '" + project + "'";

            //A.log("setChildren(5) overview:" + overview + " query:" + query);
            
            //s_log.info("setChildren() getChildMaps:" + getChildMaps + " query:" + query);

            //s_log.info("in genus set children query is : " + theQuery);
            //s_log.info("setChildren() initial query time:" + (((new GregorianCalendar()).getTimeInMillis()) - now));
            now = (new GregorianCalendar()).getTimeInMillis();

            stmt = connection.createStatement();
            rset = stmt.executeQuery(query);

            //boolean isFossil = false;
            Taxon child = null;
            String theParams = null;
            long mapTime = 0;
            long imageTime = 0;
            long browserTime = 0;
            long setupTime = 0;
            int i = 0;
            while (rset.next()) {
                ++i;
                String rank = rset.getString("taxarank");
                if (Rank.SPECIES.equals(rank)) {
                  child = new Species();
                } else {
                  child = new Subspecies();
                  child.setSubspecies(rset.getString("subspecies"));
                }
                child.setRank(rank);
                child.setSubfamily(subfamily);
                child.setGenus(genus);
                child.setSubgenus(rset.getString("subgenus"));
                child.setSpeciesGroup(rset.getString("speciesgroup"));
                child.setSpecies(rset.getString("species"));
                child.setConnection(connection);
			
                child.init();
                
                // setupTime += (((new GregorianCalendar()).getTimeInMillis()) - now);
                //  s_log.info("setChildren setup time:" + setupTime);
                //  now = (new GregorianCalendar()).getTimeInMillis();

                if ((getChildMaps) && (i < Taxon.getMaxSafeChildrenCount()) && overview instanceof LocalityOverview) {
                    child.setMap(new Map(child, (LocalityOverview) overview, connection));                
                    if ((i + 1) ==  Taxon.getMaxSafeChildrenCount()) {
                      s_log.warn("setChildren taxon:" + getGenus() + " has over " + Taxon.getMaxSafeChildrenCount() + " maps");
                    }
                }
                
                //  mapTime += (((new GregorianCalendar()).getTimeInMillis()) - now);
                //  now = (new GregorianCalendar()).getTimeInMillis();                
                browserTime += (((new GregorianCalendar()).getTimeInMillis()) - now);
                // now = (new GregorianCalendar()).getTimeInMillis();

                //A.log("setChildren() getChildImages:" + getChildImages);
                if (getChildImages) {
                    if (i > Taxon.getMaxSafeChildrenCount()) {
                      // Do something?  or now, allow.
                    }
                    child.setImages(overview, caste);
                }

/*
??? Child initTaxonSet below should get the image count.  Why fetch it separately above? Have yet to test...
On a page like: http://localhost/antweb/taxonomicPage.do?rank=genus&bioregionName=Afrotropical
We get both... that can't be right.
 2016-07-01 20:01:19,194 WARN http-bio-80-exec-8 org.calacademy.antweb.Taxon - setHasImages() XXX imageCount:52 overview:Afrotropical query: select image_count from bioregion_taxon  where taxon_name = "myrmicinaetemnothorax"  and bioregion_name ='Afrotropical'
 2016-07-01 20:01:19,194 WARN http-bio-80-exec-8 org.calacademy.antweb.geolocale.OverviewTaxon - OverviewTaxon.init query:select subfamily_count, genus_count, species_count, specimen_count, image_count  from bioregion_taxon where  bioregion_name = 'Afrotropical'   and taxon_name = 'myrmicinaetemnothorax'
*/
                 
                if (getTaxonName().contains("acanthobius")) A.log("setChildren() getChildImages:" + getChildImages + " images:" + child.getHasImagesCount() + " imagess:" + child.getImages());
                
                child.initTaxonSet(overview); 

                child.generateBrowserParams(overview);                
            
                // imageTime += (((new GregorianCalendar()).getTimeInMillis()) - now);
                // now = (new GregorianCalendar()).getTimeInMillis();
                child.setConnection(null);
                //A.log("setChildren() project:" + project + " child:" + child.getTaxonName() + " + this:" + this);                                
                theseChildren.add(child);
            }
            //s_log.info("mt" + mapTime + ", it: " + imageTime + ", bt: " + browserTime + " , st" + setupTime);
            setChildrenCount(theseChildren.size());
            //s_log.info("setChildren() total time:" + (((new GregorianCalendar()).getTimeInMillis()) - now));
            now = (new GregorianCalendar()).getTimeInMillis();
        } finally {
            DBUtil.close(stmt, rset, this, "setChildren()");
        }
        A.log("setChildren() size:" + theseChildren.size());
        //if (AntwebProps.isDevMode()) AntwebUtil.logShortStackTrace();
        this.children = theseChildren;
    }
 
    public String getTaxonomicBrowserParams() {
        String theParams = "subfamily=" + this.getSubfamily() + "&genus=" + this.getGenus() + "&rank=genus";
        return theParams;
    }
}

package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.curate.geolocale.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.data.*;
import org.calacademy.antweb.data.geonet.*;
import org.calacademy.antweb.Formatter;

public class GeolocaleDb extends AntwebDb {

    private static Log s_log = LogFactory.getLog(GeolocaleDb.class);

    public GeolocaleDb(Connection connection) {
      super(connection);
    }

    public Region getRegion(String name) {
      return (Region) getGeolocale(name, "region");      
    }
    public Subregion getSubregion(String name) {
      return (Subregion) getGeolocale(name, "subregion");      
    }
    public Country getCountry(String name) {
      return (Country) getGeolocale(name, "country");      
    }
/*
    // Must have the country to be unique    
    public Adm1 getAdm1(String name) {
      return (Adm1) getGeolocale(name, "adm1");      
    }
*/    

    public Geolocale getAdm1(String adm1, String country) {
       int id = 0;
               
       String query = "select id from geolocale where name = '" + Formatter.escapeQuotes(adm1) + "' and parent = '" + Formatter.escapeQuotes(country) + "'";
       Statement stmt = null;
       ResultSet rset = null;
       try {
            stmt = DBUtil.getStatement(getConnection(), "GeolocaleDb.getAdm1(adm1, country)");
            rset = stmt.executeQuery(query);

            while (rset.next()) {
              id = rset.getInt("id");  
            }       
        } catch (SQLException e) {
            s_log.error("getAdm1(" + adm1 + ", " + country + ") e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "GeolocaleDb.getAdm1(adm1, country)");
        }
       if (id > 0) return getGeolocale(id);    
       return null;
    }
    
    public Geolocale getGeolocale(int id) {   
        Geolocale geolocale = null;

        String query;
        Statement stmt = null;
        ResultSet rset = null;

        //name = AntFormatter.escapeQuotes(name);
        try {
            query = "select id, name, isoCode, iso3Code "
              + ", georank, is_valid, valid_name, is_un, g.source, g.bioregion, g.alt_bioregion" 
              + ", parent, region, subregion, country " 
              + ", g.extent, g.coords, g.map, is_use_children, is_use_parent_region, g.is_island, g.is_live"
              + ", g.centroid, g.centroid_fixed, g.bounding_box, g.bounding_box_fixed, woe_id, admin_notes " 
              + ", g.created "
              + ", g.subfamily_count, g.genus_count, g.species_count, g.specimen_count, g.image_count "
              + ", g.imaged_specimen_count, g.taxon_subfamily_dist_json, g.specimen_subfamily_dist_json, g.chart_color"
              + ", g.endemic_species_count, g.introduced_species_count"
			  + ", g.rev, g.georank_type"                 
	  		  //+ " , g.specimenImage1, g.specimenImage2, g.specimenImage3, g.specimenImage1Link, g.specimenImage2Link, g.specimenImage3Link, g.author, g.authorImage, g.authorbio "               

              + " from geolocale g "  // left join project on id = geolocale_id"
              + " where id = " + id
              + " order by name"              
              ;


            if (false && AntwebProps.isDevMode() && 7 == id) {  // Madagascar
              s_log.warn("getGeolocale(" + id + ") query:" + query);
              AntwebUtil.logStackTrace();
            }
/*
select g.bioregion from geolocale where name in ('Comoros', 'Ethiopia', 'Macaronesia', 'New Guinea', 'Philippines', 'Saudi Arabia', 'Solomon Islands', 'Timor-Leste', 'United Arab Emirates');
*/

            stmt = DBUtil.getStatement(getConnection(), "GeolocaleDb.getGeolocale(int)");
            rset = stmt.executeQuery(query);

            while (rset.next()) {
              String georank = rset.getString("georank");

              if ("region".equals(georank)) geolocale = new Region();
              if ("subregion".equals(georank)) geolocale = new Subregion();
              if ("country".equals(georank)) geolocale = new Country();
              if ("adm1".equals(georank)) geolocale = new Adm1();

              geolocale.setGeorank(georank);
              geolocale = popGeolocale(geolocale, rset, false);
                              
              return geolocale;
            }
        } catch (SQLException e) {
            s_log.error("getGeolocale(int) e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "GeolocaleDb.getGeolocale(int)");
        }
        return null;
    }

    // if Adm1, must have the country to be unique. Use getAdm1(name, parent).
    public Geolocale getGeolocale(String name, String georank) {   
        if ("adm1".equals(georank)) {
          s_log.warn("getGeolocale(name, georank) needs parent to adequately specify and adm1:" + name);
          return null;
        }
        // Alternate.  Be aware of diacritic issue. Mexico (country is not México (adm1).
        Geolocale geolocale = null;
        if (Georank.REGION.equals(georank)) geolocale = new Region();
        if (Georank.SUBREGION.equals(georank)) geolocale = new Subregion();
        if (Georank.COUNTRY.equals(georank)) geolocale = new Country();
        if (Georank.ADM1.equals(georank)) geolocale = new Adm1();

        String query = "";
        Statement stmt = null;
        ResultSet rset = null;

        name = Formatter.escapeQuotes(name);
        try {
            query = "select id, name, isoCode, iso3Code, georank, is_valid, valid_name, is_un, source, bioregion, alt_bioregion " 
              + ", parent, region, subregion, country " 
              + ", extent, coords, map, is_use_children, is_use_parent_region, is_island, is_live"
              + ", g.centroid, g.centroid_fixed, g.bounding_box, g.bounding_box_fixed, woe_id, admin_notes "
              + ", created "
              + ", subfamily_count, genus_count, species_count, specimen_count, image_count "
              + " , imaged_specimen_count, taxon_subfamily_dist_json, specimen_subfamily_dist_json, chart_color "  
              + " , endemic_species_count, g.introduced_species_count"    
			  + ", g.rev, g.georank_type"        
             // + " , specimenImage1, specimenImage2, specimenImage3, specimenImage1Link, specimenImage2Link, specimenImage3Link, author, authorImage, authorbio "               
              + " from geolocale g " // left join project on id = geolocale_id"
              + " where name = '" + name + "'"
              + " and georank = '" + georank + "'"
              + " order by name"
             // + "  COLLATE utf8_bin"  
              ;
             //is_live, 

             // This query can fail and yet run successfully from MySQL. Something to do with character sets.
             // Try inserting a special character name from the GeolocaleMgr and it will insert, but then fail to fetch. 
             // But it is fetched elsewhere. What gives? Simply reports MySQLException.
             
            //if (AntwebProps.isDevMode() && "Madagascar".equals(name)) s_log.warn("getGeolocale(name, georank) query:" + query);
            //if (AntwebProps.isDevMode() && id == 19) s_log.warn("getGeolocale(name, georank) query:" + query);

            stmt = DBUtil.getStatement(getConnection(), "GeolocaleDb.getGeolocale()");
            rset = stmt.executeQuery(query);

            if (geolocale == null) s_log.warn("getGeolocale() name:" + name + " georank:" + georank + " query:" + query);

            geolocale.setGeorank(georank); 
            if (rset.next()) {
              geolocale = popGeolocale(geolocale, rset, false);

              return geolocale; 
            }
            
        } catch (SQLException e) {
            s_log.error("getGeolocale() name:" + name + " georank:" + georank + " e:" + e.toString() + " query:" + query + " sqlstate:" + e.getSQLState());
             //+  " message:" + e.getMessage() + " code:" + e.getErrorCode()   Message is null and code is 0.

        } finally {
            DBUtil.close(stmt, rset, "GeolocaleDb.getGeolocale()");
        }
        return null;
    }

    public ArrayList<Geolocale> getGeolocales(String georank, String parent, boolean withChildren) {
        String orderBy = "name";
        return getGeolocales(georank, parent, withChildren, orderBy);
    }
        
    public ArrayList<Geolocale> getGeolocales(String georank, String parent, boolean withChildren, String orderBy) {
        return getGeolocales(georank, parent, withChildren, false, orderBy);
    }
    
    private ArrayList<Geolocale> getGeolocales(String georank, String parent, boolean withChildren, boolean liveOnly, String orderBy) {
        // Called during GeolocaleMgr.populate();  "none" is a valid value for parent.
        //A.log("getGeolocales(" + georank + ", " + parent + ", ...");
                
        ArrayList<Geolocale> geolocaleArray = new ArrayList<Geolocale>();

        //if ("Venezuela".equals(parent)) A.log("getGeolocales() georank:" + georank + " parent:" + parent);

        parent = AntFormatter.escapeQuotes(parent);

        String parentClause = "";
        if (parent != null) {
            parentClause = " and parent = '" + parent + "'";
        }
        if ("none".equals(parent)) {
            // This is invoked by the GeolocaleMgr to get ALL...
            parentClause = " and parent is null";
        }

        String rankClause = "";
        if (georank != null) {
          rankClause = " and georank = '" + georank + "'";
        } else {
          // These will show up as children, so exclude from the main query.
          //rankClause = " and georank != 'adm1'";
        }
          
        String liveClause = "";
        if (liveOnly) liveClause = " and is_live = 1 ";

        String orderByClause = " order by ";
        if ("isValid".equals(orderBy)) orderBy = "is_valid";
        if ("isLive".equals(orderBy)) orderBy = "is_live";  
        if ("validName".equals(orderBy)) orderBy = "valid_name";  
              
        if (orderBy != null) orderByClause += orderBy + ", ";
        orderByClause += "name collate utf8_unicode_ci, valid_name";
           // + " order by " + orderBy + " georank desc, name, valid_name"; //is_valid desc, 

        String query;
        Statement stmt = null;
        ResultSet rset = null;

        try {
            query = "select id, name, isoCode, iso3Code, georank, is_valid, valid_name, is_un, g.source, g.bioregion, g.alt_bioregion " 
              + ", parent, region, subregion, country " 
              + ", g.extent, g.coords, g.map, is_use_children, is_use_parent_region, g.is_live, g.is_island"
              + ", g.centroid, g.centroid_fixed, g.bounding_box, g.bounding_box_fixed, woe_id, admin_notes "
              + ", g.created "
              + ", g.subfamily_count, g.genus_count, g.species_count, g.specimen_count, g.image_count "
              + " , g.imaged_specimen_count, g.taxon_subfamily_dist_json, g.specimen_subfamily_dist_json, g.chart_color "
              + " , g.endemic_species_count, g.introduced_species_count"
              + ", g.rev, g.georank_type"
              //+ " , g.specimenImage1, g.specimenImage2, g.specimenImage3, g.specimenImage1Link, g.specimenImage2Link, g.specimenImage3Link, g.author, g.authorImage, g.authorbio "               
                          
              + " from geolocale g " // left join project on id = geolocale_id"
              + " where 1 = 1"
              + rankClause
              + parentClause
              + liveClause
              + orderByClause;

			//A.log("getGeolocales(" + georank + ", " + parent + ", " + withChildren + ", " + liveOnly + ", " + orderBy + " query:" + query);

            //String testParent = "Eastern Asia";
            if (query.equals("United States")) A.log("GeolocaleDb.getGeolocale(5) query:" + query);

            stmt = DBUtil.getStatement(getConnection(), "GeolocaleDb.getGeolocales()");
            rset = stmt.executeQuery(query);
            
            int i = 0;
            while (rset.next()) {
              ++i;
              georank = rset.getString("georank");
              String name = rset.getString("name");
 
              Geolocale geolocale = null;
              //if (georank == null) geolocale = new Geolocale();
              if ("region".equals(georank)) geolocale = new Region();
              if ("subregion".equals(georank)) geolocale = new Subregion();
              if ("country".equals(georank)) geolocale = new Country();
              if ("adm1".equals(georank)) geolocale = new Adm1();
              
              geolocale.setGeorank(georank); 

//			  A.log("getGeolocales() georank:" + georank + " parent:" + parent);
              
              geolocale = popGeolocale(geolocale, rset, withChildren);
              							
              //if ("Venezuela".equals(parent)) A.log("getGeolocales(5) name:" + geolocale.getName());              							
              							
              geolocaleArray.add(geolocale);
            }
        } catch (SQLException e) {
            s_log.error("getGeolocales() e:" + e + " state:" + e.getSQLState() + " nextE:" + e.getNextException());
            AntwebUtil.logStackTrace(e);            
        } finally {
            DBUtil.close(stmt, rset, "GeolocaleDb.getGeolocales()");
        }
        
        return geolocaleArray;
    }
   
    private Geolocale popGeolocale(Geolocale geolocale, ResultSet rset, boolean withChildren)
      throws SQLException {
	  
	  try {	  
		  geolocale.setId(rset.getInt("id"));
		  geolocale.setName(rset.getString("name"));
	 
		  geolocale.setIsValid((rset.getInt("is_valid") == 1) ? true : false);
		  geolocale.setValidName(rset.getString("valid_name"));
		  geolocale.setIsUn((rset.getInt("is_un") == 1) ? true : false);
		  geolocale.setIsoCode(rset.getString("isoCode"));
		  geolocale.setIso3Code(rset.getString("iso3Code"));
		  geolocale.setSource(rset.getString("source"));
		  geolocale.setRegion(rset.getString("region"));
		  geolocale.setSubregion(rset.getString("subregion"));
		  geolocale.setCountry(rset.getString("country"));
		  geolocale.setBioregion(rset.getString("bioregion"));
		  geolocale.setAltBioregion(rset.getString("alt_bioregion"));	  
		  geolocale.setParent(rset.getString("parent"));
          //if ("Kansas".equals(geolocale.getName())) A.log("populate() Kansas " + geolocale.getCountry() + " parent:" + geolocale.getParent());
		  geolocale.setIsLive((rset.getInt("is_live") == 1) ? true : false);
		  geolocale.setExtent(rset.getString("extent"));
		  geolocale.setCoords(rset.getString("coords"));
		  geolocale.setMapImage(rset.getString("map"));
	  
		  geolocale.setCentroid(rset.getString("centroid"));
		  geolocale.setCentroidFixed(rset.getString("centroid_fixed"));
	    
		  geolocale.setBoundingBox(rset.getString("bounding_box"));
		  geolocale.setBoundingBoxFixed(rset.getString("bounding_box_fixed"));
		  geolocale.setWoeId(rset.getString("woe_id")); 
		  geolocale.setAdminNotes(rset.getString("admin_notes")); 


		  geolocale.setCreated(rset.getTimestamp("created"));
		  geolocale.setIsUseChildren((rset.getInt("is_use_children") == 1) ? true : false);
		  geolocale.setIsUseParentRegion((rset.getInt("is_use_parent_region") == 1) ? true : false);
		  geolocale.setIsIsland((rset.getInt("is_island") == 1) ? true : false);
          //if ("Greece".equals(geolocale.getName())) A.log("GeolocaleDb 1 name:" + geolocale.getName() + " isUseChildren:" + geolocale.getIsUseChildren());

		  if (withChildren && !("adm1".equals(geolocale.getGeorank()))) {
		  // This functionality could exist in a Georank class.
			String childRank = null;
			if ("region".equals(geolocale.getGeorank())) childRank = "subregion";
			if ("subregion".equals(geolocale.getGeorank())) childRank = "country";
			if ("country".equals(geolocale.getGeorank())) childRank = "adm1";
			if (childRank != null) { // && geolocale.getIsValid()) {
			  geolocale.setChildren(getGeolocales(childRank, geolocale.getName(), true));
			  //if ("adm1".equals(childRank) && "Venezuela".equals(geolocale.getName())) A.log("GeolocaleDb.popGeolocale() name:" + geolocale.getName() + " size:" + geolocale.getChildren().size());
			}
		  }
	  
		  geolocale.setSubfamilyCount(rset.getInt("subfamily_count"));
		  geolocale.setGenusCount(rset.getInt("genus_count"));
		  geolocale.setSpeciesCount(rset.getInt("species_count"));
		  geolocale.setSpecimenCount(rset.getInt("specimen_count"));
		  geolocale.setImageCount(rset.getInt("image_count"));
		  geolocale.setImagedSpecimenCount(rset.getInt("imaged_specimen_count"));
		  geolocale.setTaxonSubfamilyDistJson(rset.getString("taxon_subfamily_dist_json"));
		  geolocale.setSpecimenSubfamilyDistJson(rset.getString("specimen_subfamily_dist_json"));
		  geolocale.setChartColor(rset.getString("chart_color"));             
	
		  geolocale.setEndemicSpeciesCount(rset.getInt("endemic_species_count"));
		  geolocale.setIntroducedSpeciesCount(rset.getInt("introduced_species_count"));
	      geolocale.setRev(rset.getInt("rev"));
		  geolocale.setGeorankType(rset.getString("georank_type"));
	
		  Hashtable description = (new DescEditDb(getConnection())).getDescription(geolocale.getName());
		  geolocale.setDescription(description);   
		  
		  geolocale.setAlternatives(getAlternatives(geolocale.getName(), geolocale.getGeorank()));
	
		  if (AntwebProps.isDevMode() && geolocale.getName().contains("Alabama")) {
		  //if (geolocale.getName().contains("San Jos")) {
			//A.log("popGeolocale() id:" + geolocale.getId() + " name:" + geolocale.getName() + " parent:" + geolocale.getParent() + " bounds:" + geolocale.getBoundingBox());
  	        //AntwebUtil.logShortStackTrace(14);
		  }

	  } catch (SQLException e) {
		s_log.warn("popGeolocale() e:" + e);
		AntwebUtil.logStackTrace(e);
   	  }

	  return geolocale;
    }
    
    // Just Id and name, useful for drop down list.
    public String getAlternatives(String name, String georank) {
        String alternatives = "";
        String query;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            query = "select name from geolocale"
// collate utf8_bin 
              + " where valid_name = '" + AntFormatter.escapeQuotes(name) + "'"
              + "   and georank = '" + georank + "'"
              + " order by name";
            stmt = DBUtil.getStatement(getConnection(), "GeolocaleDb.getAlternatives()");
            rset = stmt.executeQuery(query);
         
            int i = 0;
            while (rset.next()) {
              ++i;
              if (i > 1) alternatives += "; ";
              alternatives += rset.getString("name");
            }
        } catch (SQLException e) {
            s_log.error("getAlternatives() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "GeolocaleDb.getAlternatives()");
        }
        return alternatives;
    }    
       
    public ArrayList<Region> getRegions() {
        return getRegions(false);
    }    
    public ArrayList<Region> getRegions(boolean withChildren) {
        ArrayList<Region> regions = new ArrayList<Region>();
        for (Geolocale geolocale : getGeolocales("region", null, withChildren)) {
          regions.add((Region) geolocale);
        }        
        return regions;
    }

    public ArrayList<Subregion> getSubregions(String region) {
        return getSubregions(region, false);
    }   
    public ArrayList<Subregion> getSubregions(String region, boolean withChildren) {
        ArrayList<Subregion> subregions = new ArrayList<Subregion>();
        for (Geolocale geolocale : getGeolocales("subregion", region, withChildren)) {
          subregions.add((Subregion) geolocale);
        }
        return subregions;
    }

    public ArrayList<Country> getCountries() {
        return getCountries(null);
    }
    public ArrayList<Country> getCountries(String unSubregion) {
        return getCountries(unSubregion, false);
    }
    public ArrayList<Country> getCountries(String unSubregion, boolean withChildren) {
        ArrayList<Country> countries = new ArrayList<Country>();
        for (Geolocale geolocale : getGeolocales("country", unSubregion, withChildren)) {
          countries.add((Country) geolocale);
        }
        return countries;
    }

    public ArrayList<Adm1> getAdm1s(String country) {
        //return getGeolocales("adm1", country, false);

        ArrayList<Adm1> adm1s = new ArrayList<Adm1>();
        for (Geolocale geolocale : getGeolocales("adm1", country, false)) {
          adm1s.add((Adm1) geolocale);
        }
        return adm1s;
    }

    public ArrayList<Adm1> getAdm1s(String country, String orderBy) {
        //return getGeolocales("adm1", country, false);

        ArrayList<Adm1> adm1s = new ArrayList<Adm1>();
        for (Geolocale geolocale : getGeolocales("adm1", country, false, false, orderBy)) {
          adm1s.add((Adm1) geolocale);
        }
        return adm1s;
    }

    // Just Id and name, useful for drop down list.
    public ArrayList<Geolocale> getValidChildren(String parent) {
        ArrayList<Geolocale> validChildren = new ArrayList<Geolocale>()	;

        String query;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            query = "select id, name from geolocale"
              + " where parent = '" + parent + "'"
              + " and is_valid = 1 "
              + " order by name";
            stmt = DBUtil.getStatement(getConnection(), "GeolocaleDb.getValidChildren()");
            rset = stmt.executeQuery(query);

            int i = 0;
            while (rset.next()) {
              String name = rset.getString("name");
              int id = rset.getInt("id");
              Geolocale child = new Geolocale();
              child.setId(id);
              child.setName(name);
              child.setParent(parent);
              validChildren.add(child);
            }
        } catch (SQLException e) {
            s_log.error("getValidChildren() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "GeolocaleDb.getValidChildren()");
        }
        return validChildren;
    }
    
    public ArrayList<String> getValidGeolocaleNames(String georank) {
        return getGeolocaleNames(georank, 1);
    }
    public ArrayList<String> getGeolocaleNames(String georank) {
        return getGeolocaleNames(georank, -1);
    }    
    public ArrayList<String> getGeolocaleNames(String georank, int isValid) {
        ArrayList<String> validGeolocales = new ArrayList<String>()	;
 
        // isValid.  1:true, 0:false, -1:N/A.
        String isValidClause = "";
        if (isValid == 1) isValidClause = " and is_valid = 1";
        if (isValid == 0) isValidClause = " and is_valid = 0";
        
        String query;
        Statement stmt = null;
        ResultSet rset = null;

        try {
            query = "select name from geolocale"
              + " where georank = '" + georank + "'"
              + isValidClause
              + " order by name";
            //A.log("getValidGeolocaleNames() query:" + query);
            stmt = DBUtil.getStatement(getConnection(), "GeolocaleDb.getValidGeolocaleNames()");
            rset = stmt.executeQuery(query);
            
            int i = 0;
            while (rset.next()) {
              String name = rset.getString("name");
              validGeolocales.add(name);
            }
        } catch (SQLException e) {
            s_log.error("getValidGeolocaleNames() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "GeolocaleDb.getValidGeolocaleNames()");
        }
        return validGeolocales;
    }

    public boolean createGeolocale(EditGeolocaleForm form) {
        boolean success = false;
        Statement stmt = null;

        String cols = "";
        String vals = "";
        if (form.getParent() != null) {
          cols += ", parent";
          vals += ", '" + form.getParent() + "'";
        }
        
        // This will always be blank at creation.
        if (form.getGeorank().equals("adm1")) {
          cols += ", georank_type";
          vals += ", 'Province'";
        }

        cols += ", is_valid";
        vals += ", 0";

        if (Formatter.hasSpecialCharacter(form.getName())) {
          s_log.error("createGeolocale() special character detected in: " + form.getName());
          return false;
        }
        
        String dml = null;
        try {
        
            dml = "insert into geolocale (name, georank, source" + cols + ") " 
              + " values ('" + AntFormatter.escapeQuotes(form.getName()) + "', '" + form.getGeorank() + "', '" + form.getSource() + "'" + vals + ")";
            //A.log("createGeolocale() dml:" + dml);

            stmt = DBUtil.getStatement(getConnection(), "GeolocaleDb.createGeolocale()");
            int x = stmt.executeUpdate(dml);
            if (x > 0) success = true;
        } catch (SQLException e) {
            s_log.error("createGeolocale() e:" + e);
        } finally {
            DBUtil.close(stmt, null, "GeolocaleDb.createGeolocale()");
        }   
        return success;
    }    
    
    public boolean deleteGeolocale(int id) {
        boolean success = false;
        Statement stmt = null;

        String dml = null;
        try {

            dml = "delete from geolocale where id = " + id;
            //A.log("deleteGeolocale() dml:" + dml);

            stmt = DBUtil.getStatement(getConnection(), "GeolocaleDb.deleteGeolocale()");
            int x = stmt.executeUpdate(dml);
            if (x > 0) success = true;
        } catch (SQLException e) {
            s_log.error("deleteGeolocale() e:" + e);
        } finally {
            DBUtil.close(stmt, null, "GeolocaleDb.deleteGeolocale()");
        }   
        return success;
    }   

    public String updateGeolocale(EditGeolocaleForm form) {
        String returnVal = "Update successful.";
        String query;
        Statement stmt = null;

        if (false) A.log("updateGeolocale() id:" + form.getId() + " name:" + form.getName() + " georank:" + form.getGeorank() + " isValid:" + form.isValid() 
          + " isUn:" + form.isUn() + " isLive:" + form.isLive() + " isValid:" + form.isValid() + " source:" + form.getSource() + " validName:"+ form.getValidName() + " region:" + form.getRegion()
          + " centroidFixed:" + form.getCentroidFixed() + " boundingBoxFixed:" + form.getBoundingBoxFixed());

        Geolocale geolocale = getGeolocale(form.getId()); //form.getName(), form.getGeorank());
        if (geolocale == null) return "Geolocale id:" + form.getId() + " not found";  // form.getName() + " of rank:" + form.getGeorank() + " not found";

        String dml = null;
        try {
        
            dml = "update geolocale";
            dml += "  set is_valid = " + form.isValid(); 

            if (form.getGeorank() != null) dml += " , georank = '" + form.getGeorank() + "'";

            dml += ", name = '" + AntFormatter.escapeQuotes(form.getName()) + "'";
            String val = (form.getIsoCode() == null) ? "null" : "'" + form.getIsoCode() + "'";
            dml += ", isoCode = " + val;
            val = (form.getIso3Code() == null) ? "null" : "'" + form.getIso3Code() + "'";
            dml += ", iso3Code = " + val;
            dml += ", is_un = " + form.isUn(); 
            dml += ", is_live = " + form.isLive();
            dml += ", source = '" + form.getSource() + "'";
            dml += ", admin_notes = '" + form.getAdminNotes() + "'";
                                                
            if (form.getGeorankType() != null) {
              if ("adm1".equals(form.getGeorank())) dml += " , georank_type = '" + form.getGeorankType() + "'";

              // Set children georank type (Province, Region, etc...)
              if ("country".equals(form.getGeorank())) updateChildrenGeorankType(form.getName(), form.getGeorankType());
            }

            if (form.getCountry() != null) {
                dml += ", country = '" + form.getCountry() + "'";
            }

            if (form.getValidName() != null) dml += " , valid_name = '" + AntFormatter.escapeQuotes(form.getValidName()) + "'";

		    if (form.getValidNameId() > 0) {
			  Geolocale validNameGeolocale = getGeolocale(form.getValidNameId());
			  if (validNameGeolocale != null) dml += ", valid_name = '" + AntFormatter.escapeQuotes(validNameGeolocale.getName()) + "'";
		    }
		    if (form.getValidNameId() == -1) {
			  dml += ", valid_name = null";
		    }

            if (form.getParent() != null) dml += " , parent = '" + form.getParent() + "'";

            // If we set the subregion, the region will be set as well.
			Geolocale subregion = GeolocaleMgr.getSubregion(form.getParent());
			if (subregion != null) form.setRegion(subregion.getParent());

            if (form.getRegion() != null) dml += " , region = '" + form.getRegion() + "'";

            if (form.getBioregion() != null) dml += " , bioregion = '" + form.getBioregion() + "'";
            if (form.getAltBioregion() != null) dml += " , alt_bioregion = '" + form.getAltBioregion() + "'";


		    if (form.getWoeId() != null && !"".equals(form.getWoeId())) {       
			  FlickrPlace place = FlickrPlace.scrapePlace(form.getWoeId());
			  if (place != null) {
			    setFlickrData(form.getId(), place.getWoeId(), place.getBoundingBox(), place.getLatitude(), place.getLongitude());
			  }
		    }

            if (form.getCentroidFixed() != null) {
              if ("".equals(form.getCentroidFixed())) {
                dml += ", centroid_fixed = null";
              } else {
                dml += ", centroid_fixed = '" + form.getCentroidFixed() + "'";
              }
            }

            if (form.getBoundingBoxFixed() != null) {
              if ("".equals(form.getBoundingBoxFixed())) {
                dml += ", bounding_box_fixed = null";
              } else {
                dml += ", bounding_box_fixed = '" + form.getBoundingBoxFixed() + "'";
              }
            }  
            if (form.getMapImage() != null && !"".equals(form.getMapImage())) {
              dml += ", map = '" + form.getMapImage() + "'";
            }  
            dml += ", is_use_children = " + form.isUseChildren();
            dml += ", is_use_parent_region = " + form.isUseParentRegion();     
            dml += ", is_island = " + form.isIsland();     
                   
            dml += " where id = " + form.getId();
            //dml += " where name = '" + form.getName() + "'";

            //A.log("updateGeolocale() dml:" + dml);

            stmt = DBUtil.getStatement(getConnection(), "GeolocaleDb.updateGeolocale()");
            stmt.executeUpdate(dml);

        } catch (SQLException e) {
            s_log.error("updateGeolocale() e:" + e + " dml:" + dml);
            returnVal = e.toString();
        } finally {
            DBUtil.close(stmt, null, "GeolocaleDb.updateGeolocale()");
        }   
        return returnVal;
    }    
    
    public ArrayList<SpeciesListable> fetchSpeciesLists(Login login) throws SQLException {
        return fetchSpeciesLists(login, true);
    }    
    // SpeciesListable is an interface implemented by Project and Geolocale.  Country and Adm1.
    public ArrayList<SpeciesListable> fetchSpeciesLists(Login login, boolean isGetAdm1) throws SQLException {
      int loginId = login.getId();
      
      // A 0 adminId implies admin and will not restrict the search
      ArrayList<SpeciesListable> speciesListList = new ArrayList<SpeciesListable>();
        
      //A.log("fetchSpeciesLists() groupId:" + loginId);        
      Statement stmt = null;
      ResultSet rset = null;
      try {
          stmt = DBUtil.getStatement(getConnection(), "fetchSpeciesLists()");
        
          String theQuery;
          if (loginId == 0 || login.isAdmin()) {
            theQuery = "select g.id, g.name, g.is_use_children from geolocale g"
               + " where g.georank = 'country'"
               + " and g.is_valid = 1"
               + " order by g.name"
               ;
          } else {            
            theQuery = "select g.id, g.name, g.is_use_children from geolocale g, login_country lc"
               + " where lc.login_id = " + loginId
               + " and lc.country = g.name"
               + " and g.georank = 'country'"
               + " and g.is_valid = 1"
               + " order by g.name"
               ;
          } 
          
          //A.log("fetchSpeciesLists() theQuery:" + theQuery);
          rset = stmt.executeQuery(theQuery);
          String name, type;
          int id = 0;          
		  //A.log("fetchSpeciesLists(login) name:" + name + " isGetAdm1:" + isGetAdm1);              

          while (rset.next()) {
              id = rset.getInt("id");
              name = rset.getString("name");
              //type = rset.getString("type");
                //s_log.info("fetchSpeciesLists() name:" + projectName);    
              
    	      boolean isUseChildren = (rset.getInt("is_use_children") == 1) ? true : false;

				//if ("Greece".equals(name)) A.log("GeolocaleDb 2 name:" + name + " isUseChildren:" + isUseChildren);


				// For Manage login we want all of the countries.  But we don't want United States, yes we do.

//              if (!isUseChildren || isGetAdm1) {            				  
                  Geolocale country = new Country();
                  country.setId(id);
				  country.setName(name);       
				  country.setIsUseChildren(isUseChildren); 
				  country.setType(SpeciesListable.COUNTRY);
				  speciesListList.add(country);

				  if (isGetAdm1) {   // UseChildren) {
				    fetchSubSpeciesLists(name, speciesListList);
                    //A.log("fetchSpeciesLists(login) name:" + name + " isUseChildren:" + isUseChildren + " isGetAdm1:" + isGetAdm1);              
				  }
              // }

//if ("Greece".equals(name)) A.log("GeolocaleDb 3 name:" + name + " isUseChildren:" + country.getIsUseChildren());

          }
      } catch (SQLException e) {
         s_log.error("fetchSpeciesLists() e:" + e + " loginId:" + loginId);
         org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
      } finally {
        DBUtil.close(stmt, rset, "fetchSpeciesLists()");
      }
 
	  if (false && AntwebProps.isDevMode()) {
	    s_log.warn("fetchSpeciesLists(login, " + isGetAdm1 + ") loginId:" + loginId + " size:" + speciesListList.size());  
        AntwebUtil.logStackTrace();
      }
      return speciesListList;
    } 
 
    public ArrayList<SpeciesListable> fetchSubSpeciesLists(String parent, ArrayList<SpeciesListable> speciesListList) throws SQLException {
        
     // A.log("fetchSubSpeciesLists() groupId:" + loginId);        
      Statement stmt = null;
      ResultSet rset = null;
      try {
          stmt = DBUtil.getStatement(getConnection(), "fetchSubSpeciesLists()");
        
          String theQuery = "select g.id, g.name, g.parent from geolocale g"
               + " where g.georank = 'adm1'"
               + " and g.is_valid = 1"
               + " and g.is_live = 1"
               + " and g.parent = '" + parent + "'"
               + " order by g.name"
               ;
               
          //A.log("fetchSubSpeciesLists() theQuery:" + theQuery);

          rset = stmt.executeQuery(theQuery);
          String name, type;
          int id = 0;
          
          while (rset.next()) {
              id = rset.getInt("id");
              name = rset.getString("name");
              parent = rset.getString("parent");
              //type = rset.getString("type");
                //s_log.info("fetchSubSpeciesLists() name:" + projectName);    
              
              Geolocale adm1 = new Adm1();
              adm1.setId(id);
              adm1.setName(name);     
              adm1.setParent(parent);   
              adm1.setType(SpeciesListable.ADM1);
              //A.log("fetchSubSubSpeciesLists() name:" + name + " type:" + adm1.getType());              
              speciesListList.add(adm1);
          }
            
      } catch (SQLException e) {
         s_log.error("fetchSubSpeciesLists() e:" + e);
         org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
      } finally {
        DBUtil.close(stmt, rset, "fetchSubSpeciesLists()");
      }

      return speciesListList;
    }    


// ------------- Get From Specimen Data ----------------------
    
    public Geolocale getFromSpecimenData(int geolocaleId) {

        Geolocale geolocale = new Geolocale();
        geolocale.setId(geolocaleId);

        getSpecimenCount(geolocale);
        getImageCount(geolocale);

        //A.log("getFromSpecimenData() geolocaleId:" + geolocale.getId() + " s:" + geolocale.getSpecimenCount() + " i:" + geolocale.getImageCount());
    
        return geolocale;
    }
    public void getSpecimenCount(Geolocale geolocale) {

      Statement stmt = null;
      ResultSet rset = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "getSpecimenCount()");
            
        String query = "select sum(gt.specimen_count) specimen_count" 
          + " from geolocale_taxon gt, taxon t "
          + " where gt.taxon_name = t.taxon_name "
          + " and t.taxarank in ('species', 'subspecies')"
          + " and gt.geolocale_id = " + geolocale.getId();

        if (AntwebProps.isDevMode() && geolocale.getId() == 284) s_log.warn("getSpecimenCount() query:" + query);  
          
        rset = stmt.executeQuery(query);
        while (rset.next()) {          
          geolocale.setSpecimenCount(rset.getInt("specimen_count"));
        }

      } catch (SQLException e) {
        s_log.error("getSpecimenCount() e:" + e);
      } finally {
        DBUtil.close(stmt, rset, "getSpecimenCount()");
      }
    }
    
    public void getImageCount(Geolocale geolocale) {

      Statement stmt = null;
      ResultSet rset = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "getImageCount()");
                        
        String query = "select sum(gt.image_count) image_count" 
          + " from geolocale_taxon gt, taxon t "
          + " where gt.taxon_name = t.taxon_name "
          + " and t.taxarank in ('species', 'subspecies')"
          + " and gt.geolocale_id = " + geolocale.getId();

        if (AntwebProps.isDevMode() && geolocale.getId() == 284) s_log.warn("getImageCount() query:" + query);  
          
        rset = stmt.executeQuery(query);
        while (rset.next()) {

          geolocale.setImageCount(rset.getInt("image_count"));
        }

      } catch (SQLException e) {
        s_log.error("getImageCount() e:" + e);
      } finally {
        DBUtil.close(stmt, rset, "getImageCount()");
      } 
    }      

public static int a = 0;
public static int b = 0;
public static int c = 0;

    public void updateCentroid(Geolocale geolocale) {

      String centroid =  geolocale.getCentroid();
      String boundingBox = geolocale.useBoundingBox();

      if (centroid.contains("null")) return;

      if (boundingBox == null) {
        ++a;
        //A.log("updateCentroid() boundingBox is null for name:" + geolocale.getName() + " source:" + geolocale.getSource());
      } else {
        if (!boundingBox.contains("null")) {        
          boolean isWithin = LocalityOverview.isWithinBounds(centroid, boundingBox);
          if (!isWithin) {
            ++b;
            s_log.warn("updateCentroid() id:" + geolocale.getId() + " name:" + geolocale.getName() + " centroid:" + centroid + " NOT IN " + boundingBox);
            return;
          }
        }
      }
      
      String dml = null;
      Statement stmt = null;      
      try {

          stmt = DBUtil.getStatement(getConnection(), "updateCentroid()");
          int x = 0;

          dml = "update geolocale " 
            + " set centroid = '" + centroid + "'"
            + " where id = " + geolocale.getId();

          //A.log("updateCentroid() dml:" + dml);
          x = stmt.executeUpdate(dml);

      } catch (SQLException e) {
          s_log.error("updateCentroid() e:" + e);
      } finally {
          DBUtil.close(stmt, null, "updateCentroid()");
      }
    }

    public void updateBoundingBox(Geolocale geolocale) {

      String centroid =  geolocale.getCentroid();
      String boundingBox = geolocale.getBoundingBox();

      if (boundingBox.contains("null")) return;

      if (centroid == null) {
        ++a;
        //A.log("updateCentroid() boundingBox is null for name:" + geolocale.getName() + " source:" + geolocale.getSource());
      } else {
        if (!boundingBox.contains("null")) {        
          boolean isWithin = LocalityOverview.isWithinBounds(centroid, boundingBox);
          if (!isWithin) {
            ++b;
            s_log.warn("updateBoundingBox() id:" + geolocale.getId() + " name:" + geolocale.getName() + " centroid:" + centroid + " NOT IN " + boundingBox);
            return;
          }
        }
      }
      
      String dml = null;
      Statement stmt = null;      
      try {

          stmt = DBUtil.getStatement(getConnection(), "updateBoundingBox()");
          int x = 0;

          dml = "update geolocale " 
            + " set bounding_box = '" + boundingBox + "'"
            + " where id = " + geolocale.getId();

          //A.log("updateCentroid() dml:" + dml);
          x = stmt.executeUpdate(dml);

      } catch (SQLException e) {
          s_log.error("updateBoundingBox() e:" + e);
      } finally {
          DBUtil.close(stmt, null, "updateBoundingBox()");
      }
    }

// ------------- Populate Geolocale_Taxon -----------------------

    /*
    Note that Geolocale are handled differently from Museums.  Taxa in the geolocales on the
    subregion level are stored in the geolocale_taxon table.
    */
        
    public void updateCounts() throws SQLException {
    
      // Update the image_count and specimen_count of geolocales.
      // allCountCrawls will be calling GeolocaleTaxonCountDb.countCrawls() (and the other overviews)
      // which will aggregate these into the counts available on overview pages and taxonomic pages.
    
      //if (true) return;
    
  	  LogMgr.appendLog("compute.log", "  Populating Geolocales", true);    
  	      
      s_log.warn("updateCounts() Adm1s..."); // This stage is way slow. 1hr+
      updateAdm1Counts();

      s_log.warn("updateCounts() Countries...");
      updateCountryCounts();

      s_log.warn("updateCounts() Subregions...");
      updateSubregionCounts();

      s_log.warn("updateCounts() Regions...");
      updateRegionCounts();

      // These really should be in GeolocaleTaxonDb, though they operate on both.
      calcEndemic();
      calcIntroduced();

      updateColors();

	  //LogMgr.appendLog("compute.log", "Geolocales populated", true);                    
    }
            
    public void updateAdm1Counts() throws SQLException {
      ArrayList<Geolocale> adm1List = GeolocaleMgr.getValidAdm1s();
      for (Geolocale geolocale : adm1List) {
        if (geolocale.isLive())
          updateCounts(geolocale.getId());
      }
  	  LogMgr.appendLog("compute.log", "    Adm1s populated", true);    
    }
    public void updateCountryCounts() throws SQLException {
      ArrayList<Geolocale> countryList = GeolocaleMgr.getValidCountries();
      for (Geolocale geolocale : countryList) {
        updateCounts(geolocale.getId());
      }
	  LogMgr.appendLog("compute.log", "    Countries populated", true);
    }
    public void updateSubregionCounts() throws SQLException {
      ArrayList<Geolocale> subregionList = GeolocaleMgr.getGeolocales("subregion");
      for (Geolocale geolocale : subregionList) {
        updateCounts(geolocale.getId());
      }
	  LogMgr.appendLog("compute.log", "    Subregions populated", true);
    }
    public void updateRegionCounts() throws SQLException {
      ArrayList<Geolocale> regionList = GeolocaleMgr.getGeolocales("region");
      for (Geolocale geolocale : regionList) {
        updateCounts(geolocale.getId());
      }
	  LogMgr.appendLog("compute.log", "    Regions populated", true);
    }
           
    private void updateCountsFromSpecimenData(int geolocaleId) {
      //A.log("updateCountsFromSpecimenData() geolocaleId:" + geolocaleId);
      // set the image_count and specimen_count in the geolocale table.
      Geolocale geolocale = getFromSpecimenData(geolocaleId);
      if (geolocale == null) {
        s_log.error("updateCountsFromSpecimenData() geolocale not found for id:" + geolocaleId);
        return;
      }

      String dml = null;
      Statement stmt = null;      
      try {
          stmt = DBUtil.getStatement(getConnection(), "updateCountsFromSpecimenData()");
          int x = 0;

          dml = "update geolocale " 
            + " set image_count = " + geolocale.getImageCount()
            + "  , specimen_count = " + geolocale.getSpecimenCount()
            + " where id = " + geolocale.getId();

          //A.log("updateCountsFromSpecimenData() dml:" + dml);
          x = stmt.executeUpdate(dml);
        
      } catch (SQLException e) {
          s_log.error("updateCountsFromSpecimenData() e:" + e);
      } finally {
          DBUtil.close(stmt, null, "updateCountsFromSpecimenData()");
      }      
    }
                  
    public void updateCounts(int geolocaleId) throws SQLException {
      // Crawl the Geolocale_taxon table to find the counts.
      (new GeolocaleTaxonCountDb(getConnection())).childrenCountCrawl(geolocaleId);

      updateCountsFromSpecimenData(geolocaleId);

      // update fields (title, image_count, subfamily_count, genus_count, species_count).                    
      finish(geolocaleId); 
    }

    // ------------ Introduced ---------------

	/* First we will loop through the taxon_prop introducedMaps. 
		 For each, select the geolocale_taxon records for that taxon.
		   For each, select the geolocale (and it's bioregion).
			 If that bioregion is in the introducedMap as true, update the geolocale_taxon.
	   Finally, calculate the number of introducedSpecies and update the geolocale record with it.06-03
	*/

    public int calcIntroduced() {
      // Set all to false prior to setting specifics to true below...
      updateGeolocaleTaxonField("introduced", 0, null);
      updateGeolocaleFieldCount("introduced", 0, 0);
      
      return calcIntroducedGeolocales();
    }

    public int calcIntroducedGeolocales() {
      Statement stmt = null;
      ResultSet rset = null;
      int totCount = 0;

      try {
        stmt = DBUtil.getStatement(getConnection(), "calcIntroducedGeolocales()");

        // Loop through all of the geolocale_taxa records of taxa on the introduced list.
        String query = "select gt.geolocale_id, gt.taxon_name, g.bioregion"
          + " from geolocale_taxon gt, geolocale g"
          + " where gt.geolocale_id = g.id"
          + " and g.georank in ('country', 'adm1')"
          + " and taxon_name in " 
          + "     (select pt.taxon_name from proj_taxon pt, taxon t " 
          + "      where pt.taxon_name = t.taxon_name and (t.taxarank = 'species' or t.taxarank = 'subspecies') "
          + "        and project_name = 'introducedants')"
		  + " order by geolocale_id";

		A.log("calcIntroducedGeolocales() query:" + query);

        // Break on geolocale to record the count.
        int lastGeolocaleId = 0;
        int count = 0;

        rset = stmt.executeQuery(query);
        while (rset.next()) {          
          int geolocaleId = rset.getInt("geolocale_id");
          String taxonName = rset.getString("taxon_name");
          String bioregion = rset.getString("bioregion");

          // If a break on geolocale, then update the count appropriately.
          if (lastGeolocaleId != 0 && lastGeolocaleId != geolocaleId) {
            // A break has occured.
            //A.log("calcIntroduceGeolocales() geolocaleId:" + geolocaleId + " lastGeolocaleId:" + lastGeolocaleId + " count:" + count);
            updateGeolocaleFieldCount("introduced", lastGeolocaleId, count);
            count = 0;
          }

          // If the taxon is introduced in this bioregion then flag it as such.
          boolean isIntroduced = TaxonPropMgr.isIntroduced(taxonName, bioregion);

          if (isIntroduced) {
            //if (2 == geolocaleId) A.log("calcIntroducedGeolocales() isIntroduced:" + isIntroduced + " taxonName:" + taxonName + " bioregion:" + bioregion);          
             ++totCount;
             ++count;
          }
          
          //if (taxonName.contains("corde")) A.log("calcIntroducedGeolocales() isIntroduced:" + isIntroduced + " geolocaleId:" + geolocaleId + " taxonName:" + taxonName + " bioregion:" + bioregion);          
          if (isIntroduced) {
            updateGeolocaleTaxonField("introduced", geolocaleId, taxonName);
		  }

          lastGeolocaleId = geolocaleId;          
        }
        // After the loop is complete, update the last record that didn't "break on".
        if (count > 0) {
          updateGeolocaleFieldCount("introduced", lastGeolocaleId, count);          
        }      

      } catch (SQLException e) {
        s_log.error("calcIntroducedGeolocales() e:" + e);
      } finally {
        DBUtil.close(stmt, rset, "calcIntroducedGeolocales()");
      }
      return totCount;
    }    


    // ------------ Endemic ---------------

    /* 
       Endemic means that the ant is only found in one place. Could be endemic to a 
       country, or an adm1, or a mountain. Distinct from Introduced, this is calculated 
       directly from geolocale/taxon data and stored in the geolocale_taxon as a flag, 
       and the geolocale as a count.
    */

    public int calcEndemic() {
      int c = 0;
      // Set all to false prior to setting specifics to true below...
      c += updateGeolocaleTaxonField("endemic", 0, null);
      c += updateGeolocaleFieldCount("endemic", 0, 0);
      
      c += calcCountryEndemism();
      c += calcAdm1Endemism();

      //if (!AntwebProps.isDevMode())
      calcHigherEndemism();

      return c;
    }

// ----------------
        
    private int calcCountryEndemism() {
      String query = "select gt.taxon_name taxon_name, max(gt.geolocale_id) geolocale_id, count(*) count"
		+ " from geolocale g, geolocale_taxon gt, taxon"
		+ " where g.id = gt.geolocale_id and taxon.taxon_name = gt.taxon_name"
		+ " and taxon.status != 'morphotaxon' and taxon.taxarank in ('species', 'subspecies')"
        + " and taxon.fossil = 0"
		+ " and g.georank = 'country'"
	    //+ " and g.georank in ('country', 'adm1')" // This would half the result set size.
        + (new StatusSet()).getAndCriteria()
              // Project.ALLANTWEBANTS
		+ " and taxon.family = 'formicidae'"
		+ " group by gt.taxon_name having count(*) = 1 " 
		+ " order by geolocale_id";
      
      A.log("calcCountryEndemism() query:" + query);
    
      return calcEndemism(query);
    }

    private int calcAdm1Endemism() {
      String query = "select gt.taxon_name taxon_name, max(gt.geolocale_id) geolocale_id, count(*) count"
        + " from geolocale g, geolocale_taxon gt, taxon"  
        + " where g.id = gt.geolocale_id and taxon.taxon_name = gt.taxon_name"
        + " and taxon.status != 'morphotaxon' and taxon.taxarank in ('species', 'subspecies') "
        + " and taxon.fossil = 0"
        + " and g.georank = 'adm1'"
        + " and (gt.taxon_name, g.parent) in (select gt.taxon_name, g.name from geolocale g, geolocale_taxon gt where g.id = gt.geolocale_id and gt.is_endemic = 1)"
        + (new StatusSet()).getAndCriteria()
		+ " and taxon.family = 'formicidae'"
        + " group by gt.taxon_name having count(*) = 1 " 
        + " order by geolocale_id";

      //A.log("calcAdm1Endemism() query:" + query);

      return calcEndemism(query);
    }
        
    private int calcEndemism(String query) {
      int c = 0;
      Statement stmt = null;
      ResultSet rset = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "calcEndemism()");

        //A.log("calcEndemism() query:" + query);
        //AntwebUtil.logShortStackTrace();

        rset = stmt.executeQuery(query);    
        
        int lastGeolocaleId = 0;
        int count = 0;
        while (rset.next()) {
          ++count;
          int geolocaleId = rset.getInt("geolocale_id");
          String taxonName = rset.getString("taxon_name");

          //if (GeolocaleMgr.getGeolocale(geolocaleId).getGeorank().equals("adm1")) A.log("calcEndemism() adm1:" + geolocaleId);

          c += updateGeolocaleTaxonField("endemic", geolocaleId, taxonName);

          // If a break on geolocale, then update the count appropriately.
          if (lastGeolocaleId != 0 && lastGeolocaleId != geolocaleId) {
            // A break has occured.
            c += updateGeolocaleFieldCount("endemic", lastGeolocaleId, count);          
            count = 0;
          }
          
          lastGeolocaleId = geolocaleId;          
        }
        // After the loop is complete, update the last record that didn't "break on".
        if (count > 0) {
          c += updateGeolocaleFieldCount("endemic", lastGeolocaleId, count);          
        }        
      } catch (SQLException e) {
        s_log.error("calcEndemism() query:" + query + " e:" + e.toString());
      } finally {
        DBUtil.close(stmt, rset, this, "calcEndemism()");
      }    
      return c;
    }

// ---

    //private static int s_debugCount = 0;
    // Next two methods are used for both endemic and introduced.  
    private int updateGeolocaleFieldCount(String field, int geolocaleId, int count) {
        int c = 0;
        String updateDml = "update geolocale set " + field + "_species_count = ";
        if (geolocaleId == 0) {
          updateDml += 0;
        } else {
          updateDml += count + " where id = " + geolocaleId;
        }

        //if (geolocaleId == 134) {
        //  ++ s_debugCount;
        //  A.log("updateGeolocaleFieldCount() debugCount:" + s_debugCount + "field:" + field + " id:" + geolocaleId + " dml:" + updateDml);
        //}

        Statement stmt = null;
        try {
			stmt = DBUtil.getStatement(getConnection(), "updateGeolocaleFieldCount()");
			c = stmt.executeUpdate(updateDml);
        } catch (SQLException e) {
            s_log.error("updateGeolocaleFieldCount() e:" + e);
        } finally {
            DBUtil.close(stmt, null, this, "updateGeolocaleFieldCount()");
        }
        return c;
    }

    // Will set to true (1)
    private int updateGeolocaleTaxonField(String field, int geolocaleId, String taxonName) {
        int c = 0;
        String updateDml = "update geolocale_taxon set is_" + field + " = "; // will be is_endemic or is_introduced
        if (geolocaleId == 0) {
          updateDml += "false";
        } else {
          updateDml += "true where geolocale_id = " + geolocaleId + " and taxon_name = '" + taxonName + "'"; 
        }
            
        Statement stmt = null;
        try {
			stmt = DBUtil.getStatement(getConnection(), "updateGeolocaleTaxonField()");
			c = stmt.executeUpdate(updateDml);
        } catch (SQLException e) {
            s_log.error("updateGeolocaleTaxonField() e:" + e);
        } finally {
			DBUtil.close(stmt, null, this, "updateGeolocaleTaxonField()");
        }
        return c;
    }

// ----------------

    // After the Adm1 and Country endemism is calculated from geolocale_taxon data we can make sure that those values
    // are reflected in the higher georanks (subregion and region).
    public int calcHigherEndemism() {

        // select all of the geolocale_taxa where endemism = 1
        int a = calcHigherEndemismAdm1();
        A.log("calcHigherEndemism() country:" + a);
        int b = calcHigherEndemismCountry();
        A.log("calcHigherEndemism() subregion:" + b);
        int c = calcHigherEndemismSubregion();
        A.log("calcHigherEndemism() region:" + c);

        return a + b + c;
    }

    private int calcHigherEndemismAdm1() {
        String query = "select g.id, g.name, g.georank, gt.taxon_name from geolocale_taxon gt, geolocale g "
          + " where gt.geolocale_id = g.id and is_endemic = 1 and georank = 'adm1'";
        int c = 0;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "calcHigherEndemismAdm1()");
            rset = stmt.executeQuery(query);

            while (rset.next()) {
                int geolocaleId = rset.getInt("g.id");
                Adm1 adm1 = GeolocaleMgr.getAdm1(geolocaleId);

                String taxonName = rset.getString("gt.taxon_name");

                ++c;
                Country country = GeolocaleMgr.getCountry(adm1.getParent());

                int updated = updateGeolocaleTaxonField("endemic", country.getId(), taxonName);

                //A.log("calcHigherEndemismAdm1() c:" + c + " country:" + country + " taxonName:" + taxonName + " updated:" + updated);
            }
            A.log("calcHigherEndemismAdm1() c:" + c);
        } catch (SQLException e) {
            s_log.error("calcHigherEndemismAdm1() query:" + query + " e:" + e.toString());
        } finally {
            DBUtil.close(stmt, rset, this, "calcHigherEndemismAdm1()");
        }
        return c;
    }

    private int calcHigherEndemismCountry() {
        String query = "select g.id, g.name, g.georank, gt.taxon_name from geolocale_taxon gt, geolocale g "
                + " where gt.geolocale_id = g.id and is_endemic = 1 and georank = 'country'";
        int c = 0;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "calcHigherEndemismCountry()");
            rset = stmt.executeQuery(query);

            while (rset.next()) {
                int geolocaleId = rset.getInt("g.id");
                Country country = (Country) GeolocaleMgr.getGeolocale(geolocaleId);

                String taxonName = rset.getString("gt.taxon_name");

                ++c;
                Subregion subregion = (Subregion) GeolocaleMgr.getGeolocale(country.getParent());

                int updated = updateGeolocaleTaxonField("endemic", subregion.getId(), taxonName);

                //A.log("calcHigherEndemismAdm1() c:" + c + " subregion:" + subregion + " taxonName:" + taxonName + " updated:" + updated);
            }
            A.log("calcHigherEndemismCountry() c:" + c);
        } catch (SQLException e) {
            s_log.error("calcHigherEndemismCountry() query:" + query + " e:" + e.toString());
        } finally {
            DBUtil.close(stmt, rset, this, "calcHigherEndemismCountry()");
        }
        return c;
    }

    private int calcHigherEndemismSubregion() {
        String query = "select g.id, g.name, g.georank, gt.taxon_name from geolocale_taxon gt, geolocale g "
                + " where gt.geolocale_id = g.id and is_endemic = 1 and georank = 'subregion'";
        int c = 0;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "calcHigherEndemismSubregion()");
            rset = stmt.executeQuery(query);

            while (rset.next()) {
                int geolocaleId = rset.getInt("g.id");
                Subregion subregion = (Subregion) GeolocaleMgr.getGeolocale(geolocaleId);

                String taxonName = rset.getString("gt.taxon_name");

                ++c;
                Region region = (Region) GeolocaleMgr.getGeolocale(subregion.getParent());

                int updated = updateGeolocaleTaxonField("endemic", region.getId(), taxonName);

                //A.log("calcHigherEndemismAdm1() c:" + c + " region:" + region + " taxonName:" + taxonName + " updated:" + updated);
            }
            A.log("calcHigherEndemismSubregion() c:" + c);
        } catch (SQLException e) {
            s_log.error("calcHigherEndemismSubregion() query:" + query + " e:" + e.toString());
        } finally {
            DBUtil.close(stmt, rset, this, "calcHigherEndemismSubregion()");
        }
        return c;
    }
    	
    private void updateColors() {    
      String[] colors = HttpUtil.getColors();

      ArrayList<Geolocale> subregionList = GeolocaleMgr.getGeolocales("subregion");    
      int i = 0;
      // A.log("updateColors() subregions:" + subregionList);    

      for (Geolocale subregion : subregionList) {
        //A.log("updateColors() colors:" + colors[i]);    

        updateColor(subregion.getId(), colors[i]);
        ++i;      
      }

      ArrayList<Geolocale> regionList = GeolocaleMgr.getGeolocales("region");    
      i = 0;
      for (Geolocale region : regionList) {
        updateColor(region.getId(), colors[i]);
        ++i;      
      }
    }

    private void updateColor(int id, String color) {    
      UtilDb utilDb = new UtilDb(getConnection());
      utilDb.updateField("geolocale", "chart_color", "'" + color + "'", "id = " + id );
    }

    private String finish() {
      for (Geolocale subregion : GeolocaleMgr.getGeolocales("subregion")) {
        finish(subregion.getId());
      }
      return "Geolocale Finished";
    }

    private String finish(int geolocaleId) {
      updateCountableTaxonData(geolocaleId);
      updateImagedSpecimenCount(geolocaleId);
      makeCharts(geolocaleId);
      return "Geolocale Finished:" + geolocaleId;
    }

    private void updateCountableTaxonData(int geolocaleId) {        
        GeolocaleTaxonCountDb geolocaleTaxonCountDb = new GeolocaleTaxonCountDb(getConnection());
        String criteria = "geolocale_id = " + geolocaleId;
        int subfamilyCount = geolocaleTaxonCountDb.getCountableTaxonCount("geolocale_taxon", criteria, "subfamily");
        int genusCount = geolocaleTaxonCountDb.getCountableTaxonCount("geolocale_taxon", criteria, "genus");
        int speciesCount = geolocaleTaxonCountDb.getCountableTaxonCount("geolocale_taxon", criteria, "species");

		A.log("updatedCountableTaxonData(" + geolocaleId + ") speciesCount:" + speciesCount);

        criteria = "id = " + geolocaleId;
        geolocaleTaxonCountDb.updateCountableTaxonCounts("geolocale", criteria, subfamilyCount, genusCount, speciesCount);                  
    }    
      
    private void updateImagedSpecimen() {
      for (Geolocale subregion : GeolocaleMgr.getGeolocales("subregion")) {
        updateImagedSpecimenCount(subregion.getId());
      }  
    }

    private void updateImagedSpecimenCount(int geolocaleId) {
        Geolocale geolocale = GeolocaleMgr.getGeolocale(geolocaleId);        
        int count = getImagedSpecimenCount(geolocale);
        UtilDb utilDb = new UtilDb(getConnection());
        //A.log("updateImagedSpecimenCount() id:" + geolocale.getId());
        utilDb.updateField("geolocale", "imaged_specimen_count", (Integer.valueOf(count)).toString(), "id = " + geolocale.getId());
    }
  
    private int getImagedSpecimenCount(Geolocale geolocale) {
      int geolocaleId = geolocale.getId();
      int imagedSpecimenCount = 0;
      String query = null;
      Statement stmt = null;
      ResultSet rset = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "getImagedSpecimenCount()");
                
        String specimenLocaleClause = getSpecimenLocaleClause(geolocale);
        if (specimenLocaleClause == null) {
          s_log.warn("getImagedSpecimenCount() geolocale:" + geolocale + " id:" + geolocale.getId() + " No specimenLocaleClause.");
          return 0;
        }
        
        query = "select count(distinct(code)) count from specimen s, image i where s.code = i.image_of_id and " + specimenLocaleClause;

        if (289 == geolocale.getId()) {
          //A.log("getImagedSpecimenCount() query:" + query);
        }

        rset = stmt.executeQuery(query);
        while (rset.next()) {
         imagedSpecimenCount = rset.getInt("count");
        }

        //A.log("getImagedSpecimenCount() query:" + query + " imagedSpecimenCount:" + imagedSpecimenCount);       
      } catch (SQLException e) {
        s_log.error("getImagedSpecimenCount() e:" + e + " query:" + query);
      } finally {
        DBUtil.close(stmt, rset, "getImagedSpecimenCount()");
      } 
      return imagedSpecimenCount;
    }    
   
    public String getSpecimenLocaleClause(Geolocale geolocale) {
      String clause = null;
      
      if ("adm1".equals(geolocale.getGeorank())) {
        String name = AntFormatter.escapeQuotes(geolocale.getName());  
        clause = " adm1 = '" + name + "'";
        //A.log("getSpecimenLocaleClause() clause:" + clause);
      } else {

        String countryList = getCountryList(geolocale);
        if (countryList == null) {
          s_log.warn("getImagedSpecimenCount() geolocale:" + geolocale + " id:" + geolocale.getId() + " No Country List.");
          return null;
        }
        clause = " country in " + countryList;
      }   
      return clause;
    }

    private String getCountryList(Geolocale geolocale) {
      String countryList = "";
      String query = null;
      Statement stmt = null;
      ResultSet rset = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "getCountryList()");

        if ("region".equals(geolocale.getGeorank())) {
          query = "select distinct country.name from geolocale region, geolocale subregion, geolocale country " 
            + " where region.name = subregion.parent and subregion.name = country.parent "
            + " and country.name not like \"%'%\""
            + " and region.id = " + geolocale.getId();
        } else {
          if ("subregion".equals(geolocale.getGeorank())) {
            query = "select distinct country.name from geolocale subregion, geolocale country " 
              + " where subregion.name = country.parent "
              + " and country.name not like \"%'%\""
              + " and subregion.id = " + geolocale.getId();
          } else {
            if ("country".equals(geolocale.getGeorank())) {
              query = "select distinct country.name from geolocale country " 
                + " where country.name not like \"%'%\""
                + "   and country.id = " + geolocale.getId();
            } else {
              s_log.error("getCountryList() unsupported georank:" + geolocale.getGeorank());
              return null;
            }
          }
        }
        
        //A.log("getCountryList() rank:" + geolocale.getGeorank() + " query:" + query);
        rset = stmt.executeQuery(query);
      
        int i = 0;
        while (rset.next()) {
          if (i == 0) countryList += "('";
          if (i > 0) countryList += ",'";
          countryList += rset.getString("name");
          countryList += "'";
          ++i;
        }
        countryList += ")";

        if (i == 0) return null;

        //A.log("getCountryList() countryList:" + countryList);       
      } catch (SQLException e) {
        s_log.error("getCountryList() e:" + e);
      } finally {
        DBUtil.close(stmt, rset, "getCountryList()");
      } 
      return countryList;    
    }
    
        
    // --- Charts ---
        
    public void makeCharts() {
      for (Geolocale subregion : GeolocaleMgr.getGeolocales("subregion")) {
        makeCharts(subregion.getId());
      }  
    }     
            
    public void makeCharts(int geolocaleId) {
      //A.log("makeCharts(" + geolocaleId + ")");
      UtilDb utilDb = new UtilDb(getConnection());
      GeolocaleTaxonCountDb geolocaleTaxonCountDb = new GeolocaleTaxonCountDb(getConnection());
      String criteria = "id = " + geolocaleId;
      String taxonCountQuery = getTaxonSubfamilyDistJsonQuery(criteria);
      String specimenCountQuery = getSpecimenSubfamilyDistJsonQuery(criteria);

      utilDb.updateField("geolocale", "taxon_subfamily_dist_json", "'" + geolocaleTaxonCountDb.getTaxonSubfamilyDistJson(taxonCountQuery) + "'", criteria);
      utilDb.updateField("geolocale", "specimen_subfamily_dist_json", "'" + geolocaleTaxonCountDb.getSpecimenSubfamilyDistJson(specimenCountQuery) + "'", criteria);
    }

    public String getTaxonSubfamilyDistJsonQuery(String criteria) {
      String query = "select subfamily, count(*) count, t.chart_color " 
          + " from geolocale_taxon gt, taxon t, geolocale g " 
          + " where gt.taxon_name = t.taxon_name " 
          + " and g.id = gt.geolocale_id "
          + " and g." + criteria
          + " and t.status in ('valid', 'unrecognized', 'morphotaxon', 'indetermined', 'unidentifiable') " 
          + " and family = 'formicidae'" 
          + " and taxarank = 'species'"
          + " group by subfamily, t.chart_color"; 
          //if (AntwebProps.isDevMode()) s_log.info("getTaxonSubfamilyDistJsonQuery() query:" + query);
      return query;
    }
    
    public String getSpecimenSubfamilyDistJsonQuery(String criteria) {
      String query = "select subfamily, count(*) count " 
          + " from geolocale_taxon gt, specimen s, geolocale g " 
          + " where gt.taxon_name = s.taxon_name " 
          + " and g.id = gt.geolocale_id "
          + " and g." + criteria
          + " and s.status in ('valid', 'unrecognized', 'morphotaxon', 'indetermined', 'unidentifiable') " 
          + " and s.family = 'formicidae' " 
          + " group by subfamily"; 
      return query;
    }     
             
    // To support Change View options
	public ArrayList<Geolocale> getChildrenWithTaxon(String taxonName, String georank, Geolocale parent) {
        ArrayList<Geolocale> geolocales = new ArrayList<Geolocale>();
        Statement stmt = null;
        ResultSet rset = null;
		String query = "select name, id, georank, parent"
		   + " from geolocale g, geolocale_taxon gt" 
		   + " where gt.geolocale_id = g.id"
		   + "  and gt.taxon_name = '" + taxonName + "'"   
		   + " and g.georank = '" + georank + "'";
		   if (parent != null) {
             String parentName = Formatter.escapeQuotes(parent.getName());
			 query += " and g.parent = '" + parentName + "'";
		   }
 	    query += " order by name";
        try {
            Geolocale geolocale = null;
            taxonName = AntFormatter.escapeQuotes(taxonName);

            //A.log("getChildrenWithTaxon() query:" + query);
            stmt = DBUtil.getStatement(getConnection(), "getChildrenWithTaxon()");
            rset = stmt.executeQuery(query);

            while (rset.next()) {
              geolocale = null;
              //String georank = rset.getString("georank");
              if ("region".equals(georank)) geolocale = new Region();
              if ("subregion".equals(georank)) geolocale = new Subregion();
              if ("country".equals(georank)) geolocale = new Country();
              if ("adm1".equals(georank)) geolocale = new Adm1();
              
              geolocale.setGeorank(georank);
              geolocale.setId(rset.getInt("id"));
              geolocale.setName(rset.getString("name"));
              geolocale.setParent(rset.getString("parent"));
              geolocales.add(geolocale);
            }
        } catch (SQLException e) {
            s_log.error("getChildrenWithTaxon() for taxonName:" + taxonName + " query:" + query + " e:" + e);
            //throw e;
        } finally {
            DBUtil.close(stmt, rset, this, "getChildrenWithTaxon()");
        }  
        //A.log("getChidrenWithTaxon() size:" + geolocales.size() + " query:" + query);          
        return geolocales;
	 }
    
// ------------------------------------------------------------------ 
    
    

    public void deleteFetchedAdm1(String source) {    
        String dml = "delete from geolocale where georank = 'adm1' and source = '" + AntFormatter.escapeQuotes(source) + "'";
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "GeolocaleDb.deleteFetchedAdm1()");
            int x = stmt.executeUpdate(dml);
//A.log("deleteFetchedAdm1(" + source + ") x:" + x + " dml:" + dml);
        } catch (SQLException e) {
            s_log.error("deleteFetchedAdm1() e:" + e);
        } finally {
            DBUtil.close(stmt, null, "GeolocaleDb.deleteFetchedAdm1()");
        }
    }

    public void deleteFetchedAdm1(Geolocale country, String source) {    
        String dml = "delete from geolocale where georank = 'adm1' and source = '" + source + "' and parent = '" + AntFormatter.escapeQuotes(country.getName()) + "'";
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "GeolocaleDb.deleteFetchedAdm1()");
            int x = stmt.executeUpdate(dml);
//A.log("deleteFetchedAdm1(" + country + ", " + source + ") x:" + x + " dml:" + dml);
        } catch (SQLException e) {
            s_log.error("deleteFetchedAdm1() e:" + e);
        } finally {
            DBUtil.close(stmt, null, "GeolocaleDb.deleteFetchedAdm1()");
        }
    }    
    
    // Called from FlickrPlace and GeonamesPlace to insert the adm1
    public void makeAdm1(String adm1Name, String countryName, FlickrPlace flickrPlace, String source) {
      // Try to insert adm1. If already exists, then update the geo data.

      String validName = null;

      //A.log("GeolocaleDb.makeAdm1(" + adm1Name + ", " + countryName + ", " + flickrPlace + ")"); 
    
      Geolocale diacriticTwinAdm1 = getDiacriticTwinAdm1(adm1Name, countryName);
      // Asuncion and Asunción. If we find a similarly named adm1, update it's bounds.
      if (diacriticTwinAdm1 != null && diacriticTwinAdm1.getId() > 0) {
        updateGeoData(diacriticTwinAdm1.getId(), null, flickrPlace, null);
        if (diacriticTwinAdm1.getIsValid()) {
          // If a twin, set as valid name of the adm1 being made.
          validName = diacriticTwinAdm1.getName();
        }
      }

      //Geolocale adm1 = GeolocaleMgr.getAnyAdm1(adm1Name, countryName);
      Geolocale adm1 = getAdm1(adm1Name, countryName);
      if (adm1 == null) {
        insertAdm1(adm1Name, countryName, validName, flickrPlace, source);
      } else {
         updateGeoData(adm1.getId(), validName, flickrPlace, source);
      }

    }

    public Geolocale getDiacriticTwinAdm1(String adm1Name, String countryName) {
      Statement stmt = null;
      ResultSet rset = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "getDiacriticTwinAdm1()");                        
        String query = "select id, name from geolocale where name like '" + AntFormatter.escapeQuotes(adm1Name) + "' collate utf8_general_ci and name != '" + AntFormatter.escapeQuotes(adm1Name) + "'";

        rset = stmt.executeQuery(query);
        while (rset.next()) {
          int id = rset.getInt("id");
          String name = rset.getString("name");
          //A.log("getDiacriticTwinAdm1() name:" + name + " id:" + id); 
          return GeolocaleMgr.getGeolocale(id);
        }
      } catch (Exception e) {
        s_log.error("getDiacriticTwinAdm1() e:" + e);
      } finally {
        DBUtil.close(stmt, rset, "getDiacriticTwinAdm1()");
      } 
      return null;
    }  
    
    public void insertAdm1(String adm1Name, String countryName, String validName, FlickrPlace place, String source) {

        if (adm1Name == null || countryName == null) {
          s_log.warn("insertAdm1() adm1Name:" + adm1Name + " countryName:" + countryName);
          return;
        }

        String geoDataClause = "";
        String geoDataCols = "";
        if (place != null) {
          geoDataCols += ", bounding_box, centroid, woe_id"; //latitude, longitude, woe_id";
          geoDataClause += ", '" + place.getBoundingBox() + "', '" + place.getLatitude() + "', '" + place.getLongitude() + "', '" + place.getWoeId() + "'";
        }  

        int isValid = 1;
        if (validName != null) {
          geoDataCols += ", valid_name";
          geoDataClause += ", '" + AntFormatter.escapeQuotes(validName) + "'";
          isValid = 0;
        }

        if (FlickrPlace.source.equals(source)) isValid = 0;
        if (GeonamesPlace.source.equals(source)) isValid = 0;
        // Geonet are canonical. Geonames and Flickr names are not. May be useful for comparison, or copying of bounds.

        String dml = "insert into geolocale (name, georank, parent" + geoDataCols + ", source, is_valid, rev) " 
          + " values ('" + AntFormatter.escapeQuotes(adm1Name) + "', 'adm1', '" + AntFormatter.escapeQuotes(countryName) + "'" + geoDataClause + ", '" + source + "', " + isValid + ", " + AntwebProps.getRev() + ")";
        Statement stmt = null;
        try {
            //A.log("insertAdm1() dml:" + dml);

            stmt = DBUtil.getStatement(getConnection(), "GeolocaleDb.insertAdm1()");
            int x = stmt.executeUpdate(dml);        

        } catch (SQLException e) {
            s_log.error("insertAdm1() e:" + e + " dml:" + dml);
        } finally {
            DBUtil.close(stmt, null, "GeolocaleDb.insertAdm1()");
        }
    }   

    // Used only by Geoset.
    public void insertCountry(String countryName, String validName, String source) {

        int isValid = 1;
        String cols = "";
        String clause = "";
        if (validName != null) {
          cols += ", valid_name";
          clause += ", '" + AntFormatter.escapeQuotes(validName) + "'";
          
          Geolocale validNameCountry = getCountry(validName);
          if (validNameCountry != null) {
            cols += ", parent";
            clause += ", '" + AntFormatter.escapeQuotes(validNameCountry.getParent()) + "'";
          }
          isValid = 0;
        }

        if (FlickrPlace.source.equals(source)) isValid = 0;
        if (GeonamesPlace.source.equals(source)) isValid = 0;
        // Geonet are canonical. Geonames and Flickr names are not. May be useful for comparison, or copying of bounds.

        String dml = "insert into geolocale (name, georank" + cols + ", source, is_valid, rev) " 
          + " values ('" + AntFormatter.escapeQuotes(countryName) + "', 'country'" + clause + ", '" + source + "', " + isValid + ", " + AntwebProps.getRev() + ")";
        Statement stmt = null;
        try {
            //A.log("insertAdm1() dml:" + dml);

            stmt = DBUtil.getStatement(getConnection(), "GeolocaleDb.insertCountry()");
            int x = stmt.executeUpdate(dml);        

        } catch (SQLException e) {
            s_log.error("insertCountry() e:" + e + " dml:" + dml);
        } finally {
            DBUtil.close(stmt, null, "GeolocaleDb.insertCountry()");
        }
    }   

    public void updateGeoData(int id, String validName, FlickrPlace place, String source) {     
    
      //A.log("updateGeoData() id:" + id + " place:" + place);    
      
      // If the source was specimen, and the new source is geonames, update the source to geonames.
      // Reason being, geonames are considered canonical.
      // Yes, but then we can't delete and start over without deleting potentially update records.
      // Leave as source="specimen" but set is_valid=1
      // Leave it. We can always see if it has a woe_id and bounds to see if flickr found it.
      
      ArrayList<String> clauseArray = new ArrayList<String>();
      
      Geolocale oldGeolocale = getGeolocale(id);
          
      if (oldGeolocale == null) {
        A.log("updateGeoData() how can old be null and update? id:" + id + " validName:" + validName + " source:" + source);
      }
      
      String isValidClause = null;
      
      if (source != null) {
        if (GeonetMgr.source.equals(source)) {
          if (!"UN".equals(oldGeolocale.getSource())) {
//if (AntwebProps.isDevOrStageMode()) s_log.warn("updateGeoData() id:" + id + " source:" + oldGeolocale.getSource());          
            clauseArray.add(" source = '" + source + "'");      
          }
          if ("adm1".equals(oldGeolocale.getGeorank())) {
            if (validName == null) {
              clauseArray.add(" is_valid = 1");
            } 
          }
        }
      }
      
      if (validName != null) {
        clauseArray.add(" is_valid = 0");
        clauseArray.add(" valid_name = '" + AntFormatter.escapeQuotes(validName) + "'");
      }

      if (place != null) {
//          clauseArray.add(" woe_id = '" + place.getWoeId() + "', bounding_box = '" + place.getBoundingBox() + "', latitude = '" + place.getLatitude()+ "', longitude = '" + place.getLongitude() + "'");
          clauseArray.add(" woe_id = '" + place.getWoeId() + "', bounding_box = '" + place.getBoundingBox() + "', centroid = '" + place.getLatitude()+ ", " + place.getLongitude() + "'");
      }
      
      // Nope. Only on insert.
      //clauseArray.add(" rev = " + AntwebProps.getRev());
      
      String clauses = "";
      int i = 0;
      for (String clause : clauseArray) {
        i++;
        if (i > 1) clauses += ", ";
        clauses += clause;
      }
      if (i == 0) return;

      Statement stmt = null;
      String dml = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "updateGeoData()");

        dml = "update geolocale set " + clauses + " where id = " + id;
        stmt.executeUpdate(dml); 

        //if (AntwebProps.isStageMode() && dml.contains("valid_name")) s_log.warn("updateGeoData() dml" + dml);      
      } catch (SQLException e) {
        s_log.warn("updateGeoData() e:" + e + " dml:" + dml);
      } finally {
        DBUtil.close(stmt, "updateGeoData()");
      }   
    }    

    public ArrayList<Geolocale> getParentlessCountries() {
      ArrayList<Geolocale> parentlessCountries = new ArrayList<Geolocale>();
      Statement stmt = null;
      ResultSet rset = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "getParentlessCountries()");                        
        String query = "select id, name, source from geolocale where parent is null and georank = 'country' order by name";
        rset = stmt.executeQuery(query);
        while (rset.next()) {
          Geolocale parentlessCountry = new Geolocale();
          parentlessCountry.setId(rset.getInt("id"));
          parentlessCountry.setName(rset.getString("name"));
          parentlessCountry.setSource(rset.getString("source"));
          parentlessCountries.add(parentlessCountry);
        }
      } catch (Exception e) {
        s_log.error("getParentlessCountries() e:" + e);
      } finally {
        DBUtil.close(stmt, rset, "getParentlessCountries()");
      } 
      return parentlessCountries;
    }  
        
    public void addAdm1FromSpecimenData(String adm1, String country, int accessGroupId) {
        String source = "specimen" + accessGroupId;
        String dml = "insert into geolocale (name, georank, parent, source, is_valid, rev) " 
          + " values ('" + AntFormatter.escapeQuotes(adm1) + "', 'adm1', '" + AntFormatter.escapeQuotes(country) + "', '" + source + "', 0, " + AntwebProps.getRev() + ")";

		//A.log("addAddm1FromSpecimenData() 1 adm1:" + adm1 + " country:" + country + " accessGroupId:" + accessGroupId + " x:" + " dml:" + dml);

        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "GeolocaleDb.addAdm1FromSpecimenData()");
            int x = stmt.executeUpdate(dml);        

		//A.log("addAddm1FromSpecimenData() 2 adm1:" + adm1 + " country:" + country + " accessGroupId:" + accessGroupId + " x:" + x + " dml:" + dml);

        } catch (com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException e) {         
            // no problem. Set the valid name...
        } catch (SQLException e) {
            s_log.error("addAdm1FromSpecimenData() e:" + e + " dml:" + dml);
        } finally {
            DBUtil.close(stmt, null, "GeolocaleDb.addAdm1FromSpecimenData()");
        }
    }

    public void adjustGeolocale(EditGeolocaleForm form) {
      // Called from the Geolocale Mgr individual update feature.
      Statement stmt = null;
      String dml = null;
      String validNameClause = "";
      Geolocale validNameGeolocale = null;
      if (form.getValidNameId() > 0) {
         validNameGeolocale = getGeolocale(form.getValidNameId());
         if (validNameGeolocale != null) validNameClause = ", valid_name = '" + AntFormatter.escapeQuotes(validNameGeolocale.getName()) + "'";
      }
      if (form.getValidNameId() == -1) {
         validNameClause = ", valid_name = null";
      }
      if ("".equals(validNameClause) && form.getValidName() != null) {
         validNameClause = ", valid_name = '" + AntFormatter.escapeQuotes(form.getValidName()) + "'";
      }
      String revClause = ", rev = " + AntwebProps.getRev(); 
      try {
        stmt = DBUtil.getStatement(getConnection(), "adjustGeolocale()");
        dml = "update geolocale set is_valid = " + form.isValid() + ", is_live = " + form.isLive() + validNameClause + revClause 
           + " where id = " + form.getId();
        stmt.executeUpdate(dml); 

        //A.log("adjustGeolocale() dml" + dml);      
      } catch (SQLException e) {
        s_log.warn("adjustGeolocale() e:" + e);
      } finally {
        DBUtil.close(stmt, "adjustGeolocale()");
      }

      FlickrPlace place = null;
      if (form.getWoeId() != null && !"".equals(form.getWoeId())) {       
        place = FlickrPlace.scrapePlace(form.getWoeId());
        if (place != null) {
          setFlickrData(form.getId(), place.getWoeId(), place.getBoundingBox(), place.getLatitude(), place.getLongitude());
        }        
      }    

      setFlickrData(validNameGeolocale, place);
    }

    public void setFlickrData(Geolocale geolocale, FlickrPlace place) {
      if (geolocale == null || place == null) return;
      
      setFlickrData(geolocale.getId(), place.getWoeId(), place.getBoundingBox(), place.getLatitude(), place.getLongitude());
    }

    public void setFlickrData(int geolocaleId, String woeId, String boundingBox, String lat, String lon) {
      if (geolocaleId <= 0) return;
	  Statement stmt = null;
	  String dml = null;
	  try {
		stmt = DBUtil.getStatement(getConnection(), "setFlickrData()");
		dml = "update geolocale set "
		  + " woe_id = '" + woeId + "'"
		  // + ", bounding_box = '" + boundingBox + "', centroid = '" + lat + ", " + lon + "'"
		  + " where id = " + geolocaleId;
		stmt.executeUpdate(dml); 

		//A.log("setFlickrData() dml" + dml);
	  } catch (SQLException e) {
		s_log.warn("setFlickrData() e:" + e);
	  } finally {
		DBUtil.close(stmt, "setFlickrData()");
	  }
	  
	}

    public void updateAdm1FromCountryData() {
      updateBioregionFromCountry();
      updateRegionFromCountry();      
    }
    private void updateBioregionFromCountry() {
      ArrayList<Geolocale> adm1s = GeolocaleMgr.getAdm1s();
      for (Geolocale adm1 : adm1s) {
        if (adm1.getBioregion() == null) {
          Geolocale country = GeolocaleMgr.getCountry(adm1.getParent());
          if (country == null) continue;
          
          String bioregion = country.getBioregion();
          
          if (bioregion == null || "null".equals(bioregion)) continue;
		  Statement stmt = null;
		  String dml = null;
		  try {
			stmt = DBUtil.getStatement(getConnection(), "updateBioregionFromCountry()");
			dml = "update geolocale set bioregion = '" + bioregion + "'"
			  + " where id = " + adm1.getId();
			stmt.executeUpdate(dml); 

			//A.log("updateBioregionFromCountry() dml:" + dml);
		  } catch (SQLException e) {
			s_log.warn("updateBioregionFromCountry() e:" + e);
		  } finally {
 			DBUtil.close(stmt, "updateBioregionFromCountry()");
		  }
        }  
      }
    }
    private void updateRegionFromCountry() {
      ArrayList<Geolocale> adm1s = GeolocaleMgr.getAdm1s();
      for (Geolocale adm1 : adm1s) {
        if (adm1.getRegion() == null) {
          Geolocale country = GeolocaleMgr.getCountry(adm1.getParent());
          if (country == null) continue;
          
          String region = country.getRegion();
          if (region == null || "null".equals(region)) continue;
          
		  Statement stmt = null;
		  String dml = null;
		  try {
			stmt = DBUtil.getStatement(getConnection(), "updateRegionFromCountry()");
			dml = "update geolocale set region = '" + region + "'"
			  + " where id = " + adm1.getId();
			stmt.executeUpdate(dml); 

			//A.log("updateRegionFromCountry() dml:" + dml);
		  } catch (SQLException e) {
			s_log.warn("updateRegionFromCountry() e:" + e);
		  } finally {
 			DBUtil.close(stmt, "updateRegionFromCountry()");
		  }
        }
      }
	}

    public void updateRev(int id, int rev) {
	  Statement stmt = null;
	  String dml = null;
	  try {
		stmt = DBUtil.getStatement(getConnection(), "updateRev()");
		dml = "update geolocale set rev = " + rev + " where id = " + id;
		stmt.executeUpdate(dml);

		//A.log("updateRev() dml:" + dml);
	  } catch (SQLException e) {
		s_log.warn("updateRev() e:" + e);
	  } finally {
		DBUtil.close(stmt, "updateRev()");
	  }
    }

    public void updateChildrenGeorankType(String country, String georankType) {
	  Statement stmt = null;
	  String dml = null;
	  try {
		stmt = DBUtil.getStatement(getConnection(), "updateChildrenGeorankType()");
		dml = "update geolocale set georank_type = '" + georankType + "' where parent = '" + country + "'";
		stmt.executeUpdate(dml);

		//A.log("updateChildrenGeorankType() dml:" + dml);
	  } catch (SQLException e) {
		s_log.warn("updateChildrenGeorankType() e:" + e);
	  } finally {
		DBUtil.close(stmt, "updateChildrenGeorankType()");
	  }
    }
    

    // These are Geolocale with duplicate names.
    public ArrayList<Geolocale> getBlackList() {
      ArrayList<Geolocale> blackList = new ArrayList<Geolocale>();
      Statement stmt = null;
      ResultSet rset = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "getBlackList()");                        
        String query = "select id, name, georank from geolocale where name in (select name from geolocale group by name having count(*) > 1) order by georank desc, is_valid desc, name, valid_name";
        rset = stmt.executeQuery(query);
        while (rset.next()) {
          Geolocale geo = new Geolocale();
          geo.setId(rset.getInt("id"));
          geo.setName(rset.getString("name"));
          geo.setGeorank(rset.getString("georank"));
          blackList.add(geo);
        }
      } catch (Exception e) {
        s_log.error("getBlackList() e:" + e);
      } finally {
        DBUtil.close(stmt, rset, "getBlackList()");
      } 
      return blackList;
    }      
 

// --------------- AutoComplete -----------------
    // To support autoComplete search box.
    public List<String> getPlaceNames() {
      return getPlaceNames("");
    }
    public List<String> getPlaceNames(String text) {
      return getPlaceNames(text, false);
    }
    public List<String> getPlaceNames(String text, boolean asHtml) {
        List<String> placeNames = new ArrayList<String>();
        String placeName = null;

        Statement stmt = null;
        ResultSet rset = null;
        String query = "select name, parent, georank from geolocale " 
          + " where name like '%" + text + "%'"
          + "   and is_live = 1"
          + " order by georank desc, name, parent"
          ;

//A.log("getPlaceNames() query:" + query);
        try {            
            stmt = DBUtil.getStatement(getConnection(), "getPlaceNames()");
            rset = stmt.executeQuery(query);

            int count = 0;
            while (rset.next()) {
                placeName = rset.getString("name");
                String parent = rset.getString("parent");
                String georank = rset.getString("georank");
                if ("adm1".equals(georank)) placeName = placeName + ", " + parent;                
                if (asHtml) placeName = "<br><a href='" + AntwebProps.getDomainApp() + "/place.do?name=" + placeName + "'>" + placeName + "</a>";
                placeNames.add(placeName);
            }
        } catch (SQLException e) {
            s_log.error("getPlaceNames() exception:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "this", "getPlaceNames()");
        }
        
        //A.log("getPlaceNames() placeNames:" + placeNames.size() + " query:" + query);        
        return placeNames;    
    }

// --------------- End AutoComplete -----------------
     
 
   public void updateGeolocaleParentHierarchy() {

     // First update the subregions of Adm1.
     if (!AntwebProps.isDevMode()) {
       ArrayList<Geolocale> adm1s = GeolocaleMgr.getAdm1s();
       for (Geolocale adm1 : adm1s) {
         String parentName = adm1.getParent();
         Geolocale parent = GeolocaleMgr.getCountry(parentName);
         String subregion = parent.getParent();
         //A.log("update adm1:" + adm1.getName() + " to have subregion:" + subregion);   
         updateSubregion(adm1.getId(), subregion);    
       }
       GeolocaleMgr.populate(getConnection(), true, false); // force reload, initalRun
     }
          
     // Update all geolocale_taxa where the region is null;
     ArrayList<Geolocale> geolocales = GeolocaleMgr.getGeolocales();     
     for (Geolocale geolocale : geolocales) {
       String subregionName = geolocale.getSubregion();
       Subregion subregion = GeolocaleMgr.getSubregion(subregionName);
       if (subregion == null) {
         //A.log("Subregion is null for subregionName:" + subregionName);
         continue;
       }
       String region = subregion.getRegion();
       //A.log("Update geolocale:" + geolocale.getName() + " set region:" + region + " for subregion:" + subregion);
       updateRegion(geolocale.getId(), region);
     }     
   }
   
    private void updateSubregion(int geolocaleId, String subregion) {
        String updateDml = "update geolocale set subregion = '" + subregion + "' where id = " + geolocaleId; 
            
        Statement stmt = null;
        try {
			stmt = DBUtil.getStatement(getConnection(), "updateSubregion()");
			stmt.executeUpdate(updateDml);
        } catch (SQLException e) {
            s_log.error("updateSubregion() e:" + e);
        } finally {
			DBUtil.close(stmt, null, this, "updateSubregion()");
        }
    }   
    private void updateRegion(int geolocaleId, String region) {
        String updateDml = "update geolocale set region = '" + region + "' where id = " + geolocaleId; 
            
        Statement stmt = null;
        try {
			stmt = DBUtil.getStatement(getConnection(), "updateRegion()");
			stmt.executeUpdate(updateDml);
        } catch (SQLException e) {
            s_log.error("updateRegion() e:" + e);
        } finally {
			DBUtil.close(stmt, null, this, "updateRegion()");
        }
    }   

 }


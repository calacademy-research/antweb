package org.calacademy.antweb.home;

//import java.util.*;
import java.sql.*;

//import org.apache.regexp.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

/**
 * Reads one locality (by locality code, by name, or either) from table specimen: place, coordinates, elevation,
 * groups, and its collections (one per collection code). Used by the Locality page (locality.do,
 * LocalityAction), the big map (bigMap.do) and the field guide. Tables specimen, ant_group.
 * Docs: docs/flows/places.md, docs/flows/specimen-and-image-pages.md. Tests: tests/e2e/test_specimen_pages.py.
 */
public class LocalityDb extends AntwebDb {

    private static final Log s_log = LogFactory.getLog(LocalityDb.class);

    public LocalityDb(Connection connection) {
      super(connection);
    }

    public Locality getLocalityByCodeOrName(String value) throws SQLException {
      Locality locality = getLocalityByCode(value);
      if (locality != null) return locality;
      locality = getLocalityByName(value);      
      s_log.debug("getLocalityByCodeOrName() value:" + value);
      return locality;
    }
  
    public Locality getLocalityByCode(String code) throws SQLException {
      // sqlLiteral, not escapeQuotes: escapeQuotes doubles a double quote, which inside a single-quoted literal
      // stays two characters, so a code such as KM55"805 was never found (2026-10-22).
      String clause = "sp.localitycode = " + DBUtil.sqlLiteral(code);
      return getLocality(clause);
    }
    
    public Locality getLocalityByName(String name) throws SQLException {
      String clause = "sp.localityname = " + DBUtil.sqlLiteral(name);
      return getLocality(clause);
    }     
    
    public Locality getLocality(String clause) throws SQLException {
       
        Locality locality = null;
    
        // Locality Query         
        String localityQuery = "select distinct sp.country, sp.island_country, sp.adm1, sp.adm2"
            + ", sp.localitycode, sp.localityname, sp.other, sp.bioregion, sp.museum"
            + ", sp.decimal_longitude, sp.decimal_latitude "
            + ", sp.localityNotes, sp.elevation, sp.elevationMaxError, sp.latlonmaxerror"
            + ", sp.access_group "
            + ", datecollectedstart, datecollectedend "
            + ", ant_group.name as groupname"
            + " from specimen as sp " 
                + " left outer join ant_group on sp.access_group = ant_group.id "
            + " where " + clause; 

        // A.log("getLocality() query:" + localityQuery);

        ResultSet localityRset=null;
        Statement locStmt = getConnection().createStatement();
        Statement colStmt = null;
        localityRset = locStmt.executeQuery(localityQuery);

        try { 
          if (localityRset != null) {
            while (localityRset.next()) {
                locality = new Locality();
                locality.setCountry(localityRset.getString("country"));
                locality.setIslandCountry(localityRset.getString("island_country"));
                locality.setAdm1(localityRset.getString("adm1"));
                locality.setAdm2(localityRset.getString("adm2"));
                locality.setLocalityName(localityRset.getString("localityname"));
                locality.setLocalityCode(localityRset.getString("localitycode"));
                locality.setBioregion(localityRset.getString("bioregion"));

                String museumCode = localityRset.getString("museum");  
                if (museumCode != null) {
                  locality.setMuseumCode(museumCode);
                  Museum museum = MuseumMgr.getMuseum(museumCode);
                  if (museum != null) locality.setMuseumName(museum.getName());
                }

                locality.setDecimalLatitude(localityRset.getFloat("decimal_latitude"));
                locality.setDecimalLongitude(localityRset.getFloat("decimal_longitude"));

                locality.setLocalityNotes(localityRset.getString("localitynotes"));
                locality.setElevation(localityRset.getString("elevation"));
                locality.setElevationMaxError(localityRset.getString("elevationmaxerror"));
                //setLocXYAccuracy(localityRset.getString("locxyaccuracy"));
                locality.setLatLonMaxError(localityRset.getString("latlonmaxerror"));
                //setLocalityNotes(localityRset.getString("localitynotes"));
 
                Group group = GroupMgr.getGroup(localityRset.getInt("access_group"));
                locality.addToGroupList(group);
                
                /*
                // then parse the sucker into the description Hashtable
                String xmlString = localityRset.getString(localityRset.findColumn("other"));
                Hashtable description = (new SpecimenXML()).getHashtable(xmlString);
                if (description == null) {
                  s_log.warn("getLocality() description is null for locality:" + this);
                  return false;
                }
                */
                // Not retrieved from the elevation database table.
                //setElevation((String) description.get("elevation"));
                
                break;
            }
          }
            
          if (locality != null) {
			  // This crazy bit... 
			  // Collection Query - Very bad object design. This should be in Collection.
			  // One row per collection code (B51, 2026-10-27): "select distinct" over the code and the habitat, method,
			  // coordinates, collector, microhabitat and dates listed a collection once for every different value of
			  // these among its specimens, and specimens without a collection code as a collection of their own, so
			  // "N collections" was higher than the collections of the locality.
			  String collectionQuery = "select sp.collectioncode"
				+ ", min(sp.habitat) as habitat, min(sp.method) as method, min(sp.decimal_longitude) as decimal_longitude"
				+ ", min(sp.decimal_latitude) as decimal_latitude, min(sp.collectedby) as collectedby"
				+ ", min(sp.microhabitat) as microhabitat"
				+ ", min(sp.datecollectedstart) as datecollectedstart, max(sp.datecollectedend) as datecollectedend"
				+ " from specimen as sp "
				+ " where " + clause + " and sp.collectioncode is not null and sp.collectioncode != ''"
				+ " group by sp.collectioncode order by sp.collectioncode";
			  ResultSet collectionRset = null;
			  colStmt = getConnection().createStatement();
			  collectionRset = colStmt.executeQuery(collectionQuery);
			  if (collectionRset != null) {
				Collection collection = null;
				while (collectionRset.next()) {
					collection = new Collection();
					locality.getCollections().add(collection.makeCollectionFromRset(collectionRset));
				}
			  }
           }        
        } finally {
          locStmt.close();            
          if (colStmt != null) colStmt.close();
        }

        return locality;
    }
}
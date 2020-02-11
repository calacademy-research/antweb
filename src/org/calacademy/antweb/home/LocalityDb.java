package org.calacademy.antweb.home;

//import java.util.*;
import java.util.Date;
import java.sql.*;

import javax.servlet.http.*;
import org.apache.struts.action.*;
//import org.apache.regexp.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;

public class LocalityDb extends AntwebDb {

    private static Log s_log = LogFactory.getLog(LocalityDb.class);

    public LocalityDb(Connection connection) {
      super(connection);
    }

    public Locality getLocalityByCodeOrName(String value) throws SQLException {
      Locality locality = getLocalityByCode(value);
      if (locality != null) return locality;
      locality = getLocalityByName(value);      
      A.log("getLocalityByCodeOrName() value:" + value);
      return locality;
    }
  
    public Locality getLocalityByCode(String code) throws SQLException {
      String safeLocalityCode = AntFormatter.escapeQuotes(code);
      String clause = "sp.localitycode = '" + safeLocalityCode + "'";
      return getLocality(clause);
    }
    
    public Locality getLocalityByName(String name) throws SQLException {
      String safeLocalityName = AntFormatter.escapeQuotes(name);    
      String clause = "sp.localityname = '" + safeLocalityName + "'";
      return getLocality(clause);
    }     
    
    public Locality getLocality(String clause) throws SQLException {
       
        Locality locality = null;
    
        // Locality Query         
        String localityQuery = "select distinct sp.country, sp.adm1, sp.adm2"
            + ", sp.localitycode, sp.localityname, sp.other, sp.bioregion, sp.museum"
            + ", sp.decimal_longitude, sp.decimal_latitude "
            + ", sp.localityNotes, sp.elevation, sp.elevationMaxError, sp.latlonmaxerror"            + ", sp.access_group "
            + ", datecollectedstart, datecollectedend "
            + ", groups.name as groupname"             
            + " from specimen as sp " 
                + " left outer join groups on sp.access_group = groups.id "              
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
			  String collectionQuery = "select distinct sp.collectioncode"
				+ " , sp.habitat, sp.method, sp.decimal_longitude, sp.decimal_latitude, sp.collectedby "
				+ ", sp.microhabitat "
				+ ", datecollectedstart, datecollectedend "            
				+ " from specimen as sp " 
				+ " where " + clause;
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
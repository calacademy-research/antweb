package org.calacademy.antweb.home;

//import java.util.*;
import java.util.Date;
import java.util.ArrayList;
import java.sql.*;

import javax.servlet.http.*;
import org.apache.struts.action.*;
//import org.apache.regexp.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.search.*;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;

public class CollectionDb extends AntwebDb {

    private static Log s_log = LogFactory.getLog(CollectionDb.class);

    public CollectionDb(Connection connection) {
      super(connection);
    }

    public Collection getCollection(String code) throws SQLException {
       
        Collection collection = new Collection();
        ArrayList<ResultItem> resultsList = collection.getSpecimenResults().getResults();
    
        String query = " select sp.code, sp.type_status, sp.country, sp.localitycode, sp.collectioncode " 
            + ", sp.caste, sp.species, sp.genus, sp.localityname, sp.habitat, sp.microhabitat, sp.method"
            + ", sp.collectedby, sp.collectionnotes, sp.adm1, sp.adm2, sp.decimal_longitude, sp.decimal_latitude "
            + ", sp.access_group, sp.bioregion, sp.museum "
            + ", sp.dnaextractionnotes, sp.determinedby, sp.collectedby "
            + ", sp.datecollectedstart, sp.datecollectedend"               
            + ", sp.decimal_latitude, sp.decimal_longitude, sp.elevation, sp.elevationmaxerror, sp.latlonmaxerror, sp.localitynotes"
            + ", groups.name as groupname, ownedby" 
                
            + ", count(image.image_of_id) as images " 
            + " from specimen as sp "
            + " left join image on sp.code = image.image_of_id "
                + " left outer join groups on sp.access_group = groups.id "            
            // + " where sp.collectionCode like '%" + code + "%' "
            + " where sp.collectionCode = '" + Formatter.escapeQuotes(code) + "' "  // so the new index can function
            + " group by sp.code, sp.type_status, sp.country, sp.localitycode, sp.collectioncode "
            + " , sp.caste, sp.species, sp.genus, sp.localityname, sp.habitat, sp.microhabitat, sp.method " 
            + " , sp.collectedBy, sp.collectionnotes, sp.adm1, sp.adm2, sp.decimal_longitude, sp.decimal_latitude "
            + " , sp.access_group, sp.bioregion, sp.museum "            
            + ", sp.dnaextractionnotes, sp.determinedby, sp.collectedby"
            + ", sp.datecollectedstart, sp.datecollectedend" 
            + ", sp.decimal_latitude, sp.decimal_longitude, sp.elevation, sp.elevationmaxerror, sp.latlonmaxerror, sp.localitynotes"
            + ", groupname, ownedby" 
            ;

        A.log("getCollection() code:" + code + " query:" + query);
        ResultSet rset = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getCollection()");
            rset = stmt.executeQuery(query);
        
            if (rset != null) {            
              while (rset.next()) {
                collection.setCode(rset.getString(rset.findColumn("collectioncode")));
                collection.setHabitat(rset.getString(rset.findColumn("habitat")));
                collection.setMicrohabitat(rset.getString(rset.findColumn("microhabitat")));
                collection.setMethod(rset.getString(rset.findColumn("method")));
                collection.setCollectedBy(rset.getString(rset.findColumn("collectedby")));
                collection.setCollectionNotes(rset.getString(rset.findColumn("collectionnotes")));
                collection.setDateCollectedStart(rset.getString("datecollectedstart"));                
                collection.setDateCollectedEnd(rset.getString("datecollectedend")); 
                
                Locality locality = new Locality();
                locality.setCountry(rset.getString(rset.findColumn("country")));
                locality.setAdm1(rset.getString(rset.findColumn("adm1")));
                locality.setAdm2(rset.getString(rset.findColumn("adm2")));
                locality.setLocalityName(rset.getString(rset.findColumn("localityname")));
                locality.setLocalityCode(rset.getString(rset.findColumn("localitycode")));
                locality.setBioregion(rset.getString(rset.findColumn("bioregion")));
                
                String museumCode = rset.getString("museum");  
                if (museumCode != null) {
                  locality.setMuseumCode(museumCode);
                  Museum museum = MuseumMgr.getMuseum(museumCode);
                  if (museum != null) locality.setMuseumName(museum.getName());
                }

				locality.setDecimalLatitude(rset.getFloat(rset.findColumn("decimal_latitude")));
				locality.setDecimalLongitude(rset.getFloat(rset.findColumn("decimal_longitude")));
				locality.setLatLonMaxError(rset.getString(rset.findColumn("latlonmaxerror")));
                locality.setElevation(rset.getString(rset.findColumn("elevation")));
				locality.setElevationMaxError(rset.getString(rset.findColumn("elevationmaxerror")));
				locality.setLocalityNotes(rset.getString(rset.findColumn("localitynotes")));                

                // MarkMap.
                locality.setDecimalLatitude(rset.getFloat("decimal_latitude"));
                locality.setDecimalLongitude(rset.getFloat("decimal_longitude"));
                collection.setLocality(locality);
                
                int groupId = rset.getInt(rset.findColumn("access_group"));
                collection.addToGroupList(GroupMgr.getGroup(groupId));
                //addAccessGroupList(rset.getInt(rset.findColumn("access_group")));
                //addAccessGroupNameList(rset.getString(rset.findColumn("groupname")));
                //s_log.warn("getCollection() access_group: " + rset.getInt(rset.findColumn("access_group")) + " query:" + query);                    

                ResultItem resultItem = Collection.makeResultItem(rset);
                A.log("getCollection() add rset:" + rset + " resultItem:" + resultItem);
                resultsList.add(resultItem);
              }
            }
        } catch (SQLException e1) {
            s_log.error("getCollection() e:" + e1 + " query:" + query);
            //e1.printStackTrace();
            throw e1;
        } catch (NullPointerException e2) {
            s_log.error("getCollection() e:" + e2 + " query:" + query);
            org.calacademy.antweb.util.AntwebUtil.logStackTrace(e2);
            throw e2;
        } finally {
            DBUtil.close(stmt, rset, this, "getCollection()");
        }
        collection.getSpecimenResults().setResults(resultsList);
        A.log("getCollection() collection:" + collection + " size:" + collection.getSpecimenResults().getResults().size());
        return collection;
    }
}
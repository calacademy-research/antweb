package org.calacademy.antweb.home;

//import java.util.*;
import java.util.ArrayList;
import java.util.HashSet;

import java.sql.*;

//import org.apache.regexp.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.search.*;
import org.calacademy.antweb.util.*;

/**
 * Pre-computed maps (table object_map, one row per country, adm1, museum or group). Nightly set 5 (genObjectMaps)
 * makes the country, adm1 and museum maps; a specimen upload makes its group's map (UploadAction). Read by the
 * place and museum pages (OverviewAction), the group page (group.do) and the big map (bigMap.do).
 * Docs: docs/flows/places.md, docs/flows/nightly.md (set 5), docs/data-model.md (object_map).
 */
public class ObjectMapDb extends AntwebDb {

    private static final Log s_log = LogFactory.getLog(ObjectMapDb.class);

    public ObjectMapDb(Connection connection) {
      super(connection);
    }

    public void genObjectMaps() throws SQLException {
      genCountryObjectMaps();
      
      genMuseumObjectMaps();
      
      genAdm1ObjectMaps();

     // genGroupObjectMaps();
    }

    public void genMuseumObjectMaps() throws SQLException {
	    //s_log.warn("execute() genCountryObjectMaps()");
        ArrayList<Museum> museums = MuseumMgr.getMuseums();
        if (museums == null) {
            s_log.warn("genMuseumObjectMaps() aborted. Perhaps server initializing?");
            return;
        }

        boolean skip = AntwebProps.isDevModeSkipping() && false;
        if (skip) s_log.info("genMuseumObjectMaps() DEV MODE SKIPPING genMuseumObjectMap(). Only gen for museum:AFRC.");
        for (Museum museum : museums) {
        if (museum.getIsActive()) {
            if (skip) {
                if ("AFRC".equals(museum.getCode())) {
                    //  || "JTLC".equals(museum.getCode())) {
                    genMuseumObjectMap(museum);
                }
            } else {
                genMuseumObjectMap(museum);
            }
        }
      }      
      //s_log.warn("done genMuseumObjectMap()");
    }

    public void genMuseumObjectMap(Museum museum) throws SQLException {
        //s_log.warn("getMuseumObjectMap() generating map:" + museum.getMap());
        String code = museum.getCode();

		SearchAction.setTempSpecimenSearchLimit(SearchAction.noSpecimenSearchLimit);
		Map map;
		try {   // finally (2026-10-29): a failed map left every search without its limit until restart
		  map = new AdvancedSearchAction().getGoogleMap(museum, ResultRank.SPECIMEN, Output.MAP_LOCALITY, getConnection());
		} finally {
		  SearchAction.undoSetTempSpecimenSearchLimit();
		}

		if (map != null) {		  
          deleteMap("museum_code = " + lit(museum.getCode())); 
		  setMuseumMap(museum.getCode(), map);         
		}
    }
    
    public Map getMuseumMap(String museumCode) throws SQLException {
      String clause = " museum_code = " + lit(museumCode);
      return getMap(clause);
    }
    public void setMuseumMap(String code, Map map) throws SQLException {
      setMap(code, "museum_code", map);
    } 
    
    public void genCountryObjectMaps() throws SQLException {
	  //s_log.warn("execute() genCountryObjectMaps()");    
      ArrayList<Geolocale> countries = GeolocaleMgr.getCountries();
      for (Geolocale country : countries) {
        if (country.isValid() && country.isLive())
          genGeolocaleObjectMap(country);
      }      
      //s_log.warn("done genCountryObjectMap()");
    }

    public void genAdm1ObjectMaps() throws SQLException {
	  //s_log.warn("execute() genAdm1ObjectMaps()");    
      ArrayList<Geolocale> adm1s = GeolocaleMgr.getAdm1s();
      for (Geolocale adm1 : adm1s) {
        if (adm1.isValid() && adm1.isLive())
          genGeolocaleObjectMap(adm1);
      }      
      //s_log.warn("done genAdm1ObjectMap()");
    }
    
    public void genGeolocaleObjectMap(Geolocale geolocale) throws SQLException {
        int id = geolocale.getId();

        // We won't redo done ones.        
        //if (getGeolocaleMap(id) != null) return;
        
        String country = null;
        String adm1 = null;

	    if ("country".equals(geolocale.getGeorank())) {
		  country = geolocale.getName();
	    } else if ("adm1".equals(geolocale.getGeorank())) {
		  country = geolocale.getParent();
		  adm1 = geolocale.getName();
	    }

		SearchAction.setTempSpecimenSearchLimit(SearchAction.noSpecimenSearchLimit);
		Map map;
		try {
		  map = new AdvancedSearchAction().getGoogleMap(country, adm1, ResultRank.SPECIMEN, Output.MAP_LOCALITY, getConnection());
		} finally {
		  SearchAction.undoSetTempSpecimenSearchLimit();
		}

		if (map != null) {
		  deleteMap("geolocale_id = " + geolocale.getId()); 
		  setGeolocaleMap(geolocale.getId(), map);         
		}
    }
    
    public void setGeolocaleMap(int id, Map map) throws SQLException {
      setMap(id, "geolocale_id", map);
    }    

    /**
     * A map text as a quoted SQL literal: a place name with an apostrophe (Cote d'Ivoire, Hawke's Bay) made the insert
     * fail after the old map was deleted, so the place had no map. null stays the text 'null', as before.
     */
    static String lit(String value) {
        return DBUtil.sqlLiteral(String.valueOf(value));
    }

    public void setMap(int id, String keyCol, Map map) throws SQLException {
	  //A.log("setMap(int, str, map) id:" + id);
      String googleMapFunction = map.getGoogleMapFunction();
      if (googleMapFunction == null) {
        s_log.debug("setMap(int, str, map) googleMapFunction is null for " + keyCol + ":" + id);
        return;
      }
      googleMapFunction = HttpUtil.encode(googleMapFunction);

      String fields = keyCol + ", google_map_function, title, subtitle, info";
      String values = id 
        + ", " + lit(googleMapFunction)
        + ", " + lit(map.getTitle())
        + ", " + lit(map.getSubtitle())
        + ", " + lit(map.getInfo());
      try {
        insertMap(fields, values);    
      } catch (SQLException e) {
        s_log.error("setMap(int, str, map) e:" + e);
        throw e;
      }
    }
    
    public void setMap(String key, String keyCol, Map map) throws SQLException {
	  s_log.debug("setMap(str, str, map) key:" + key);
      String googleMapFunction = map.getGoogleMapFunction();
      if (googleMapFunction == null) {
        s_log.debug("setMap(str, str, map) googleMapFunction is null for " + keyCol + ":" + key);
        return;
      }
      googleMapFunction = HttpUtil.encode(googleMapFunction);
      String fields = keyCol + ", google_map_function, title, subtitle, info";
      String values = lit(key)
        + ", " + lit(googleMapFunction)
        + ", " + lit(map.getTitle())
        + ", " + lit(map.getSubtitle())
        + ", " + lit(map.getInfo());
      try {
        insertMap(fields, values);    
      } catch (SQLException e) {
        s_log.error("setMap(str, str, map) e:" + e);
        throw e;
      }
    }    
    
    public Map getGeolocaleMap(int id) throws SQLException {
      String clause = " geolocale_id = " + id;
      return getMap(clause);
    }

    public void genGroupObjectMaps() throws SQLException {
      HashSet<Group> groups = new HashSet<>();
      ArrayList<Login> logins = LoginMgr.getLogins();
      for (Login login : logins) {
        if (login.isUploadSpecimens())
          groups.add(login.getGroup());       
      }
      for (Group group : groups) {
          genGroupObjectMap(group);      
      }

      //s_log.warn("done genGroupObjectMap()");
    } 

    public void genGroupObjectMap(Group group) throws SQLException {
		SearchAction.setTempSpecimenSearchLimit(SearchAction.noSpecimenSearchLimit);
		Map map;
		try {
		  map = new AdvancedSearchAction().getGoogleMap(group, ResultRank.SPECIMEN, Output.MAP_LOCALITY, getConnection());
		} finally {
		  SearchAction.undoSetTempSpecimenSearchLimit();
		}

		if (map != null) {
          deleteMap("group_id = " + group.getId()); 
 		  setMap(group.getId(), "group_id", map);         
		}
    }
    
    public void genGroupObjectMap(int groupId) throws SQLException {
      Group group = GroupMgr.getGroup(groupId);
      if (group == null) {
        s_log.warn("genGroupObjectMap() group not found for id:" + groupId);
        return;
      }
      genGroupObjectMap(group);    
    }
                 
                 
                 
    public Map getGroupMap(int id) throws SQLException {
      String clause = " group_id = " + id;
      return getMap(clause);
    }
        
    private Map getMap(String clause) throws SQLException {
        Map map = null;
        
        String query = "select google_map_function, title, subtitle, info " 
          + " from object_map"             
          + " where " + clause; 
        //A.log("getMap() query:" + query);
        ResultSet rset = null;
        Statement stmt = null;
        try { 
          stmt = DBUtil.getStatement(getConnection(), "getMap()");          
          rset = stmt.executeQuery(query);
          while (rset.next()) {
			map = new Map();			
			String googleMapFunction = rset.getString("google_map_function");
            googleMapFunction = HttpUtil.decode(googleMapFunction);

			String title = rset.getString("title");
			String subtitle = rset.getString("subtitle");
			String info = rset.getString("info");
			map.setGoogleMapFunction(googleMapFunction);
            //map.setIsGoogleMapFunction(true);			
            //A.log("getMap() clause:" + clause + " title:" + title + " googleMapFunction:" + googleMapFunction);
			map.setTitle(title);
			map.setSubtitle(subtitle);
			map.setInfo(info);
          }   
		} catch (SQLException e) {
			s_log.error("getMap() query:" + query);
			throw e;
		} finally {
            DBUtil.close(stmt, rset, this, "getMap()");		
		}
        return map;
    }

    public void insertMap(String fields, String values) throws SQLException {
		String dml = "insert into object_map (" + fields + ")" // + ", created) " 
		  + " values (" + values + ")"; // + "', '" + createdStr + "')";
				   
		//A.log("insertMap() dml:" + dml);
		
		Statement stmt = null;
		try {
			stmt = DBUtil.getStatement(getConnection(), "insertMap()");
			stmt.executeUpdate(dml);
		} catch (SQLException e) {
			s_log.error("insertMap() theInsert:" + dml + " e:" + e);
			throw e;
		} finally {
		    DBUtil.close(stmt, "insertMap()");
		}
    }
    
    public void deleteMap(String clause) throws SQLException {
		//String createdStr = DateUtil.getFormatDateTimeStr(operationLock.getCreated());		
		//A.slog("deleteMap() clause:" + clause);

		String dml = "delete from object_map where " + clause;		   
        //A.log("deleteMap() dml:" + dml);

		Statement stmt = null;
		try {
			stmt = DBUtil.getStatement(getConnection(), "deleteMap()");		
			stmt.executeUpdate(dml);
		} catch (SQLException e) {
			s_log.error("deleteMap() dml:" + dml + " e:" + e);
			throw e;
		} finally {
		    DBUtil.close(stmt, "deleteMap()");
		}
    }

}
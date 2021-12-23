package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.geolocale.*;


public class TaxonPropDb extends AntwebDb {
    
    private static Log s_log = LogFactory.getLog(TaxonPropDb.class);
        
    public TaxonPropDb(Connection connection) {
      super(connection);
    }
        
    // ------------------------ Bioregion Map Methods ------------------------------------    

	/*
	Records are stored in the taxon_prop table, with prop = 'bioregionMap', only for genus records. In the format:

	Neotropical:true Afrotropical:true Malagasy:true Australasia:true Oceania:true Indomalaya:true Palearctic:true Nearctic:true'
	*/
	
    
    public static boolean isWordInArray(String searchWord, String[] words) {
	   for (String word : words) {
		 if (word.equals(searchWord)) return true;
	   }
	   return false;
    }	

    public String deleteBioregionMaps() {
        Statement stmt = null;
        ResultSet rset = null;
        String dml = "delete from taxon_prop where prop = 'bioregionMap'";
        try {            
            stmt = DBUtil.getStatement(getConnection(), "getBioregionMaps()");
            stmt.execute(dml);
        } catch (SQLException e) {
            s_log.error("deleteBioregionMaps() exception:" + e + " dml:" + dml);
        } finally {
            DBUtil.close(stmt, rset, "this", "DeleteBioregionMaps()");
        }
        return null;
    }  
        
    public String getBioregionMap(String taxonName) {
        Statement stmt = null;
        ResultSet rset = null;
        String theQuery = "select value from taxon_prop where taxon_name = '" + taxonName + "' and prop = 'bioregionMap'";
        try {            
            stmt = DBUtil.getStatement(getConnection(), "getBioregionMap()");
            rset = stmt.executeQuery(theQuery);

            while (rset.next()) {
                return rset.getString("value");
            }
        } catch (SQLException e) {
            s_log.error("getBioregionMap() exception:" + e + " theQuery:" + theQuery);
        } finally {
            DBUtil.close(stmt, rset, "this", "getBioregionMap()");
        }
        return null;
    }    

    public String getDefaultFor(String code) {
        Statement stmt = null;
        ResultSet rset = null;
        String theQuery = "select taxon_name from taxon_prop where value = '" + code + "'";
        try {            
            stmt = DBUtil.getStatement(getConnection(), "getDefaultFor()");
            rset = stmt.executeQuery(theQuery);

            while (rset.next()) {
                return rset.getString("taxon_name");
            }
        } catch (SQLException e) {
            s_log.error("getDefaultFor() exception:" + e + " theQuery:" + theQuery);
        } finally {
            DBUtil.close(stmt, rset, "this", "getDefaultFor()");
        }
        return null;
    }    

    public void updateBioregionMap(String taxonName, String bioregionMap) {
	  Statement stmt = null;
	  String dml = null;
	  try {
		stmt = DBUtil.getStatement(getConnection(), "updateBioregionMap()");
		dml = "update taxon_prop set value = '" + bioregionMap + "' where taxon_name = '" + taxonName + "' and prop = 'bioregionMap'";
		int i = stmt.executeUpdate(dml);

        if (i == 0) {
            //A.log("updateBioregionMap i == 0");
			dml = "insert into taxon_prop (taxon_name, prop, value) values ('" + taxonName + "', 'bioregionMap', '" + bioregionMap + "')";
			i = stmt.executeUpdate(dml);
        } else {
          s_log.debug("updateBioregionMap i:" + i);
        }

		//A.log("updateRev() dml:" + dml);
	  } catch (SQLException e) {
		s_log.warn("updateBioregionMap() e:" + e);
	  } finally {
		DBUtil.close(stmt, "updateBioregionMap()");
	  }
    }    

    public void refreshBioregionMap() {      
        // Delete the existing records from taxon_prop that are for bioregionMap
        deleteBioregionMaps();

        // Select the distinct Bioregions for each genera in the specimen table.      
        HashMap<String, HashSet<String>> bioregionMaps = new HashMap<>();
        Statement stmt = null;
        ResultSet rset = null;
        String query = null;

        // Build bioregionMaps based on specimen bioregion and taxon.
        query = "select taxon_name, bioregion from specimen ";
        try {            
            stmt = DBUtil.getStatement(getConnection(), "refreshBioregionMap()");
            rset = stmt.executeQuery(query);

            while (rset.next()) {
                String taxonName = rset.getString("taxon_name");
                String genus = Taxon.getGenusTaxonNameFromName(taxonName);
                String bioregion = rset.getString("bioregion");

                if (isWordInArray(bioregion, Bioregion.list)) {
                  HashSet bioregionSet = bioregionMaps.get(genus);
                  if (bioregionSet == null) bioregionSet = new HashSet<String>();
                  bioregionSet.add(bioregion);
                  bioregionMaps.put(genus, bioregionSet);
                } else {
                  if (bioregion != null) {
                    s_log.debug("refreshBioregionMap() illegal bioregion:" + bioregion);
                  }
                }
            }
        } catch (SQLException e) {
            s_log.error("refreshBioregionMap() exception:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "this", "refreshBioregionMap()");
        }


        //Loop through bioregionMaps making the insert strings.
        for (String genus : bioregionMaps.keySet()) {
          HashSet<String> bioregionSet = bioregionMaps.get(genus);
          String bioregionMapStr = "";
          int i = 0;

            for (String bioregion : bioregionSet) {
                ++i;
                if (i > 1) bioregionMapStr += " ";
                bioregionMapStr += bioregion + ":true";
            }
          s_log.debug("refreshBioregionMap() genus:" + genus + " bioregionMapStr:" + bioregionMapStr);
        
          // insert into taxon_props genus, bioregionMapStr
          updateBioregionMap(genus, bioregionMapStr);
        }
    }

    // ------------------------ Introduced Map Methods ------------------------------------    

	/*
	Records are stored in the taxon_prop table, with prop = 'introducedMap', only for species records. In the format:

	Neotropical:true Afrotropical:true Malagasy:true Australasia:true Oceania:true Indomalaya:true Palearctic:true Nearctic:true
	*/

    public String deleteIntroducedMaps() {
        Statement stmt = null;
        ResultSet rset = null;
        String dml = "delete from taxon_prop where prop = 'introducedMap'";
        try {            
            stmt = DBUtil.getStatement(getConnection(), "getIntroducedMaps()");
            stmt.execute(dml);
        } catch (SQLException e) {
            s_log.error("deleteIntroducedMaps() exception:" + e + " dml:" + dml);
        } finally {
            DBUtil.close(stmt, rset, "this", "DeleteIntroducedMaps()");
        }
        return null;
    }  

    public String getIntroducedMap(String taxonName) {
        taxonName = DBUtil.escapeQuotes(taxonName);

        Statement stmt = null;
        ResultSet rset = null;
        String theQuery = "select value from taxon_prop where taxon_name = '" + taxonName + "' and prop = 'introducedMap'";
        try {            
            stmt = DBUtil.getStatement(getConnection(), "getIntroducedMap()");
            rset = stmt.executeQuery(theQuery);

            while (rset.next()) {
                return rset.getString("value");
            }
        } catch (SQLException e) {
            s_log.error("getIntroducedMap() exception:" + e + " theQuery:" + theQuery);
        } finally {
            DBUtil.close(stmt, rset, "this", "getIntroducedMap()");
        }
        return null;
    }    

    public HashMap<String, String> getTaxaIntroducedMaps() {
        HashMap<String, String> taxaMap = new HashMap<>();
        Statement stmt = null;
        ResultSet rset = null;
        String theQuery = "select taxon_name, value from taxon_prop where prop = 'introducedMap'";
        try {
            stmt = DBUtil.getStatement(getConnection(), "getTaxaIntroducedMaps()");
            rset = stmt.executeQuery(theQuery);

            while (rset.next()) {
                String taxonName = (String) rset.getString("taxon_name");
                String value = (String) rset.getString("value");
                taxaMap.put(taxonName, value);
            }
        } catch (SQLException e) {
            s_log.error("getTaxaIntroducedMaps() exception:" + e + " theQuery:" + theQuery);
        } finally {
            DBUtil.close(stmt, rset, "this", "getTaxaIntroducedMaps()");
        }
        return taxaMap;
    }

    public ArrayList<String> getIntroducedList(String orderBy) {
        ArrayList<String> introducedList = new ArrayList<>();

        Statement stmt = null;
        ResultSet rset = null;
        try {
            String query = "select proj_taxon.taxon_name from proj_taxon, taxon where proj_taxon.taxon_name = taxon.taxon_name " 
              + " and (taxon.taxarank = 'species' or taxon.taxarank = 'subspecies') and project_name = 'introducedants' "
              + orderBy;

            stmt = DBUtil.getStatement(getConnection(), "getIntroducedList()");
            rset = stmt.executeQuery(query);
            while (rset.next()) {
              String taxonName = rset.getString("taxon_name");
              introducedList.add(taxonName); 
            }
        } catch (SQLException e) {
          s_log.error("getIntroducedList() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, this, "getIntroducedList)");        
        }    
        return introducedList;
    }

    // introducedMap is of the same format as bioregionMap.
    public void updateIntroducedMap(String taxonName, String introducedMap) {
	  Statement stmt = null;
	  String dml = null;
	  try {
		stmt = DBUtil.getStatement(getConnection(), "updateIntroducedMap()");
		dml = "update taxon_prop set value = '" + introducedMap + "' where taxon_name = '" + taxonName + "' and prop = 'introducedMap'";
		int i = stmt.executeUpdate(dml);

        if (i == 0) {
            //A.log("updateIntroducedMap i == 0");
			dml = "insert into taxon_prop (taxon_name, prop, value) values ('" + taxonName + "', 'introducedMap', '" + introducedMap + "')";
			i = stmt.executeUpdate(dml);
        }

		//A.log("updateRev() dml:" + dml);
	  } catch (SQLException e) {
		s_log.warn("updateIntroducedMap() e:" + e);
	  } finally {
		DBUtil.close(stmt, "updateIntroducedMap()");
	  }
    }    
    
// ----------------------------

    public String deleteConflictedDefaultImages() {
        Statement stmt = null;
        ResultSet rset = null;
        int i = 0;
        try {
            String query = "select tp.taxon_name, tp.prop, tp.value, tp.created, tp.login_id, specimen.taxon_name from taxon_prop tp, specimen where tp.value = code and tp.prop like '%Specimen' and tp.taxon_name != specimen.taxon_name";
            stmt = DBUtil.getStatement(getConnection(), "deleteConflictedDefaultImages()");
            rset = stmt.executeQuery(query);
            while (rset.next()) {
              String taxonName = rset.getString("tp.taxon_name");
              String prop = rset.getString("tp.prop");
              deleteTaxonProp(taxonName, prop);
              ++i;
            }
        } catch (SQLException e) {
          s_log.error("deleteConflictedDefaultImages() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, this, "deleteConflictedDefaultImages()");        
        }      
        return "deleted:" + i;
    }

    public String deleteTaxonProp(String taxonName, String prop) {
      String returnVal = "";
	  Statement stmt = null;
	  String dml = null;
	  try {
		stmt = DBUtil.getStatement(getConnection(), "deleteTaxonProp()");
        dml = "delete from taxon_prop where taxon_name = '" + taxonName + "' and prop = '" + prop + "'";
		int i = stmt.executeUpdate(dml);

		returnVal = "deleted:" + i;
        s_log.debug("deleteTaxonProp() returnVal:" + returnVal + " dml:" + dml);
	  } catch (SQLException e) {
		s_log.warn("deleteTaxonProp() e:" + e);
	  } finally {
		DBUtil.close(stmt, "deleteTaxonProp()");
	  }
      
      return returnVal;
    }    
    
    
/*    
    public void refreshIntroducedMap() {
        // Delete the existing records from taxon_prop that are for introducedMap

      deleteIntroducedMaps();

      HashMap<String, HashSet<String>> introducedMaps = new HashMap<String, HashSet<String>>();

	  ArrayList<String> introducedList = getIntroducedList("");
      int i = 0;
      for (String taxonName : introducedList) {      
        ++i;
        A.log("refreshIntroducedMaps() e:" + i);
    
        Statement stmt = null;
        ResultSet rset = null;
        String query = null;

        // Build introducedMaps based on specimen introduced and taxon.
        query = "select distinct bioregion from specimen where taxon_name = '" + taxonName + "'";
        try {
            stmt = DBUtil.getStatement(getConnection(), "refreshIntroducedMap()");
            rset = stmt.executeQuery(query);

            while (rset.next()) {
                String bioregion = rset.getString("bioregion");

                if (isWordInArray(bioregion, Bioregion.list)) {
                  HashSet introducedSet = introducedMaps.get(taxonName);
                  if (introducedSet == null) introducedSet = new HashSet<String>();
                  introducedSet.add(bioregion);
                  introducedMaps.put(taxonName, introducedSet);
                } else {
                  if (bioregion != null) {
                    A.log("refreshIntroducedMap() illegal bioregion:" + bioregion);
                  }
                }
            }
        } catch (SQLException e) {
            s_log.error("refreshIntroducedMap() exception:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "this", "refreshIntroducedMap()");
        }
      }

        //Loop through introducedMaps making the insert strings.
        for (String taxonName : introducedMaps.keySet()) {
          HashSet<String> introducedSet = introducedMaps.get(taxonName);
          String introducedMapStr = "";
          int j = 0;
          
          Iterator<String> iterator = introducedSet.iterator();
          while (iterator.hasNext()) {
            String bioregion = iterator.next();
            ++j;
            if (j > 1) introducedMapStr += " ";
            introducedMapStr += bioregion + ":true";            
          }
          A.log("refreshIntroducedMap() taxonName:" + taxonName + " introducedMapStr:" + introducedMapStr);
        
          // insert into taxon_props genus, introducedMapStr
          updateIntroducedMap(taxonName, introducedMapStr);
        }
    }
*/

}
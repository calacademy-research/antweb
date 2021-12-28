package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

//import java.time.format.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;

public class SpecimenDb extends AntwebDb {
    
    private static Log s_log = LogFactory.getLog(SpecimenDb.class);
        
    public SpecimenDb(Connection connection) throws SQLException {
      super(connection);
    }
    
    public boolean exists(String code) throws SQLException {
      Statement stmt = null;
      ResultSet rset = null;
      String query = "select count(*) count from specimen where code = '" + code + "'";
        try {
            stmt = DBUtil.getStatement(getConnection(), "exists()");
            rset = stmt.executeQuery(query);

            while (rset.next()) {
                int c = rset.getInt("count");
                if (c == 1) return true;
            }
            //A.log("isDuplicatedTaxonName() count:" + count);
        } catch (SQLException e) {
            s_log.error("exists() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "exists()");
        }
        return false;
    }

    public Specimen getSpecimen(String code) throws SQLException {
      Specimen specimen = null;
      try {
          specimen = new Specimen(code, getConnection());
      } catch (SQLException e) {
          s_log.debug("getSpecimen() code:" + code + " e:" + e);
          throw e;
      }
      return specimen;
    }

    public ArrayList<String> getAntwebSpecimenCodes(Overview overview, String family) throws SQLException {
        return getAntwebSpecimenCodes(overview, family, null);
    }

    public ArrayList<String> getAntwebSpecimenCodes(Overview overview, String family, String subfamily) throws SQLException {
        return getAntwebSpecimenCodes(overview, family, subfamily, null);
    }

    public ArrayList<String> getAntwebSpecimenCodes(Overview overview, String family, String subfamily, String genus) throws SQLException {
        return getAntwebSpecimenCodes(overview, family, subfamily, genus, null);
    }

    public ArrayList<String> getAntwebSpecimenCodes(Overview overview, String family, String subfamily, String genus, String species) throws SQLException {
        return getAntwebSpecimenCodes(overview, family, subfamily, genus, species, null);
    }

    public ArrayList<String> getAntwebSpecimenCodes(Overview overview, String family, String subfamily, String genus, String species, String subspecies) throws SQLException {
        ArrayList<String> specimenCodes = new ArrayList<>();
        String code = null;

        String project = overview.getName();

        Statement stmt = null;
        ResultSet rset = null;
        String query = "select code from specimen "; 

        if (!Project.ALLANTWEBANTS.equals(overview.getName())) {
          query += ", " + overview.getSpecimenTaxonSetClause() + " and ";
        } else {
          query += " where";
        }
        
        //if (project != null && !Project.ALLANTWEBANTS.equals(project)) query += ", proj_taxon"; 
        //query += " where ";
        //if (project != null && !Project.ALLANTWEBANTS.equals(project)) 
        //    query += " specimen.taxon_name = proj_taxon.taxon_name and project_name = '" + project + "' and "; 

        query += " family = '" + family + "'";
        if (subfamily != null) {
          query += " and subfamily = '" + subfamily + "'";
        }
        if (genus != null) {
          query += " and genus = '" + genus + "'";
        }
        if (species != null) {
          query += " and species = '" + species + "'";
        }
        if (subspecies != null) {
          query += " and subspecies = '" + subspecies + "'";
        }
        s_log.debug("getAntwebSpecimenCodes() query:" + query);
        try {
            stmt = DBUtil.getStatement(getConnection(), "getAntwebSpecimenCodes()");
            rset = stmt.executeQuery(query);

            int count = 0;
            while (rset.next()) {
                ++count;
                code = rset.getString("code");
                specimenCodes.add(code);
            }
            s_log.debug("getAntwebSpecimenCodes() count:" + count);

        } catch (SQLException e) {
            s_log.error("getAntwebSpecimenCodes() e:" + e + " query:" + query);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "getAntwebSpecimenCodes()");
        }
        
        return specimenCodes;
    }

    public String getSpecimenDetailXML(String code) throws SQLException {
        // Also called "Other" and "Features".
        String theXML = null;
        Formatter formatter = new Formatter();
        Statement stmt = null;
        ResultSet rset = null;
        try {
            String theQuery = "select other from specimen where code='" + AntFormatter.escapeQuotes(code) + "'";

            stmt = DBUtil.getStatement(getConnection(), "getSpecimenDetailXML()");
            rset = stmt.executeQuery(theQuery);

            while (rset.next()) {
                // theXML = new Formatter().convertToUTF8(rset.getString(1));
                theXML = rset.getString(1);
            }
        } catch (SQLException e) {
            s_log.error("getSpecimenDetailXML() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, this, "getSpecimenDetailXML()");
        }

        return formatter.dequote(theXML);
    }

    public ArrayList<String> getIntroducedByGroup(int groupId) throws SQLException {
      ArrayList<String> introducedSpecimen = new ArrayList<>();
  	  introducedSpecimen.add("<tr><td>Group</td><td>Bioregion</td><td>Code</td><td>Taxon Name</td><td>Country</td></tr>");
	  introducedSpecimen.add("<tr><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td></tr>");
      Statement stmt = null;
      ResultSet rset = null;
      String query = "select ant_group.name as groupName, bioregion, code, taxon_name, country from specimen, ant_group "
        + " where specimen.access_group = ant_group.id "
        + " and access_group = " + groupId
        + " and is_introduced = 1 " 
        + "order by access_group, bioregion, country ";
        try {
            stmt = DBUtil.getStatement(getConnection(), "getIntroducedByGroup()");
            rset = stmt.executeQuery(query);

            int count = 0;
            while (rset.next()) {
                ++count;
                String group = rset.getString("groupName");
                String bioregion = rset.getString("bioregion");
                String code = rset.getString("code");
                String taxonName = rset.getString("taxon_name");
                String country = rset.getString("country");
                
                String codeLink = "<a href='" + AntwebProps.getDomainApp() + "/specimen.do?code=" + code + "'>" + code + "</a>";
                String taxonLink = "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + Taxon.getPrettyTaxonName(taxonName) + "</a>";
                introducedSpecimen.add("<tr><td>" + group + "</td><td>" + bioregion + "</td><td>" + codeLink + "</td><td>" + taxonLink + "</td><td>" + country + "</td></tr>");
            }
            s_log.debug("getIntroducedByGroup() count:" + count + " query:" + query);

        } catch (SQLException e) {
            s_log.error("getIntroducedByGroup() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "getIntroducedByGroup()");
        }
      
      return introducedSpecimen;
    }

    public ArrayList<String> getSpecimensWithMorphoGenera(int groupId) throws SQLException {
        ArrayList<String> specimen = new ArrayList<>();
  	    specimen.add("<tr><td>Code</td><td>Subfamily</td><td>Genus</td></tr>");
	    specimen.add("<tr><td><hr></td><td><hr></td><td><hr></td></tr>");
        Statement stmt = null;
        ResultSet rset = null;
		String query = "select code, subfamily, genus, subgenus from specimen where taxon_name in (select taxon_name from taxon where (rank = 'species' or rank = 'subspecies') "
		  + " and genus not like '(%'"
		  + " and (subfamily, genus) in ( select subfamily, genus from taxon where rank = 'genus' and status = 'morphotaxon') and access_group = " + groupId + ") " 
		  + " and access_group = " + groupId;
              
        try {
            stmt = DBUtil.getStatement(getConnection(), "getSpecimensWithMorphoGenera()");
            rset = stmt.executeQuery(query);

            int count = 0;
            while (rset.next()) {
                ++count;
                String code = rset.getString("code");
                String subfamily = rset.getString("subfamily");
                String genus = rset.getString("genus");
                String subgenus = rset.getString("subgenus"); // Not used.

                String codeLink = "<a href='" + AntwebProps.getDomainApp() + "/specimen.do?code=" + code + "'>" + code + "</a>";
                specimen.add("<tr><td>" + codeLink + "</td><td>" + subfamily + "</td><td>" + genus + "</td></tr>");
            }
            s_log.debug("getSpecimensWithMorphoGenera() count:" + count + " query:" + query);

        } catch (SQLException e) {
            s_log.error("getSpecimensWithMorphoGenera() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "getSpecimensWithMorphoGenera()");
        }
      
      return specimen;
    }

 // *** island_country?
    public boolean hasSpecimen(String taxonName, String country) throws SQLException {
      return hasSpecimenWithClause(taxonName, " and country = '" + country + "'");
    }
    public boolean hasSpecimen(String taxonName, String adm1, String country) throws SQLException {
      return hasSpecimenWithClause(taxonName, " and country = '" + country + "' and adm1 = '" + adm1 + "'");
    }
    public boolean hasSpecimenWithClause(String taxonName, String clause) throws SQLException {
      String dups = "";
      Statement stmt = null;
      ResultSet rset = null;
      taxonName = AntFormatter.escapeQuotes(taxonName);

      String query = "select code from specimen where taxon_name = '" + taxonName + "' " + clause;
        try {
            stmt = DBUtil.getStatement(getConnection(), "hasSpecimenWithClause()");
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                return true;
            }
        } catch (SQLException e) {
            s_log.error("hasSpecimenWithClause() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "hasSpecimenWithClause()");
        }
        return false;
    }
 
    public ArrayList<ArrayList<String>> getMultiBioregionTaxaList(int groupId) throws SQLException {
        ArrayList<ArrayList<String>> multiBioregionTaxa = new ArrayList<>();

		ArrayList<String> values = new ArrayList<>();
		values.add("Taxon Name");
		values.add("Bioregions");
		values.add("Count");
		multiBioregionTaxa.add(values);

        Statement stmt = null;
        ResultSet rset = null;
        String query = "select taxon_name, group_concat(distinct bioregion) bioregions, count(distinct bioregion) count" 
                + " from specimen where bioregion is not null and taxon_name not like '%indet%' and country != 'Port of Entry'" 
                + " and access_group = " + groupId + " and taxon_name not in (select distinct taxon_name from proj_taxon" 
                + " where project_name = 'introducedants') group by taxon_name having count(distinct bioregion) > 1" 
                + " order by count(distinct bioregion) desc, taxon_name";
        try {
            stmt = DBUtil.getStatement(getConnection(), "getMultiBioregionTaxaList()");
            rset = stmt.executeQuery(query);
            int i = 0;
            while (rset.next()) {
                ++i;
                values = new ArrayList<>();
                values.add(rset.getString("taxon_name"));
                values.add(rset.getString("bioregions"));
                values.add("" + rset.getInt("count"));
                multiBioregionTaxa.add(values);
            }
        } catch (SQLException e) {
            s_log.error("getMultiBioregionTaxaList() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "getMultiBioregionTaxaList()");
        }
        return multiBioregionTaxa;
    }

// ------ Calc Caste. Male, worker, queen.
    
    public void calcCaste() throws SQLException {
      Statement stmt = null;
      ResultSet rset = null;
      String query = "select distinct access_group groupId from specimen"; 
        try {
            stmt = DBUtil.getStatement(getConnection(), "calcCaste()");
            rset = stmt.executeQuery(query);

            while (rset.next()) {
              int groupId = rset.getInt("groupId");

              calcCaste(groupId);    
            }
        } catch (SQLException e) {
            s_log.error("calcCaste() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "calcCaste()");
        }
    }
       
    public void calcCaste(int groupId) throws SQLException {
      //A.log("calcCaste() for groupId:" + groupId);
      String clause = " access_group = " + groupId;
      calcCasteQuery(clause);
    }

    public void calcCaste(String code) throws SQLException {
      s_log.debug("calcCaste() for code:" + code);
      String clause = " code = '" + code + "'";
      calcCasteQuery(clause);
    }
    
    private void calcCasteQuery(String clause) throws SQLException {
      Statement stmt = null;
      ResultSet rset = null;          
      String query = "select code, caste, subcaste, life_stage from specimen where "  + clause;
        try {
            stmt = DBUtil.getStatement(getConnection(), "calcCasteQuery()");
            rset = stmt.executeQuery(query);

            String dml = "update specimen set caste = ?, subcaste = ? where code = ?";
            PreparedStatement dmlStmt = getConnection().prepareStatement(dml);

            int count = 0;
            while (rset.next()) {
              ++count;
              if (count % 10000 == 0) s_log.debug("calcCasteQuery() count:" + count);

              String code = rset.getString("code");
              String casteNotes = rset.getString("life_stage");
              String caste = rset.getString("caste");
              String subcaste = rset.getString("subcaste");

              if (casteNotes != null) {
                String[] casteValues = Caste.getCasteValues(casteNotes);
                s_log.debug("calcCasteQuery() casteValues[0]:" + casteValues[0] + " casteValues[1]:" + casteValues[1]);
                updateCaste(dmlStmt, code, casteValues[0], casteValues[1]);    
              } else {
                if (caste != null || subcaste != null) {
                  updateCaste(dmlStmt, code, null, null);
                }
                //updateCaste(code, caste, subcaste);
              }
            }

            dmlStmt.close();
            dmlStmt = null;
            
        } catch (SQLException e) {
            s_log.error("calcCasteQuery() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "calcCasteQuery()");
        }
    }

    private void updateCaste(String code, String caste, String subcaste) throws SQLException {
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "updateCaste()");
            String dml = "update specimen s set s.caste = '" + caste + "', s.subcaste = '" + subcaste + "' where s.code = '" + code + "'";
        
            //A.log("updateCaste dml:" + dml);
            stmt.executeUpdate(dml);   
        } catch (SQLException e) {
            s_log.error("updateCaste() " + e);
            throw e;
        } finally {
          DBUtil.close(stmt, "updateCaste()");
        }         
    }


    // Note we only update if a positive. Assumed that specimen values will all be 0 beforehand,
    // because a computed field, not set during upload.
    private void updateCaste(PreparedStatement dmlStmt, String code, String caste, String subcaste) throws SQLException {
       // if (!(isMale || isWorker || isQueen)) return;

        try {
			dmlStmt.setString(1, caste);
			dmlStmt.setString(2, subcaste);
            dmlStmt.setString(3, code);
			int count = dmlStmt.executeUpdate();            
            s_log.debug("updateCaste() count:" + count);
        } catch (SQLException e) {
          s_log.error("updateCaste() code:" + code + " e:" + e);
            throw e;
        }  
    }


    // To be called following specimen upload (via UtilData.do).
    public String updateSpecimenStatus() throws SQLException {
      return updateSpecimenStatus(0);
    }
    public String updateSpecimenStatus(int groupId) throws SQLException {
        // This function maintains the denormalized (taxon) status in the specimen record.
        int c = 0;
            
        if (false && AntwebProps.isDevOrStageMode()) {
           String message = "updateSpecimenStatus()  Skipping...";
           s_log.warn("execute() " + message);  // would be faster for testing
     	   return message;       
        }
 
        String groupClause = "";
        if (groupId > 0) groupClause = " where s.access_group = " + groupId;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "updateSpecimenStatus()");
            String dml = "update specimen s set s.status = (select status from taxon where taxon_name = s.taxon_name)"
              + groupClause;
            c = stmt.executeUpdate(dml);    
            s_log.debug("updateSpecimenStatus(" + groupId + ") c:" + c + " dml:" + dml);
        } catch (SQLException e) {
            s_log.error("updateSpecimenStatus() " + e);
            throw e;
        } finally {
          DBUtil.close(stmt, "updateSpecimenStatus()");
        }         
        String appendStr = "";
        if (groupId > 0) appendStr += " for group:" + groupId + ":" + GroupMgr.getGroup(groupId);
        return c + " specimen records updated" + appendStr;
    }

/*
    // --------- Update locality codes to avoid null -----------
    public int updateNullLocalityCodes() {
        int i = 0;
        Statement stmt = null;
        ResultSet rset = null;
        String query = "select decimal_latitude, decimal_longitude from specimen where (decimal_latitude is not null and decimal_longitude is not null) and localitycode is null group by decimal_latitude, decimal_longitude";
        try {
            stmt = DBUtil.getStatement(getConnection(), "updateNullLocalityCodes()");
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                i = i + 1;
                float lat = rset.getFloat("decimal_latitude");
                float lon = rset.getFloat("decimal_longitude");
                //String localityCode = "tlc" + AntwebUtil.getRandomNumber(); // tlc stands for temporary locality code
                String localityCode = "tlc" + i;
                updateSpecimenLocalityCode(localityCode, lat, lon);
            }
        } catch (SQLException e) {
            s_log.error("updateNullLocalityCodes() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "this", "updateNullLocalityCodes()");
        }
        return i;
    }
    
    public void updateSpecimenLocalityCode(String localityCode, float lat, float lon) 
     throws SQLException {
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "updateSpecimenStatus()");
            String dml = "update specimen set localitycode = '" + localityCode + "' where decimal_latitude = " + lat + " and decimal_longitude = " + lon;
          A.slog("updateSpecimenLocalityCode() dml:" + dml);
            stmt.executeUpdate(dml);    
        } catch (SQLException e) {
            s_log.error("updateSpecimenLocalityCode() " + e);
            throw e;
        } finally {
          DBUtil.close(stmt, "updateSpecimenLocalityCode()");
        }         
    }
*/

    // --------- Update locality codes to avoid null -----------
    public int updateNullLocalityCodes() throws SQLException {
        int i = 0;
        Statement stmt = null;
        ResultSet rset = null;
        String query = "select code, decimal_latitude, decimal_longitude from specimen where (decimal_latitude is not null and decimal_longitude is not null) and localitycode is null order by decimal_latitude, decimal_longitude";
        try {
            stmt = DBUtil.getStatement(getConnection(), "updateNullLocalityCodes()");
            rset = stmt.executeQuery(query);
            float lastLat = 0;
            float lastLon = 0;
            String localityCode = "tlc" + AntwebUtil.getRandomNumber();
            while (rset.next()) {
                i = i + 1;
                String code = rset.getString("code");
                float lat = rset.getFloat("decimal_latitude");
                float lon = rset.getFloat("decimal_longitude");
                if (lastLat == 0 && lastLon == 0) {
                  lastLat = lat;
                  lastLon = lon;
                }
                if (lat != lastLat || lon != lastLon) {
                  localityCode = "tlc" + AntwebUtil.getRandomNumber();
                  lastLat = lat;
                  lastLon = lon;                
                }
                updateSpecimenLocalityCode(code, localityCode);
                
                updateSpecimenLocalityCode(code, localityCode);
            }
        } catch (SQLException e) {
            s_log.error("updateNullLocalityCodes() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "updateNullLocalityCodes()");
        }
        A.slog("updateNullLocalityCodes() i:" + i);
        return i;
    }
    
    public void updateSpecimenLocalityCode(String code, String localityCode) throws SQLException {
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "updateSpecimenLocalityCode()");
            String dml = "update specimen set localitycode = '" + localityCode + "' where code = '" + code + "'";
            //A.slog("updateSpecimenLocalityCode() dml:" + dml);
            stmt.executeUpdate(dml);    
        } catch (SQLException e) {
            s_log.error("updateSpecimenLocalityCode() " + e);
            throw e;
        } finally {
          DBUtil.close(stmt, "updateSpecimenLocalityCode()");
        }         
    }

    public static String getFlagCriteria() {
      return " (flag is null or flag != 'red') ";
    }
    public static String getStatusCriteria() {
      return (new StatusSet()).getCriteria("specimen");
    }
    public static String getTaxaCriteria() {
      return " family = 'formicidae'";
    }    
    public static String getAntwebSubfamilyCriteria() {
      // select distinct subfamily from taxon where  status = "valid" and fossil = 0 and subfamily != "";
      return " subfamily in ('agroecomyrmecinae', 'amblyoponinae', 'aneuretinae' , 'apomyrminae', 'dolichoderinae', 'dorylinae', 'ectatomminae', 'formicinae', 'heteroponerinae', 'leptanillinae', 'martialinae', 'myrmeciinae', 'myrmicinae', 'paraponerinae', 'ponerinae', 'proceratiinae', 'pseudomyrmecinae')";
    }    
    public static String getAntwebGenusCriteria() {
      return " status = 'valid' and genus not in ('agroecomyrmex', 'archimyrmex', 'asymphylomyrmex', 'attopsis', 'baikuris', 'bradoponera', 'brownimecia', 'burmomyrma', 'camelomecia', 'camponotites', 'casaleia', 'cataglyphoides', 'cephalopone', 'ceratomyrmex', 'chronomyrmex', 'cretopone', 'ctenobethylus', 'cyrtopone', 'drymomyrmex', 'elaeomyrmex', 'eldermyrmex', 'electromyrmex', 'emplastus', 'enneamerus', 'eocenomyrma', 'eoformica', 'eulithomyrmex', 'formicium', 'gerontoformica', 'glaphyromyrmex', 'haidomyrmex', 'haidomyrmodes', 'haidoterminus', 'ktunaxia', 'kyromyrma', 'leucotaphus', 'linguamyrmex', 'messelepone', 'miomyrmex', 'myanmyrma', 'paraneuretus', 'paraphaenogaster', 'petropone', 'ponerites', 'prionomyrmex', 'procerapachys', 'proiridomyrmex', 'protazteca', 'protomyrmica', 'protopone', 'pseudectatomma', 'solenopsites', 'sphecomyrma', 'taphopone', 'titanomyrma', 'yantaromyrmex', 'zherichinius', 'zigrasimecia')";    
    }



// ---------------------------------- Reports ------------------------------------------------


    /*
      Create report for specimen with multiple taxa. (casent-d anomalies).
      Where casent and casent-dxx have different taxon names.
      Invoked as: https://www.antweb.org/list.do?action=casentDAnamalies
    */
    public ArrayList<String> getCasentDAnamalies() throws SQLException {
        ArrayList<String> bads = new ArrayList<>();
        Statement stmt = null;
        ResultSet rset = null;
        String query = "select concat(substring_index(code, '-d', 1)) as codeFrag from specimen where code like '%-d%'";
        try {
            stmt = DBUtil.getStatement(getConnection(), "getBads()");
            rset = stmt.executeQuery(query);

            int count = 0;
            while (rset.next()) {
                ++count;
                String codeFrag = rset.getString("codeFrag");
                boolean isDup = isDuplicatedTaxonName(codeFrag);
                if (isDup) {
                    //A.log("getBads() isDup:true codeFrag:" + codeFrag);
                    bads.add(getDups(codeFrag));
                }
            }
            s_log.debug("getBads() count:" + count);

        } catch (SQLException e) {
            s_log.error("getBads() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "getBads()");
        }

        return bads;
    }

    public boolean isDuplicatedTaxonName(String codeFrag) throws SQLException {
        Statement stmt = null;
        ResultSet rset = null;
        String query = "select count(distinct taxon_name) as c from specimen where code like '" + codeFrag + "%'";
        try {
            stmt = DBUtil.getStatement(getConnection(), "isDuplicatedTaxonName()");
            rset = stmt.executeQuery(query);

            int count = 0;
            while (rset.next()) {
                int c = rset.getInt("c");
                if (c > 1) return true;
            }
            //A.log("isDuplicatedTaxonName() count:" + count);
        } catch (SQLException e) {
            s_log.error("getBads() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "isDuplicatedTaxonName()");
        }
        return false;
    }

    public String getDups(String codeFrag) throws SQLException {
        String dups = "";
        Statement stmt = null;
        ResultSet rset = null;
        String query = "select code, taxon_name from specimen where code like '" + codeFrag + "%'";
        try {
            stmt = DBUtil.getStatement(getConnection(), "getDups()");
            rset = stmt.executeQuery(query);

            int count = 0;
            while (rset.next()) {
                ++count;
                String code = rset.getString("code");
                String taxonName = rset.getString("taxon_name");
                dups += "<br><b>" + code + ":</b>" + taxonName;
            }
        } catch (SQLException e) {
            s_log.error("getDups() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "getDups()");
        }
        return dups;
    }


    /*
    Must be logged in as admin. Called like this: http://localhost/antweb/list.do?action=recentCASPinnedPonerinae

    ! On Dev, in mysql, must run this first: SET GLOBAL sql_mode=(SELECT REPLACE(@@sql_mode,'ONLY_FULL_GROUP_BY',''));

    I need to know the date of the most recent pinned specimens of every Ponerinae species located at CAS.
    But I need specimens that are not with method containing pitfall, Malaise, yellow pan trap, sweeping, Winkler, sifter, Berlese
    but if the only specimens are from those methods, then the most recent is OK.

    For every species of Ponerinae located at CASC provide
    subfamily, Genus, species, bioregion, casent of most recent collected specimen, date of most recent collected specimen,
      micohabitat, method [preferably not containing pitfall, Malaise, yellow pan trap, sweeping, Winkler, sifter, Berlese],

    select distinct method from specimen where method like '%pitfall%' or method like '%malaise%' or method like '%yellow pan%' or method like '%sweeping%' or method like '%winkler%' or method like '%berlese%';

    select code, max(created), subfamily, genus, species  from specimen where subfamily = "ponerinae" group by subfamily, genus, species, created;
    */

    public QueryReport getRecentCASPinnedPonerinaeQueryReport() throws SQLException {
        QueryReport queryReport = new QueryReport();
        queryReport.setName("RecentCASPinnedPonerinae");
        queryReport.setDesc("Most recent CAS pinned Ponerinae specimen not in the set of methods (pitfall, malaise, yellow pan, sifter, sifted, MW, sweeping, winkler, berlese) if available.");

        ArrayList<String> list = new ArrayList<>();
        Statement stmt = null;
        ResultSet rset = null;

        queryReport.setHeading("<tr><th>#</th><th>Genus</th><th>Species</th><th>Subspecies</th><th>Code</th><th>Status</th><th>DateCollected</th><th>Bioregions</th><th>Microhabitat</th><th>Is Ideal</th><th>Method</th></tr>");

        BioregionTaxonDb bioregionTaxonDb = new BioregionTaxonDb(getConnection());
        try {
            stmt = DBUtil.getStatement(getConnection(), "getRecentCASPinnedPonerinaeQueryReport()");

            String query = "select taxon_name, subfamily, genus, species, IFNULL(subspecies, '') as subspecies, code, status, max(datecollectedstart) as dateCollected, IFNULL(microhabitat, '') as microhabitat, IFNULL(method, '') as method from specimen "
            + "  where subfamily = 'ponerinae'"
            + " and locatedat = 'CASC'"
            + " group by subfamily, genus, species, subspecies"
            + " order by subfamily, genus, species, subspecies";

            //A.log("getRecentCASPinnedPonerinae() query:" + query);
            queryReport.setQuery(query);

            int i = 0;
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                ++i;
                String taxonName = rset.getString("taxon_name");
                //String subfamily = rset.getString("subfamily");
                String genus = rset.getString("genus");
                String species = rset.getString("species");
                String subspecies = rset.getString("subspecies");
                String code = rset.getString("code");
		        String status = rset.getString("status");
                Timestamp dateCollected = rset.getTimestamp("dateCollected");
                //String formatDate = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(dateCollected);
                String dateCollectedStr = "";
                if (dateCollected != null) dateCollectedStr = new java.text.SimpleDateFormat("yyyy-MM-dd").format(dateCollected);
                String microhabitat = rset.getString("microhabitat");
                String method = rset.getString("method");
                //String ideal = rset.getString("ideal");
                String bioregions = bioregionTaxonDb.getBioregionList(taxonName);

                String record = null;
                String ideal = "Yes"; // maybe not, but to start with.
                /*
                Logic is... get the most recent record which may or may not be ideal (not with method of pitfall, malaise, etc...
                if it is not ideal then try to get an ideal one (which may be older than the most recent).
                 */
                String lowerMethod = null;
                if (method != null) lowerMethod = method.toLowerCase();
                if (lowerMethod != null && (lowerMethod.contains("pitfall") || lowerMethod.contains("malaise") || lowerMethod.contains("yellow pan") 
                  || lowerMethod.contains("sifter") || lowerMethod.contains("sifted") || lowerMethod.contains("mw")
                  || lowerMethod.contains("sweeping") || lowerMethod.contains("winkler") || lowerMethod.contains("berlese") )) {
                  String idealRecord = getMostRecentIdealMethodRecord(i, taxonName, queryReport);
                  if (idealRecord != null) {
                      //A.log("getRecentCASPinnedPonerinaeQueryReport() using ideal for i:" + i + " taxonName:" + taxonName);
                      record = idealRecord;
                  } else {
                      ideal = "No";
                  }
                }
                if (record == null) record = "<tr><td>" + i + ".</td><td>" + Formatter.initCap(genus)  + "</td><td>" + species  + "</td><td>" + subspecies + "</td><td>" + code + "</td><td>" + status + "</td><td>" + dateCollectedStr + "</td><td>" + bioregions + "</td><td>" + microhabitat + "</td><td>" + ideal  + "</td><td>" + method  + "</td></tr>";

                list.add(record);
            }
        } catch (SQLException e) {
            s_log.error("getRecentCASPinnedPonerinaeQueryReport() e:" + e);
            queryReport.setError(e.toString());
            throw e;
        } finally {
            DBUtil.close(stmt, "getRecentCASPinnedPonerinaeQueryReport()");
        }

        queryReport.setList(list);
        return queryReport;
    }

    public String getMostRecentIdealMethodRecord(int i, String taxonName, QueryReport queryReport) throws SQLException {
        String record = null;
        Statement stmt = null;
        ResultSet rset = null;

        BioregionTaxonDb bioregionTaxonDb = new BioregionTaxonDb(getConnection());
        try {
            stmt = DBUtil.getStatement(getConnection(), "getMostRecentIdealMethodRecord()");

            String query = "select subfamily, genus, species, IFNULL(subspecies, '') as subspecies, code, status, max(datecollectedstart) as dateCollected, IFNULL(microhabitat, '') as microhabitat, IFNULL(method, '') as method from specimen "
                    + "  where subfamily = 'ponerinae'"
                    + " and not (method like '%pitfall%' or method like '%malaise%' or method like '%yellow pan%' " 
                    + " or method like '%sifted%' or method like '%sifter%' or method like '%MW%'"
                    + " or method like '%sweeping%' or method like '%winkler%' or method like '%berlese%')"
                    + " and locatedat = 'CASC'"
                    + " and taxon_name = '" + taxonName + "'"
                    + " group by subfamily, genus, species, subspecies"
                    + " order by subfamily, genus, species, subspecies";

            //A.log("getMostRecentIdealMethodRecord() query:" + query);

            rset = stmt.executeQuery(query);
            while (rset.next()) {
                queryReport.setSubquery(query);

                //String subfamily = rset.getString("subfamily");
                String genus = rset.getString("genus");
                String species = rset.getString("species");
                String subspecies = rset.getString("subspecies");
                String code = rset.getString("code");
                String status = rset.getString("status");		
                Timestamp dateCollected = rset.getTimestamp("dateCollected");
                String dateCollectedStr = new java.text.SimpleDateFormat("yyyy-MM-dd").format(dateCollected);
                String microhabitat = rset.getString("microhabitat");
                String method = rset.getString("method");
                String ideal = "Yes";
                String bioregions = bioregionTaxonDb.getBioregionList(taxonName);
                record = "<tr><td>" + i + ".</td><td>" + Formatter.initCap(genus)  + "</td><td>" + species + "</td><td>"
                        + subspecies + "</td><td>" + code + "</td><td>" + status + "</td><td>" + dateCollectedStr + "</td><td>" + bioregions + "</td><td>"
                        + microhabitat + "</td><td>" + ideal  + "</td><td>" + method  + "</td></tr>";
            }
        } catch (SQLException e) {
            s_log.error("getMostRecentIdealMethodRecord() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, "getMostRecentIdealMethodRecord()");
        }
        return record;
    }

    public String parseDates() throws SQLException {
        String message = "";
        int count = 0;
        int updated = 0;
        int notUpdated = 0;
        int suspect = 0;
        int newNulls1 = 0;
        int newNulls2 = 0;
        Statement stmt = null;
        ResultSet rset = null;
        String query = "select code, access_group, datecollectedstartstr, datecollectedendStr, datecollectedstart, datecollectedend from specimen"; // where access_group = 21 and taxon_name = 'fmnhins0000122716'";

        //if (AntwebProps.isDevMode()) query += " where taxon_name = 'pseudomyrmecinaetetraponera natalensis'";
        //if (AntwebProps.isDevMode()) query += " where code = 'fmnhins0000109397'";

        try {
            stmt = DBUtil.getStatement(getConnection(), "dateParse()");
            rset = stmt.executeQuery(query);
            s_log.debug("parseDates() query:" + query);
            while (rset.next()) {
                //++count;
                count = 0;
                String code = rset.getString("code");
                String accessGroup = rset.getString("access_group");
                String startStr = rset.getString("datecollectedstartstr");
                String start = rset.getString("datecollectedstart");
                String startNew = DateUtil.getConstructDateStr(startStr);
                String endStr = rset.getString("datecollectedendstr");
                String end = rset.getString("datecollectedend");
                String endNew = DateUtil.getConstructDateStr(endStr);

/*
                // Show the unfixed.
                message = "";
                if (start != null && !start.equals(startNew)) {
                    message = "startStr:" + startStr; // + " start:" + start + " -> " + startNew;
                }
                if (end != null && !end.equals(endNew)) {
                    message = "endStr:" + endStr; // + " end:" + end + " -> " + endNew;
                }
                if (!message.equals("")) {
                    // These ones can not be fixed.
                    s_log.warn("parseDates() Unfixable. Code:" + code + " accessGroup:" + accessGroup + " " + message);
                    // Should really update so that the start and end are null for these codes.
                    ++suspect;

                    continue;
                }
*/

                // We have an old value. mem222072 |            8 | 0000-04-24             | 0000-04-24
                if ((startNew != null && start == null) || ("mem222072".equals(code))) {
                    newNulls1 += updateCollectedStartAsNull(code);
                    s_log.debug("parseDates() code:" + code + " start:" + start + " startNew:" + startNew);
                }
                //ex: | sam-hym-c005977   | pseudomyrmecinaetetraponera natalensis | 1947/12/              | 1947/12/-00-00
                if ((start != null && startNew == null)) {
                    newNulls2 += updateCollectedStartAsNull(code);
                    s_log.debug("parseDates() WTF code:" + code + " start:" + start + " startStr:" + startStr + " startNew:" + startNew);
                }

/*
                if (startNew != null) {
                    java.util.Date newD = DateUtil.constructDate(startNew);
                    java.util.Date date1700 = DateUtil.constructDate("1700");
                    if (newD == null || date1700 == null) {
                        A.log("parseDates() newD is null from startNew:" + startNew + " date1700:" + date1700);
                        continue;
                    }
                    if (newD.before(date1700)) {
                        A.log("parseDates() LESS THAN startStr:" + startStr + " start:" + start + " startNew:" + startNew);
                        //startNew = null;
                    }
                }
*/

                if ((startNew != null && !startNew.equals(start)) || (endNew != null && !endNew.equals(end))) {
                    count = updateParsedDates(code, startNew, endNew);
                    s_log.warn("parseDates() count:" + count + " code:" + code + " startStr:" + startStr + " start:" + start + " -> " + startNew + " end:" + end + " -> " + endNew);
                    if (count < 1) {
                        ++notUpdated;
                    } else {
                        ++updated;
                    }
                }
                //if (count > 0) A.log("parseDates() updated:" + updated + " startNew:" + startNew + " was start:" + start );
            }
        } catch (SQLException e) {
            s_log.error("dateParse() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "dateParse()");
        }
        message = "Updated: " + updated + " notUpdated: " + notUpdated + " newNulls1:" + newNulls1 + " newNulls2:" + newNulls2 + " suspect:" + suspect + ".";
        return message;
    }

    // http://localhost/antweb/advancedSearch.do?searchMethod=advancedSearch&advanced=true&isIgnoreInsufficientCriteria=false&sortBy=taxonname&collGroupOpen=none&specGroupOpen=none&geoGroupOpen=none&typeGroupOpen=none&typeGroupOpen=none&searchType=contains&name=&familySearchType=equals&family=Formicidae&subfamilySearchType=equals&subfamily=none&genusSearchType=equals&genus=anochetus&speciesSearchType=contains&species=&subspeciesSearchType=contains&subspecies=&bioregion=&country=&adm1=&adm2SearchType=contains&adm2=&localityNameSearchType=contains&localityName=&localityCodeSearchType=contains&localityCode=&habitatSearchType=contains&habitat=&elevationSearchType=greaterThanOrEqual&elevation=&methodSearchType=contains&method=&microhabitatSearchType=equals&microhabitat=&collectedBySearchType=equals&collectedBy=&collectionCodeSearchType=contains&collectionCode=&dateCollectedSearchType=greaterThanOrEqual&dateCollected=&specimenCodeSearchType=contains&specimenCode=&locatedAtSearchType=contains&locatedAt=&lifeStageSearchType=contains&lifeStage=&casteSearchType=contains&caste=&mediumSearchType=contains&medium=&specimenNotesSearchType=contains&specimenNotes=&dnaExtractionNotesSearchType=contains&dnaExtractionNotes=&museumCodeSearchType=equals&museumCode=&ownedBySearchType=contains&ownedBy=&createdSearchType=equals&created=&groupName=&uploadId=0&type=&types=off&statusSet=all&imagesOnly=off&resultRank=specimen&output=list&x=27&y=14
    private int updateParsedDates(String code, String start, String end) throws SQLException {
        int count = 0;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "updateParsedDates()");
            String updateStart = null;
            String updateEnd = null;
            if (start != null) updateStart = "datecollectedstart = '" + start + "' ";
            if (end != null) updateEnd = "datecollectedend = '" + end + "' ";

            if (updateStart != null || updateEnd != null) {
                String dml = "update specimen set ";
                if (updateStart != null) dml += updateStart;
                if (updateStart != null && updateEnd != null) dml += ", ";
                if (updateEnd != null) dml += updateEnd;
                dml += " where code = '" + code + "'";
                //s_log.warn("updateParsedDates() dml:" + dml);

                count = stmt.executeUpdate(dml);
                count = 1;
            } else s_log.debug("updateParsedDates() updateStart:" + updateStart + " updateEnd:" + updateEnd);
        } catch (SQLException e) {
            s_log.error("updateParsedDates() " + e);
            throw e;
        } finally {
            DBUtil.close(stmt, "updateParsedDates()");
        }
        return count;
    }

    private int updateCollectedStartAsNull(String code) throws SQLException {
        int count = 0;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "updateCollectedStartAsNull()");
            String updateStart = null;
                String dml = "update specimen set datecollectedstart = null where code = '" + code + "'";
                s_log.warn("updateParsedDates() dml:" + dml);

                count = stmt.executeUpdate(dml);
        } catch (SQLException e) {
            s_log.error("updateCollectedStartAsNull() " + e);
            throw e;
        } finally {
            DBUtil.close(stmt, "updateCollectedStartAsNull()");
        }
        return count;
    }

    // Currently testing. Fetch all of the type statuses to see how well we can handle them... Called from TestAction.java.
    public ArrayList<String> getTypeStatusList(int groupId) throws SQLException {
        ArrayList<String> typeStatusList = new ArrayList<>();
        Statement stmt = null;
        ResultSet rset = null;
        int count = 0;
        String query = "select distinct type_status from specimen where type_status is not null and access_group = " + groupId;
        try {
            ++count;
            stmt = DBUtil.getStatement(getConnection(), "getTypeStatusList()");
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                String typeStatus = rset.getString("type_status");
                typeStatusList.add(typeStatus);
                //A.log("getTypeStatusList() i:" + i + " typeStatus:" + typeStatus);
            }
        } catch (SQLException e) {
            s_log.error("getTypeStatusList() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "getTypeStatusList()");
        }
        s_log.debug("getTypeStatusList() total type_status selected:" + count);
        return typeStatusList;
    }


/*

select taxon_name, subfamily, genus, species, subspecies, code, 'ideal' as ideal, max(created) as created, microhabitat, method from specimen
where subfamily = 'ponerinae'
and not (method like '%pitfall%' or method like '%malaise%' or method like '%yellow pan%' or method like '%sweeping%' or method like '%winkler%' or method like '%berlese%')
and locatedat = 'CASC'
group by subfamily, genus, species, subspecies
union select taxon_name, subfamily, genus, species, subspecies, code, 'not ideal' as ideal, max(created) as created, microhabitat, method
from specimen  where subfamily = 'ponerinae'
and (method like '%pitfall%' or method like '%malaise%' or method like '%yellow pan%' or method like '%sweeping%' or method like '%winkler%' or method like '%berlese%')
and locatedat = 'CASC' group by taxon_name, subfamily, genus, species, subspecies
order by subfamily, genus, species, subspecies



select subfamily, genus, species, subspecies, code, "ideal", max(created), microhabitat, method from specimen  where subfamily = "ponerinae"
and not (method like '%pitfall%' or method like '%malaise%' or method like '%yellow pan%' or method like '%sweeping%' or method like '%winkler%' or method like '%berlese%')
group by subfamily, genus, species, subspecies

union

select subfamily, genus, species, subspecies, code, "not ideal", max(created), microhabitat, method  from specimen  where subfamily = "ponerinae"
group by subfamily, genus, species, subspecies

order by subfamily, genus, species, subspecies;

*/


    // --------------------------- End Reports -----------------------------------

}


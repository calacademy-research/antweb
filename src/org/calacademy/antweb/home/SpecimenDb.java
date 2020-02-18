package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import javax.servlet.http.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.upload.*;

public class SpecimenDb extends AntwebDb {
    
    private static Log s_log = LogFactory.getLog(SpecimenDb.class);
        
    public SpecimenDb(Connection connection) {
      super(connection);
    }
    
    public boolean exists(String code) {
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
        } finally {
            DBUtil.close(stmt, rset, "this", "exists()");
        }
        return false;
    }

    public Specimen getSpecimen(String code) {
      Specimen specimen = null;
      try {
          specimen = new Specimen(code, getConnection());
      } catch (SQLException e) {
          A.log("getSpecimen() code:" + code + " e:" + e);
      }
      return specimen;
    }

    public ArrayList<String> getAntwebSpecimenCodes(Overview overview, String family) {
        return getAntwebSpecimenCodes(overview, family, null);
    }

    public ArrayList<String> getAntwebSpecimenCodes(Overview overview, String family, String subfamily) {
        return getAntwebSpecimenCodes(overview, family, subfamily, null);
    }

    public ArrayList<String> getAntwebSpecimenCodes(Overview overview, String family, String subfamily, String genus) {
        return getAntwebSpecimenCodes(overview, family, subfamily, genus, null);
    }

    public ArrayList<String> getAntwebSpecimenCodes(Overview overview, String family, String subfamily, String genus, String species) {
        return getAntwebSpecimenCodes(overview, family, subfamily, genus, species, null);
    }

    public ArrayList<String> getAntwebSpecimenCodes(Overview overview, String family, String subfamily, String genus, String species, String subspecies) {
        ArrayList<String> specimenCodes = new ArrayList<String>();
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
        A.log("getAntwebSpecimenCodes() query:" + query);        
        try {
            stmt = DBUtil.getStatement(getConnection(), "getAntwebSpecimenCodes()");
            rset = stmt.executeQuery(query);

            int count = 0;
            while (rset.next()) {
                ++count;
                code = rset.getString("code");
                specimenCodes.add(code);
            }
            A.log("getAntwebSpecimenCodes() count:" + count);

        } catch (SQLException e) {
            s_log.error("getAntwebSpecimenCodes() e:" + e + " query:" + query);
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

    public ArrayList<String> getIntroducedByGroup(int groupId) {
      ArrayList<String> introducedSpecimen = new ArrayList<String>();
  	  introducedSpecimen.add("<tr><td>Group</td><td>Bioregion</td><td>Code</td><td>Taxon Name</td><td>Country</td></tr>");
	  introducedSpecimen.add("<tr><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td></tr>");
      Statement stmt = null;
      ResultSet rset = null;
      String query = "select groups.name as groupName, bioregion, code, taxon_name, country from specimen, groups " 
        + " where specimen.access_group = groups.id " 
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
            A.log("getIntroducedByGroup() count:" + count + " query:" + query);

        } catch (SQLException e) {
            s_log.error("getIntroducedByGroup() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "this", "getIntroducedByGroup()");
        }
      
      return introducedSpecimen;
    }

    public ArrayList<String> getSpecimensWithMorphoGenera(int groupId) {
        ArrayList<String> specimen = new ArrayList<String>();
  	    specimen.add("<tr><td>Code</td><td>Subfamily</td><td>Genus</td></tr>");
	    specimen.add("<tr><td><hr></td><td><hr></td><td><hr></td></tr>");
        Statement stmt = null;
        ResultSet rset = null;
		String query = "select code, subfamily, genus from specimen where taxon_name in (select taxon_name from taxon where (rank = 'species' or rank = 'subspecies') " 
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
                
                String codeLink = "<a href='" + AntwebProps.getDomainApp() + "/specimen.do?code=" + code + "'>" + code + "</a>";
                specimen.add("<tr><td>" + codeLink + "</td><td>" + subfamily + "</td><td>" + genus + "</td></tr>");
            }
            A.log("getSpecimensWithMorphoGenera() count:" + count + " query:" + query);

        } catch (SQLException e) {
            s_log.error("getSpecimensWithMorphoGenera() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "this", "getSpecimensWithMorphoGenera()");
        }
      
      return specimen;
    }
 

/*
  Create report for specimen with multiple taxa. (casent-d anomalies).
  Where casent and casent-dxx have different taxon names.
  Invoked as: https://www.antweb.org/list.do?action=casentDAnamalies
*/
    public ArrayList<String> getCasentDAnamalies() {
      ArrayList<String> bads = new ArrayList<String>();
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
            A.log("getBads() count:" + count);

        } catch (SQLException e) {
            s_log.error("getBads() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "this", "getBads()");
        }
      
      return bads;
    }

    public boolean isDuplicatedTaxonName(String codeFrag) {
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
        } finally {
            DBUtil.close(stmt, rset, "this", "isDuplicatedTaxonName()");
        }
        return false;
    }

    public String getDups(String codeFrag) {
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
        } finally {
            DBUtil.close(stmt, rset, "this", "getDups()");
        }
        return dups;
    }
 
 
    public boolean hasSpecimen(String taxonName, String country) {
      return hasSpecimenWithClause(taxonName, " and country = '" + country + "'");
    }
    public boolean hasSpecimen(String taxonName, String adm1, String country) {
      return hasSpecimenWithClause(taxonName, " and country = '" + country + "' and adm1 = '" + adm1 + "'");
    }
    public boolean hasSpecimenWithClause(String taxonName, String clause) {
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
        } finally {
            DBUtil.close(stmt, rset, "this", "hasSpecimenWithClause()");
        }
        return false;
    }
 
    public ArrayList<ArrayList<String>> getMultiBioregionTaxaList(int groupId) {
        ArrayList<ArrayList<String>> multiBioregionTaxa = new ArrayList<ArrayList<String>>();

		ArrayList<String> values = new ArrayList<String>();
		values.add("Taxon Name");
		values.add("Bioregions");
		values.add("Count");
		multiBioregionTaxa.add(values);

        Statement stmt = null;
        ResultSet rset = null;
        String query = "select taxon_name, group_concat(distinct bioregion) bioregions, count(distinct bioregion) count from specimen where bioregion is not null and taxon_name not like '%indet%' and country != 'Port of Entry' and access_group = " + groupId + " and taxon_name not in (select distinct taxon_name from proj_taxon where project_name = 'introducedants') group by taxon_name having count(distinct bioregion) > 1 order by count(distinct bioregion) desc, taxon_name";
        try {
            stmt = DBUtil.getStatement(getConnection(), "getMultiBioregionTaxaList()");
            rset = stmt.executeQuery(query);
            int i = 0;
            while (rset.next()) {
                ++i;
                values = new ArrayList<String>();
                values.add(rset.getString("taxon_name"));
                values.add(rset.getString("bioregions"));
                values.add("" + rset.getInt("count"));
                multiBioregionTaxa.add(values);
            }
        } catch (SQLException e) {
            s_log.error("getMultiBioregionTaxaList() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "this", "getMultiBioregionTaxaList()");
        }
        return multiBioregionTaxa;
    }

// ------ Calc Caste. Male, worker, queen.
    
    public void calcCaste() {
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
        } finally {
            DBUtil.close(stmt, rset, "this", "calcCaste()");
        }
    }
       
    public void calcCaste(int groupId) {
      //A.log("calcCaste() for groupId:" + groupId);
      String clause = " access_group = " + groupId;
      calcCasteQuery(clause);
    }

    public void calcCaste(String code) {
      A.log("calcCaste() for code:" + code);
      String clause = " code = '" + code + "'";
      calcCasteQuery(clause);
    }
    
    private void calcCasteQuery(String clause) {
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
              if (count % 10000 == 0) A.log("calcCasteQuery() count:" + count);

              String code = rset.getString("code");
              String casteNotes = rset.getString("life_stage");
              String caste = rset.getString("caste");
              String subcaste = rset.getString("subcaste");

              if (casteNotes != null) {
                String[] casteValues = Caste.getCasteValues(casteNotes);
                A.log("calcCasteQuery() casteValues[0]:" + casteValues[0] + " casteValues[1]:" + casteValues[1]);
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
        } finally {
            DBUtil.close(stmt, rset, "this", "calcCasteQuery()");
        }
    }

    private void updateCaste(String code, String caste, String subcaste) 
     throws SQLException {
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "updateSpecimenStatus()");
            String dml = "update specimen s set s.caste = '" + caste + "', s.subcaste = '" + subcaste + "' where s.code = '" + code + "'";
        
A.log("updateCaste dml:" + dml);
            stmt.executeUpdate(dml);   
        } catch (SQLException e) {
            s_log.error("updateCaste() " + e);
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
            A.log("updateCaste() count:" + count);
        } catch (SQLException e) {
          s_log.error("updateCaste() code:" + code + " e:" + e);
        }  
    }


    // To be called following specimen upload (via UtilData.do).
    public String updateSpecimenStatus()      
     throws SQLException {
      return updateSpecimenStatus(0);
    }
    public String updateSpecimenStatus(int groupId) 
     throws SQLException {
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
            A.log("updateSpecimenStatus(" + groupId + ") c:" + c + " dml:" + dml);    
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
    public int updateNullLocalityCodes() {
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
        } finally {
            DBUtil.close(stmt, rset, "this", "updateNullLocalityCodes()");
        }
        A.slog("updateNullLocalityCodes() i:" + i);
        return i;
    }
    
    public void updateSpecimenLocalityCode(String code, String localityCode) 
     throws SQLException {
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
}


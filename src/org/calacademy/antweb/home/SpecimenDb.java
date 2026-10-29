package org.calacademy.antweb.home;

import java.text.SimpleDateFormat;
import java.util.*;
import java.sql.*;

//import java.time.format.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.upload.UploadUtil;

/**
 * Table specimen: reads a specimen (logMgr.do), whether a code exists, the specimens of a Download
 * Data file (getSpecimenList.do) and of a specimen list lookup (specimenListLookup.do), and curator reports
 * (list.do: introduced specimens, multi-bioregion taxa, parts with different names). After an upload or as an
 * admin job (utilData.do): caste and status columns and locality names. Tables specimen, taxon, proj_taxon,
 * ant_group. Docs: docs/flows/specimen-upload.md, docs/flows/places.md. Tests: DownloadGuardsDbTest,
 * tests/e2e/test_download_guards.py, tests/e2e/test_parts_by_group.py.
 */
public class SpecimenDb extends AntwebDb {
    
    private static final Log s_log = LogFactory.getLog(SpecimenDb.class);
        
    public SpecimenDb(Connection connection) throws SQLException {
      super(connection);
    }

    /*
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
        } catch (SQLException e) {
            s_log.error("exists() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "exists()");
        }
        return false;
    }*/

    public boolean exists(String code) throws SQLException {
        PreparedStatement stmt = null;
        ResultSet rset = null;
        String query = "select 1 from specimen where code = ? limit 1";
        try {
            stmt = DBUtil.getPreparedStatement(getConnection(), "exists()", query);
            stmt.setString(1, code);
            rset = stmt.executeQuery();

            return rset.next();
        } catch (SQLException e) {
            s_log.error("exists() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "exists()");
        }
    }


    // This will not return null if it does not exist. Bad object design of specimen.
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

    public java.util.Map<String, Specimen> getSpecimensByCodes(List<String> codes) throws SQLException {
        java.util.Map<String, Specimen> specimens = new HashMap<>();
        final int batchSize = 500;

        for (int start = 0; start < codes.size(); start += batchSize) {
            int end = Math.min(start + batchSize, codes.size());
            List<String> batch = codes.subList(start, end);
            StringBuilder placeholders = new StringBuilder();
            for (int i = 0; i < batch.size(); i++) {
                if (i > 0) placeholders.append(',');
                placeholders.append('?');
            }

            String query = "select " + Specimen.getTaxonomicInfoColumns()
                    + " from specimen where code in (" + placeholders + ")";
            PreparedStatement stmt = null;
            ResultSet rset = null;
            try {
                stmt = DBUtil.getPreparedStatement(getConnection(), "getSpecimensByCodes()", query);
                for (int i = 0; i < batch.size(); i++) stmt.setString(i + 1, batch.get(i));
                rset = stmt.executeQuery();
                while (rset.next()) {
                    Specimen specimen = Specimen.fromTaxonomicInfoResult(rset);
                    specimens.put(specimen.getCode().toLowerCase(Locale.ROOT), specimen);
                }
            } finally {
                DBUtil.close(stmt, rset, this, "getSpecimensByCodes()");
            }
        }
        return specimens;
    }

    public ArrayList<String> getAntwebSpecimenCodes(String family) throws SQLException {
        Overview overview = OverviewMgr.getOverview(Project.ALLANTWEBANTS);
        return getAntwebSpecimenCodes(overview, family, null);
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
        String query = "select specimen.code from specimen ";

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

    /**
     * "from ... where ..." (specimen alias s) of the specimens a Download Data file of this page may hold
     * (getSpecimenList.do, Brian Fisher 2026-10-21): a place gives only the specimens collected there,
     * as the place counts take them: countries, adm1s and islands by Geolocale.getSpecimenPlaceCriteria()
     * (specimen.country, adm1 or island_country), regions and subregions by the specimens of their
     * countries (Geolocale.getSpecimenPlaceJoin()), a museum its own specimens (specimen.museum), a
     * bioregion specimen.bioregion. A project gives the specimens of the names on its list (the project
     * counts, proj_taxon), allantwebants all specimens. Null when the page is none of these (no download).
     * Before, every overview used its taxon list only, so a country download held every specimen,
     * worldwide, of the names on the country's list.
     */
    public static String getDownloadFromWhere(Overview overview) {
        if (overview == null) return null;
        if (overview instanceof org.calacademy.antweb.geolocale.Geolocale) {
            org.calacademy.antweb.geolocale.Geolocale g = (org.calacademy.antweb.geolocale.Geolocale) overview;
            String join = g.getSpecimenPlaceJoin();
            String criteria = g.getSpecimenPlaceCriteria();
            if ("".equals(join) && "".equals(criteria)) return null;  // a place rank without its own specimens
            return " from specimen s" + join + " where 1 = 1" + criteria;
        }
        if (overview instanceof org.calacademy.antweb.geolocale.Bioregion) {
            return " from specimen s where s.bioregion = '" + sqlText(overview.getName()) + "'";
        }
        if (overview instanceof Museum) {
            return " from specimen s where s.museum = '" + sqlText(((Museum) overview).getCode()) + "'";
        }
        if (overview instanceof Project) {
            if (Project.ALLANTWEBANTS.equals(overview.getName())) return " from specimen s where 1 = 1";
            return " from specimen s join proj_taxon pt on pt.taxon_name = s.taxon_name"
                + " and pt.project_name = '" + sqlText(overview.getName()) + "' where 1 = 1";
        }
        return null;
    }

    private static String sqlText(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("'", "''");
    }

    /** " and s.family = ... and s.genus = ..." for the given names (null: any). */
    public static String getDownloadTaxonCriteria(String family, String subfamily, String genus, String species, String subspecies) {
        String c = "";
        if (family != null) c += " and s.family = '" + sqlText(family) + "'";
        if (subfamily != null) c += " and s.subfamily = '" + sqlText(subfamily) + "'";
        if (genus != null) c += " and s.genus = '" + sqlText(genus) + "'";
        if (species != null) c += " and s.species = '" + sqlText(species) + "'";
        if (subspecies != null) c += " and s.subspecies = '" + sqlText(subspecies) + "'";
        return c;
    }

    /** Number of specimens a Download Data file of this page and taxon would hold (-1: no download for this page). */
    public int countDownloadSpecimens(Overview overview, String family, String subfamily, String genus, String species, String subspecies) throws SQLException {
        String fromWhere = getDownloadFromWhere(overview);
        if (fromWhere == null) return -1;
        String query = "select count(distinct s.code)" + fromWhere + getDownloadTaxonCriteria(family, subfamily, genus, species, subspecies);
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "countDownloadSpecimens()");
            rset = stmt.executeQuery(query);
            return rset.next() ? rset.getInt(1) : 0;
        } catch (SQLException e) {
            s_log.error("countDownloadSpecimens() e:" + e + " query:" + query);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "countDownloadSpecimens()");
        }
    }

    /** Codes of the specimens of a Download Data file, by code, at most limit of them (limit <= 0: all). */
    public ArrayList<String> getDownloadSpecimenCodes(Overview overview, String family, String subfamily, String genus, String species, String subspecies, int limit) throws SQLException {
        ArrayList<String> codes = new ArrayList<>();
        String fromWhere = getDownloadFromWhere(overview);
        if (fromWhere == null) return codes;
        String query = "select distinct s.code" + fromWhere + getDownloadTaxonCriteria(family, subfamily, genus, species, subspecies)
            + " order by s.code" + (limit > 0 ? " limit " + limit : "");
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getDownloadSpecimenCodes()");
            rset = stmt.executeQuery(query);
            while (rset.next()) codes.add(rset.getString(1));
        } catch (SQLException e) {
            s_log.error("getDownloadSpecimenCodes() e:" + e + " query:" + query);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "getDownloadSpecimenCodes()");
        }
        return codes;
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

        return Formatter.dequote(theXML);
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
                
                // Values from uploaded files: escaped for the page, encoded in the links.
                String codeLink = "<a href='" + AntwebProps.getDomainApp() + "/specimen.do?code=" + Html.escAttr(Html.url(code)) + "'>" + Html.esc(code) + "</a>";
                String taxonLink = "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + Html.escAttr(Html.url(taxonName)) + "'>" + Html.esc(Taxon.getPrettyTaxonName(taxonName)) + "</a>";
                introducedSpecimen.add("<tr><td>" + Html.esc(group) + "</td><td>" + Html.esc(bioregion) + "</td><td>" + codeLink + "</td><td>" + taxonLink + "</td><td>" + Html.esc(country) + "</td></tr>");
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
      String clause = " code = " + DBUtil.sqlLiteral(code);
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

    public static String FLAG_INCLUDE = "include";
    public static String FLAG_ONLY = "only";
    public static String FLAG_EXCLUDE = "exclude";

    public static String getFlagCriteria() {
        return getFlagCriteria(FLAG_EXCLUDE);
    }

    public static String getFlagCriteria(String option) {
        if (FLAG_INCLUDE.equals(option)) return " 1 = 1 ";
        if (FLAG_ONLY.equals(option)) return " (flag = 'red') ";
        if (FLAG_EXCLUDE.equals(option)) return " (flag is null or flag != 'red') ";

        //A.iLog("getFlagCriteria() this should not happen option: " + option + " " + AntwebUtil.getStackTrace());
        return " 1 = 1 ";
    }

    /**
     * Ant specimens only (Brian Fisher, 2026-10-21: non-ant specimens may be uploaded, and are found by
     * search, but are not listed on place pages): family Formicidae and a subfamily that AntCat has as
     * a valid ant subfamily (taxon rows of rank subfamily). alias: the specimen table's alias.
     */
    public static String getAntSpecimenCriteria(String alias) {
      return " " + alias + ".family = 'formicidae' and " + alias + ".subfamily in"
          + " (select ant_sf.taxon_name from taxon ant_sf where ant_sf.taxarank = 'subfamily' and ant_sf.family = 'formicidae' and ant_sf.status = 'valid')";
    }

    public static String getStatusCriteria() {
      return new StatusSet().getCriteria("specimen");
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
    /** A part code ends in "-d" and digits (casent0001234-d01). MySQL REGEXP; case follows the column collation. */
    public static final String PART_SUFFIX_SQL = "-d[0-9]+$";
    private static final java.util.regex.Pattern PART_SUFFIX = java.util.regex.Pattern.compile("(?i)^(.+)-d[0-9]+$");

    /** The base specimen code of a part code (casent0001234-d01 -> casent0001234), else null. Only a suffix at the end counts. */
    public static String partBase(String code) {
        if (code == null) return null;
        java.util.regex.Matcher m = PART_SUFFIX.matcher(code.trim());
        return m.matches() ? m.group(1) : null;
    }

    /** One record of a part report: a part code, or the base specimen's own record. */
    public static final class PartRow {
        public String code;
        public String taxonName;
        public String originalTaxonName;   // the name as uploaded, when AntWeb replaced it with the current valid name
        public int groupId;                // specimen.access_group (0 when missing)
        public String groupName;           // ant_group.name
        public String museum;              // specimen.museum (institution code)
        public String determinedBy;        // specimen.determinedby
        public String dateDetermined;      // specimen.datedetermined, else datedeterminedstr

        public PartRow() {}
        public PartRow(String code, String taxonName, int groupId, String groupName) {
            this.code = code; this.taxonName = taxonName; this.groupId = groupId; this.groupName = groupName;
        }

        /** The uploaded name when it differs from the name shown, else null. */
        public String uploadedName() {
            if (originalTaxonName == null || originalTaxonName.isBlank()) return null;
            return originalTaxonName.equals(taxonName) ? null : originalTaxonName;
        }
    }

    /** One base specimen whose parts (and the base record itself, if there is one) carry different taxon names. */
    public static final class PartGroup {
        public String base;
        public final List<PartRow> parts = new ArrayList<>();
        public final Set<String> names = new TreeSet<>();

        /** The groups that uploaded any record of this specimen. */
        public Set<Integer> groupIds() {
            Set<Integer> ids = new LinkedHashSet<>();
            for (PartRow r : parts) ids.add(r.groupId);
            return ids;
        }
    }

    /**
     * Specimen parts (-D codes) with different names, in one query: every code ending in -d and digits, grouped
     * by its base code together with the base specimen's own record; groups with more than one taxon name.
     * Each record carries the group that uploaded it, the museum code and the determination.
     */
    public List<PartGroup> getPartsWithDifferentNames() throws SQLException {
        String cols = "code, taxon_name, original_taxon_name, access_group, museum, determinedby, datedetermined, datedeterminedstr";
        String query = "select x.base, x.code, x.taxon_name, x.original_taxon_name, x.access_group, g.name,"
            + " x.museum, x.determinedby, x.datedetermined, x.datedeterminedstr from ("
            + " select regexp_replace(code, ?, '') as base, " + cols + " from specimen where code regexp ?"
            + " union all"
            + " select s.code as base, s." + cols.replace(", ", ", s.") + " from specimen s where s.code in"
            + "   (select regexp_replace(code, ?, '') from specimen where code regexp ?)"
            + ") x join ("
            + " select base from ("
            + "   select regexp_replace(code, ?, '') as base, taxon_name from specimen where code regexp ?"
            + "   union all"
            + "   select s.code, s.taxon_name from specimen s where s.code in"
            + "     (select regexp_replace(code, ?, '') from specimen where code regexp ?)"
            + " ) y group by base having count(distinct taxon_name) > 1"
            + ") d on d.base = x.base left join ant_group g on g.id = x.access_group order by x.base, x.code";
        LinkedHashMap<String, PartGroup> groups = new LinkedHashMap<>();
        try (PreparedStatement ps = getConnection().prepareStatement(query)) {
            for (int k = 1; k <= 8; k++) ps.setString(k, PART_SUFFIX_SQL);
            try (ResultSet rset = ps.executeQuery()) {
                while (rset.next()) {
                    String base = rset.getString(1);
                    PartGroup g = groups.computeIfAbsent(base, b -> { PartGroup n = new PartGroup(); n.base = b; return n; });
                    PartRow r = new PartRow();
                    r.code = rset.getString(2);
                    r.taxonName = rset.getString(3);
                    r.originalTaxonName = rset.getString(4);
                    r.groupId = rset.getInt(5);
                    r.groupName = rset.getString(6);
                    r.museum = rset.getString(7);
                    r.determinedBy = rset.getString(8);
                    String date = rset.getString(9);
                    if (date == null || date.isBlank()) date = rset.getString(10);
                    r.dateDetermined = date;
                    g.parts.add(r);
                    if (r.taxonName != null) g.names.add(r.taxonName);
                }
            }
        }
        return new ArrayList<>(groups.values());
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
                if (startNew != null && start == null || "mem222072".equals(code)) {
                    newNulls1 += updateCollectedStartAsNull(code);
                    s_log.debug("parseDates() code:" + code + " start:" + start + " startNew:" + startNew);
                }
                //ex: | sam-hym-c005977   | pseudomyrmecinaetetraponera natalensis | 1947/12/              | 1947/12/-00-00
                if (start != null && startNew == null) {
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

                if (startNew != null && !startNew.equals(start) || endNew != null && !endNew.equals(end)) {
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
    public String processLocalityNames() throws SQLException {
        int count = 0;
        int issue = 0;
        int empty = 0;

        Statement stmt = null;
        ResultSet rset = null;
        String query = "select distinct localityname, localitycode, collectioncode, country from specimenBak";
        try {
            stmt = DBUtil.getStatement(getConnection(), "processLocalityNames()");
            rset = stmt.executeQuery(query);

            while (rset.next()) {
                ++count;

                if (count % 10000 == 0) s_log.info("processLocalityNames() count:" + count);

                String locName = rset.getString("localityname");
                String locCode = rset.getString("localitycode");
                String colCode = rset.getString("collectioncode");
                String country = rset.getString("country");

                String code = UploadUtil.generateKey(locName);

                //s_log.warn("processLocalityNames() code:" + code + " locCode:" + locCode + " country:" + country + " locName:" + locName);

                if (locName == null) {
                    //s_log.warn("processLocalityNames() loc name is null code:" + code + " locName:" + locName + " locCode:" + locCode + " country:" + country);
++empty;
                    continue;
                }

                if (locName.contains("'") || locName.contains("�")) {
++issue;
                    s_log.warn("processLocalityNames() contains code:" + code + " locName:" + locName + " locCode:" + locCode + " country:" + country);
                    continue;
                }
               // updateLocalityCodes(code, name);
            }
        } catch (SQLException e) {
            s_log.error("processLocalityNames() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "processLocalityNames)");
        }
        return "count:" + count + " issues:" + issue + " empty:" + empty;
    }
*/


}


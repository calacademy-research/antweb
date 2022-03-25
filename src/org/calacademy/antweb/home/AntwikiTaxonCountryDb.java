package org.calacademy.antweb.home;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.AntFormatter;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.Taxon;
import org.calacademy.antweb.geolocale.AntwikiTaxonCountry;
import org.calacademy.antweb.util.DBUtil;

import java.sql.*;
import java.util.ArrayList;

public class AntwikiTaxonCountryDb extends AntwebDb {
    
    private static final Log s_log = LogFactory.getLog(AntwikiTaxonCountryDb.class);
        
    public AntwikiTaxonCountryDb(Connection connection) {
      super(connection);
    }

    public void emptyTaxonCountry() { // , boolean isIntroduced

        int rev = 1;  // Get the max rev and increment.

        String sql = null;
        Statement stmt = null;

        try {

          sql = "delete from antwiki_taxon_country where rev = " + rev; // where country = '" + country + "' and taxon_name = '" + taxonName + "'";
          stmt = getConnection().createStatement();
          stmt.executeUpdate(sql);

        } catch (SQLException e) {
            s_log.error("emptyTaxonCountry() e:" + e);
        } finally {
          try {
            if (stmt != null) stmt.close();
          } catch (SQLException e) {
            s_log.error("emptyTaxonCountry() e:" + e);
          }
        }
    }

    public int storeTaxonCountry(String shortName, String originalTaxonName, String taxonName, String country, String region, boolean isIntroduced, String source) 
      throws SQLException {
        int c = 0;
        int rev = 1;  // Get the max rev and increment.

        String sql = null;
        PreparedStatement stmt = null;

        country = AntFormatter.escapeQuotes(country);

        int introduced = 0;
        if (isIntroduced) introduced = 1;

        try {

            sql = "insert into antwiki_taxon_country (rev, short_taxon_name, original_taxon_name, taxon_name, country, region, is_introduced, source, )" +
                    " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

//          sql = "insert into antwiki_taxon_country (rev, short_taxon_name";
//          if (originalTaxonName != null) sql += ", original_taxon_name";
//          sql += ", taxon_name, country, region, is_introduced, source) VALUES (" + rev + ", '" + shortName + "', '";
//          if (originalTaxonName != null) sql += originalTaxonName + "', '";
//          sql += taxonName + "', '" + country + "', '" + region + "', " + isIntroduced + ", '" + source + "')";

          if (isIntroduced) s_log.debug("storeTaxonCountry() sql: " + sql);

          stmt = getConnection().prepareStatement(sql);

            stmt.setInt(1, rev);
            stmt.setString(2, shortName);
            stmt.setString(3, taxonName);
            stmt.setString(4, originalTaxonName);   // ok if string null, since column default is null
            stmt.setString(5, country);
            stmt.setString(6, region);
            stmt.setBoolean(7, isIntroduced);
            stmt.setString(8, source);

          c += stmt.executeUpdate();

        } catch (SQLIntegrityConstraintViolationException e) {
            //s_log.error("storeTaxonCountry() e:" + e);        
        //} catch (SQLException e) {
        //    s_log.error("storeTaxonCountry() e:" + e);
        } finally {
          try {
            if (stmt != null) stmt.close();
          } catch (SQLException e) {
            s_log.error("storeTaxonCountry() e:" + e);
          }
          //DBUtil.close(stmt, "this", "getAntwebSpecimenCodes()");
        }
        return c;
    }

    public void deleteValidTaxa() {
        String sql = null;
        Statement stmt = null;

        try {
          sql = "delete from antwiki_valid_taxa";
          stmt = DBUtil.getStatement(getConnection(), "deleteValidTaxa()");
          stmt.executeUpdate(sql);
        } catch (SQLException e) {
          s_log.error("deleteValidTaxa() e:" + e);
        } finally {
          DBUtil.close(stmt, "deleteValidTaxa");
        }
    }    
    public void insertValidTaxa(String taxonName) throws SQLException {

        String sql = null;
        Statement stmt = null;

        try {
            // todo use prepared statement, skip escaping quotes
          taxonName = Formatter.escapeQuotes(taxonName);
          sql = "insert into antwiki_valid_taxa(taxon_name) values ('" + taxonName + "')";
          
          //A.log("insertValidTaxa() taxonName:" + taxonName);

          stmt = DBUtil.getStatement(getConnection(), "insertValidTaxa()");
          stmt.executeUpdate(sql);
        } catch (SQLException e) {
          s_log.warn("insertValidTaxa() taxonName:" + taxonName + " e:" + e);
        } finally {
          DBUtil.close(stmt, "insertValidTaxa()");
        }
    }
    
    public void deleteFossilTaxa() {
        String sql = null;
        Statement stmt = null;

        try {
          sql = "delete from antwiki_fossil_taxa";
          stmt = DBUtil.getStatement(getConnection(), "deleteFossilTaxa()");
          stmt.executeUpdate(sql);
        } catch (SQLException e) {
          s_log.error("deleteFossilTaxa() e:" + e);
        } finally {
          DBUtil.close(stmt, "deleteFossilTaxa");
        }
    }    
    public void insertFossilTaxa(String taxonName) throws SQLException {

        String sql = null;
        Statement stmt = null;

        try {
          taxonName = Formatter.escapeQuotes(taxonName);
          sql = "insert into antwiki_fossil_taxa(taxon_name) values ('" + taxonName + "')";

          stmt = DBUtil.getStatement(getConnection(), "insertFossilTaxa()");
          stmt.executeUpdate(sql);
        } catch (SQLException e) {
          s_log.warn("insertFossilTaxa() taxonName:" + taxonName + " e:" + e);
        } finally {
          DBUtil.close(stmt, "insertFossilTaxa()");
        }
    }
        
    
    //----------------------------------------------------------------------------------------

    public ArrayList<AntwikiTaxonCountry> getAntwikiTaxonCountries() {
      ArrayList<AntwikiTaxonCountry> taxonCountries = new ArrayList<>();

        String query = "";
        ResultSet rset = null;
        Statement stmt = null;
        try {
        
            ProjectDb projectDb = new ProjectDb(getConnection());
            stmt = DBUtil.getStatement(getConnection(), "getAntwikiTaxonCountries()");

            query = "select id, rev, short_taxon_name, original_taxon_name, taxon_name "
              + ", country, is_introduced, created " //, project_name " 
              + " from antwiki_taxon_country"
              + " where " + Taxon.getNotMorphoCriteria() 
              + "   and " + Taxon.getNotQuadrinomialCriteria()
              + " order by country, taxon_name "
              ;

            s_log.debug("getAntwikiTaxonCountries() query:" + query);

            rset = stmt.executeQuery(query);

            while (rset.next()) {
                int id = rset.getInt("id");
                int rev = rset.getInt("rev");
                String shortTaxonName = rset.getString("short_taxon_name");
                String originalTaxonName = rset.getString("original_taxon_name");
                String taxonName = rset.getString("taxon_name");
                String country = rset.getString("country");
                boolean isIntroduced = rset.getInt("is_introduced") == 1;
                Timestamp created = rset.getTimestamp("created");
                // String projectName = rset.getString("project_name");
                AntwikiTaxonCountry taxonCountry = new AntwikiTaxonCountry();
                taxonCountry.setId(id);
                taxonCountry.setRev(rev);
                taxonCountry.setShortTaxonName(shortTaxonName);
                taxonCountry.setOriginalTaxonName(originalTaxonName);
                taxonCountry.setTaxonName(taxonName);
                taxonCountry.setCountry(country);
                taxonCountry.setIsIntroduced(isIntroduced);
                taxonCountry.setCreated(created);
                // taxonCountry.setProjectName(projectName);
                taxonCountries.add(taxonCountry);               
            }
        } catch (SQLException e) {
            s_log.error("getAntwikiTaxonCountries() e:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "this", "getAntwikiTaxonCountries()");
        }                
      return taxonCountries;
    }
    
    
/*    
    // Data has been loaded to antwiki_Taxon_country, now needs to be pushed to...
    public String finishCountryUpload() throws SQLException {
        String messageStr = "";

        GeolocaleTaxonDb geolocaleTaxonDb = new GeolocaleTaxonDb(getConnection());
        return geolocaleTaxonDb.finishCountryUpload();
    }
 */
 
    public String setIntroducedSpecimen() {
        String message = "Specimen flagged as introduced:";
        String query = "";
        ResultSet rset = null;
        Statement stmt = null;
        int c = 0;
        try {
            ProjectDb projectDb = new ProjectDb(getConnection());
            stmt = DBUtil.getStatement(getConnection(), "setIntroducedSpecimen()");
            query = "select s.code from antwiki_taxon_country a, specimen s where a.taxon_name = s.taxon_name and a.country = s.country and a.is_introduced = 1;";
            s_log.debug("updateIntroducedSpecimen() query:" + query);
            rset = stmt.executeQuery(query);

            while (rset.next()) {
                ++c;
                String code = rset.getString("code");
                updateIntroducedSpecimen(code);
            }
        } catch (SQLException e) {
            s_log.error("setIntroducedSpecimen() e:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "this", "setIntroducedSpecimen()");
        }
        message += "" + c;
        return message;
    }

    private void updateIntroducedSpecimen(String code) throws SQLException {
        PreparedStatement stmt = null;
        String dml = null;
        try {
            dml = "update specimen set is_introduced = 1 where code = ?";
            stmt = DBUtil.getPreparedStatement(getConnection(), "updateIntroducedSpecimen()", dml);
            stmt.setString(1, code);
            stmt.executeUpdate();
        } catch (SQLException e) {
            s_log.warn("updateIntroducedSpecimen() e:" + e);
        } finally {
            DBUtil.close(stmt, "updateIntroducedSpecimen()");
        }
    }
    
}
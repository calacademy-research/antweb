package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.*;

public class ImagePickDb extends AntwebDb {

    private static final Log s_log = LogFactory.getLog(ImagePickDb.class);

    public ImagePickDb(Connection connection) {
      super(connection);
    }


    public String getDefaultSpecimen(String caste, ArrayList<String> taxaNameSet) throws SQLException {
        String setStr = SqlUtil.getSetStr(taxaNameSet);
        if (setStr == null) return null;
        String taxonClause = " taxon_name in " + setStr;
        return getDefaultSpecimenWithClause(caste, taxonClause);
    }

    private String getDefaultSpecimenWithClause(String caste, String taxonClause) throws SQLException {
        String defaultSpecimen = null;

        String casteClause = " where " + Caste.getSpecimenClause(caste);
        //if ("male".equals(caste)) casteClause = " where is_male = 1";
        //if ("worker".equals(caste)) casteClause = " where is_worker = 1";
        //if ("queen".equals(caste)) casteClause = " where is_queen = 1";
        if (caste == null || Caste.DEFAULT.equals(caste)) casteClause = " where is_worker = 1 or is_queen = 1";

        String query = "select value from taxon_prop where "
			+ taxonClause
			+ " and prop = '" + Caste.getProp(caste) + "'"
			+ " and value in (select code from specimen " + casteClause + ")"
			;

        if (taxonClause.contains("adeto")) s_log.debug("ImagePickDb.getDefaultSpecimenWithClause(str, str) query:" + query);

        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getDefaultSpecimenWithClause()");
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                defaultSpecimen = rset.getString("value");
                break;
            }
        } catch (Exception e) {
            s_log.error("getDefaultSpecimenWithClause() e:" + e);
            throw e;
        } finally {
          DBUtil.close(stmt, rset, this, "getDefaultSpecimenWithClause()");
        }   
        return defaultSpecimen;
    }

    public String getDefaultSpecimen(String caste, Taxon taxon) throws SQLException {
      if (taxon == null) return null;
      String taxonNameClause = "";
      
      //A.log("ImagePickDb.getDefaultSpecimen() caste:" + caste + " taxonName:" + taxon.getTaxonName() + " speciesOrSubspecies:" + taxon.isSpeciesOrSubspecies() + " taxon:" + taxon + " class:" + taxon.getClass());

      if (taxon.isSpeciesOrSubspecies()) {
 	    taxonNameClause = " taxon_name = " + DBUtil.sqlQuote(taxon.getTaxonName());
      } else {
 	    taxonNameClause = higherTaxonClause(taxon.getRank(), taxon.getTaxonName());
        //s_log.warn("getDefaultSpecimen() caste:" + caste + " rank:" + taxon.getRank() + " taxonName:" + taxon.getTaxonName());
        //return null;
      }
      return getDefaultSpecimen(caste, taxonNameClause);
    }

    /**
     * The taxon_prop rows of a genus or subfamily and the names under it. A genus is its own name or its name
     * followed by a space (its species): a plain prefix also matched a longer genus of the same subfamily
     * (myrmicinaepheidole% matched myrmicinaepheidologeton ...), whose image then stood for the genus.
     * LIKE wildcards in the name are escaped.
     */
    public static String higherTaxonClause(String rank, String taxonName) {
      String like = taxonName == null ? "" : taxonName.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
      if (org.calacademy.antweb.Rank.GENUS.equals(rank)) {
        return " (taxon_name = " + DBUtil.sqlQuote(taxonName) + " or taxon_name like " + DBUtil.sqlQuote(like + " %") + ")";
      }
      return " taxon_name like " + DBUtil.sqlQuote(like + "%");
    }
    public String getDefaultSpecimenForTaxon(String caste, String taxonName) throws SQLException {
        // Caste.getPropsClause only produces fixed text; the taxon name is bound.
        String query = "select value from taxon_prop where taxon_name = ? and " + Caste.getPropsClause(caste)
            + " order by prop desc, taxon_name";
        PreparedStatement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getPreparedStatement(getConnection(), "getDefaultSpecimenForTaxon()", query);
            stmt.setString(1, taxonName);
            rset = stmt.executeQuery();
            if (rset.next()) return rset.getString("value");
            return null;
        } finally {
            DBUtil.close(stmt, rset, this, "getDefaultSpecimenForTaxon()");
        }
    }
    private String getDefaultSpecimen(String caste, String taxonNameClause) throws SQLException {

        String defaultSpecimen = null;
        String query = "select value from taxon_prop where "
			+ taxonNameClause
			+ " and " + Caste.getPropsClause(caste)
			+ " order by prop desc, taxon_name"
			;
        //A.log("ImagePickDb.getDefaultSpecimen() caste:" + caste + " query:" + query);
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getDefaultSpecimen()");
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                defaultSpecimen = rset.getString("value");            
                //A.log("getDefaultSpecimen() default:" + defaultSpecimen + " caste:" + caste + " query:" + query);
                break;
            }
        } catch (Exception e) {
            s_log.error("getDefaultSpecimen() e:" + e + " query:" + query);
            throw e;
        } finally {
          DBUtil.close(stmt, rset, this, "getDefaultSpecimen()");
        }   
        return defaultSpecimen;
    }

    public void unsetDefaultSpecimen(String caste, String taxonName, Login accessLogin) throws SQLException {
      setDefaultSpecimen(caste, taxonName, null, accessLogin);
    }
    public void setDefaultSpecimen(String caste, String taxonName, String specimenCode) throws SQLException {
      setDefaultSpecimen(caste, taxonName, specimenCode, null);
    }    
    public void setDefaultSpecimen(String caste, String taxonName, String specimenCode, Login accessLogin) throws SQLException {
        String message = "no message";
        
        int loginId = 0;
        if (accessLogin != null) loginId = accessLogin.getId();

        // All request values are bound; Caste.getPropsClause/getProp only produce fixed text.
        PreparedStatement stmt = null;
        String dml = null;
    	try {
          if (specimenCode == null) {
            dml = "delete from taxon_prop where taxon_name = ? and " + Caste.getPropsClause(caste);
            stmt = DBUtil.getPreparedStatement(getConnection(), "setDefaultSpecimen()", dml);
            stmt.setString(1, taxonName);
          } else {
            String defaultSpecimen = getDefaultSpecimenForTaxon(caste, taxonName);
            if (defaultSpecimen == null) {
              dml = "insert into taxon_prop (taxon_name, prop, value, login_id) values (?, ?, ?, ?)";
              stmt = DBUtil.getPreparedStatement(getConnection(), "setDefaultSpecimen()", dml);
              stmt.setString(1, taxonName);
              stmt.setString(2, Caste.getProp(caste));
              stmt.setString(3, specimenCode);
              stmt.setInt(4, loginId);
            } else if (!specimenCode.equals(defaultSpecimen)) {
              dml = "update taxon_prop set value = ?, login_id = ? where taxon_name = ? and " + Caste.getPropsClause(caste);
              stmt = DBUtil.getPreparedStatement(getConnection(), "setDefaultSpecimen()", dml);
              stmt.setString(1, specimenCode);
              stmt.setInt(2, loginId);
              stmt.setString(3, taxonName);
            }
          }
         
          if (stmt == null) return; // already the correct specimen code.


			stmt.executeUpdate();

            s_log.debug("setDefaultSpecimen() dml:" + dml + " taxonName:" + taxonName + " code:" + specimenCode);
		} catch (SQLException e) {
			s_log.error("setDefaultSpecimen() dml" + dml + " e:" + e);
			throw e;
		} finally { 		
			DBUtil.close(stmt, "setDefaultSpecimen()");
		}
	}

    /** True if the specimen exists and is filed under this taxon name. */
    public boolean isSpecimenOfTaxon(String specimenCode, String taxonName) throws SQLException {
        if (specimenCode == null || taxonName == null) return false;
        PreparedStatement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getPreparedStatement(getConnection(), "isSpecimenOfTaxon()",
                "select 1 from specimen where code = ? and taxon_name = ? limit 1");
            stmt.setString(1, specimenCode);
            stmt.setString(2, taxonName);
            rset = stmt.executeQuery();
            return rset.next();
        } finally {
            DBUtil.close(stmt, rset, this, "isSpecimenOfTaxon()");
        }
    }

    /** True if the taxon exists. */
    public boolean isTaxon(String taxonName) throws SQLException {
        if (taxonName == null) return false;
        PreparedStatement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getPreparedStatement(getConnection(), "isTaxon()",
                "select 1 from taxon where taxon_name = ? limit 1");
            stmt.setString(1, taxonName);
            rset = stmt.executeQuery();
            return rset.next();
        } finally {
            DBUtil.close(stmt, rset, this, "isTaxon()");
        }
    }

}    


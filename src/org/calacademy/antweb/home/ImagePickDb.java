package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.*;

public class ImagePickDb extends AntwebDb {

    private static Log s_log = LogFactory.getLog(ImagePickDb.class);

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
 	    taxonNameClause = " taxon_name = '" + taxon.getTaxonName() + "'";
      } else {
 	    taxonNameClause = " taxon_name like '" + taxon.getTaxonName() + "%'";
        //s_log.warn("getDefaultSpecimen() caste:" + caste + " rank:" + taxon.getRank() + " taxonName:" + taxon.getTaxonName());
        //return null;
      }
      return getDefaultSpecimen(caste, taxonNameClause);
    }
    public String getDefaultSpecimenForTaxon(String caste, String taxonName) throws SQLException {
      
      String taxonNameClause = " taxon_name = '" + AntFormatter.escapeQuotes(taxonName) + "'";
      return getDefaultSpecimen(caste, taxonNameClause);
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

        Statement stmt = null;
        String dml = null;
    	try {
          if (specimenCode == null) {
            String casteClause = Caste.getPropsClause(caste);
            dml = "delete from taxon_prop where taxon_name = '" + taxonName + "' and " + casteClause;
          } else {
            String defaultSpecimen = getDefaultSpecimenForTaxon(caste, taxonName);
            if (defaultSpecimen == null) {
              dml = "insert into taxon_prop (taxon_name, prop, value, login_id) values ('" + taxonName + "', '" + Caste.getProp(caste) + "', '" + specimenCode + "', " + loginId + ")";
            } else if (!specimenCode.equals(defaultSpecimen)) {
              dml = "update taxon_prop set value = '" + specimenCode + "', login_id = " + loginId 
              + " where taxon_name = '" + taxonName + "'"
               + " and " + Caste.getPropsClause(caste);
              //+ " and prop = 'default_specimen'";
            }
          }
         
          if (dml == null) return; // already the correct specimen code.          
          
            stmt = DBUtil.getStatement(getConnection(), "setDefaultSpecimen()");

			stmt.executeUpdate(dml);

            s_log.debug("setDefaultSpecimen() dml:" + dml);
		} catch (SQLException e) {
			s_log.error("setDefaultSpecimen() dml" + dml + " e:" + e);
			throw e;
		} finally { 		
			DBUtil.close(stmt, "setDefaultSpecimen()");
		}
	}

}    


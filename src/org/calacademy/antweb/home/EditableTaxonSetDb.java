package org.calacademy.antweb.home;

//import org.apache.regexp.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
        import org.calacademy.antweb.util.*;

        import java.sql.*;

public abstract class EditableTaxonSetDb extends TaxonSetDb {

    private static Log s_log = LogFactory.getLog(EditableTaxonSetDb.class);

    public EditableTaxonSetDb(Connection connection) {
      super(connection);
    }

    public abstract int delete(String speciesListName, String taxonName) throws SQLException;

    public abstract TaxonSet get(String speciesListName, String taxonName);

    public abstract int insert(Overview overview, String taxonName, String source) throws SQLException;

    public abstract boolean hasTaxonSetSpecies(String speciesListName, String genus);   
    public abstract boolean hasTaxonSetGenera(String speciesListName, String subfamily);

// ---

    public boolean hasTaxonSetSpecies(String speciesListName, String genus, String fromWhereClause) {

        boolean exists = false;
        String query = "";
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "hasTaxonSetSpecies()");
            query = "select 'x' as x " + fromWhereClause
               + " and taxon_name like '" + genus + " %'";  // contains a space at the end (so is a species); 
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                String x = (String) rset.getObject("x");
                exists = true;
            }
        } catch (SQLException e) {
            s_log.error("hasTaxonSetSpecies:" + speciesListName + ", " + genus);
        } finally {
            DBUtil.close(stmt, rset, "hasTaxonSetSpecies()");
        }
        A.log("hasTaxonSetSpecies() exists:" + exists + " query:" + query);
        return exists;
    }
    
    public boolean hasTaxonSetGenera(String speciesListName, String subfamily, String fromWhereClause) {
        
        boolean exists = false;
        String query = "";
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "hasTaxonSetGenera()");
            query = "select 'x' as x " + fromWhereClause
               + " and taxon_name like '" + subfamily + "%'"
               + " and taxon_name != '" + subfamily + "'"
               + " and taxon_name not like '% %'";
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                String x = (String) rset.getObject("x");
                exists = true;
            }
        } catch (SQLException e) {
            s_log.error("hasTaxonSetGenera:" + speciesListName + ", " + subfamily);
        } finally {
            DBUtil.close(stmt, rset, "hasTaxonSetGenera()");
        }
        A.log("hasTaxonSetGenus() exists:" + exists + " query:" + query);               
        return exists;
    }     
    
}

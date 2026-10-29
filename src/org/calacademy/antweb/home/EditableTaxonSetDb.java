package org.calacademy.antweb.home;

//import org.apache.regexp.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
        import org.calacademy.antweb.util.*;

        import java.sql.*;

/**
 * Base class of the species lists curators can edit, GeolocaleTaxonDb (geolocale_taxon) and ProjTaxonDb
 * (proj_taxon): the get, insert, delete and has-species / has-genera methods that SpeciesListDb and the
 * Species List Tool (speciesListTool.do) call on either kind of list. Docs: docs/data-model.md section 5.
 */
public abstract class EditableTaxonSetDb extends TaxonSetDb {

    private static final Log s_log = LogFactory.getLog(EditableTaxonSetDb.class);

    public EditableTaxonSetDb(Connection connection) {
      super(connection);
    }

    public abstract int delete(String speciesListName, String taxonName) throws SQLException;

    public abstract TaxonSet get(String speciesListName, String taxonName) throws SQLException;

    public abstract int insert(Overview overview, String taxonName, String source) throws SQLException;

    public abstract boolean hasTaxonSetSpecies(String speciesListName, String genus) throws SQLException ;
    public abstract boolean hasTaxonSetGenera(String speciesListName, String subfamily) throws SQLException ;

// ---

    public boolean hasTaxonSetSpecies(String speciesListName, String genus, String fromWhereClause) throws SQLException {

        boolean exists = false;
        String query = "";
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "hasTaxonSetSpecies()");
            query = "select 'x' as x " + fromWhereClause
               + " and taxon_name like " + DBUtil.sqlQuote(genus + " %");  // contains a space at the end (so is a species); 
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                String x = (String) rset.getObject("x");
                exists = true;
            }
        } catch (SQLException e) {
            s_log.error("hasTaxonSetSpecies:" + speciesListName + ", " + genus);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "hasTaxonSetSpecies()");
        }
        s_log.debug("hasTaxonSetSpecies() exists:" + exists + " query:" + query);
        return exists;
    }
    
    public boolean hasTaxonSetGenera(String speciesListName, String subfamily, String fromWhereClause) throws SQLException {
        
        boolean exists = false;
        String query = "";
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "hasTaxonSetGenera()");
            query = "select 'x' as x " + fromWhereClause
               + " and taxon_name like " + DBUtil.sqlQuote(subfamily + "%")
               + " and taxon_name != " + DBUtil.sqlQuote(subfamily)
               + " and taxon_name not like '% %'";
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                String x = (String) rset.getObject("x");
                exists = true;
            }
        } catch (SQLException e) {
            s_log.error("hasTaxonSetGenera:" + speciesListName + ", " + subfamily);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "hasTaxonSetGenera()");
        }
        s_log.debug("hasTaxonSetGenus() exists:" + exists + " query:" + query);
        return exists;
    }     
    
}

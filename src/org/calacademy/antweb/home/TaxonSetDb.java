package org.calacademy.antweb.home;

import java.util.*;
import java.util.Date;
import java.sql.*;

import javax.servlet.http.*;
import org.apache.struts.action.*;
//import org.apache.regexp.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.geolocale.*;

import java.sql.*;

public abstract class TaxonSetDb extends AntwebDb {

    private static Log s_log = LogFactory.getLog(TaxonSetDb.class);

    public TaxonSetDb(Connection connection) {
      super(connection);
    }

    public abstract ArrayList<Taxon> getTaxa(String name);
    
    public ArrayList<Taxon> getTaxa(Overview overview) {
        ArrayList<Taxon> taxa = new ArrayList<Taxon>();    
        
        String theQuery = "";
        ResultSet rset = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getTaxa()");

            theQuery = " select taxon.taxon_name, taxon.subfamily, taxon.genus, taxon.species, taxon.subspecies, taxon.author_date" 
              + " from taxon" 
              + overview.getFetchChildrenClause()
              + " and taxon.taxarank in ('species', 'subspecies')";
            theQuery += " order by subfamily, genus, species, subspecies, author_date";

            A.log("getTaxa() query:" + theQuery);
            rset = stmt.executeQuery(theQuery);

            int count = 0;
            while (rset.next()) {
                ++count;
                Taxon taxon = new Taxon();
                //taxon.setTaxonName(rset.getString("taxon_name"));
                taxon.setSubfamily(rset.getString("subfamily"));
                taxon.setGenus(rset.getString("genus"));
                taxon.setSpecies(rset.getString("species"));
                taxon.setSubspecies(rset.getString("subspecies"));
                taxon.setAuthorDate(rset.getString("author_date"));                
                taxa.add(taxon);
            }
            if (AntwebProps.isDevMode()) if (count == 0) s_log.error("getTaxa() not found overview:" + overview);
        } catch (SQLException e) {
            s_log.error("getTaxa() overview:" + overview + " exception:" + e + " theQuery:" + theQuery);
        } finally {
            DBUtil.close(stmt, rset, "this", "getTaxa()");
        }

        if (AntwebProps.isDevMode()) s_log.info("getTaxa() overview:" + overview + " query:" + theQuery);        
        return taxa;
    }       

// ---------------------------------------------------------------------------------------


    // This should only need to be run once. Run from UtilData.
    // Retained in scheduler because a specimen could be removed from a specimen list.
    public static String dataCleanup(Connection connection) {
      String message = "";
      // Only operating on the Editable ones.
      GeolocaleTaxonDb geolocaleTaxonDb = new GeolocaleTaxonDb(connection);
      message += geolocaleTaxonDb.deleteUncuratedMorphosWithoutSpecimen();
      message += " " + new ProjTaxonDb(connection).deleteUncuratedMorphosWithoutSpecimen();
      message += " " + geolocaleTaxonDb.deleteGeolocaleTaxaWithoutTaxon();     

      int c = (new UtilDb(connection)).deleteFrom("proj_taxon", "where taxon_name not in (select taxon_name from taxon) and project_name not in ('worldants')");
      A.log("dataCleanup() records deleted:" + c);

      return message;   
    }

    //update the taxon_names with the current_valid_names
    abstract String updateTaxonNames();
    public static String updateTaxonSetTaxonNames(Connection connection) {
      String message = "";
      message += new MuseumTaxonDb(connection).updateTaxonNames();
      message += " " + new BioregionTaxonDb(connection).updateTaxonNames();
      message += " " + new GeolocaleTaxonDb(connection).updateTaxonNames();
      message += " " + new ProjTaxonDb(connection).updateTaxonNames();
      
      return message;   
    }
        
    protected int updateTaxonSetTaxonName(String tableName, String taxonName, String currentValidName, String whereClause) {
      int c = 0;
      Statement stmt = null;
	  String dml = "update " + tableName + " set taxon_name = '" + currentValidName + "' where taxon_name = '" + taxonName + "' and " + whereClause;
      try {
          stmt = DBUtil.getStatement(getConnection(), "TaxonSetDb.updateTaxonSetTaxonName()");
          c = stmt.executeUpdate(dml);
          //A.log("updateTaxonSetTaxonName() c:" + c + " dml: " + dml);
      } catch (MySQLIntegrityConstraintViolationException e) {
        // This will slow things down, but tricky to replace.
        // We delete the record that we could not update to point at the new key which already exists.
        //s_log.warn("updateTaxonSetTaxonName() REALLY? why delete if pre-existing? tableName:" + tableName + " taxonName:" + taxonName + " whereClause:" + whereClause);
        deleteTaxonSetTaxonName(tableName, taxonName, whereClause);
      } catch (SQLException e) {
        s_log.error("TaxonSetDb.updateTaxonSetTaxonName() e:" + e);
      } finally {
        DBUtil.close(stmt, null, "TaxonSetDb.updateTaxonSetTaxonName()");
      }
      return c;
    }

    protected void deleteTaxonSetTaxonName(String tableName, String taxonName, String whereClause) {
      Statement stmt = null;
      try {
          String dml = "delete from " + tableName + " where taxon_name = '" + taxonName + "' and " + whereClause;
          stmt = DBUtil.getStatement(getConnection(), "TaxonSetDb.deleteTaxonSetTaxonName()");
          int x = stmt.executeUpdate(dml);
      } catch (SQLException e) {
        s_log.error("TaxonSetDb.deleteTaxonSetTaxonName() e:" + e);
      } finally {
        DBUtil.close(stmt, null, "TaxonSetDb.deleteTaxonSetTaxonName()");
      }
    }

// ---------------------------------------------------------------


/*
    public static String fixSpeciesLists(ArrayList<SpeciesListable> speciesListList, Connection connection) {
      String message = "";      
      //A.log("fixSpeciesLists() speciesListList:" + speciesListList);
      for (SpeciesListable speciesList : speciesListList) {  	
        if ("geolocale".equals(speciesList.getType()) || "country".equals(speciesList.getType()) || "adm1".equals(speciesList.getType())) {
          message += (new GeolocaleTaxonDb(connection)).fixSpeciesList((Geolocale) speciesList);
        }
        //message += (new ProjTaxonDb(connection)).fixSpeciesLists();
      }
      return message;
    }
*/
       
}

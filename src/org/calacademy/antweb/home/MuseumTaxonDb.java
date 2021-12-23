package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

public class MuseumTaxonDb extends TaxonSetDb {
    
    private static Log s_log = LogFactory.getLog(MuseumTaxonDb.class);
        
    public MuseumTaxonDb(Connection connection) {
      super(connection);
    }

    public ArrayList<Taxon> getTaxa(String name) throws SQLException {
        Museum museum = MuseumMgr.getMuseum(name);
        
        return super.getTaxa(museum);
    }

    String updateTaxonNames() throws SQLException {
      // For each of the following museum_taxon, update the taxon_name with the current_valid_name.
      Statement stmt = null;
	  String taxonName = null;
	  String currentValidName = null;
	  String code = null;
	  String tableName = null;
	  String whereClause = null;         
	  int c = 0;  
      try {
          String query = "select m.code, t.taxon_name, t.current_valid_name from taxon t, museum_taxon mt, museum m " 
          + " where t.taxon_name = mt.taxon_name and mt.code = m.code and t.status != 'valid' and current_valid_name is not null order by code, taxon_name";
          stmt = DBUtil.getStatement(getConnection(), "updateTaxonNames()");

          ResultSet rset = stmt.executeQuery(query);
          while (rset.next()) {
            taxonName = rset.getString("taxon_name");
            currentValidName = rset.getString("current_valid_name");
            code = rset.getString("code");

            tableName = "museum_taxon";
            whereClause = "code = '" + code + "'";

            c += updateTaxonSetTaxonName(tableName, taxonName, currentValidName, whereClause);            
          } 
      } catch (SQLException e) {
        s_log.error("updateTaxonNames() e:" + e);
      } finally {
        DBUtil.close(stmt, null, "updateTaxonNames()");
      }
      return c + " Museum Taxon Names updated to current valid Taxon Names.  ";
    }
    


    public static ArrayList<ArrayList<String>> getStatisticsByMuseum(Connection connection) throws SQLException {
        ArrayList<ArrayList<String>> statistics = new ArrayList<>();
        Statement stmt = null;
        ResultSet rset = null;
        String query = "select code, count(*) from museum_taxon group by code order by count(*) desc";
        try {
          stmt = DBUtil.getStatement(connection, "getStatisticsByMuseum()");              
          rset = stmt.executeQuery(query);

          while (rset.next()) {
              String museumCode = rset.getString(1);
              statistics.add(MuseumTaxonDb.getStatistics(museumCode, connection));
          }                                
        } finally {
          DBUtil.close(stmt, rset, "getStatisticsByMuseum()");
        }
        return statistics;
    }
    
    public static ArrayList<String> getStatistics(String museumCode, Connection connection) 
        throws SQLException {
        ArrayList<String> statistics = new ArrayList<>();
        int extinctSubfamily = 0;
        int extinctGenera= 0;
        int extinctSpecies = 0;
        int extantSubfamily = 0;
        int extantGenera = 0;
        int extantSpecies = 0;
        int validSubfamily = 0;
        int validGenera = 0;
        int validSpecies = 0;
        int validImagedSpecies = 0;
        int totalTaxa = 0;
        
        Statement stmt = null;
        ResultSet rset = null;

        String query = "select count(*) from taxon, museum_taxon where taxon.taxon_name = museum_taxon.taxon_name and taxon.fossil = 1 and museum_taxon.code = '" + museumCode + "' and rank=\"subfamily\"";

        try { 
            stmt = DBUtil.getStatement(connection, "getStatistics()");             
            rset = stmt.executeQuery(query);

            while (rset.next()) {
                extinctSubfamily = rset.getInt(1);
            }
        
            query = "select count(*) from taxon, museum_taxon where taxon.taxon_name = museum_taxon.taxon_name and taxon.fossil = 1 and museum_taxon.code = '" + museumCode + "' and rank=\"genus\"";
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                extinctGenera = rset.getInt(1);
            }
            query = "select count(*) from taxon, museum_taxon where taxon.taxon_name = museum_taxon.taxon_name and taxon.fossil = 1 and museum_taxon.code = '" + museumCode + "' and rank in ('species', 'subspecies')";
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                extinctSpecies = rset.getInt(1);
            }
            query = "select count(*) from taxon, museum_taxon where taxon.taxon_name = museum_taxon.taxon_name and taxon.fossil = 0 and museum_taxon.code = '" + museumCode + "' and rank=\"subfamily\"";
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                extantSubfamily = rset.getInt(1);
            }
            query = "select count(*) from taxon, museum_taxon where taxon.taxon_name = museum_taxon.taxon_name and taxon.fossil = 0 and museum_taxon.code = '" + museumCode + "' and rank=\"genus\"";
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                extantGenera = rset.getInt(1);
            }
            query = "select count(*) from taxon, museum_taxon where taxon.taxon_name = museum_taxon.taxon_name and taxon.fossil = 0 and museum_taxon.code = '" + museumCode + "' and rank in ('species', 'subspecies')";
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                extantSpecies = rset.getInt(1);
            }
            query = "select count(*) from taxon, museum_taxon where taxon.taxon_name = museum_taxon.taxon_name  and museum_taxon.code = '" + museumCode + "' and status='valid' and rank=\"subfamily\"";
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                validSubfamily = rset.getInt(1);
            }
            query = "select count(*) from taxon, museum_taxon where taxon.taxon_name = museum_taxon.taxon_name and museum_taxon.code = '" + museumCode + "' and status='valid' and rank=\"genus\"";
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                validGenera = rset.getInt(1);
            }
            query = "select count(*) from taxon, museum_taxon where taxon.taxon_name = museum_taxon.taxon_name and museum_taxon.code = '" + museumCode + "' and status='valid' and rank in ('species', 'subspecies')";
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                validSpecies = rset.getInt(1);
            }

            query = "select count(*) from taxon, museum_taxon where taxon.taxon_name = museum_taxon.taxon_name and museum_taxon.code = '" + museumCode + "'"
              + " and taxon.status = 'valid' and rank in ('species', 'subspecies') and museum_taxon.image_count > 0";
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                validImagedSpecies = rset.getInt(1);
            }        
        
            query = "select count(*) from taxon, museum_taxon where taxon.taxon_name = museum_taxon.taxon_name and museum_taxon.code = '" + museumCode + "'";
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                totalTaxa = rset.getInt(1);
            }
        } finally {
            DBUtil.close(stmt, rset, "getStatistics()");
        }

        statistics.add(museumCode); 
        statistics.add("" + extinctSubfamily);
        statistics.add("" + extantSubfamily); 
        statistics.add("" + validSubfamily);
        statistics.add("" + (extinctSubfamily + extantSubfamily));
        statistics.add("" + extinctGenera); 
        statistics.add("" + extantGenera);
        statistics.add("" + validGenera);
        statistics.add("" + (extinctGenera + extantGenera)); 
        statistics.add("" + extinctSpecies); 
        statistics.add("" + extantSpecies); 
        statistics.add("" + validSpecies); 
        statistics.add("" + validImagedSpecies);
        statistics.add("" + (extinctSpecies + extantSpecies));  
        statistics.add(""+totalTaxa);

        // A.log("getMuseumStatistics() statistics:" + statistics);                    
        return statistics;
    }
        
    
}
package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.geolocale.*;

public class BioregionTaxonDb extends TaxonSetDb {
    
    private static Log s_log = LogFactory.getLog(BioregionTaxonDb.class);
        
    public BioregionTaxonDb(Connection connection) {
      super(connection);
    }

    public ArrayList<Taxon> getTaxa(String name) throws SQLException {
        Bioregion bioregion = BioregionMgr.getBioregion(name);
        A.log("getTaxa() name:" + name);
        return super.getTaxa(bioregion);
    }

    String updateTaxonNames() throws SQLException {
      // For each of the following bioregion_taxon, update the taxon_name with the current_valid_name.
      Statement stmt = null;
      ResultSet rset = null;
	  String taxonName = null;
	  String currentValidName = null;
	  String bioregionName = null;
	  String tableName = null;
	  String whereClause = null;        
	  int c = 0;  
	  try {
          String query = "select bt.bioregion_name, t.taxon_name, t.current_valid_name from taxon t, bioregion_taxon bt, bioregion b " 
          + " where t.taxon_name = bt.taxon_name and bt.bioregion_name = b.name and t.status != 'valid' and current_valid_name is not null order by name, taxon_name";
          stmt = DBUtil.getStatement(getConnection(), "updateTaxonNames()");

          rset = stmt.executeQuery(query);
          while (rset.next()) {
            taxonName = rset.getString("taxon_name");
            currentValidName = rset.getString("current_valid_name");
            bioregionName = rset.getString("bioregion_name");
            
            tableName = "bioregion_taxon";
            whereClause = "bioregion_name = '" + bioregionName + "'";

            c += updateTaxonSetTaxonName(tableName, taxonName, currentValidName, whereClause);            
          }       
      } catch (SQLException e) {
        s_log.error("updateTaxonNames() e:" + e);
        throw e;
      } finally {
        DBUtil.close(stmt, rset, "updateTaxonNames()");
      }
      return c + " Bioregion Taxon Names updated to current valid Taxon Names.  ";
    }

    public int populateSpeciesFromSpecimen(Bioregion bioregion) {
      Statement stmt = null;
      ResultSet rset = null;
      int taxaCount = 0;

      //A.log("populateSpeciesFromSpecimen()");

      try {
          String query = "select s.taxon_name, count(*) specimenCount, count(id) imageCount " 
            + " from taxon t, specimen s " 
            + " left join image i on s.code = i.image_of_id " 
            + " where s.taxon_name = t.taxon_name"
            + " and t.fossil = 0" 
            + " and s.bioregion = '" + bioregion.getName() + "'" 
            + " and s.status in " + StatusSet.getCountables()
            + " and " + SpecimenDb.getFlagCriteria()
            + " group by taxon_name";

          A.log("populateSpeciesFromSpecimen() query:" + query);

          stmt = DBUtil.getStatement(getConnection(), "populateSpeciesFromSpecimen()"); 
          rset = stmt.executeQuery(query);

          while (rset.next()) {
            ++taxaCount;
            String taxonName = rset.getString("taxon_name");
            int specimenCount = rset.getInt("specimenCount");
            int imageCount = rset.getInt("imageCount");

			if (AntwebProps.isDevMode() && taxonName.equals("ponerinaeleptogenys ixta")) {
			  //s_log.warn("populateSpeciesFromSpecimen() query:" + query);
			}  

            insertSpecies(bioregion, taxonName, specimenCount, imageCount, "specimen");
          }

          LogMgr.appendLog("taxonSet.log", "bioregionTaxon.populateSpeciesFromSpecimen(" + bioregion + "):" + taxaCount, true);
          //A.log("populateSpeciesFromSpecimen() bioregion name:" + bioregion.getName() + " taxaCount:" + taxaCount + " query:" + query);

      } catch (SQLException e) {
        s_log.error("populateSpeciesFromSpecimen() e:" + e);
      } finally {
        DBUtil.close(stmt, rset, "populateSpeciesFromSpecimen()");
      }
      //A.log("populateSpeciesFromSpecimen() taxonCount:" + taxaCount);

      return taxaCount;
    }
    

    public int populateSpeciesFromGeolocaleTaxon(Bioregion bioregion) {
    
/*
Not used as of Dec 8 2018. Some countries have multiple bioregions. Could choose to only
use in cases where alt_bioregion is null or "none"?
See BioregionDb.java:77 where this call is commented out.
*/    

      Statement stmt = null;
      ResultSet rset = null;
      int taxaCount = 0;    
    
      try {
        String query = "select gt.taxon_name, gt.source " 
          + " from geolocale_taxon gt, geolocale g, taxon t " 
          + " where gt.taxon_name = t.taxon_name and gt.geolocale_id = g.id " 
          + " and g.bioregion = '" + bioregion.getName() + "'" 
          + " and g.georank = 'country'"
          + " and (g.alt_bioregion is null or g.alt_bioregion = 'none')"          
          + " and t.taxarank in ('species', 'subspecies')"
          + " and t.fossil = 0"
          + " order by source desc";

          stmt = DBUtil.getStatement(getConnection(), "populateSpeciesFromGeolocaleTaxon()"); 
          rset = stmt.executeQuery(query);

          String lastTaxonName = "";
          while (rset.next()) {
            ++taxaCount;
            String taxonName = rset.getString("taxon_name");
            String source = rset.getString("source");

            if (taxonName.equals(lastTaxonName)) {
              lastTaxonName = taxonName;
              continue;
			}
            lastTaxonName = taxonName;

			if (AntwebProps.isDevMode() && taxonName.equals("myrmicinaemelissotarsus ethiopiensis")) {
			  s_log.info("populateSpeciesFromGeolocaleTaxon() query:" + query);
			}  

            insertSpecies(bioregion, taxonName, 0, 0, source);
          }

          LogMgr.appendLog("taxonSet.log", "bioregionTaxon.populateSpeciesFromGeolocaleTaxon(" + bioregion + "):" + taxaCount, true);
          //A.log("populateSpeciesFromGeolocaleTaxon() bioregion name:" + bioregion.getName() + " taxaCount:" + taxaCount + " query:" + query);

      } catch (SQLException e) {
        s_log.error("populateSpeciesFromGeolocaleTaxon() e:" + e);
      } finally {
        DBUtil.close(stmt, rset, "populateSpeciesFromGeolocaleTaxon()");
      }
      return taxaCount;
    }

    // Overrides so as to get source
    public BioregionTaxon get(String bioregionName, String taxonName) throws SQLException {
        BioregionTaxon bioregionTaxon = null;
        String query = "";
        Statement stmt = null;
        ResultSet rset = null;
        try {  
            query = "select subfamily_count, genus_count, species_count, specimen_count, image_count, created, source " 
              + " from bioregion_taxon"
              + " where bioregion_name = '" + bioregionName + "'"
              + "   and taxon_name = '" + taxonName + "'";

            //A.log("init query:" + query);    //  && getTaxonName().contains("anceps")
        
            stmt = DBUtil.getStatement(getConnection(), "BioregionTaxonDb.get()"); 
            rset = stmt.executeQuery(query);
            
            while (rset.next()) {
                bioregionTaxon = new BioregionTaxon();
                bioregionTaxon.setTaxonName(taxonName);
                bioregionTaxon.setBioregionName(bioregionName);
                bioregionTaxon.setSubfamilyCount(rset.getInt("subfamily_count"));
                bioregionTaxon.setGenusCount(rset.getInt("genus_count"));
                bioregionTaxon.setSpeciesCount(rset.getInt("species_count"));
                bioregionTaxon.setSpecimenCount(rset.getInt("specimen_count"));
                bioregionTaxon.setImageCount(rset.getInt("image_count"));
                bioregionTaxon.setCreated(rset.getTimestamp("created"));
                bioregionTaxon.setSource(rset.getString("source"));
                                
                //if (AntwebProps.isDevMode() && "ectatomminae".equals(taxonName)) s_log.warn("init() taxonName:" + taxonName + " keyClause:" + getKeyClause() + " genusCount:" + getGenusCount() + " imageCount:" + imageCount);
            }             
            //A.log("init() warn() taxonName:" + taxonName + " projectName:" + projectName + " imageCount:" + imageCount);
        } catch (SQLException e) {
            s_log.error("get() Cannot convert value 0000-00-00 00:00:00 taxonName:" + taxonName + " bioregion:" + bioregionName + "  e:" + e + " query:" + query);
            throw e;
        } finally {
          DBUtil.close(stmt, rset, "BioregionTaxonDb.get()");
        }
        return bioregionTaxon;
    }   

    private void insertSpecies(Bioregion bioregion, String taxonName, int specimenCount, int imageCount, String source) {
      Statement stmt = null;
      try {          
          if (get(bioregion.getName(), taxonName) != null) {
            A.iLog("insertSpecies integrity checked");
            return;
          }
          String dml = "insert into bioregion_taxon (bioregion_name, taxon_name, specimen_count, image_count, insert_method, source) values ('" 
            + bioregion.getName() + "', '" + taxonName + "', " + specimenCount + ", " + imageCount + ",'insertBioregionSpecies', '" + source + "')";
            
          stmt = DBUtil.getStatement(getConnection(), "insertSpecies()");

          int x = stmt.executeUpdate(dml);
          
      } catch (java.sql.SQLIntegrityConstraintViolationException e) {
        s_log.info("insertSpecies() expected integrity exception. bioregion:" + bioregion.getName() + " taxonName:" + taxonName + " source:" + source); //e:" + e);

        // expected. Can we do an update here instead?  
      } catch (SQLException e) {
        s_log.error("insertSpecies() e:" + e);
      } finally {
        DBUtil.close(stmt, null, "insertSpecies()");
      }
    }
    
    public void deleteSource(String source) {
      Statement stmt = null;
      try {
          String dml = "delete from bioregion_taxon where source = '" + source + "'";
            
          stmt = DBUtil.getStatement(getConnection(), "deleteSource()");

          int deleteCount = stmt.executeUpdate(dml);
          LogMgr.appendLog("taxonSet.log", "bioregionTaxon.deleteSource(" + source + "):" + deleteCount, true);
      } catch (SQLException e) {
        s_log.error("deleteSource() e:" + e);
      } finally {
        DBUtil.close(stmt, null, "deleteSource()");
      }
    }
    
    public boolean insertTaxon(String bioregionName, String taxonName, String insertMethod, String source) throws SQLException {
        // for each record, insert into bioregion_taxon.  Ignore constraint conflicts.    

		if (get(bioregionName, taxonName) != null) {
		  A.iLog("BioregionTaxonDb.insertTaxon integrity checked");
		  return false;
		}
        String dml = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "insertTaxon()");

            dml = "insert into bioregion_taxon(bioregion_name, taxon_name, insert_method, source"
              + " ) values ('" + bioregionName + "', '" + taxonName + "','" + insertMethod + "', '" + source + "')";
            //A.log("insertTaxon() dml:" + dml);
            stmt.executeUpdate(dml);
        } catch (java.sql.SQLIntegrityConstraintViolationException e) {
            A.iLog("insertTaxon() optimize by checking for existence first? e:" + e);
            return false;
        } catch (SQLException e) {
            s_log.error("insertTaxon() e:" + e + " dml:" + dml);
        } finally {
            DBUtil.close(stmt, "insertTaxon()");
        }     
        return true;
    }    
    
    public void populateHigherTaxa() {
        populateHigherTaxa(null);
    }
    
    public void populateHigherTaxa(String bioregionName) {
        String bioregionNameClause = "";
        if (bioregionName != null) bioregionNameClause = " and bioregion_name = '" + bioregionName + "'";
        String query = "";

        ResultSet rset = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "populateHigherTaxa()");

            query = "select bioregion_name, taxon.taxon_name, bioregion_taxon.source" // , subfamily, genus, species, subspecies "
              + " from bioregion_taxon, taxon"
              + " where bioregion_taxon.taxon_name = taxon.taxon_name"
              + bioregionNameClause
              + " and taxon.status in " + StatusSet.getCountables()
              + " order by source desc, bioregion_name, taxon.taxon_name"
              ;

            //A.log("populateHigherTaxa() query:" + query);
            rset = stmt.executeQuery(query);

            int count = 0;
            while (rset.next()) {
                ++count;
                String rsetBioregionName = rset.getString("bioregion_name");
                String taxonName = rset.getString("taxon_name");
                String source = rset.getString("source");

                //if (AntwebProps.isDevMode()) if (taxonName.contains("pseudomyrmecinae")) s_log.error("populateHigherTaxa() taxonName:" + taxonName);

                insertHigherTaxon(rsetBioregionName, taxonName, source);
            }
            LogMgr.appendLog("taxonSet.log", "bioregionTaxon.populateHigherTaxa(" + bioregionName + "):" + count, true);

        } catch (SQLException e) {
            s_log.error("populateHigherTaxa() e:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "this", "populateHigherTaxa()");
        }
    }

    private int s_subfamilyCount = 0;
    private int s_generaCount = 0;
    private String s_lastSubfamily = "";
    private String s_lastGenus = "";
    private String s_lastBioregionName = "";

    public void insertHigherTaxon(String bioregionName, String taxonName, String source) throws SQLException {
        // On break of subfamily, create and insert a subfamily musuem record.
        String thisSubfamily = Taxon.getSubfamilyFromName(taxonName);
        String thisGenus = Taxon.getGenusFromName(taxonName);

        if (thisSubfamily == null || thisGenus == null) {
          //s_log.warn("insertHigherMusemTaxon() thisSubfamily null for bioregionName:" + bioregionName + " taxonName:" + taxonName + " subfamily:" + thisSubfamily + " lastSubfamily:" + s_lastSubfamily + " genus:" + thisGenus + " lastGenus:" + s_lastGenus);
          return;
        }

        if (!bioregionName.equals(s_lastBioregionName)) {
          s_lastBioregionName = bioregionName;
          //A.log("insertHigherTaxon() formicidae");
          boolean inserted = insertTaxon(bioregionName, "formicidae", "higherTaxonFamily", source);
        }
        
        if (!thisSubfamily.equals(s_lastSubfamily)) {
          s_lastSubfamily = thisSubfamily;
          boolean inserted = insertTaxon(bioregionName, thisSubfamily, "higherTaxonSubfamily", source);
          //A.log("insertHigherMusemTaxon() subfamily bioregionName:" + bioregionName + " taxonName:" + taxonName + " subfamily:" + thisSubfamily);
          if (inserted) ++s_subfamilyCount;
        }

        if (!thisGenus.equals(s_lastGenus)) {
          s_lastGenus = thisGenus;
          boolean inserted = insertTaxon(bioregionName, thisSubfamily + thisGenus, "higherTaxonGenus", source);
          // A.log("insertHigherMusemTaxon() genus bioregionName:" + bioregionName + " taxonName:" + thisSubfamily + thisGenus );
          if (inserted) ++s_generaCount;
        }    
    }

    public void insertBioregionTaxon(Bioregion bioregion, String taxonName) throws SQLException {
      Statement stmt = null;
      String dml = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "insertBioregionTaxon()");
        dml = "insert into bioregion_taxon (bioregion_name, taxon_name) "
            + " values ('" + bioregion.getName() + "', '" + taxonName + "')";
        stmt.executeUpdate(dml); 
      } catch (java.sql.SQLIntegrityConstraintViolationException e) {
        // do nothing.  Return false;  
      } catch (SQLException e) {
        s_log.warn("insertBioregionTaxon() e:" + e);
      } finally {
        DBUtil.close(stmt, "insertBioregionTaxon()");
      }   
    }

    public String getBioregionList(String taxonName) throws SQLException {
        String bioregionList = "";
        ArrayList<Bioregion> bioregions = getBioregions(taxonName);
        int i = 0;
        for (Bioregion bioregion : bioregions) {
            ++i;
            if (i > 1) bioregionList += ", ";
            bioregionList += bioregion.getName();
        }
        return bioregionList;
    }

    public ArrayList<Bioregion> getBioregions(String taxonName) throws SQLException {
        ArrayList<Bioregion> bioregions = new ArrayList<>();
        Statement stmt = null;
        ResultSet rset = null;
        String query = null;
        try {
            Bioregion bioregion = null;
            taxonName = AntFormatter.escapeQuotes(taxonName);        
            query =
                " select b.name, b.title" 
              + " from bioregion b, bioregion_taxon bt" //, bioregion b" 
              + " where b.name = bt.bioregion_name"
              + " and bt.taxon_name = '" + taxonName + "'"
              + " order by bt.bioregion_name";

            //A.log("Taxon.setGeolocales() query:" + query);
            stmt = DBUtil.getStatement(getConnection(), "getBioregions()");
            rset = stmt.executeQuery(query);
            int i = 0;
            while (rset.next()) {
              ++i;
              bioregion = new Bioregion();
              bioregion.setName(rset.getString("name"));
              bioregion.setTitle(rset.getString("title"));
              //bioregion.setGeorank(rset.getString("georank"));
              //bioregion.setSubregion(rset.getString("subregion"));
              //bioregion.setRegion(rset.getString("region"));
              //geolocale.setTitle(rset.getString("project_title"));
              //geolocale.setSource(rset.getString("source"));
              //bioregion.setBioregion(rset.getString("bioregion"));
              //geolocale.setDisplayKey(rset.getString("display_key"));
              //A.log("setGeolocales() i:" + i + " geolocale:" + geolocale);
              bioregions.add(bioregion);
            }

        } catch (SQLException e) {
            s_log.error("getBioregions() for taxonName:" + taxonName + " query:" + query + " e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, this, "getBioregions()");
        }            
        return bioregions;
    }
    

    public static ArrayList<ArrayList<String>> getStatisticsByBioregion(Connection connection) //ArrayList<ArrayList<String>>
        throws SQLException {

        ArrayList<ArrayList<String>> statistics = new ArrayList<>();
        Statement stmt = DBUtil.getStatement(connection, "getStatisticsByBioregion()");
        ResultSet resultSet = null;
        String query = "select bioregion_name, count(*) from bioregion_taxon group by bioregion_name order by count(*) desc";
        try {
            resultSet = stmt.executeQuery(query);

            while (resultSet.next()) {
                String bioregionName = resultSet.getString(1);
                statistics.add(BioregionTaxonDb.getStatistics(bioregionName, connection));
                //statsArray.add(ProjTaxonDb.getProjectStatistics(project, connection));
            }

        } catch (SQLException e) {
            s_log.error("getStatisticsByBioregion() query:" + query + " e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, resultSet, null, "getStatisticsByBioregion()"); // null was this
        }

        return statistics;
    }
    
    public static ArrayList<String> getStatistics(String bioregionName, Connection connection) 
        throws SQLException {

        ArrayList<String> statistics = new ArrayList<>();

        String query = "select count(*) from taxon, bioregion_taxon where taxon.taxon_name = bioregion_taxon.taxon_name and taxon.fossil = 1 and bioregion_taxon.bioregion_name = '" + bioregionName + "' and taxarank=\"subfamily\"";

        Statement stmt = null;
        ResultSet rset = null;
    try {

        stmt = DBUtil.getStatement(connection, "getStatistics()");
        rset = stmt.executeQuery(query);


        int extinctSubfamily = 0;
        while (rset.next()) {
            extinctSubfamily = rset.getInt(1);
        }
        query = "select count(*) from taxon, bioregion_taxon where taxon.taxon_name = bioregion_taxon.taxon_name and taxon.fossil = 1 and bioregion_taxon.bioregion_name = '" + bioregionName + "' and taxarank=\"genus\"";
        rset = stmt.executeQuery(query);
        int extinctGenera= 0;
        while (rset.next()) {
            extinctGenera = rset.getInt(1);
        }
        query = "select count(*) from taxon, bioregion_taxon where taxon.taxon_name = bioregion_taxon.taxon_name and taxon.fossil = 1 and bioregion_taxon.bioregion_name = '" + bioregionName + "' and taxarank in ('species', 'subspecies')";
        rset = stmt.executeQuery(query);
        int extinctSpecies = 0;
        while (rset.next()) {
            extinctSpecies = rset.getInt(1);
        }
        query = "select count(*) from taxon, bioregion_taxon where taxon.taxon_name = bioregion_taxon.taxon_name and taxon.fossil = 0 and bioregion_taxon.bioregion_name = '" + bioregionName + "' and taxarank=\"subfamily\"";
        rset = stmt.executeQuery(query);
        int extantSubfamily = 0;
        while (rset.next()) {
            extantSubfamily = rset.getInt(1);
        }
        query = "select count(*) from taxon, bioregion_taxon where taxon.taxon_name = bioregion_taxon.taxon_name and taxon.fossil = 0 and bioregion_taxon.bioregion_name = '" + bioregionName + "' and taxarank=\"genus\"";
        rset = stmt.executeQuery(query);
        int extantGenera = 0;
        while (rset.next()) {
            extantGenera = rset.getInt(1);
        }
        query = "select count(*) from taxon, bioregion_taxon where taxon.taxon_name = bioregion_taxon.taxon_name and taxon.fossil = 0 and bioregion_taxon.bioregion_name = '" + bioregionName + "' and taxarank in ('species', 'subspecies')";
        rset = stmt.executeQuery(query);
        int extantSpecies = 0;
        while (rset.next()) {
            extantSpecies = rset.getInt(1);
        }
        query = "select count(*) from taxon, bioregion_taxon where taxon.taxon_name = bioregion_taxon.taxon_name  and bioregion_taxon.bioregion_name = '" + bioregionName + "' and status='valid' and taxarank=\"subfamily\"";
        rset = stmt.executeQuery(query);
        int validSubfamily = 0;
        while (rset.next()) {
            validSubfamily = rset.getInt(1);
        }
        query = "select count(*) from taxon, bioregion_taxon where taxon.taxon_name = bioregion_taxon.taxon_name and bioregion_taxon.bioregion_name = '" + bioregionName + "' and status='valid' and taxarank=\"genus\"";
        rset = stmt.executeQuery(query);
        int validGenera = 0;
        while (rset.next()) {
            validGenera = rset.getInt(1);
        }
        query = "select count(*) from taxon, bioregion_taxon where taxon.taxon_name = bioregion_taxon.taxon_name and bioregion_taxon.bioregion_name = '" + bioregionName + "' and status='valid' and taxarank in ('species', 'subspecies')";
        rset = stmt.executeQuery(query);
        int validSpecies = 0;
        while (rset.next()) {
            validSpecies = rset.getInt(1);
        }
// 10
        query = "select count(*) from taxon, bioregion_taxon where taxon.taxon_name = bioregion_taxon.taxon_name and bioregion_taxon.bioregion_name = '" + bioregionName + "'"
          + " and taxon.status = 'valid' and taxarank in ('species', 'subspecies') and bioregion_taxon.image_count > 0";
        rset = stmt.executeQuery(query);
        int validImagedSpecies = 0;
        while (rset.next()) {
            validImagedSpecies = rset.getInt(1);
        }
        
        query = "select count(*) from taxon, bioregion_taxon where taxon.taxon_name = bioregion_taxon.taxon_name and bioregion_taxon.bioregion_name = '" + bioregionName + "'";
        rset = stmt.executeQuery(query);
        int totalTaxa = 0;
        while (rset.next()) {
            totalTaxa = rset.getInt(1);
        }

        statistics.add(bioregionName); 
        statistics.add("" + extinctSubfamily);
        statistics.add("" + extantSubfamily); 
        statistics.add("" + validSubfamily);
        statistics.add("" + (extinctSubfamily + extantSubfamily)); // all
        statistics.add("" + extinctGenera); 
        statistics.add("" + extantGenera);
        statistics.add("" + validGenera);
        statistics.add("" + (extinctGenera + extantGenera)); // all
        statistics.add("" + extinctSpecies); 
        statistics.add("" + extantSpecies); 
        statistics.add("" + validSpecies); 
        statistics.add("" + validImagedSpecies);
        statistics.add("" + (extinctSpecies + extantSpecies)); // all 
        statistics.add("" + totalTaxa);

    } catch (SQLException e) {
        s_log.error("getStatistics() query:" + query + " e:" + e);
        throw e;
    } finally {
        DBUtil.close(stmt, rset, null, "getStatistics()"); // null was this
    }

        // A.log("getProjectStatistics() statistics:" + statistics);                    
        return statistics;
    }
    
          
}
package org.calacademy.antweb.home;

import java.io.*;
import java.util.*;
import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.util.*;

public class StatisticsDb extends AntwebDb {
    
    private static Log s_log = LogFactory.getLog(StatisticsDb.class);

    public StatisticsDb(Connection connection) {
      super(connection);
    }

/*

Current Statistics:

Extant: 17 valid subfamilies, 334 valid genera, 13500 valid species, 1900 valid subspecies
Fossil: 3 valid subfamilies, 152 valid genera, 751 valid species, 3 valid subspecies

Number and % of valid species imaged.  (3000? are they all in one genus? Distributed across groups?).

List of species not imaged would be useful.

For each:

bioregion, 
  Extant: 17 valid subfamilies, 334 valid genera, 13500 valid species, 1900 valid subspecies
museum 
  Extant: 17 valid subfamilies, 334 valid genera, 13500 valid species, 1900 valid subspecies
  Fossil: 3 valid subfamilies, 152 valid genera, 751 valid species, 3 valid subspecies


*/


    public StatSet getExtantData() {
      UtilDb utilDb = new UtilDb(getConnection());
    
      int extantValidSubfamilies = utilDb.getCount("taxon", "fossil = 0 and status = 'valid' and taxarank = 'subfamily'");
      int extantValidGenera = utilDb.getCount("taxon", "fossil = 0 and status = 'valid' and taxarank = 'genus'");
      int extantValidSpecies = utilDb.getCount("taxon", "fossil = 0 and status = 'valid' and taxarank = 'species'");
      int extantValidSubspecies = utilDb.getCount("taxon", "fossil = 0 and status = 'valid' and taxarank = 'subspecies'");
      
      StatSet statSet = new StatSet("Extant");
      statSet.set1("Valid Subfamiles", extantValidSubfamilies);
      statSet.set2("Valid Genera", extantValidGenera);
      statSet.set3("Valid Species", extantValidSpecies);
      statSet.set4("Valid Subspecies", extantValidSubspecies);

      return statSet;      
    }

    public StatSet getFossilData() {
      UtilDb utilDb = new UtilDb(getConnection());

      int fossilValidSubfamilies = utilDb.getCount("taxon", "fossil = 1 and status = 'valid' and taxarank = 'subfamily'");
      int fossilValidGenera = utilDb.getCount("taxon", "fossil = 1 and status = 'valid' and taxarank = 'genus'");
      int fossilValidSpecies = utilDb.getCount("taxon", "fossil = 1 and status = 'valid' and taxarank = 'species'");
      int fossilValidSubspecies = utilDb.getCount("taxon", "fossil = 1 and status = 'valid' and taxarank = 'subspecies'");
        
      StatSet statSet = new StatSet("Fossil");
      statSet.set1("Valid Subfamiles", fossilValidSubfamilies);
      statSet.set2("Valid Genera", fossilValidGenera);
      statSet.set3("Valid Species", fossilValidSpecies);
      statSet.set4("Valid Subspecies", fossilValidSubspecies);

      return statSet; 
    }

// Bioregion data
    public ArrayList<StatSet> getBioregionData() {
      ArrayList<StatSet> statSets = new ArrayList<>();
      ArrayList<Bioregion> bioregions = BioregionMgr.getBioregions();
      if (bioregions == null) return statSets;
      
      for (Bioregion bioregion : bioregions) {
        statSets.add(getBioregionData(bioregion));
      }
      return statSets;
    }
    
    public StatSet getBioregionData(Bioregion bioregion) {
      UtilDb utilDb = new UtilDb(getConnection());

      int extantValidSubfamilies = utilDb.getCount("from taxon t, bioregion_taxon bt where t.taxon_name = bt.taxon_name and bioregion_name = '" + bioregion.getName() + "' and fossil = 0 and status = 'valid' and taxarank = 'subfamily'");
      int extantValidGenera = utilDb.getCount("from taxon t, bioregion_taxon bt where t.taxon_name = bt.taxon_name and bioregion_name = '" + bioregion.getName() + "' and fossil = 0 and status = 'valid' and taxarank = 'genus'");
      int extantValidSpecies = utilDb.getCount("from taxon t, bioregion_taxon bt where t.taxon_name = bt.taxon_name and bioregion_name = '" + bioregion.getName() + "' and fossil = 0 and status = 'valid' and taxarank = 'species'");
      int extantValidSubspecies = utilDb.getCount("from taxon t, bioregion_taxon bt where t.taxon_name = bt.taxon_name and bioregion_name = '" + bioregion.getName() + "' and fossil = 0 and status = 'valid' and taxarank = 'subspecies'");
        
      StatSet statSet = new StatSet(bioregion.getName());
      statSet.set1("Subfamiles", extantValidSubfamilies);
      statSet.set2("Genera", extantValidGenera);
      statSet.set3("Species", extantValidSpecies);
      statSet.set4("Subspecies", extantValidSubspecies);

      return statSet;
    }

// --- Museum Data ---
    public ArrayList<StatSet> getExtantMuseumData() {
      ArrayList<StatSet> statSets = new ArrayList<>();
      ArrayList<Museum> museums = MuseumMgr.getMuseums();
      if (museums == null) return statSets;
      for (Museum museum : museums) {
        statSets.add(getExtantMuseumData(museum));
      }
      return statSets;
    }
    
    public StatSet getExtantMuseumData(Museum museum) {
      UtilDb utilDb = new UtilDb(getConnection());

      int extantValidSubfamilies = utilDb.getCount("from taxon t, museum_taxon mt where t.taxon_name = mt.taxon_name and code = '" + museum.getCode() + "' and fossil = 0 and status = 'valid' and taxarank = 'subfamily'");
      int extantValidGenera = utilDb.getCount("from taxon t, museum_taxon mt where t.taxon_name = mt.taxon_name and code = '" + museum.getCode() + "' and fossil = 0 and status = 'valid' and taxarank = 'genus'");
      int extantValidSpecies = utilDb.getCount("from taxon t, museum_taxon mt where t.taxon_name = mt.taxon_name and code = '" + museum.getCode() + "' and fossil = 0 and status = 'valid' and taxarank = 'species'");
      int extantValidSubspecies = utilDb.getCount("from taxon t, museum_taxon mt where t.taxon_name = mt.taxon_name and code = '" + museum.getCode() + "' and fossil = 0 and status = 'valid' and taxarank = 'subspecies'");
        
      StatSet statSet = new StatSet(museum.getCode());
      statSet.set1("Subfamiles", extantValidSubfamilies);
      statSet.set2("Genera", extantValidGenera);
      statSet.set3("Species", extantValidSpecies);
      statSet.set4("Subspecies", extantValidSubspecies);

      return statSet;
    }

    public ArrayList<StatSet> getFossilMuseumData() {
      ArrayList<StatSet> statSets = new ArrayList<>();
      ArrayList<Museum> museums = MuseumMgr.getMuseums();
      if (museums == null) return statSets;      
      for (Museum museum : museums) {
        statSets.add(getFossilMuseumData(museum));
      }
      return statSets;
    }

    public StatSet getFossilMuseumData(Museum museum) {
      UtilDb utilDb = new UtilDb(getConnection());

      int extantValidSubfamilies = utilDb.getCount("from taxon t, museum_taxon mt where t.taxon_name = mt.taxon_name and code = '" + museum.getCode() + "' and fossil = 1 and status = 'valid' and taxarank = 'subfamily'");
      int extantValidGenera = utilDb.getCount("from taxon t, museum_taxon mt where t.taxon_name = mt.taxon_name and code = '" + museum.getCode() + "' and fossil = 1 and status = 'valid' and taxarank = 'genus'");
      int extantValidSpecies = utilDb.getCount("from taxon t, museum_taxon mt where t.taxon_name = mt.taxon_name and code = '" + museum.getCode() + "' and fossil = 1 and status = 'valid' and taxarank = 'species'");
      int extantValidSubspecies = utilDb.getCount("from taxon t, museum_taxon mt where t.taxon_name = mt.taxon_name and code = '" + museum.getCode() + "' and fossil = 1 and status = 'valid' and taxarank = 'subspecies'");
        
      StatSet statSet = new StatSet(museum.getCode());
      statSet.set1("Valid Subfamiles", extantValidSubfamilies);
      statSet.set2("Valid Genera", extantValidGenera);
      statSet.set3("Valid Species", extantValidSpecies);
      statSet.set4("Valid Subspecies", extantValidSubspecies);

      return statSet;
    }

    public StatSet getImageData(Museum museum) {
      UtilDb utilDb = new UtilDb(getConnection());

      int extantValidSubfamilies = utilDb.getCount("from taxon t, museum_taxon mt where t.taxon_name = mt.taxon_name and code = '" + museum.getCode() + "' and fossil = 1 and status = 'valid' and taxarank = 'subfamily'");
      int extantValidGenera = utilDb.getCount("from taxon t, museum_taxon mt where t.taxon_name = mt.taxon_name and code = '" + museum.getCode() + "' and fossil = 1 and status = 'valid' and taxarank = 'genus'");
      int extantValidSpecies = utilDb.getCount("from taxon t, museum_taxon mt where t.taxon_name = mt.taxon_name and code = '" + museum.getCode() + "' and fossil = 1 and status = 'valid' and taxarank = 'species'");
      int extantValidSubspecies = utilDb.getCount("from taxon t, museum_taxon mt where t.taxon_name = mt.taxon_name and code = '" + museum.getCode() + "' and fossil = 1 and status = 'valid' and taxarank = 'subspecies'");
        
      StatSet statSet = new StatSet(museum.getCode());
      statSet.set1("Valid Subfamiles", extantValidSubfamilies);
      statSet.set2("Valid Genera", extantValidGenera);
      statSet.set3("Valid Species", extantValidSpecies);
      statSet.set4("Valid Subspecies", extantValidSubspecies);

      return statSet;
    }


    // Everything below is not used and unnecessary. Oops. Really?
    // for statistics.do
    public String getStatistics() {
        String statistics = null;
        try {
            Statement stmt = getConnection().createStatement();              

            String query = "select count(*) from specimen";            
            ResultSet resultSet = stmt.executeQuery(query);
            int count = 0;
            while (resultSet.next()) {
                count = resultSet.getInt(1);
            }            
            statistics = "<br><br>current specimen count:<b>" + count + "</b><br><br>";

            query = "select id, action, specimens, extant_taxa, total_taxa, proj_taxa, bioregion_taxa, museum_taxa, geolocale_taxa, geolocale_taxa_introduced, geolocale_taxa_endemic " 
              + " , total_images, specimens_imaged, species_imaged, valid_species_imaged, login_id, created, exec_time from statistics " 
              + " order by created desc limit 1000";
            resultSet = stmt.executeQuery(query);

            statistics += "<table border=1><tr><td>Id</td><td>Action </td><td>Specimens</td><td>V. Sp.</td>" 
              + "<td>Total Taxa</td><td>Project Taxa</td><td>Bioregion Taxa</td><td>Museum Taxa</td><td>Geolocale Taxa</td><td>Introduced</td><td>Endemic</td><td>Total Images</td><td>Specimens Imaged </td>" 
              + "<td>Species Imaged </td><td>V. Sp. Imaged </td><td>Login Id</td><td>Created</td><td>Exec Time</td></tr>";

            count = 0;
            while (resultSet.next()) {
                ++count;
                int id = resultSet.getInt("id");
                String action = resultSet.getString("action");
                if (action == null) action = "";
                int specimens = resultSet.getInt("specimens");
                int extantTaxa = resultSet.getInt("extant_taxa");
                int totalTaxa = resultSet.getInt("total_taxa");
                int projTaxa = resultSet.getInt("proj_taxa");
                int bioregionTaxa = resultSet.getInt("bioregion_taxa");
                int museumTaxa = resultSet.getInt("museum_taxa");
                int geolocaleTaxa = resultSet.getInt("geolocale_taxa");
                int geolocaleTaxaIntroduced = resultSet.getInt("geolocale_taxa_introduced");
                int geolocaleTaxaEndemic = resultSet.getInt("geolocale_taxa_endemic");
                int totalImages = resultSet.getInt("total_images");
                int specimensImaged = resultSet.getInt("specimens_imaged");
                int speciesImaged = resultSet.getInt("species_imaged");
                int loginId = resultSet.getInt("login_id");
                Timestamp timestamp = resultSet.getTimestamp("created");
                String execTime = resultSet.getString("exec_time");
                if (execTime == null || "null".equals(execTime)) execTime = "n/a";
                int validSpeciesImaged = resultSet.getInt("valid_species_imaged");
                String formatDate = timestamp.toString();

                String specimensStr = "" + specimens;
                if (count == 1) specimensStr = "<a href=\"" + AntwebProps.getDomainApp() + "/statistics.do?byUpload=true\">" + A.commaFormat(specimens) + "</a>";

                String projectStr = "" + A.commaFormat(projTaxa);
                if (count == 1) projectStr = "<a href=\"" + AntwebProps.getDomainApp() + "/statistics.do?byProject=true\">" + A.commaFormat(projTaxa) + "</a>";

                String bioregionStr = "" + A.commaFormat(bioregionTaxa);
                if (count == 1) bioregionStr = "<a href=\"" + AntwebProps.getDomainApp() + "/statistics.do?byBioregion=true\">" + A.commaFormat(bioregionTaxa) + "</a>";

                String museumStr = "" + A.commaFormat(museumTaxa);
                if (count == 1) museumStr = "<a href=\"" + AntwebProps.getDomainApp() + "/statistics.do?byMuseum=true\">" + A.commaFormat(museumTaxa) + "</a>";

                String geolocaleStr = "" + A.commaFormat(geolocaleTaxa);
                if (count == 1) geolocaleStr = "<a href=\"" + AntwebProps.getDomainApp() + "/statistics.do?byGeolocale=true\">" + A.commaFormat(geolocaleTaxa) + "</a>";

                statistics += "<tr><td>" + id + "</td><td>" + action + "</td><td>" + specimensStr + "</td>" 
                + "<td>" + A.commaFormat(extantTaxa) + "</td><td>"+ A.commaFormat(totalTaxa) + "</td>" 
                + "<td>" + projectStr + "</td>" + "<td>" + bioregionStr + "</td>" + "<td>" + museumStr + "</td>"
                + " <td>" + geolocaleStr + "</td><td>" + A.commaFormat(geolocaleTaxaIntroduced) + "</td><td>" + A.commaFormat(geolocaleTaxaEndemic) + "</td>" 
                + " <td>" + A.commaFormat(totalImages) + "</td><td>" + A.commaFormat(specimensImaged) + "</td><td>" + A.commaFormat(speciesImaged) + "</td>" 
                + " <td>" + A.commaFormat(validSpeciesImaged) + "</td><td>" + loginId + "</td><td>" + formatDate + "</td>" 
                + " <td>" + execTime + "</td></tr>";
            }
            statistics += "</table>";

            stmt.close();
        } catch (SQLException e) {
          s_log.error("execute() e:" + e);        
        }
        return statistics;
	}

    public ArrayList<ArrayList<String>> getStatisticsByProject() {
        ArrayList<ArrayList<String>> statistics = null;
        try {
            statistics = ProjTaxonDb.getStatisticsByProject(getConnection());

        } catch (SQLException e) {
          s_log.error("getStatisticsByProject() e:" + e);        
        }
        
        return statistics;
	}

    public ArrayList<ArrayList<String>> getStatisticsByBioregion() {
        ArrayList<ArrayList<String>> statistics = null; 
        try {
            statistics = BioregionTaxonDb.getStatisticsByBioregion(getConnection());
        } catch (SQLException e) {
          s_log.error("getStatisticsByBioregion() e:" + e);        
        }
        return statistics;
	}

    public ArrayList<ArrayList<String>> getStatisticsByMuseum() {
		ArrayList<ArrayList<String>> statistics = null;		
        Connection connection = null;
        try {
            statistics = MuseumTaxonDb.getStatisticsByMuseum(getConnection());          
        } catch (SQLException e) {
          s_log.error("getStatisticsByMuseum() e:" + e);        
        }
        return statistics;
	}

    public ArrayList<ArrayList<String>> getStatisticsByGeolocale() {
        ArrayList<ArrayList<String>> statistics = null;
        try {
            statistics = GeolocaleTaxonDb.getStatisticsByGeolocale(getConnection());       
        } catch (SQLException e) {
          s_log.error("getStatisticsByGeolocale() e:" + e);        
        }
        
        return statistics;
	}
			
    public ArrayList<ArrayList<String>> getStatisticsByAProject(String project) {
		ArrayList<ArrayList<String>> statistics = null;
        try {
            statistics = new ArrayList<>();
            statistics.add(ProjTaxonDb.getProjectStatistics(project, getConnection()));
         
        } catch (SQLException e) {
          s_log.error("execute() e:" + e);        
        }
        
        return statistics;
	}
	
    public String getStatisticsByUpload() {
        String statistics = null;
        try {
            Statement stmt = getConnection().createStatement();              

            String query = "select count(code), name, id from specimen, ant_group where access_group = id group by access_group";
            ResultSet resultSet = stmt.executeQuery(query);

            statistics = "<table border=1><tr><td> Specimen Count </td><td> Group Name </td></tr>";
            while (resultSet.next()) {
                int count = resultSet.getInt(1);
                String groupName = resultSet.getString(2);
                int id = resultSet.getInt(3);
                statistics += "<tr><td>" + count + "</td><td>" + groupName + "(" + id + ")" + "</td></tr>";
            }            
            statistics += "</table>";            
            
            stmt.close();         
        } catch (SQLException e) {
          s_log.error("execute() e:" + e);        
        }
        
        return statistics;
	}

    // Called by the Scheduler (or at startup?)
    public void populateStatistics() throws SQLException, IOException {
        populateStatistics("routine", 0, null, AntwebProps.getDocRoot());
    }

    public void populateStatistics(String action, int loginId, String execTime, String docBase)
            throws SQLException, IOException {

            UtilDb utilDb = new UtilDb(getConnection());

            // number of specimens imaged
            String query = "select count(distinct specimen.code) from specimen,image where "
                    + " specimen.code = image.image_of_id";
            int imagedSpecimens = utilDb.getCount(query);
            AntwebMgr.setImagedSpecimensCount(imagedSpecimens);
            //A.log("populateStatistics 1 query:" + query);

            // number of specimen records
            query = "select count(*) from specimen";
            int specimenRecords = utilDb.getCount(query);
            AntwebMgr.setSpecimensCount(specimenRecords);
            //A.log("populateStatistics 2 query:" + query);

            // number of species + spp. imaged
            query = "select distinct genus, species, subspecies from specimen, image where"
                    + " specimen.code = image.image_of_id";
            // and then find the number of rows in result
            int imagedSpecies = utilDb.getCountFromQuery(query);
            AntwebMgr.setImagedSpeciesCount(imagedSpecies);

            //A.log("populateStatistics 3 query:" + query);
            query = "select count(*) from image where image_of_id in "
                + " (select s.code from specimen s, taxon t where s.taxon_name = t.taxon_name and t.status = 'valid')";
            int validSpeciesImaged = utilDb.getCount(query);
            //A.log("populateStatistics 4 query:" + query);

            // total number of image records
            query = "select count(*) from image";
            int totalImages = utilDb.getCount(query);
            AntwebMgr.setTotalImagesCount(totalImages);
            //A.log("populateStatistics 5 query:" + query);

            // number of valid species in database
            query = "select count(taxon_name) from taxon where status = 'valid' and taxarank in ('species', 'subspecies')";
            //A.log("runStatistics() query:" + query);            
            int validSpecies = utilDb.getCount(query);
            AntwebMgr.setValidSpeciesCount(validSpecies);

            //A.log("populateStatistics 6 query:" + query);

            query = "select count(*) from taxon where taxarank in ('species', 'subspecies')";
            int numberTotalTaxa = utilDb.getCount(query);

            query = "select count(*) from proj_taxon";
            int numberProjTaxa = utilDb.getCount(query);
            //if (numberProjTaxa < 78000) AdminAlertMgr.add("Project_taxa below expected count:" + numberProjTaxa, getConnection());

            query = "select count(*) from bioregion_taxon";
            int numberBioregionTaxa = utilDb.getCount(query);

            query = "select count(*) from museum_taxon";
            int numberMuseumTaxa = utilDb.getCount(query);

            query = "select count(*) from geolocale_taxon";
            int numberGeolocaleTaxa = utilDb.getCount(query);

            query = "select count(*) from geolocale_taxon where is_introduced = 1";
            int numberGeolocaleTaxaIntroduced = utilDb.getCount(query);

            query = "select count(*) from geolocale_taxon where is_endemic = 1";
            int numberGeolocaleTaxaEndemic = utilDb.getCount(query);
            A.log("populateStatistics query:" + query);

            if (docBase != null) {

                File outputFile = new File(docBase + "/web/genInc/" + "statistics.jsp");
                FileWriter outFile = new FileWriter(outputFile);

                // here write out the results
                //outFile.write("number of types imaged: " + imagedTypes + "<br>\n");
            
                outFile.write("<span class=\"numbers\">" + validSpecies + "</span> valid species + ssp.<br/>\n");
                //outFile.write("<span class=\"numbers\">" + numberTotalTaxa + "</span> total species + ssp.<br/>\n");            
                outFile.write("<span class=\"numbers\">" + specimenRecords + "</span> specimen records<br/>\n");
                outFile.write("<span class=\"numbers\">" + imagedSpecies + "</span> species + ssp. imaged <br/>\n");
                outFile.write("<span class=\"numbers\">" + imagedSpecimens + "</span> specimens imaged<br/>\n");
                outFile.write("<span class=\"numbers\">" + totalImages + "</span> total specimen images<br/>\n");

                outFile.close();
            }

            String update = "insert into statistics "
                    + " (action, specimens, extant_taxa, total_taxa, proj_taxa, bioregion_taxa, museum_taxa, geolocale_taxa, geolocale_taxa_introduced, geolocale_taxa_endemic " 
                    + " , total_images, specimens_imaged, species_imaged, valid_species_imaged, login_id, exec_time) "  //
                    + " values ('" + action + "'," +  specimenRecords + "," + validSpecies + "," + numberTotalTaxa
                    + "," + numberProjTaxa + "," + numberBioregionTaxa + "," + numberMuseumTaxa + "," + numberGeolocaleTaxa + "," + numberGeolocaleTaxaIntroduced + "," + numberGeolocaleTaxaEndemic
                    + "," + totalImages + "," + imagedSpecimens 
                    + "," + imagedSpecies + "," + validSpeciesImaged + "," + loginId + ", '" + execTime + "')";
            int c = utilDb.runDml(update);
    }
}
        
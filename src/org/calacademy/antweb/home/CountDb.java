package org.calacademy.antweb.home;

import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.Geolocale;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

public class CountDb extends AntwebDb {
    
/*
    CountDb is overridden by GeolocaleTaxonCountDb, BioregionTaxonCountDb, MuseumTaxonCountDb and ProjTaxonCountDb. 

    This functionality is not to be confused with geolocale counts which are computed by GeolocaleDb.populate()
      or with museum counts which are computed by MuseumDb.populate(), etc...
      These counts are Taxon related.

		select id, name, subfamily_count, genus_count, species_count, specimen_count, image_count, imaged_specimen_count from geolocale where id = 7;
		+----+------------+-----------------+-------------+---------------+----------------+-------------+-----------------------+
		| id | name       | subfamily_count | genus_count | species_count | specimen_count | image_count | imaged_specimen_count |
		+----+------------+-----------------+-------------+---------------+----------------+-------------+-----------------------+
		|  7 | Madagascar |               8 |          62 |           883 |         115315 |       28080 |                  5302 |

    Here we are calculating TaxonSets (ProjTaxon, GeolocaleTaxon, MuseumTaxon, BioregionTaxon).
      These are specimen counts. First we count specimen in species, then we sum up to 
      genus, then we sum up to subfamily.    

    Primary access point is TaxonCountDb.allCountCrawls() via /utilData.do?action=countCrawls
      This invokes all of the subclass countCrawls().

      Can be invoked for testing on Overview page in Admin Info 
        or on TaxonPage under admin section with link under TaxonSet: Count Crawl Madagascar
        http://localhost/antweb/utilData.do?action=countCrawl&num=7
*/

    private static Log s_log = LogFactory.getLog(CountDb.class);
        
    // Best to use a lightly populated subfamily name.    
    //String debugName = "martialinae";    
    String debugTaxonName = "dorylinaeacanthostichus davisi";
    int debugGeolocaleId = 0;
    //int debugGeolocaleId = 2;
    boolean debug = true;

    public static boolean s_isBulk = false; // We want to turn of logging if invoked from the scheduler.

    public CountDb(Connection connection) {
      super(connection);
    }

    // Useful for debugging? Code to execute and get results or a particular count.
    private static String s_query = null;  // Just used for reporting.
    private static String s_report = "";
    private static boolean s_debug = false;
    protected String childrenCountReport(Countable countable) throws SQLException {
        if (countable == null) A.log("childrenCountReport() why is countable null?");
        debug = AntwebProps.isDevMode();
        s_query = null;
        s_report = "";
        s_debug = true;
        int count = countChildren(countable, "species", "species_count");
        return "<br><br>count: " + count + " <br><br>query: " + s_query + " <br><br>report: " + s_report;
    }

    // Countables include Geolocale, Bioregion, Museum, Project.
    protected void childrenCountCrawl(Countable countable) throws SQLException {
      if (countable == null) A.log("childrenCountCrawl() why is countable null?");
        debug = AntwebProps.isDevMode();
		int specimenCount = countSpecimens(countable);

		int speciesSpeciesCount = countChildren(countable, "species", "species_count");
		int speciesSpecimenCount = countGrandChildren(countable, "species", "specimen_count");

		int genusGenusCount = countChildren(countable, "genus", "genus_count");
		int genusSpeciesCount = countGrandChildren(countable, "genus", "species_count");
		int genusSpecimenCount = countGrandChildren(countable, "genus", "specimen_count");

		int subfamilyCount = countChildren(countable, "subfamily", "subfamily_count");
		int subfamilyGenusCount = countGrandChildren(countable, "subfamily", "genus_count");
		int subfamilySpeciesCount = countGrandChildren(countable, "subfamily", "species_count");
		int subfamilySpecimenCount = countGrandChildren(countable, "subfamily", "specimen_count");

        String message = "childrenCountCrawl()" 
		  + " countable:" + countable 
		  + " specimenCount:" + specimenCount 
		  + " speciesSpeciesCount:" + speciesSpeciesCount 
		  + " speciesSpecimenCount:" + speciesSpecimenCount 
		  + " genusGenusCount:" + genusGenusCount 
		  + " genusSpeciesCount:" + genusSpeciesCount 
		  + " genusSpecimenCount:" + genusSpecimenCount 
		  + " subfamilyCount:" + subfamilyCount 
		  + " subfamilyGenusCount:" + subfamilyGenusCount 
		  + " subfamilySpeciesCount:" + subfamilySpeciesCount 
		  + " subfamilySpecimenCount:" + subfamilySpecimenCount
		  ;
		if (countable != null && (
		        "Alabama".equals(countable.toString())
             || "Alaska".equals(countable.toString())
             || "Colorado".equals(countable.toString())
                )) A.log("chilrenCountCrawl() message:" + message);
    }
    
    protected int countSpecimens(Countable countable) 
        throws SQLException {
        // was: String query = "select count(*) count, taxon_name from specimen group by taxon_name";  
        //      parent_taxon_id pid from specimen group by parent_taxon_id";
        
        // select count(*) count, specimen.taxon_name taxonName from specimen 
        // join geolocale_taxon gt on specimen.taxon_name = gt.taxon_name 
        // where gt.geolocale_id = 7 
        // and specimen.status in  ('valid', 'unrecognized', 'morphotaxon', 'indetermined', 'unidentifiable') 
        // group by taxonName        

      int taxonCount = 0;
      int sum = 0;
      
      if (countable == null) {
        A.log("countSpecimens() countable is null");
        return 0;
      }  
      String query = countable.getCountSpecimensQuery();
      s_query = query; // For report.

       // getCountSpecimensLocalQuery is like CountSpecimensQuery but will add a geolocale
       // context sensitive criteria like: and specimen.adm1 = "California"  
       // or specimen.country = "United States"
      Statement stmt = null;
      ResultSet rset = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "countSpecimens()");
        rset = stmt.executeQuery(query);
        
        //if (countable instanceof Geolocale) A.log("countSpecimens() query:" + query);
        /*
        select count(*) count, specimen.taxon_name taxonName from specimen  join geolocale_taxon gt on specimen.taxon_name = gt.taxon_name  
        where gt.geolocale_id = 392 and specimen.status in  ('valid', 'unrecognized', 'morphotaxon', 'indetermined', 'unidentifiable') group by taxonName;
        */       
        
        String taxonName = null;
        while (rset.next()) {
          ++taxonCount;
          int count = rset.getInt("count");
          sum += count;
          taxonName = rset.getString("taxonName");
          updateCount(countable, taxonName, "specimen_count", count);       

          if (AntwebProps.isDevMode()) {
            //A.log("countSpecimens() update countable:" + countable + " taxonName:" + taxonName + " count:" + count);
            if (taxonName.contains(debugTaxonName)) {
	 		  if (query.contains("geolocale_id = " + debugGeolocaleId + " ")) {
                A.log("countSpecimens() countable:" + countable + " taxonName:" + taxonName + " count:" + count + " query:" + query);
              }
            }
          }            
        }
        //if (debug) s_log.warn("countSpecimen() sum:" + sum + " query:" + query);
        
      } finally {
          DBUtil.close(stmt, rset, this, "countSpecimens()");
      }
      return taxonCount;
    }         

    protected int countChildren(Countable countable, String rank, String column)
      throws SQLException {

        int sum = 0;
        int taxonSetSum = 0;
        int count = -1;

        if (countable == null) {
          A.log("countChildren() countable is null");
          return 0;
        }  

        String query = countable.getCountChildrenQuery(rank);
        s_query = query; // For report.

        Statement stmt = null;
        ResultSet rset = null;
        try {
		    stmt = DBUtil.getStatement(getConnection(), "countChildren()");
			rset = stmt.executeQuery(query);
		
			String parentTaxonName = null;
			while (rset.next()) {
			  count = rset.getInt("count");
			  sum += count;
			  parentTaxonName = rset.getString("parentTaxonName");

              if (true && "Channel Islands".equals(countable.toString())) {
                A.log("countChildren() rank:" + rank + " count:" + count + " sum:" + sum + " parentTaxonName:" + parentTaxonName + " query:" + query);
              }

              // For diagnosing if counts are off. Consider invoking with: http://localhost/antweb/utilData.do?action=countReport&num=768  (geolocaleId)
              if (s_debug) {
                if (countable instanceof Geolocale) {
                    // We would like to know if the count is contestable? Sometimes genus counts are off (frequently by 1 or 2).
                    GeolocaleTaxonDb geolocaleTaxonDb = new GeolocaleTaxonDb(getConnection());
                    int taxonSetCount = geolocaleTaxonDb.getGeolocaleTaxonCount((Geolocale) countable, parentTaxonName);
                    taxonSetSum += taxonSetCount;
                    if (count != taxonSetCount) {
                      String report = "countable:" + countable + " parentTaxonName:" + parentTaxonName + " sum:" + sum + " taxonSetSum:" + taxonSetSum + " count:" + count + " taxonSetCount:" + taxonSetCount;
                      s_log.warn("countChildren() report: " + report);
                      s_report += "<br>" + report;
                    }
                }
              }

              if (!s_debug) updateCount(countable, parentTaxonName, column, count);
              //A.log("countChildren() parentTaxonName null for countable:" + countable);
              //if (AntwebProps.isDevMode() && (parentTaxonName != null && parentTaxonName.contains(debugTaxonName))) 
              // if (query.contains("geolocale_id = " + debugGeolocaleId + " ")) {
				//A.log("CountDb.countChildren() countable:" + countable + " rank:" + rank + " column:" + column + " parentTaxonName:" + parentTaxonName + " count:" + count + " query:" + query);
               //} 
			}
            // if (debug) s_log.warn("countChildren() sum:" + sum + " query:" + query);
		} finally {
			DBUtil.close(stmt, rset, "countChildren()");
            s_debug = false;
        }

        //A.log("countChildren() countable:" + countable + " rank:" + rank + " column:" + column + " count:" + count + " sum:" + sum + " query:" + query);
        return sum; 
    }    

    //*** If performance slows, this query was fixed. Geolocale.getCountGrandChildrenQuery()
    // was returning rank instead of rankClause. Not sure this query is necessary as it
    // populates specimen_count of formicidae.
    //  select sum(gt.specimen_count) sum, taxon.parent_taxon_name parentTaxonName  from taxon  join geolocale_taxon gt on taxon.taxon_name = gt.taxon_name   where  taxon.taxarank = 'subfamily'   and gt.geolocale_id = 392  group by parentTaxonName;
     protected int countGrandChildren(Countable countable, String rank, String column) 
       throws SQLException {
       
        if (countable == null) {
          A.log("countGrandChildren() countable:" + countable + " rank:" + rank + " column:" + column);
          //AntwebUtil.logShortStackTrace();
          return 0;
        }
       
        int sum = 0;
        int count = 0;

        String query = countable.getCountGrandChildrenQuery(rank, column);
        s_query = query; // For report.

        Statement stmt = null;
        ResultSet rset = null;
        try {
          stmt = DBUtil.getStatement(getConnection(), "countGrandChildren()");
          rset = stmt.executeQuery(query);
          String parentTaxonName = null;

          while (rset.next()) {
            count = rset.getInt("sum");
            sum += count;
            parentTaxonName = rset.getString("parentTaxonName");
            updateCount(countable, parentTaxonName, column, count);

            //if (parentTaxonName == null && debug) s_log.warn("countGrandChildren() parentTaxonName null for countable:" + countable);
            //if (parentTaxonName != null && parentTaxonName.equals(debugTaxonName)) 
            //A.log("countGrandChildren() countable:" + countable + " parentTaxonName:" + parentTaxonName + " count:" + count + " sum:" + sum);
          }
        } finally {
            DBUtil.close(stmt, rset, this, "countGrandChildren()");
        }

         //A.logi("countGrandChildren()", 7, "count:" + count + " query:" + query);

         // return count;
        return sum;
     }     
     

    protected void updateCount(Countable countable, String parentTaxonName, String columnName, int count) 
      throws SQLException {
      
		//if (AntwebProps.isDevMode()) {
		//  prepUpdateCount(countable, parentTaxonName, columnName);
		//  return;
		//}
    
        Statement stmt = null;
        String otherUpdateCountSQL = null;
        
        String updateCountSQL = null;
        
        try {
          stmt = DBUtil.getStatement(getConnection(), "updateCount()");

          updateCountSQL = countable.getUpdateCountSQL(parentTaxonName, columnName, count);
          int taxonUpdateCount = stmt.executeUpdate(updateCountSQL);
        
          //A.log("updateCount() taxonUpdateCount:" + taxonUpdateCount + " dml:" + updateCountSQL);

          //if (taxonUpdateCount == 0)
            //A.log("updateCount() None Updated countable:" + countable + " columnName:" + columnName + " parentTaxonName:" + parentTaxonName); // + " updateCountSQL:" + updateCountSQL);

        } catch (SQLException e) {
          s_log.warn("updateCount() countable:" + countable + " parentTaxonName:" + parentTaxonName + " columnName:" + columnName + " count:" + count + " updateCountSQL:" + updateCountSQL + " otherUpdateCountSQL:" + otherUpdateCountSQL);
          throw e;
        } finally {
          DBUtil.close(stmt, null, this, "updateCount()");
        }
    }   	    
    
// =================== Image Counts =======================

    protected void imageCountCrawl(Countable countable) throws SQLException {
        if (countable == null) {
          s_log.warn("imageCountCrawl WST countable is null");
          AntwebUtil.logShortStackTrace();
          return;
        }
        
        String query = countable.getTaxonImageCountQuery();
       
        //A.log("imageCountCrawl() countable:" + countable);
               
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "imageCountCrawl()");
            rset = stmt.executeQuery(query);
         
            //A.log("imageCountCrawl(" + countable + ") query:" + query);
/*
select s.taxon_name taxonName, s.family family, s.subfamily subfamily 
  , s.genus genus, s.species species, sum(s.image_count) imageSum
  from specimen s join museum_taxon pt on s.taxon_name = mt.taxon_name 
  where mt.museum_id = 17
  group by s.taxon_name, s.family, s.subfamily, s.genus, s.species;
*/         
        
			String taxonName = null;
			String family = null;
			String subfamily = null;
			String genus = null;
			String species = null;
			int imageSum = 0;
			int genusImageSum = 0, subfamilyImageSum = 0, familyImageSum = 0;
			String lastFamily = null, lastSubfamily = null, lastGenus = null;         
			while (rset.next()) {
			   taxonName = rset.getString("taxonName");
			   family = rset.getString("family");
			   subfamily = rset.getString("subfamily");
			   genus = rset.getString("genus");
			   species = rset.getString("species");
			   imageSum = rset.getInt("imageSum");
		   
			   //if (AntwebProps.isDevMode() && taxonName.equals(debugTaxonName)) s_log.warn("imageCountCrawl() query:" + query);

			   if ((lastGenus != null) && (genus != null) && (!genus.equals(lastGenus))) {
				 // break on genus             
				 String genusTaxonName = lastSubfamily + lastGenus;
				 updateCountableTaxonImageCount(countable, genusTaxonName, genusImageSum, "genus");
				 genusImageSum = imageSum;
			   } else {
				 genusImageSum += imageSum;
			   }
			   lastGenus = genus;
		   
			   if ((lastSubfamily != null) && (subfamily != null) && (!subfamily.equals(lastSubfamily))) {
				 // break on subfamily             
				 updateCountableTaxonImageCount(countable, lastSubfamily, subfamilyImageSum, "subfamily");
				 subfamilyImageSum = imageSum;
			   } else {
				 subfamilyImageSum += imageSum;
			   }
			   lastSubfamily = subfamily;
		   
			   if ((lastFamily != null) && (!family.equals(lastFamily))) {
				 // break on family
				 updateCountableTaxonImageCount(countable, lastFamily, familyImageSum, "family");
				 familyImageSum = imageSum;
			   } else {
				 familyImageSum += imageSum;
			   }
			   lastFamily = family;

			   updateCountableTaxonImageCount(countable, taxonName, imageSum, "species");
			}
		 
			// Once more for good measure...
			if (lastGenus != null) {
			   // break on genus             
			   String genusTaxonName = lastSubfamily + lastGenus;
			   updateCountableTaxonImageCount(countable, genusTaxonName, genusImageSum, "genus");
			   genusImageSum = imageSum;
			} else {
			   genusImageSum += imageSum;
			}
			lastGenus = genus;
		   
			if (lastSubfamily != null) {
			   // break on subfamily             
			   updateCountableTaxonImageCount(countable, lastSubfamily, subfamilyImageSum, "subfamily");
			   subfamilyImageSum = imageSum;
			} else {
			   subfamilyImageSum += imageSum;
			}
			lastSubfamily = subfamily;
		   
			if (lastFamily != null) {
			   // break on family
			   updateCountableTaxonImageCount(countable, lastFamily, familyImageSum, "family");
			   familyImageSum = imageSum;
			} else {
			   familyImageSum += imageSum;
			}
			lastFamily = family;
		  
         //if (AntwebProps.isDevMode()) s_log.info("countSpecimen() count:" + count);                               
        } catch (SQLException e) {
          s_log.warn("imageCountCrawl() query:" + query + " e:" + e);
          throw e;
        } finally {
          DBUtil.close(stmt, rset, this, "imageCountCrawl()");
        }
    }

     public void updateCountableTaxonImageCount(Countable countable, String taxonName, int sum, String rank)
       throws SQLException {
        Statement stmt = null;
        try {
          stmt = DBUtil.getStatement(getConnection(), "updateCountableTaxonImageCount()");
          String updateSql = countable.getUpdateImageCountSQL(taxonName, sum); //, rank);

          int taxonUpdateCount = stmt.executeUpdate(updateSql);

          //if (AntwebProps.isDevMode() && taxonName.equals(debugTaxonName))  s_log.warn("updateCountableTaxonImageCount() rank:" + rank + " return:" + taxonUpdateCount + " taxonName:" + taxonName + " count:" + sum + " updateSql:" + updateSql);
        
        } finally {
          DBUtil.close(stmt, null, this, "updateCountableTaxonImageCount()");
        }
     }  
    
    protected int getCountableTaxonCount(String table, String criteria, String rank) throws SQLException {
      int taxonCount = 0;
      String query = null;
      Statement stmt = null;
      ResultSet rset = null;
      
      debug = false && AntwebProps.isDevMode() && !s_isBulk;
      
      try {
        stmt = DBUtil.getStatement(getConnection(), "getCountableTaxonCount()");
        query = "select sum(" + rank + "_count) count from " + table + " where " + criteria + " and " + rank + "_count != 0";    

 	    if ("proj_taxon".equals(table) || "geolocale_taxon".equals(table) || "museum_taxon".equals(table) || "bioregion_taxon".equals(table)) {
	 	  if ("genus".equals(rank) || "species".equals(rank)) {
		    query += " and taxon_name != 'formicidae'";
		  }
		  if ("species".equals(rank)) {
		    query += " and taxon_name not in (select taxon_name from taxon where taxarank = 'subfamily') and taxon_name in (select taxon_name from taxon where family = 'formicidae')";
		  }
	    }

        rset = stmt.executeQuery(query);
        while (rset.next()) {
         taxonCount = rset.getInt("count");
        }

        if (debug) s_log.warn("getCountableTaxonCount() taxonCount:" + taxonCount + " query:" + query);       

      } catch (SQLException e) {
        s_log.error("getCountableTaxonCount() 2 e:" + e);
        throw e;
      } finally {
          DBUtil.close(stmt, rset, this, "getCountableTaxonCount()");
      }
      
      return taxonCount;
    }

/*
	at org.calacademy.antweb.home.CountDb.updateCountableTaxonCounts(CountDb.java:363)
	at org.calacademy.antweb.home.GeolocaleDb.updateCountableTaxonData(GeolocaleDb.java:1381)
	at org.calacademy.antweb.home.GeolocaleDb.finish(GeolocaleDb.java:1365)
	at org.calacademy.antweb.home.GeolocaleDb.populate(GeolocaleDb.java:1135)
	at org.calacademy.antweb.OverviewAction.execute(OverviewAction.java:241)
*/                
    protected void updateCountableTaxonCounts(String table, String criteria, int subfamilyCount, int genusCount, int speciesCount) throws SQLException {
        String dml = null;
        Statement stmt = null;
        try {
          stmt = DBUtil.getStatement(getConnection(), "updateCountableTaxonCounts()");
          dml = "update " + table + " set " 
            + "    subfamily_count = " + subfamilyCount
            + "  , genus_count = " + genusCount
            + "  , species_count = " + speciesCount
            + " where " + criteria;
          
          stmt.executeUpdate(dml);

          //if (true && criteria.contains("formicinaeplagiolepis europa_sp1"))
            if (debug) s_log.warn("updateCountableTaxonCounts() dml:" + dml);

        } catch (SQLException e) {
            s_log.error("updateCountableTaxonCounts() e:" + e);
            throw e;
        } finally {
          DBUtil.close(stmt, null, this, "updateCountableTaxonCounts()");
        } 
    }


// -------------  Charts -------------------

    protected String getTaxonSubfamilyDistJson(String query) throws SQLException {
      String distJson = "";

      Statement stmt = null;      
      ResultSet rset = null;
      try { 
          stmt = DBUtil.getStatement(getConnection(), "getTaxonSubfamilyDistJson()");
          
          //A.log("getTaxonSubfamilyDistJson() query:" + query);
          rset = stmt.executeQuery(query);

          int i = 0;
          while (rset.next()) {
             if (i > 0) distJson += ",";
             ++i;
             String subfamily = rset.getString("subfamily");
             int count = rset.getInt("count");
             String chartColor = rset.getString("chart_color");

             if (chartColor == null) {
               Subfamily subfamilyObj = TaxonMgr.getSubfamily(subfamily);
               if (subfamilyObj == null) {
                 s_log.warn("getTaxonSubfamilyDistJson() subfamilyObj is null for subfamily:" + subfamily + ". Subfamilies:" + TaxonMgr.getSubfamilies()); 
               } else {
                 chartColor = subfamilyObj.getChartColor();
                 //A.log("getTaxonSubfamilyDistJson() color:" + chartColor);
               }
             }            

             distJson += HttpUtil.getJsonElement(i, Formatter.initCap(subfamily), count, chartColor);
          }           
      } catch (SQLException e) {
          s_log.warn("getTaxonSubfamilyDistJson() e:" + e + " query:" + query);
          throw e;
      } finally {
          DBUtil.close(stmt, rset, "getTaxonSubfamilyDistJson()");        
      }

      return distJson;
    }

    protected String getSpecimenSubfamilyDistJson(String query) throws SQLException {
      String distJson = "";

      Statement stmt = null;      
      ResultSet rset = null;
      try { 
          stmt = DBUtil.getStatement(getConnection(), "getSpecimenSubfamilyDistJson()");
          rset = stmt.executeQuery(query);
                  
          int i = 0;
          while (rset.next()) {
             if (i > 0) distJson += ",";
             ++i;
             String subfamily = rset.getString("subfamily");
             int count = rset.getInt("count");
             
             Taxon theSubfamily = new TaxonDb(getConnection()).getTaxon(subfamily);
             String chartColor = null;
             if (theSubfamily != null) {
               chartColor = theSubfamily.getChartColor();
               //A.log("getSpecimenSubfamilyDistJSon() subfamily:" + subfamily + " chartColor:" + chartColor);
               distJson += HttpUtil.getJsonElement(i, Formatter.initCap(subfamily), count, chartColor);
             } else {
               A.log("getSpecimenSubfamilyDistJson() subfamily not found:" + subfamily);
             }
             
          }           
      } catch (SQLException e) {
          s_log.warn("getSpecimenSubfamilyDistJson() e:" + e + " query:" + query);
          throw e;
      } finally {
          DBUtil.close(stmt, rset, "getSpecimenSubfamilyDistJson()");        
      }

      return distJson;
    }   
        
              
}
     
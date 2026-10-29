package org.calacademy.antweb.home;

import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.Geolocale;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

/**
 * Base class of the count crawls of the four species-list tables (GeolocaleTaxonCountDb, ProjTaxonCountDb,
 * BioregionTaxonCountDb, MuseumTaxonCountDb). For one place (a Countable): counts the specimens of each name and
 * adds them up the taxon tree with the species, genus and subfamily counts (childrenCountCrawl), counts images
 * per name and up the tree (imageCountCrawl), and writes the place's totals and subfamily chart JSON. Run by
 * nightly sets 2 to 4. Docs: docs/numbers.md, docs/flows/nightly.md.
 * Tests: TaxonTreeCountDbTest, NightlyCountOrderDbTest, RegionCountDbTest.
 */
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

    private static final Log s_log = LogFactory.getLog(CountDb.class);
        
    // Best to use a lightly populated subfamily name.    
    //String debugName = "martialinae";    
    final String debugTaxonName = "dorylinaeacanthostichus davisi";
    final int debugGeolocaleId = 0;
    //int debugGeolocaleId = 2;
    boolean debug = true;

    public static boolean s_isBulk = false; // We want to turn of logging if invoked from the scheduler.

    public CountDb(Connection connection) {
      super(connection);
    }

    // Useful for debugging? Code to execute and get results or a particular count.
    private static String s_query;  // Just used for reporting.
    private static String s_report = "";
    private static boolean s_debug = true;
    
    protected String childrenCountReport(Countable countable) throws SQLException {
        if (countable == null) s_log.debug("childrenCountReport() why is countable null?");
        debug = AntwebProps.isDevMode();
        s_query = null;
        s_report = "";
        s_debug = true;
        int count = countChildren(countable, "species", "species_count");
        return "<br><br>count: " + count + " <br><br>query: " + s_query + " <br><br>report: " + s_report;
    }

    // Countables include Geolocale, Bioregion, Museum, Project.
    protected void childrenCountCrawl(Countable countable) throws SQLException {
        //A.log("childrenCountCrawl countable:" + countable);

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
            // || "Alaska".equals(countable.toString())
            // || "Colorado".equals(countable.toString())
            // || "worldants".equals(countable.toString())
               || "bayareaants".equals(countable.toString())
                )) A.log("childrenCountCrawl() message:" + message);
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
        s_log.debug("countSpecimens() countable is null");
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
                s_log.debug("countSpecimens() countable:" + countable + " taxonName:" + taxonName + " count:" + count + " query:" + query);
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
          s_log.debug("countChildren() countable is null");
          return 0;
        }  

        String query = countable.getCountChildrenQuery(rank);
        s_query = query; // For report.

        boolean debug = "Alabama".equals(countable.toString())
                || "bayareaants".equals(countable.toString());
        if (debug && "subfamily".equals(rank))
            A.log("countChildren() countable:" + countable + " rank:" + rank + "  query:" + query);


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

              if (false && "bayareaants".equals(countable.toString())) {
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

              updateCount(countable, parentTaxonName, column, count);
              //A.log("countChildren() parentTaxonName null for countable:" + countable);
              //if (AntwebProps.isDevMode() && (parentTaxonName != null && parentTaxonName.contains(debugTaxonName))) 
              // if (query.contains("geolocale_id = " + debugGeolocaleId + " ")) {
              if (debug && "subfamily".equals(rank))
                  A.log("countChildren() countable:" + countable + " rank:" + rank + " column:" + column + " parentTaxonName:" + parentTaxonName + " count:" + count + " query:" + query);

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
        
        if (false && "subfamily".equals(rank)) {
            A.log("countGrandChildren() countable:" + countable + " rank:" + rank + " column:" + column);
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
        
          if (AntwebProps.isDevMode() && "bayareaants".equals(countable.toString()) && "subfamily_count".equals(columnName)) { // && 95 == taxonUpdateCount) {
              A.log("updateCount() taxonUpdateCount:" + taxonUpdateCount + " dml:" + updateCountSQL);
              AntwebUtil.logShortStackTrace();
          }

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

    /** Sets image_count to 0 on every row of a taxon list table that has another value (before a full image count crawl). */
    protected void resetImageCounts(String table) throws SQLException {
        if (!java.util.Set.of("geolocale_taxon", "bioregion_taxon", "museum_taxon", "proj_taxon").contains(table)) {
            throw new IllegalArgumentException(table);
        }
        try (Statement stmt = getConnection().createStatement()) {
            stmt.executeUpdate("update " + table + " set image_count = 0 where image_count != 0");
        }
    }

    /**
     * The Images column of a list (nightly set 4). Each species or subspecies row (any row with specimens filed under
     * its name) gets the images of those specimens, from countable.getTaxonImageCountQuery(). The genus, subfamily and
     * family rows get the images of every row under them, by the taxon tree (taxon.parent_taxon_name), the same tree the
     * specimen counts of the lists roll up by (countGrandChildren) and the count check reads (list.images).
     *
     * Before 2026-10-22 the higher rows were added up from the specimen's own genus and subfamily columns (genus row =
     * specimen.subfamily + specimen.genus). A synonym or obsolete combination takes the parent of its current valid
     * name, so the images of specimens filed under it went to its old genus row and not to the genus that holds it:
     * staging worldants ectatomminaeholcoponera 596 while its rows summed to 604 (Gnamptogenys names now in
     * Holcoponera), myrmicinaemycetomoellerius 337 (345), myrmicinaeallomerus 394 (401, Wasmannia) (B40).
     */
    protected void imageCountCrawl(Countable countable) throws SQLException {
        if (countable == null) {
          s_log.warn("imageCountCrawl WST countable is null");
          AntwebUtil.logShortStackTrace();
          return;
        }

        String query = countable.getTaxonImageCountQuery();
        java.util.Map<String, Integer> sums = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        try (Statement stmt = DBUtil.getStatement(getConnection(), "imageCountCrawl()");
             ResultSet rset = stmt.executeQuery(query)) {
            while (rset.next()) {
                String taxonName = rset.getString("taxonName");
                if (taxonName == null) continue;
                int imageSum = rset.getInt("imageSum");
                sums.merge(taxonName, imageSum, Integer::sum);
                for (String ancestor : imageAncestors(taxonName, rset.getString("family"), rset.getString("subfamily"), rset.getString("genus"))) {
                    sums.merge(ancestor, imageSum, Integer::sum);
                }
            }
        } catch (SQLException e) {
            s_log.warn("imageCountCrawl() query:" + query + " e:" + e);
            throw e;
        }
        for (java.util.Map.Entry<String, Integer> e : sums.entrySet()) {
            updateCountableTaxonImageCount(countable, e.getKey(), e.getValue(), null);
        }
    }

    /** taxon_name -> {taxarank, parent_taxon_name}, read once per CountDb (one crawl over every place). */
    private java.util.Map<String, String[]> taxonTree;

    private java.util.Map<String, String[]> taxonTree() throws SQLException {
        if (taxonTree == null) {
            java.util.Map<String, String[]> tree = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            try (Statement stmt = getConnection().createStatement();
                 ResultSet r = stmt.executeQuery("select taxon_name, taxarank, parent_taxon_name from taxon")) {
                while (r.next()) tree.put(r.getString(1), new String[] {r.getString(2), r.getString(3)});
            }
            taxonTree = tree;
        }
        return taxonTree;
    }

    /** Forgets the taxon tree read by the image crawl (the next crawl reads it again). */
    public void forgetTaxonTree() {
        taxonTree = null;
    }

    /**
     * The rows above a name that hold its images: its ancestors in the taxon tree of rank genus and above (a species
     * above a subspecies is skipped: a species row counts only the specimens filed under its own name, B25). A name
     * that is not in the taxon table falls back to the specimen's columns, as before: subfamily + genus, subfamily,
     * family.
     */
    java.util.List<String> imageAncestors(String taxonName, String family, String subfamily, String genus) throws SQLException {
        java.util.List<String> ancestors = new java.util.ArrayList<>();
        java.util.Map<String, String[]> tree = taxonTree();
        String[] node = tree.get(taxonName);
        if (node == null) {
            if (subfamily != null && genus != null && !genus.isEmpty()) ancestors.add(subfamily + genus);
            if (subfamily != null && !subfamily.isEmpty()) ancestors.add(subfamily);
            if (family != null && !family.isEmpty()) ancestors.add(family);
            ancestors.remove(taxonName);
            return ancestors;
        }
        java.util.Set<String> seen = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        seen.add(taxonName);
        String parent = node[1];
        // at most kingdom ... subspecies; a loop in the tree stops at the name seen before
        for (int depth = 0; depth < 12 && parent != null && !parent.isEmpty() && seen.add(parent); depth++) {
            String[] p = tree.get(parent);
            if (p == null) break;
            if (!Rank.SPECIES.equals(p[0]) && !Rank.SUBSPECIES.equals(p[0])) ancestors.add(parent);
            if (Rank.FAMILY.equals(p[0])) break;
            parent = p[1];
        }
        return ancestors;
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
      
      debug = false && "subfamily".equals(rank) && AntwebProps.isDevMode() && !s_isBulk;
      
      try {
        stmt = DBUtil.getStatement(getConnection(), "getCountableTaxonCount()");
        query = "select sum(" + rank + "_count) count from " + table + " where " + criteria + " and " + rank + "_count != 0";

        // XXX
        // This would break geolocale subfamily count and fix project count, only because project subfamilies have a subfamiliy count of 1.
        // Fix the project subfamily subfamily_counts?
        // if ("subfamily".equals(rank)) query += " and taxon_name != 'formicidae'";

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

        if (debug) s_log.warn("getCountableTaxonCount() rank:" + rank + " taxonCount:" + taxonCount + " query:" + query);

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
                 s_log.info("getTaxonSubfamilyDistJson() subfamilyObj is null for subfamily:" + subfamily + ". Subfamilies:" + TaxonMgr.getSubfamilies());
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
             
             Subfamily theSubfamily = TaxonMgr.getSubfamily(subfamily);
             String chartColor = null;
             if (theSubfamily != null) {
               chartColor = theSubfamily.getChartColor();
               //A.log("getSpecimenSubfamilyDistJSon() subfamily:" + subfamily + " chartColor:" + chartColor);
               distJson += HttpUtil.getJsonElement(i, Formatter.initCap(subfamily), count, chartColor);
             } else {
               s_log.info("getSpecimenSubfamilyDistJson() subfamily not found:" + subfamily);
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
     
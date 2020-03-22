package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import java.util.Date;
import java.text.DecimalFormat;

import javax.servlet.http.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.curate.speciesList.*;

public class GeolocaleTaxonDb extends EditableTaxonSetDb {
    
    private static Log s_log = LogFactory.getLog(GeolocaleTaxonDb.class);
        
    public GeolocaleTaxonDb(Connection connection) {
      super(connection);
    }
    
/*
  Called from SpeciesListMapping on the save function.
  When removing a taxon from geolocale_taxon, if it is a morpho, if it is removed from the only
  geolocale it is in, then delete the taxon too (if it has no specimens).
  Allow the description_edit to be orphaned. This was ProjTaxonDb documentation.  Uncertain.

Description of Source.

NO! This is not true now:
"specimen" is king. They get blown away and recreated from specimen data. We like that.
* - No longer is specimen king. We do not rename curator to specimen as we tried. Curator is king.

We never blow away "speciesList" or "curator" records. But specimen can overwrite, and then be blown away.
  These will be displayed on the site as "Curator".

"antwiki" can be blown away, but will not overwrite the source of specimen.
  These will be displayed on the site as "Literature".
*/
// --------------------------------------------------------------------------------------
    // Satisfies the abstract method of EditableTaxonSetDb.
    public TaxonSet get(String speciesListName, String taxonName) {
        int geolocaleId = GeolocaleMgr.getGeolocaleId(speciesListName);
        return get(geolocaleId, taxonName);
    }
/*
*/    
    public TaxonSet get(int geolocaleId, String taxonName) {
        String query = "";
        GeolocaleTaxon geolocaleTaxon = null;
        Statement stmt = null;
        ResultSet rset = null;
        try {

            stmt = DBUtil.getStatement(getConnection(), "get()");
            query = "select geolocale_id, taxon_name, source, rev, is_introduced " 
               + " from geolocale_taxon " 
               + " where geolocale_id = " + geolocaleId
               + " and taxon_name = '" + taxonName + "'";

            rset = stmt.executeQuery(query);
            while (rset.next()) {
                geolocaleTaxon = new GeolocaleTaxon();
                geolocaleTaxon.setGeolocaleId(rset.getInt("geolocale_id"));
                geolocaleTaxon.setTaxonName((String) rset.getString("taxon_name"));
                geolocaleTaxon.setSource((String) rset.getString("source"));
                geolocaleTaxon.setRev(rset.getInt("rev"));
                geolocaleTaxon.setIsIntroduced((rset.getInt("is_introduced") == 1) ? true : false);
           }

          //A.log("getTaxonSet() query:" + query);

        } catch (SQLException e) {
            s_log.error("get() e:" + e);
        } finally {
            DBUtil.close(stmt, "get()");
        }
        return geolocaleTaxon;
    }
    

    // --------------------- Recursive Insertion ---------------------------
    /**    Recursive method. Follow a pattern like this. But, for performance, do not insert a pair as per s_queryGovernor.

      insert species adm1
        insert genus adm1
          insert subfamily adm1
            insert family adm1
      insert species country
        insert genus country
          insert subfamily country
            insert family country
      insert species subregion
        insert genus subregion
          insert subfamily subregion
            insert familly subregion
      insert species region
        insert genus region
          insert subfamily region
            insert familly region
    */    
    // Doubly recursive. Will insert records for all taxon parents and all geolocale parents.

    public void resetQueryGovernors() {
      s_queryInsertGovernor = new HashSet<String>();
      s_queryUpdateGovernor = new HashSet<String>();      
    }
    public void clearQueryGovernors() {
      s_queryInsertGovernor = null;
      s_queryUpdateGovernor = null;
    }

    // All insertion should happen here.
    // Satisfies the abstract method of EditableTaxonSetDb.
    // Called from SpeciesListTool and by setTaxonSet() and insertGeolocaleTaxaFromSpecimens() and populateFromAntwikiData(), checkGeolocaleParentage() below.
    public int insert(Overview overview, String taxonName, String source) throws SQLException {
        if (taxonName.contains("dorylinaecerapachys")) { // && "Madagascar".equals(overview)) {
            if (AntwebProps.isLocal()) s_log.warn("insert('" + overview.getName() + "', '" + taxonName + "', '" + source + "')");
            if ("Madagascar".equals(overview.getName())) {
                AntwebUtil.logStackTrace();
            }
        }
        //s_log.warn("insert() taxonName:" + taxonName);
/*
      if ("formicinaeformica podzolica".equals(taxonName) && "California".equals(overview.getName())) {
        A.log("insert() overview:" + overview + " taxonName:" + taxonName + " source:" + source);
        AntwebUtil.logStackTrace();
      }
*/
      Geolocale geolocale = (Geolocale) overview;

      // it is a genus or subfamily. Record it in the list so that we can efficiently avoid re-queries.
      String key = geolocale.getId() + taxonName;
      
      // If a method is using the queryGovernor to save time on insertions
      //   it must call resetQueryGovernor beforehand and clearQueryGovernor afterwards.
      if (s_queryInsertGovernor != null) {
        if (s_queryInsertGovernor.contains(key)) {
          return 0;
        }
        s_queryInsertGovernor.add(key);
      }

      int insertCount = 0;    

      TaxonSet dispute = new GeolocaleTaxonLogDb(getConnection()).getDispute(geolocale.getId(), taxonName);  
      if (dispute != null) {
        //A.log("insert() DISPUTE geolocaleId:" + geolocale.getId() + " taxonName:" + taxonName);
        return 0;
      }

      try {
		  insertCount = insertItem(geolocale, taxonName, source);

	      if (!source.contains(TaxonSet.PROXY)) source = TaxonSet.PROXY + source;
	
		  if (!"adm1".equals(geolocale.getGeorank()) && !"region".equals(geolocale.getGeorank())) {
			Geolocale parentGeolocale = GeolocaleMgr.getGeolocale(geolocale.getParent());
			if (parentGeolocale == null) {
			  A.log("insert() parent is null for parent:" + geolocale.getName() + "!");
			  return insertCount;
			}
			insertCount += insert(parentGeolocale, taxonName, source); // parent geolocale recursive call.
		  }
	  
		  if ("formicidae".equals(taxonName)) {
			//A.log("insert() formicidae. name:" + geolocale.getName() + " id:" + geolocale.getId() + " insertCount:" + insertCount);
			return insertCount;
		  }
	  
		  String parentTaxonName = Taxon.getParentTaxonNameFromName(taxonName);
		  if (parentTaxonName == null || parentTaxonName.equals(taxonName)) {
			//String parentTaxonName = TaxonMgr.getTaxon(taxonName).getParentTaxonName();      
			//A.log("insert() parentTaxonName:" + parentTaxonName + " for taxonName:" + taxonName);
			return insertCount;
		  }

		  insertCount += insert(geolocale, parentTaxonName, source); // parent taxon recursive call

      } catch (Exception e) {      
        A.log("insert() e:" + e);
        throw e;
      }
      
      return insertCount;
    }
    
     // All updates should happen here? Recursive.
    public int update(Overview overview, String taxonName, String source) throws SQLException {
        if (taxonName.contains("dorylinaecerapachys")) {
            s_log.warn("update() name:" + overview.getName() + "' taxonName:" + taxonName + " source:" + source );
            AntwebUtil.logStackTrace();
        }

      Geolocale geolocale = (Geolocale) overview;

      // it is a genus or subfamily. Record it in the list so that we can efficiently avoid re-queries.
      String key = geolocale.getId() + taxonName;
      
      // If a method is using the queryGovernor to save time on insertions
      //   it must call resetQueryGovernor beforehand and clearQueryGovernor afterwards.
      if (s_queryUpdateGovernor != null) {
        if (s_queryUpdateGovernor.contains(key)) {
          return 0;
        }
        s_queryUpdateGovernor.add(key);
      }

      int updateCount = 0;    

      try {
		  updateCount = updateItem(geolocale.getId(), taxonName, source);

	      if (!source.contains(TaxonSet.PROXY)) source = TaxonSet.PROXY + source;
	
		  if (!"adm1".equals(geolocale.getGeorank()) && !"region".equals(geolocale.getGeorank())) {
			Geolocale parentGeolocale = GeolocaleMgr.getGeolocale(geolocale.getParent());
			if (parentGeolocale == null) {
			  A.log("update() parent is null for parent:" + geolocale.getName() + "!");
			  return updateCount;
			}
			updateCount += update(parentGeolocale, taxonName, source); // parent geolocale recursive call.
		  }
	  
		  if ("formicidae".equals(taxonName)) {
			//A.log("insert() formicidae. name:" + geolocale.getName() + " id:" + geolocale.getId() + " insertCount:" + insertCount);
			return updateCount;
		  }
	  
		  String parentTaxonName = Taxon.getParentTaxonNameFromName(taxonName);
		  if (parentTaxonName == null || parentTaxonName.equals(taxonName)) {
			//String parentTaxonName = TaxonMgr.getTaxon(taxonName).getParentTaxonName();      
			//A.log("insert() parentTaxonName:" + parentTaxonName + " for taxonName:" + taxonName);
			return updateCount;
		  }

		  updateCount += update(geolocale, parentTaxonName, source); // parent taxon recursive call

      } catch (Exception e) {      
        A.log("update() e:" + e);
        throw e;
      }
      
      return updateCount;
    }

    // Preferred method
    public int delete(Overview overview, String taxonName) throws SQLException {
      return delete(overview.getName(), taxonName);
    }

    // Satisfies the abstract method of EditableTaxonSetDb.
    public int delete(String speciesListName, String taxonName) throws SQLException {
        
      int geolocaleId = GeolocaleMgr.getGeolocaleId(speciesListName);
      int c = 0;  
      String delete = null;
      ResultSet rset1 = null;
      Statement stmt1 = null;
      try {
        stmt1 = DBUtil.getStatement(getConnection(), "delete()");

        delete = "delete from geolocale_taxon " 
          + " where geolocale_id = " + geolocaleId 
          + "   and taxon_name = '" + taxonName + "'";
        //A.log("removeFromTaxonSet() 1 delete:" + delete);
        c = stmt1.executeUpdate(delete);

        // if taxon is in no other geolocale
        // and has no specimens!? then delete the taxon outright, and the allantweb
        // projTaxon record.
        String query = "select count(geolocale_id) from geolocale_taxon " 
          + " where taxon_name = '" + taxonName + "'";

        //A.log("delete() 2 query:" + query);

        rset1 = stmt1.executeQuery(query);
        while (rset1.next()) {
          int count = rset1.getInt(1);
          if (count == 0) {  // Apparently not in worldants or any other project

            Statement stmt2 = getConnection().createStatement();
            query = "select count(code) from specimen "
              + " where taxon_name = '" + taxonName + "'";

            //A.log("delete() 3 query:" + query);

            ResultSet rset2 = stmt2.executeQuery(query);
            while (rset2.next()) {
              if (rset2.getInt(1) == 0) {

                // Morpho check?  We already know that it is not in Bolton and does not have specimens.
                // So we will delete the taxon outright.
                TaxonDb taxonDb = (new TaxonDb(getConnection()));
                taxonDb.deleteTaxon(taxonName);
              }
            }
            stmt2.close();
          }
        }        
      } catch (SQLException e) {
        s_log.error("delete() geolocaleId:" + geolocaleId + " e:" + e + " delete:" + delete);
      } finally {
        DBUtil.close(stmt1, rset1, "this", "delete()");
      }  
      return c;      
    }    

    // Satisfies the abstract method of EditableTaxonSetDb.
    public boolean hasTaxonSetSpecies(String speciesListName, String genus) {
        int geolocaleId = GeolocaleMgr.getGeolocaleId(speciesListName);
        String fromWhereClause = "from geolocale_taxon where geolocale_id = " + geolocaleId;
        return super.hasTaxonSetSpecies(speciesListName, genus, fromWhereClause);
    }
    // Satisfies the abstract method of EditableTaxonSetDb.
    public boolean hasTaxonSetGenera(String speciesListName, String subfamily) {
        int geolocaleId = GeolocaleMgr.getGeolocaleId(speciesListName);
        String fromWhereClause = "from geolocale_taxon where geolocale_id = " + geolocaleId;
        return super.hasTaxonSetGenera(speciesListName, subfamily, fromWhereClause);
    }


	 
// ------------------------- support methods -----------------------------
    private boolean hasItem(int id, String taxonName) throws SQLException {
        String query = "";
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "hasItem()");
            query = "select 'x' as x from geolocale_taxon"
               + " where geolocale_id = " + id
               + " and taxon_name = '" + taxonName + "'";
            ResultSet rset = stmt.executeQuery(query);
            while (rset.next()) {
                String x = (String) rset.getObject("x");
                return true;
            }
            stmt.close();
        } catch (SQLException e) {
            s_log.error("hasItem() id:" + id + " taxonName:" + taxonName);
        } finally {
            DBUtil.close(stmt, "hasItem()");
        }
        //A.log("hasItem() exists:" + exists + " query:" + query);
        return false;    
    }
    
    // Only to be used by the recursive insert above, which takes care of parents.
    private int insertItem(Geolocale geolocale, String taxonName, String source) 
      throws SQLException {

        if (taxonName.contains("podzolica")) {
          A.log("insertItem(" + geolocale.getId() + ", " + taxonName + ", " + source + ")");
          //AntwebUtil.logStackTrace();
        }

        // We can't insert. Already exists. Update source if priority allows.
		TaxonSet taxonSet = get(geolocale.getId(), taxonName);
		if (taxonSet != null) {
            if (Source.aTrumpsB(source, taxonSet.getSource())) {
              update(geolocale, taxonName, source);  
            }
            return 0;
        }          

/*	        
    // Brian:  record was showing up in Madagascar because myrmicinaepyramica hoplites is found in Madagascar according to Antcat and it's current valid name is myrmicinaestrumigenys hoplites. Let me know how you would like that handled.
	at org.calacademy.antweb.home.GeolocaleTaxonDb.insert(GeolocaleTaxonDb.java:146)
	at org.calacademy.antweb.home.GeolocaleTaxonDb.setTaxonSet(GeolocaleTaxonDb.java:389)
	at org.calacademy.antweb.upload.SpeciesListUpload.importSpeciesList(SpeciesListUpload.java:693)
        if (false && "myrmicinaestrumigenys hoplites".equals(taxonName) && geolocale.getId() == 7) {
          A.log("insertItem(" + geolocale.getId() + ", " + taxonName + ", " + source + ") taxonSet:" + taxonSet); // Source trumps existing:" + taxonSet.getSource());
          AntwebUtil.logStackTrace();              
        }
*/          
        String dml = null;
        Statement stmt = null;
        int count = 0;
        try {
            stmt = DBUtil.getStatement(getConnection(), "insertItem()");
            dml = "insert into geolocale_taxon (geolocale_id, taxon_name, insert_method, source)"
              + " values (" + geolocale.getId() + ", '" + taxonName + "', 'insertItem', '" + source + "')";

            count = stmt.executeUpdate(dml);
            //A.log("insertItem() id:" + id + " taxonName:" + taxonName + " source:" + source + " count:" + count);
	    } catch (SQLException e) {
          A.log("insertItem() e:" + e + " source:" + source);
          throw e;
        } finally {
            DBUtil.close(stmt, "insertItem()");
        }
        return count;   		
	}

    // insert or update as necessary.
	public int setTaxonSet(Geolocale geolocale, String taxonName, String source) throws SQLException {
      int c = 0;
      int geolocaleId = geolocale.getId();
      TaxonSet taxonSet = get(geolocaleId, taxonName);
/*
      if ("dorylinaecerapachys mayri brachynodus".equals(taxonName) && "Madagascar".equals(geolocale.getName())) {
          A.log("setTaxonSet geolocale:" + geolocale + " taxonName:" + taxonName + " taxonSet:" + taxonSet);
      }
*/
      if (taxonSet == null) {
        insert(geolocale, taxonName, source);
      } else {
        if (!source.equals(taxonSet.getSource())) {
          if (Source.aTrumpsB(source, taxonSet.getSource())) {
            A.log("setTaxonSet() calling updateItem (SHOULD BE RECURSIVE?) with geolocaleId:" + geolocaleId + " taxonName:" + taxonName + " source:" + source);
            c = updateItem(geolocaleId, taxonName, source); // *** SHOULD BE RECURSIVE.
          }
        }
      }
      return c;
	}
	
	// *** SHOULD ONLY BE USED BY RECURSIVE ABOVE. NEED TO UPDATE PARENT SOURCE TO BE PROXY...
    private int updateItem(int id, String taxonName, String source) 
        throws SQLException {
        String dml = null;
        Statement stmt = null;
        int count = 0;
        try {
            stmt = DBUtil.getStatement(getConnection(), "updateItem()");

            dml = "update geolocale_taxon set source = '" + source + "' where geolocale_id = " + id
              + " and taxon_name = '" + taxonName + "'";

//if (!AntwebProps.isDevMode()) // For testing
            count = stmt.executeUpdate(dml);
//else A.log("updateItem() id:" + id + " taxonName:" + taxonName + " source:" + source + " count:" + count);

	    } catch (SQLException e) {
          A.log("updateItem e:" + e + " source:" + source);
          throw e;
        } finally {
            DBUtil.close(stmt, "updateItem()");
        }
        return count;      
	}	

	// *** SHOULD ONLY BE USED BY RECURSIVE ABOVE. NEED TO UPDATE PARENT SOURCE TO BE PROXY...
    protected int deleteItem(int id, String taxonName) throws SQLException {
        String dml = null;
        Statement stmt = null;
        int count = 0;
        try {
            stmt = DBUtil.getStatement(getConnection(), "deleteItem()");

            dml = "delete from geolocale_taxon where geolocale_id = " + id
              + " and taxon_name = '" + taxonName + "'";
            count = stmt.executeUpdate(dml);
        } finally {
            DBUtil.close(stmt, "deleteItem()");
        }
        return count;
    }    
    	 

    // Satisfies TaxonSetDb abstract method.
    public ArrayList<Taxon> getTaxa(String name) {
        Geolocale geolocale = GeolocaleMgr.getGeolocale(name);  
        
        return super.getTaxa(geolocale);
    }

// -----------------------------------------------------------
    

int s_adm1Specimen = 0;
int s_speciesListTool = 0;
int s_specimen = 0;
int s_other = 0;
int s_found = 0;
int s_notFound = 0;

    private void reportOnTaxonSetItem(String speciesListName, String taxonName) {

        int geolocaleId = GeolocaleMgr.getGeolocaleId(speciesListName);
            
        String query = "";
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "reportOnTaxonSet()");
            query = "select source from geolocale_taxon where geolocale_id = " + geolocaleId + " and taxon_name = '" + taxonName + "'";

            rset = stmt.executeQuery(query);
            boolean found = false;
            while (rset.next()) {
              found = true;
              String source = (String) rset.getObject("source");
			  if ("specimen".equals(source)) {
			    ++s_specimen;
			  } else if ("specimenListTool".equals(source)) { 
			    ++s_speciesListTool;
			  } if ("adm1Specimen".equals(source)) {
			    ++s_adm1Specimen;
			  } else {
			    ++s_other;
			  }
              A.log("reportOnTaxonSet() taxonName:" + taxonName + " source:" + source);
           }
           //A.log("reportOnTaxonSet() query:" + query);

           if (found) ++s_found; else ++s_notFound;

        } catch (SQLException e) {
            s_log.error("reportOnTaxonSet() e:" + e);
        } finally {
            DBUtil.close(stmt, "reportOnTaxonSet()");
        }
    }
    
    public String getTaxaOutsideOfNativeBioregion() {
        StringBuffer buffer = new StringBuffer();
        
        ArrayList<String> taxaOutside = new ArrayList<String>();
        HashSet<String> bioregionMapsNotFound = new HashSet<String>();
        HashSet<String> genusNotFound = new HashSet<String>();
        
        // Loop through all geolocale...
        //String query = "select taxon_name, group_concat(distinct g.bioregion) bioregions from geolocale_taxon gt, geolocale g where gt.geolocale_id = g.id group by taxon_name";
        //String query = "select taxon_name, gt.geolocale_id id, g.name, g.bioregion bioregion, gt.is_introduced from geolocale_taxon gt, geolocale g where gt.geolocale_id = g.id order by g.georank desc, g.name";

String query = "select taxon_name, gt.geolocale_id id, g.name, g.bioregion bioregion from geolocale_taxon gt, geolocale g where gt.geolocale_id = g.id "
+ " and taxon_name not in (select taxon_name from proj_taxon where project_name = 'introducedants') " 
+ " and taxon_name not in (select taxon_name from proj_taxon where project_name = 'fossilants') " 
+ " order by g.georank desc, g.name";

        Statement stmt = null;
        ResultSet rset = null;
        
        int total = 0;
        try {
            String lastGeorank = "";
            stmt = DBUtil.getStatement(getConnection(), "getTaxaOutsideOfNativeBioregion()");

            rset = stmt.executeQuery(query);
            while (rset.next()) {
              
              String taxonName = (String) rset.getObject("taxon_name");
              
              if (!Taxon.isSpeciesOrSubspecies(taxonName)) continue;
              
              String id = "" + rset.getObject("id");
              String  georank = GeolocaleMgr.getGeolocale(id).getGeorank();
              if (!lastGeorank.equals(georank)) {
                buffer.append("<br><br><h2>" + Formatter.initCap(georank) + "</h2>");
                lastGeorank = georank;
              }

              String name = (String) rset.getObject("name");              

/*
              Boolean isIntroduced = (Boolean) rset.getBoolean("is_introduced");
              if (isIntroduced) {
                //A.log("getTaxaOutsideOfNativeBioregion() introduced:" + taxonName);
                continue;
              }
*/

              String genus = Taxon.getGenusTaxonNameFromName(taxonName);
              if (genus == null) {
                genusNotFound.add(taxonName);
                //A.log("getTaxaOutsideOfNativeBioregion() genus not found for taxonName:" + taxonName);
                continue;
              }

              String bioregionMap = new TaxonPropDb(getConnection()).getBioregionMap(genus);
              if (bioregionMap == null) {
                bioregionMapsNotFound.add(genus);
                //A.log("getTaxaOutsideOfNativeBioregion() no bioregionMap found for genus:" + genus);
                continue;
              }

             // String bioregions = (String) rset.getObject("bioregions");
              String bioregion = (String) rset.getObject("bioregion");
			  boolean isNative = TaxonPropMgr.isBioregionNative(bioregion, bioregionMap);
			  if (!isNative) {
                  ++total;
			  
				  //taxaOutside.add(taxonName);
				  String idLink = "<a href='" + AntwebProps.getDomainApp() + "/geolocale.do?id=" + id + "'>" + id + "</a>";
                  String taxonLink = "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + taxonName + "</a>"; 
				  String message = "geolocale:" + name + "(" + idLink + ") taxonName:" + taxonLink;
				  buffer.append("<br>" + message);
				  A.log("getTaxaOutsideOfNativeBioregion() " + message);
				  //bioregion:" + bioregion + " is not native for genus:" + genus + " bioregionMap:" + bioregionMap);		    
			  }
           }
           String totalStr = "total:" + total;
           buffer.append("<br><br>" + totalStr);
           A.log("getTaxaOutsideOfNativeBioregion() " + totalStr);

           String bioregionsNotFound = "bioregionMapsNotFound:" + bioregionMapsNotFound;
           buffer.append("<br><br>" + bioregionsNotFound);           
           A.log("getTaxaOutsideOfNativeBioregion() " + bioregionsNotFound);

           String genusNotFoundStr = "genusNotFound:" + genusNotFound;
           buffer.append("<br><br>" + genusNotFoundStr);
           A.log("getTaxaOutsideOfNativeBioregion() " + genusNotFoundStr);

        } catch (SQLException e) {
            s_log.error("getTaxaOutsideOfNativeBioregion() e:" + e);
        } finally {
            DBUtil.close(stmt, "getTaxaOutsideOfNativeBioregion()");
        }

        return buffer.toString();
    }

// --------------------------------------------------------------------------------------

	public String generateGeolocaleTaxaFromSpecimens() {

	  Date startTime = new Date();
      int deleteTotal = 0;
      String deleteMorphoStr = null;
      int insertTotal = 0;
      try {
          getConnection().setAutoCommit(false);

          deleteTotal = deleteGeolocaleTaxaFromSpecimens();
          deleteMorphoStr += deleteUncuratedMorphosWithoutSpecimen();
          insertTotal = insertGeolocaleTaxaFromSpecimens();
          
          getConnection().commit();
    
      } catch (AntwebException e) {
          s_log.error("generateGeolocaleTaxaFromSpecimens() e:" + e);       

		  // If there is an error then rollback the changes.
		  A.log("Rolling back data....");
		  try{
			 if(getConnection() != null) getConnection().rollback();
		  }catch(SQLException se2){
			 AntwebUtil.logStackTrace(se2);
		  }
      } catch (SQLException e) {
          s_log.error("generateGeolocaleTaxaFromSpecimens() e:" + e);       

		  // If there is an error then rollback the changes.
		  A.log("Rolling back data....");
		  try{
			 if(getConnection() != null) getConnection().rollback();
		  }catch(SQLException se2){
			 AntwebUtil.logStackTrace(se2);
		  }
      }

      (new UtilDb(getConnection())).deleteFrom("geolocale_taxon", "where (geolocale_id, taxon_name) in (select geolocale_id, taxon_name from geolocale_taxon_dispute)");

      return "deleteTotal:" + deleteTotal + " insertTotal:" + insertTotal + " " + deleteMorphoStr;
	}
		
    private int deleteSource(String source) throws SQLException {
        String dml = null;
        Statement stmt = null;
        int count = 0;
        try {
            stmt = DBUtil.getStatement(getConnection(), "deleteSource()");

            dml = "delete from geolocale_taxon where source like '%" + source + "%'";
             // + " and taxon_name in (select taxon_name from taxon where status = 'valid')"; // Why this criteria?
            count = stmt.executeUpdate(dml);

        } finally {
            DBUtil.close(stmt, "deleteSource()");
        }
        return count;
    }
    		
	private int deleteGeolocaleTaxaFromSpecimens() throws SQLException {
	
      int deleteTotal = deleteSource("specimen");
      A.log("specimen deleteTotal:" + deleteTotal);	

      return deleteTotal;
	}

    /*
    DML below uses an alias to allow deletion from a table that is also in the subquery.
    */    
    String deleteUncuratedMorphosWithoutSpecimen() {
        String dml 
          = " delete from geolocale_taxon where taxon_name in ("
          + "   select taxon_name from ("
          + "     select t.taxon_name from geolocale_taxon gt, taxon t "
          + "     where gt.taxon_name = t.taxon_name "
          + "     and t.rank in ('species', 'subspecies') "
          + "     and t.status = 'morphotaxon' "
          + "     and gt.source = '" + Source.SPECIMEN + "'" // Corrected
          + "     and t.taxon_name not in ("
          + "       select taxon_name from specimen"
          + "     )"
          + "   ) alias"
          + " )";
        //s_log.warn("deleteUncuratedMorphosWithoutSpecimen() query:" + dml);
        Statement stmt = null;
        int c = 0;
        try {
            stmt = DBUtil.getStatement(getConnection(), "deleteUncuratedMorphosWithoutSpecimen()");
            c = stmt.executeUpdate(dml);
        } catch (SQLException e) {
            s_log.error("deleteUncuratedMorphosWithoutSpecimen() e:" + e + " dml:" + dml);
        } finally {
            DBUtil.close(stmt, "deleteUncuratedMorphosWithoutSpecimen()");
        }
        String message = "GeolocaleTaxonDb.deleteUncuratedMorphosWithoutSpecimen:" + c + " ";
        return message;
    }

    public String deleteGeolocaleTaxaWithoutTaxon() {
      String dmlWhereClause = "where taxon_name not in (select taxon_name from taxon)";
      int retVal = (new UtilDb(getConnection())).deleteFrom("geolocale_taxon", dmlWhereClause);
      return retVal + " deleteGeolocaleTaxaWithoutTaxon. ";
    }

    private HashSet<String> s_queryInsertGovernor = null;
    private HashSet<String> s_queryUpdateGovernor = null;
    public static int s_updateCount = 0;
    private static int s_insertCount = 0;
    private static int s_constraintCount = 0;

/*
    private int deleteMorphosWithoutSpecimen() throws SQLException {
		int count = 0;
  	    Date startTime = new Date();
        String message = "";

        // This query will fetch geolocale_taxon records if the taxon is a morpho and not in the specimen table 
        //   or, if the parent_taxon_name, geolocale_id pair are not in geolocale_taxon - so it can be recreated. 
		String query = "select gt.taxon_name, gt.geolocale_id, '' as name from geolocale_taxon gt, taxon t " 
		  + " where gt.taxon_name = t.taxon_name " 
		  + " and t.status = 'morphotaxon' " 
		  + " and gt.source = '" + Source.SPECIMEN + "'"
		  + " and (t.taxon_name not in (select taxon_name from specimen) " 
		  + " or (t.parent_taxon_name, gt.geolocale_id) not in (select taxon_name, geolocale_id from geolocale_taxon))";
        count += deleteGeolocaleTaxa(query);
        message += "adm1Count:" + count;
  
        double timePassed = AntwebUtil.doubleMinsSince(startTime);
        DecimalFormat formatter = new DecimalFormat("#0.00");
        message += " mins:" + formatter.format(timePassed);

        //s_log.warn("deleteMorphosWithoutSpecimen() " + message);

        return count;
    }
    
    private int deleteGeolocaleTaxa(String query) throws SQLException {
        int count = 0;

        Statement stmt = null;
        ResultSet rset = null;
        Connection connection = getConnection();
        try {
            stmt = DBUtil.getStatement(connection, "deleteGeolocaleTaxa()");
            rset = stmt.executeQuery(query);
            while (rset.next()) {
              int geolocaleId = rset.getInt("geolocale_id");
              String taxonName = rset.getString("taxon_name");   
              String name = rset.getString("name");   // geolocale name.
              //A.log("deleteGeolocaleTaxa(query) geolocaleId:" + geolocaleId + " taxonName:" + taxonName + " name:" + name);
              count += deleteItem(geolocaleId, taxonName);
            }            
        } catch (SQLException e) {
            s_log.error("deleteGeolocaleTaxa() e:" + e);
        } finally {
            DBUtil.close(stmt, "deleteGeolocaleTaxa()");
        }
        return count;
    }
       */

    private int insertGeolocaleTaxaFromSpecimens() throws SQLException, AntwebException {
        
        s_updateCount = 0;
        s_insertCount = 0;
        s_constraintCount = 0;
        
        int specimenCount = 0;
        int insertCount = 0;
        String query = "";
        Statement stmt = null;
        ResultSet rset = null;

		HashSet<String> unfoundCountries = new HashSet<String>();
		HashSet<String> unfoundAdm1s = new HashSet<String>();

        try {
            stmt = DBUtil.getStatement(getConnection(), "insertGeolocaleTaxaFromSpecimens()");
			
			String clause = "";
			//if (AntwebProps.isDevMode()) clause = " and country = 'Chile'";

            query = "select distinct country, adm1, taxon_name " 
              + " from specimen " 
              + " where (status = 'valid')"
              + " and " + SpecimenDb.getFlagCriteria()
              + " and country is not null" 
              + clause
              + " order by country, adm1, taxon_name"
            ;
            A.log("insertGeolocaleTaxaFromSpecimens() query:" + query);
            rset = stmt.executeQuery(query);

            Geolocale country = null;
            Geolocale adm1 = null;
            String lastCountryName = null;
            String lastTaxonName = null;
            int count = 0;
            
            while (rset.next()) {
                ++specimenCount;
                if ((specimenCount % 10000) == 0) s_log.warn("insertGeolocaleTaxaFromSpecimens() specimenCount:" + specimenCount);

                //if (AntwebProps.isDevMode() && (specimenCount % 2000) == 0) throw new AntwebException("test");

                String taxonName = rset.getString("taxon_name");
                String countryName = rset.getString("country");
                String adm1Name = rset.getString("adm1");
              
                boolean countryChanged = !countryName.equals(lastCountryName);
                lastCountryName = countryName;
                if (countryChanged) resetQueryGovernors();

                // Update the country records
				country = GeolocaleMgr.getValidCountry(countryName);
  			    if (country == null) {
  		          unfoundCountries.add(adm1Name);
				  //A.log("insertGeolocaleTaxaFromSpecimens() Country not found:" + countryName);
				  continue;
			    }
			    count += insert(country, taxonName, "specimen");
			    //A.log("insertGeolocaleTaxaFromSpecimens() country:" + country + " taxonName:" + taxonName + " count:" + count);
			    insertCount += count;
			  
			  
                // Update the Adm1 records
			    if (adm1Name != null) {
				  adm1 = GeolocaleMgr.getValidAdm1(adm1Name, countryName);
				  if (adm1 == null) {
				    unfoundAdm1s.add(adm1Name);
				    //A.log("insertGeolocaleTaxaFromSpecimens() Adm1 not found:" + adm1Name);
				    continue;
                  }				  
				  if (adm1 != null && taxonName != null) {
				    count += insert(adm1, taxonName, "specimen");
  	  	  	 	    //A.log("insertGeolocaleTaxaFromSpecimens() adm1:" + adm1 + " taxonName:" + taxonName + " count:" + count);			   		        
				    insertCount += count;
				  }
			    }

                //if (count > 1) return "testing";
                //A.log("insertGeolocaleTaxonSpecimens() insertCount:" + insertCount + " country:" + country + " adm1:" + adm1 + " taxonName:" + taxonName);              
			    
			    //reportOnTaxonSetItem(countryName, taxonName);            
            }
        } finally {
            DBUtil.close(stmt, "insertGeolocaleTaxaFromSpecimens()");
        }
        A.log("insertGeolocaleTaxaFromSpecimens() unfoundCountries:" + unfoundCountries);
        A.log("insertGeolocaleTaxaFromSpecimens() unfoundAdm1:" + unfoundAdm1s);
        A.log("insertGeolocaleTaxaFromSpecimens() s_constraintCount:" + s_constraintCount);
        return insertCount;
    }
    

// -------------------- an ----------------------

// -----------------  Population Support methods ------------------------

    private static int s_subfamilyCount = 0;
    private static int s_generaCount = 0;
    private static int s_speciesCount = 0;

// ---------------------------------------


    public String populateFromAntwikiData() throws SQLException {
      String disputes = "";
      String unfoundCountriesStr = "";
      HashSet unfoundCountries = new HashSet<String>();

      s_subfamilyCount = 0;
      s_generaCount = 0;
      s_speciesCount = 0;
      int updateCount = 0;

      resetQueryGovernors();

      ArrayList<AntwikiTaxonCountry> taxonCountries = (new AntwikiTaxonCountryDb(getConnection())).getAntwikiTaxonCountries();
      //Collections.sort(taxonCountries);
      for (AntwikiTaxonCountry speciesCountry : taxonCountries) {
        //String projectName = speciesCountry.getProjectName();
        String country = speciesCountry.getCountry();
        String taxonName = speciesCountry.getTaxonName();
        int rev = speciesCountry.getRev();
        boolean isIntroduced = speciesCountry.getIsIntroduced();
        
        //if (projectName == null) continue;
        if (country == null) continue;
        if (taxonName == null) continue;

        GeolocaleTaxonLogDb geolocaleTaxonLogDb = new GeolocaleTaxonLogDb(getConnection());
        if (geolocaleTaxonLogDb.getDispute(country, taxonName) != null) {  // was: projectName,
          disputes += "<br>&nbsp;&nbsp;&nbsp;" + country + " - " + taxonName;  // was: projectName
          // if there is a dispute (a geolocale_taxon deleted through the Species List Tool), skip.
          continue;
        }
        
        Geolocale validCountry = GeolocaleMgr.getValidCountry(country);
        if (validCountry != null) {
          updateCount += insert(validCountry, taxonName, "antwiki");
          //insertTaxonSetItem(validCountry.getId(), taxonName, rev, isIntroduced, "antwiki");
        } else {
          unfoundCountries.add(country);
        }        
      }
      //String message = s_subfamilyCount + " subfamilies, " + s_generaCount + " genera, " + s_speciesCount + " species inserted from antwiki_taxon_country into geolocale_taxon.";
      
      String message = updateCount + " updates.";
      if (disputes.length() > 1) message += "<br>Disputed Geolocale/Taxa not inserted due to Species List Tool action:" + disputes;
      if (unfoundCountries.size() > 0) message += "<Br>Unfound countries:" + unfoundCountries;
      return message;
    }
    
    public String undoPopulateFromAntwikiData() throws SQLException {
      return undoPopulate("antwiki");
    }
    public String undoPopulate(String source) throws SQLException {
        String dml = null;
        Statement stmt = null;
        try {
        
            stmt = DBUtil.getStatement(getConnection(), "undoPopulate()");

            dml = "delete from geolocale_taxon where source = '" + source + "'";
            stmt.executeUpdate(dml);
            
        } catch (SQLException e) {
            s_log.error("undoPopulate() e:" + e + " dml:" + dml);
        } finally {
            DBUtil.close(stmt, "undoPopulate()");
        }     
        String message = "Geolocale_taxon undo-populated of " + source + " data.";
        return message;
    } 


// -------------------- Populate Geolocale Taxon Source=specimen from specimen data -------------------

// 6398 geolocale_taxon Geolocale parentage records created. 6314 geolocale_taxon Taxon parentage records created.  3.73 mins
// 569 geolocale_taxon Geolocale parentage records created. 725 geolocale_taxon Taxon parentage records created.  2.77 mins
// 395 geolocale_taxon Geolocale parentage records created. 725 geolocale_taxon Taxon parentage records created. 2.67 mins 


/*
SOME OF THE SOURCES ARE fixTaxonParentage. This obscures the true source which depends on it's children (prefer specimen over literature).
This query shows that we could reclaim that data.

mysql> select gt.taxon_name, gt.geolocale_id, gt.source from geolocale_taxon gt, taxon t where gt.taxon_name = t.taxon_name and (t.parent_taxon_name, geolocale_id) in (select taxon_name, geolocale_id from geolocale_taxon where source = "fixTaxonParentage");

*/

    public String testGeolocaleTaxonParentage() {
      boolean fixIt = false;

      String message = "";
      message += checkGeolocaleParentage(fixIt);
      message += checkTaxonParentage(fixIt);
      
      return message;
    }
    
    public String fixGeolocaleTaxonParentage() {
      boolean fixIt = true;
      //boolean fixIt = false;
      
      if (fixIt) {
        try {
          if (true || !AntwebProps.isDevMode()) {
            deleteSource(TaxonSet.PROXY);
            //deleteSource("fixGeolocaleParentage");
            //deleteSource("fixTaxonParentage");
          }
        } catch (SQLException e) {
          A.log("geolocaleTaxonParentage() e:" + e);
        }
      }
      String message = "";
      message += checkGeolocaleParentage(fixIt);
      message += checkTaxonParentage(fixIt);
      
      return message;
    }
    
    private String lastLine = "";

/*
If this order by source and updates all, shouldn't we run in reverse order. No correct as is. Here is the source order:
select group_concat( distinct source) from geolocale_taxon order by source;
  antwiki,curator,proxyantwiki,proxycurator,proxyproxyspecimen,proxyspeciesList,proxyspeciesListTool,proxyspecimen,speciesList,speciesListTool,specimen
*/    
    public String checkGeolocaleParentage(boolean fixIt) {
      // Verify that all geolocale_taxon records have a record present for the geolocale's parent, with the taxon_name.
      int outOfIntegrity = 0;
      int fixed = 0;      
      String query = "select gt.taxon_name, gt.geolocale_id, gt.source " 
        + " from geolocale_taxon gt, geolocale g " 
        + " where gt.geolocale_id = g.id " 
        + " order by gt.source, gt.geolocale_id"        
        ; 
      A.log("checkGeolocaleParentage() query:" + query);
        
      Statement stmt = null;      
      try {
      
          resetQueryGovernors();     
      
          stmt = DBUtil.getStatement(getConnection(), "checkGeolocaleParentage()");
          ResultSet rset = stmt.executeQuery(query);
          while (rset.next()) {
            String taxonName = rset.getString("taxon_name");
            int geolocaleId = rset.getInt("geolocale_id");
            String source = rset.getString("source");

            Geolocale geolocale = GeolocaleMgr.getGeolocale(geolocaleId);
            if ("region".equals(geolocale.getGeorank())) continue;
            Geolocale parentGeolocale = GeolocaleMgr.getGeolocale(geolocale.getParent());
            
          boolean d = ("Chile".equals(geolocale.getName()) || "Chile".equals(parentGeolocale.getName()));
            if (parentGeolocale == null) {
              String message = "null parentGeolocale for geolocaleId:" + geolocaleId + " name:" + geolocale.getName() + " parent:" + geolocale.getParent();
              if (!message.equals(lastLine)) {
                if (d) A.log("checkGeolocaleParentage() " + message);
              }
              lastLine = message;
              continue;
            } else {
              int parentGeolocaleId = parentGeolocale.getId();
              TaxonSet taxonSet = get(parentGeolocaleId, taxonName);
              if (taxonSet == null) {
                ++outOfIntegrity;
                if (fixIt) {
                  fixed += insert(parentGeolocale, taxonName, TaxonSet.PROXY + source);
                  //fixed += insert(parentGeolocale, taxonName, "fixGeolocaleParentage");
                } else {
                  A.log("checkGeolocaleParentage() outOfIntegrity:" + outOfIntegrity + " fixed:" + fixed + " parentGeolocale:" + parentGeolocaleId + " (" + parentGeolocale.getName() + ") taxonName:" + taxonName + " source:" + source);              
                }
                if(d) A.log("checkGeolocaleParentage() outOfIntegrity:" + outOfIntegrity + " fixed:" + fixed + " parentGeolocale:" + parentGeolocaleId + " (" + parentGeolocale.getName() + ") taxonName:" + taxonName + " source:" + source);              
              }
            }
          }
          
          clearQueryGovernors();

      } catch (SQLException e) {
        s_log.error("checkGeolocaleParentage() e:" + e);
      } finally {
        DBUtil.close(stmt, null, "checkGeolocaleParentage()");
      }
      String appendStr =  ".";
      if (fixIt) appendStr = ", " + fixed + " inserted. ";
      String message = outOfIntegrity + " geolocale_taxon Geolocale Parentage records missing" + appendStr;     
      return message;    
    }

    public String checkTaxonParentage(boolean fixIt) {
      // Verify that all geolocale_taxon records have a record present for the geolocale's parent, with the taxon_name.
      int outOfIntegrity = 0;
      int fixed = 0;
      String query = "select gt.taxon_name, gt.geolocale_id, gt.source, t.parent_taxon_name " 
        + " from geolocale_taxon gt, taxon t "
        + " where gt.taxon_name = t.taxon_name " 
        + " and (t.parent_taxon_name, gt.geolocale_id) not in (select taxon_name, geolocale_id from geolocale_taxon) " 
        + " order by gt.source, gt.taxon_name"
        ; 
      A.log("checkTaxonParentage() query:" + query);
      Statement stmt = null;      
      try {
          resetQueryGovernors();     
      
          stmt = DBUtil.getStatement(getConnection(), "checkTaxonParentage()");
          ResultSet rset = stmt.executeQuery(query);
          while (rset.next()) {
            String taxonName = rset.getString("taxon_name");
            int geolocaleId = rset.getInt("geolocale_id");
            String source = rset.getString("source");
            String parentTaxonName = rset.getString("parent_taxon_name");

            if ("hymenoptera".equals(parentTaxonName)) continue;
            
            Geolocale geolocale = GeolocaleMgr.getGeolocale(geolocaleId);
          boolean d = ("Chile".equals(geolocale.getName()));
            
            String geolocaleName = geolocale.getName();
            ++outOfIntegrity;
			if (fixIt) {
              fixed += insert(geolocale, taxonName, TaxonSet.PROXY + source);			
              //fixed += insert(geolocale, parentTaxonName, "fixTaxonParentage");
            } else {
              A.log("checkTaxonParentage() outOfIntegrity:" + outOfIntegrity + " parentTaxonName:" + parentTaxonName + " geolocaleId:" + geolocaleId + "(" + geolocaleName + ") source:" + source);
            }
            if (d) A.log("checkTaxonParentage() outOfIntegrity:" + outOfIntegrity + " parentTaxonName:" + parentTaxonName + " geolocaleId:" + geolocaleId + "(" + geolocaleName + ") source:" + source);

          }
          
          clearQueryGovernors();
          
      } catch (SQLException e) {
        s_log.error("checkTaxonParentage() e:" + e);
      } finally {
        DBUtil.close(stmt, null, "checkTaxonParentage()");
      }
      String appendStr = ".";
      if (fixIt) appendStr = ", " + fixed + " inserted. ";
      String message = outOfIntegrity + " geolocale_taxon Taxon Parentage records" + appendStr;
      return message;      
    }


// -----

    static int s_fixed = 0;

    public String setSpecimenSource() {
      int outOfIntegrity = 0;
      s_fixed = 0;      
      String query = "select distinct country, subfamily, genus from specimen where country is not null";
      A.log("setSpecimenSource() query:" + query);
        
      Statement stmt = null;      
      try {
          stmt = DBUtil.getStatement(getConnection(), "setSpecimenSource()");
          ResultSet rset = stmt.executeQuery(query);
          while (rset.next()) {
            String countryName = rset.getString("country");
            String subfamily = rset.getString("subfamily");
            String genus = rset.getString("genus");

            String genusName = subfamily + genus;
            String subfamilyName = subfamily;
            
            Country country = GeolocaleMgr.getCountry(countryName);
            if (country == null) {
              A.log("setSpecimenSource() country:" + countryName + " not found.");
              continue;
            }
            String subregionName = country.getSubregion();
            Subregion subregion = GeolocaleMgr.getSubregion(subregionName);
            String regionName = country.getRegion();
            Region region = GeolocaleMgr.getRegion(regionName);
            
            setTaxonSet(country, genusName, Source.SPECIMEN);
            setTaxonSet(country, subfamilyName, Source.SPECIMEN);
            setTaxonSet(subregion, genusName, Source.SPECIMEN);
            setTaxonSet(subregion, subfamilyName, Source.SPECIMEN);
            setTaxonSet(region, genusName, Source.SPECIMEN);
            setTaxonSet(region, subfamilyName, Source.SPECIMEN);
            ++s_fixed;
          }

      } catch (SQLException e) {
        s_log.error("setSpecimenSource() e:" + e);
      } finally {
        DBUtil.close(stmt, null, "setSpecimenSource()");
      }
      String appendStr =  ".";
      //if (fixIt) appendStr = ", " + fixed + " inserted. ";
      String message = s_fixed + "(x6) geolocale_taxon Taxon parentage records fixed" + appendStr;     
      return message;    
    }


// -----


    String updateTaxonNames() {
      // For each of the following geolocale_taxon, update the taxon_name with the current_valid_name.
      Statement stmt = null;
	  String taxonName = null;
	  String currentValidName = null;
	  int geolocaleId = 0;;
	  String tableName = null;
	  String whereClause = null;      
	  int c = 0;   
  
      try {
          String query = "select g.id geolocale_id, t.taxon_name, t.current_valid_name " 
            + " from taxon t, geolocale_taxon gt, geolocale g " 
            + " where t.taxon_name = gt.taxon_name and gt.geolocale_id = g.id " 
            + " and t.status != 'valid' " 
            + " and current_valid_name is not null " 
            + " and current_valid_name != t.taxon_name";
          stmt = DBUtil.getStatement(getConnection(), "updateTaxonNames()");

          ResultSet rset = stmt.executeQuery(query);
          while (rset.next()) {
            taxonName = rset.getString("taxon_name");
            currentValidName = rset.getString("current_valid_name");
            geolocaleId = rset.getInt("geolocale_id");
            
            tableName = "geolocale_taxon";
            whereClause = "geolocale_id = " + geolocaleId;

            c += updateTaxonSetTaxonName(tableName, taxonName, currentValidName, whereClause);            
          }        
      } catch (SQLException e) {
        s_log.error("updateTaxonNames() e:" + e);
      } finally {
        DBUtil.close(stmt, null, "updateTaxonNames()");
      }
      return c + " Geolocale Taxon Names updated to current valid Taxon Names.  ";
    }

// ---------------------------------------------------------------------------------------


    // To be called like
    // (new GeolocaleTaxonDb(connection)).hasCalMorphos();
    public boolean hasCalMorphos() {
      boolean retVal = false;
      String query = "select taxon_name from geolocale_taxon where geolocale_id = 392 and taxon_name = 'myrmicinaeaphaenogaster us-ca01'";
      Statement stmt = null;
      try {
          stmt = DBUtil.getStatement(getConnection(), "HasCalMorphos()");
          ResultSet rset = stmt.executeQuery(query);
          while (rset.next()) {
            String taxonName = rset.getString("taxon_name");
            return true;
          }        
      } catch (SQLException e) {
        s_log.error("HasCalMorphos() e:" + e);
      }      
      s_log.warn("HasCalMorphos() no morphos found in California");
      AdminAlertMgr.add("No California Morphos", getConnection());
      return false;
    }    

    public ArrayList<Country> getCountries(String taxonName) throws SQLException {
        ArrayList<Country> countries = new ArrayList<Country>();
        Statement stmt = null;
        ResultSet rset = null;
        String query = null;
        try {
            Country country = null;
            taxonName = AntFormatter.escapeQuotes(taxonName);        
            query =
                " select g.id, g.name, g.parent, g.bioregion, g.georank, g.region, g.subregion" 
              + " from geolocale_taxon gt, geolocale g" 
              + " where gt.taxon_name= '" + taxonName + "'"
              + "   and g.id = gt.geolocale_id"
              //+ "   and g.is_use_children = 0"
              + "   and g.bioregion not in ('NULL', 'none')"
              + "   and g.georank = 'country'"
              + " order by g.region, g.name";

            //A.log("setGeolocales() query:" + query);
            stmt = DBUtil.getStatement(getConnection(), "getCountries()");
            rset = stmt.executeQuery(query);
            int i = 0;
            while (rset.next()) {
              ++i;
              country = new Country();
              country.setId(rset.getInt("id"));
              country.setName(rset.getString("name"));
              country.setParent(rset.getString("parent"));
              country.setGeorank(rset.getString("georank"));
              country.setSubregion(rset.getString("subregion"));
              country.setRegion(rset.getString("region"));
              //geolocale.setTitle(rset.getString("project_title"));
              //geolocale.setSource(rset.getString("source"));
              country.setBioregion(rset.getString("bioregion"));
              //geolocale.setDisplayKey(rset.getString("display_key"));
              //A.log("setGeolocales() i:" + i + " geolocale:" + geolocale);
              countries.add(country);
            }

        } catch (SQLException e) {
            s_log.error("getCountries() for taxonName:" + taxonName + " query:" + query + " e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, this, "getCountries()");
        }            
        return countries;
    }
    
// --- Statistics ---

    public static ArrayList<ArrayList<String>> getStatisticsByGeolocale(Connection connection) //ArrayList<ArrayList<String>>
        throws SQLException {
        Statement stmt = connection.createStatement();              

        String query = "select geolocale.name, geolocale_id, count(*) from geolocale_taxon, geolocale " 
          + " where geolocale_taxon.geolocale_id = geolocale.id group by geolocale.name, geolocale_id order by count(*) desc";

        ResultSet resultSet = stmt.executeQuery(query);

        ArrayList<ArrayList<String>> statistics = new ArrayList<ArrayList<String>>();

        while (resultSet.next()) {
            String geolocaleName = resultSet.getString(1);
            int geolocaleId = resultSet.getInt(2);
            statistics.add(GeolocaleTaxonDb.getStatistics(geolocaleName, geolocaleId, connection));
        }                        
        stmt.close();
        return statistics;
    }
    
    public static ArrayList<String> getStatistics(String geolocaleName, int geolocaleId, Connection connection) 
        throws SQLException {
        ArrayList<String> statistics = new ArrayList<String>();
        //HashMap<String, String> stats = new HashMap<String, String>();

        String query = "select count(*) from taxon, geolocale_taxon where taxon.taxon_name = geolocale_taxon.taxon_name and taxon.fossil = 1 and geolocale_taxon.geolocale_id = '" + geolocaleId + "' and rank=\"subfamily\"";
        Statement stmt2 = connection.createStatement();              
        ResultSet resultSet2 = stmt2.executeQuery(query);
        int extinctSubfamily = 0;
        while (resultSet2.next()) {
            extinctSubfamily = resultSet2.getInt(1);
        }
        query = "select count(*) from taxon, geolocale_taxon where taxon.taxon_name = geolocale_taxon.taxon_name and taxon.fossil = 1 and geolocale_taxon.geolocale_id = " + geolocaleId + " and rank=\"genus\"";
        resultSet2 = stmt2.executeQuery(query);
        int extinctGenera= 0;
        while (resultSet2.next()) {
            extinctGenera = resultSet2.getInt(1);
        }
        query = "select count(*) from taxon, geolocale_taxon where taxon.taxon_name = geolocale_taxon.taxon_name and taxon.fossil = 1 and geolocale_taxon.geolocale_id = " + geolocaleId + " and rank in ('species', 'subspecies')";
        resultSet2 = stmt2.executeQuery(query);
        int extinctSpecies = 0;
        while (resultSet2.next()) {
            extinctSpecies = resultSet2.getInt(1);
        }
        query = "select count(*) from taxon, geolocale_taxon where taxon.taxon_name = geolocale_taxon.taxon_name and taxon.fossil = 0 and geolocale_taxon.geolocale_id = " + geolocaleId + " and rank=\"subfamily\"";
        resultSet2 = stmt2.executeQuery(query);
        int extantSubfamily = 0;
        while (resultSet2.next()) {
            extantSubfamily = resultSet2.getInt(1);
        }
        query = "select count(*) from taxon, geolocale_taxon where taxon.taxon_name = geolocale_taxon.taxon_name and taxon.fossil = 0 and geolocale_taxon.geolocale_id = " + geolocaleId + " and rank=\"genus\"";
        resultSet2 = stmt2.executeQuery(query);
        int extantGenera = 0;
        while (resultSet2.next()) {
            extantGenera = resultSet2.getInt(1);
        }
        query = "select count(*) from taxon, geolocale_taxon where taxon.taxon_name = geolocale_taxon.taxon_name and taxon.fossil = 0 and geolocale_taxon.geolocale_id = " + geolocaleId + " and rank in ('species', 'subspecies')";
        resultSet2 = stmt2.executeQuery(query);
        int extantSpecies = 0;
        while (resultSet2.next()) {
            extantSpecies = resultSet2.getInt(1);
        }
        query = "select count(*) from taxon, geolocale_taxon where taxon.taxon_name = geolocale_taxon.taxon_name  and geolocale_taxon.geolocale_id = " + geolocaleId + " and status='valid' and rank=\"subfamily\"";
        resultSet2 = stmt2.executeQuery(query);
        int validSubfamily = 0;
        while (resultSet2.next()) {
            validSubfamily = resultSet2.getInt(1);
        }
        query = "select count(*) from taxon, geolocale_taxon where taxon.taxon_name = geolocale_taxon.taxon_name and geolocale_taxon.geolocale_id = " + geolocaleId + " and status='valid' and rank=\"genus\"";
        resultSet2 = stmt2.executeQuery(query);
        int validGenera = 0;
        while (resultSet2.next()) {
            validGenera = resultSet2.getInt(1);
        }
        query = "select count(*) from taxon, geolocale_taxon where taxon.taxon_name = geolocale_taxon.taxon_name and geolocale_taxon.geolocale_id = " + geolocaleId + " and status='valid' and rank in ('species', 'subspecies')";
        resultSet2 = stmt2.executeQuery(query);
        int validSpecies = 0;
        while (resultSet2.next()) {
            validSpecies = resultSet2.getInt(1);
        }
        
        query = "select count(*) from taxon, geolocale_taxon where taxon.taxon_name = geolocale_taxon.taxon_name and geolocale_taxon.geolocale_id = " + geolocaleId 
          + " and taxon.status = 'valid' and geolocale_taxon.image_count > 0";
        resultSet2 = stmt2.executeQuery(query);
        int validImagedSpecies = 0;
        while (resultSet2.next()) {
            validImagedSpecies = resultSet2.getInt(1);
        }   
                
        query = "select count(*) from taxon, geolocale_taxon where taxon.taxon_name = geolocale_taxon.taxon_name and geolocale_taxon.geolocale_id = " + geolocaleId;
        resultSet2 = stmt2.executeQuery(query);
        int totalTaxa = 0;
        while (resultSet2.next()) {
            totalTaxa = resultSet2.getInt(1);
        }

       statistics.add(geolocaleName); 
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
       statistics.add("" + totalTaxa);

        // A.log("getProjectStatistics() statistics:" + statistics);                    
        return statistics;
    }
    
        
}
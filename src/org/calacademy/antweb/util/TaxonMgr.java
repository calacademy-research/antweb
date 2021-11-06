    package org.calacademy.antweb.util;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class TaxonMgr extends Manager {

    private static final Log s_log = LogFactory.getLog(TaxonMgr.class);

    private static ArrayList<Taxon> s_subfamilies = null;
    
    private static HashMap<String, Taxon> s_genera = null;
    
    private static HashMap<String, Taxon> s_species = null;

    // Shallow copies
    private static HashMap<String, Taxon> s_taxa = null;

    private static HashMap<String, ArrayList<String>> s_subgenusHashMap = new HashMap<>();

    //private static List<String> taxaNamesList = null;
    private static List<String> prettyTaxaNamesList = null;

    public static void populate(Connection connection, boolean forceReload, boolean initialRun) {
        if (!forceReload && (s_subfamilies != null)) return;

        TaxonDb taxonDb = new TaxonDb(connection);
        s_subfamilies = taxonDb.getTaxa(Rank.SUBFAMILY);
        //A.log("populate() subfamilies:" + s_subfamilies);

        ArrayList<Taxon> genera = taxonDb.getTaxa(Rank.GENUS);
        s_genera = new HashMap<>();

        //A.log("populate() genera.size:" + genera.size());
        for (Taxon taxon : genera) {
            s_genera.put(taxon.getTaxonName(), taxon);
        }

        // For Taxon Name Search Autocomplete
        prettyTaxaNamesList = new ArrayList<>();
        prettyTaxaNamesList.addAll(CommonNames.getNames());

        List<String> taxaNamesList = taxonDb.getTaxonNames();
        for (String taxonName : taxaNamesList) {
            prettyTaxaNamesList.add(Taxon.getPrettyTaxonName(taxonName));
        }

        s_taxa = new HashMap<>();
        ArrayList<Taxon> taxa = taxonDb.getShallowTaxa();
        for (Taxon taxon : taxa) {
            s_taxa.put(taxon.getTaxonName(), taxon);
        }

        s_subgenusHashMap = taxonDb.getSubgenusHashMap();
    }

    //Called through UtilAction to, in a separate thread.
    public static void postInitialize(Connection connection) throws SQLException {
    }

    // For Taxon Name Search Autocomplete
    public static List<String> getPrettyTaxaNames(String text) {
      if (prettyTaxaNamesList == null) {
        //A.log("getPrettyTaxaNames(text) initializing...");
        return null;
      }
      if (text == null) {
        s_log.warn("TaxonMgr.getPrettyTaxaNames(text) text is null");
        return null;
      }
      text = text.toLowerCase();
      //A.log("getPrettyTaxaNames(text) text:" + text + " prettyTaxaListSize:" + prettyTaxaNamesList.size());      
      String[] texts = text.split(" ");
      List<String> prettyTaxaNamesSubset = new ArrayList<>();
      int i = 0;

      for (String taxonName : prettyTaxaNamesList) {
        boolean containsAll = true;
          for (String s : texts) {
              //log("getPrettyTaxaNames() text:" + text + " j:" + texts[j] + " taxonName:" + taxonName);
              if (!taxonName.toLowerCase().contains(s)) containsAll = false;
              if (!containsAll) break;
          }
        if (containsAll) {
          prettyTaxaNamesSubset.add(taxonName);
          ++i;
          if (i > 5000) break; // Because there are 4600 species in genus Camponotus  
        }  
      } 
      //A.log("getPrettyTaxaNames(q) returning size:" + prettyTaxaNamesSubset.size());
      return prettyTaxaNamesSubset;
    }
    
    public static ArrayList<Taxon> getSubfamilies() {
      //A.log("getSubfamilies() TaxonMgr.subfamilies:" + s_subfamilies);
      return s_subfamilies;
    }
    
    public static Subfamily getSubfamily(String subfamilyName) {
      if (subfamilyName == null) return null;
      Subfamily subfamily = null;
      for (Taxon taxon : getSubfamilies()) {
        if (subfamilyName.equals(taxon.getSubfamily())) return (Subfamily) taxon;
      }
      return null;
    }

    public static ArrayList<Genus> getGenera() {
      ArrayList<Genus> genera = new ArrayList<>();
      for (Taxon genus : s_genera.values()) {
        genera.add((Genus)genus);
      }
      Collections.sort(genera);
      return genera;
    }
    
    // Used from Specimen upload and specimen-body.jsp
    public static Genus getGenus(String genusTaxonName) {
      if (genusTaxonName == null) return null;
      if (s_genera == null) return null; // Could happen due to server initialization.
      return (Genus) s_genera.get(genusTaxonName);
    }
    public static Genus getGenusFromName(String genusName) {
      if (genusName == null) return null;
      //A.log("getGenus() s_genera:" + s_genera.size());

      for (Taxon genus : s_genera.values()) {
        //A.log("getGenus() genusTaxonName:" + genusName + " genus:" + genus.getName());
        if (genusName.equals(genus.getName())) return (Genus) genus;    
      }
      return null;
    }
    
    // Seems this has been changed. Only now used by TypeStatusMgr and TestAction.
    // !!! Only used by Specimen Upload. (First call will cause a 40 second delay). Nope, used to be slow. Now fast.
    public static Species getSpecies(Connection connection, String taxonName) {
      if (taxonName == null) return null;

      if (s_species == null) {
        s_species = new HashMap<>();
        TaxonDb taxonDb = new TaxonDb(connection);      
        ArrayList<Taxon> species = taxonDb.getTaxa("taxarank in ('" + Rank.SPECIES + "', '" + Rank.SUBSPECIES + "')");
        A.log("getSpecies() speciesCount:" + species.size());
        for (Taxon taxon : species) {
          s_species.put(taxon.getTaxonName(), taxon);
        } 
      }
      return (Species) s_species.get(taxonName);
    }


    public static boolean s_useRefreshedTaxonMgr = false; //AntwebProps.isDevMode();
/*
    Potential workaround for mid-upload process queries "getDummyTaxon()" necessary because we can't trust freshness.
    Called from AntwebUpload.saveTaxon() immediately after saving the taxon. Should verify that other methods don't
    modify taxa in the database that would also require refresh().
    The gains are not as we much as we like because to rely on the TaxonMgr for DummyTaxa we need to update
    The taxa when we save it.
    If the taxon data wasn't held in a hashtable, we would simply insert it into the TaxonMgr. Could we reliably
    construct taxon objects from the hashtable to serve the purpose of dummyTaxons? Would need source and taxonomic
    data at least. Gains are recorded below.
    This functionality might optimize methods AntwebUpload.setStatusAndCurrentValidName(), 
    SpecimenUpload.setStatusAndCurrentValidName(), TaxonDb.getCurrentValidTaxonName(getConnection(), currentValidName);
    Results report at the end of SpecimenUpload.importSpecimens();
    Gains as getDummyTaxon() calls are replaced:
    Exec Time: 502 secs (8.366666666666667 min)  // Without s_useRefreshedTaxonMgr
    Exec Time: 459 secs (7.65 min)               // AntwebUpload.setStatusAndCurrentValidName()
    Exec Time: 407 secs (6.783333333333333 min)  // SpecimenUploadProcess.setStatusAndCurrentValidName()
    Exec Time: 377 secs (6.283333333333333 min)  // Added AntwebUpload.enactExceptions()
    Recommenation: Option 1. Rewrite the upload process to use Taxon objects instead of Hashtables. Fairly major 
      refactoring - Maybe 3-5 days. Option 2. This current "Refresh implementation" would need further testing to be 
      sure that other methods don't update underlying data, and it must be tested with the Worldants upload". Currently
      seems to cut upload times by 25%. Option 3. Generate taxon objects from hashtable for insertion into TaxonMgr. 
      Might be fastest (because it doesn't require database requests to update, but would require care to ensure that
      no errors are introduced.
    Note: It would be good to refactor the two setStatusAndCurrentValidName() methods (AntwebUpload and SpecimenUpload).
    */
    public static int s_refreshTaxonCount = 0;
    public static void refreshTaxon(Connection connection, String taxonName) throws SQLException {
        if (taxonName == null) return;

        s_log.warn("refreshCount() taxonName:" + taxonName);

        TaxonDb taxonDb = new TaxonDb(connection);
        Taxon taxon = taxonDb.getTaxon(taxonName);
        if (taxon == null) {
            A.log("refreshTaxon taxon:" + taxonName + " not found.");
            return;
        }

        if (s_species != null) {
            if (taxon instanceof Species) s_species.put(taxonName, taxon);  // Is species used?
        }

        A.log("refreshTaxon() taxon:" + taxon + " class:" + taxon.getClass());
        // Is it ever a subclass? Is it actually ever a genus?

        if (taxon instanceof Genus) s_genera.put(taxonName, taxon);
        s_taxa.put(taxonName, taxon);

        s_refreshTaxonCount = s_refreshTaxonCount + 1;
    }




// TaxonDb.s_currentValidFetchCount = 20 TaxonMgr.refreshTaxonCount:8 TaxonDb.s_dummyFetchCount:245055

    public static int s_gottenTaxon = 0;

    public static Taxon getTaxon(String taxonName) {
      if (taxonName == null) return null;
      if (s_taxa == null) {
          A.log("getTaxon() returning null because s_taxa is null");
          // Could happen due to serverinitialization.
          return null;
      }

      Taxon taxon = s_taxa.get(taxonName);
      if (taxon == null) {
          A.log("getTaxon() taxon not found:" + taxonName);
          return null;
      }
      //A.log("getTaxon() returning taxon:" + taxon);

      ++s_gottenTaxon;
      return taxon;
    }

    public static String getSubgenus(String taxonName) {
        Taxon taxon = getTaxon(taxonName);
        if (taxon == null) return null;
        return taxon.getSubgenus();
    }

    public static List<String> getSubgenera(String genusName) {

        if (s_subgenusHashMap == null) return null;
        return s_subgenusHashMap.get(genusName);
    }
    
}


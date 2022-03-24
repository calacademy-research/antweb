    package org.calacademy.antweb.util;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.Collection;

import org.calacademy.antweb.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class TaxonMgr extends Manager {

    private static final Log s_log = LogFactory.getLog(TaxonMgr.class);

    private static ArrayList<Taxon> s_subfamilies;
    
    private static HashMap<String, Taxon> s_genera;
    
    private static HashMap<String, Taxon> s_species;

    // Shallow copies
    private static HashMap<String, Taxon> s_taxa;

    private static HashMap<String, ArrayList<String>> s_subgenusHashMap = new HashMap<>();

    //private static List<String> taxaNamesList = null;
    private static List<String> prettyTaxaNamesList;

    private static Date s_populateTime;

    public static void populate(Connection connection, boolean forceReload, boolean initialRun) throws SQLException {
        if (!forceReload && s_subfamilies != null) return;

        TaxonDb taxonDb = new TaxonDb(connection);
        s_subfamilies = taxonDb.getTaxa(Rank.SUBFAMILY);
        //A.log("populate() subfamilies:" + s_subfamilies);

        ArrayList<Taxon> genera = taxonDb.getTaxa(Rank.GENUS);
        s_genera = new HashMap<>();

        //A.log("populate() genera.size:" + genera.size());
        for (Taxon taxon : genera) {
            //if ("apterogyna".equals(taxon.getGenus())) A.log("populate() genus:" + taxon);
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
        ArrayList<Taxon> taxa = taxonDb.getTaxa();
        for (Taxon taxon : taxa) {
            s_taxa.put(taxon.getTaxonName(), taxon);
        }

        s_subgenusHashMap = taxonDb.getSubgenusHashMap();

        s_populateTime = new Date();

        s_log.warn("populate() " + report());
       // if (AntwebProps.isDevMode()) AntwebUtil.logShortStackTrace();
    }

    public static String report() {
        return "TaxonMgr last loaded:" + s_populateTime + " subfamily:" + getSubfamilies().size() + " genera:" + s_genera.size() + " species:" + getSpeciesCount() + " taxa:" + s_taxa.size();
    }

    public static int getSpeciesCount() {
        int c = 0;
        for (Taxon t : s_taxa.values()) {
            if ("species".equals(t.getRank())) c = c + 1;
        }
        return c;
    }

    //Called through UtilAction to, in a separate thread.
    public static void postInitialize(Connection connection) throws SQLException {
    }

    public static Collection<Taxon> getTaxa() {
        return s_taxa.values();
    }

    public static int getValidTaxonCount() {
        int validTaxonCount = 0;
        for (Taxon taxon : getTaxa()) {
            if (taxon.isValid()) ++validTaxonCount;
        }
        return validTaxonCount;
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

    private static boolean s_ambiguousGenusReported = false;
    // Used from Specimen upload and specimen-body.jsp
    // genusTaxonName is [subfamily][genus]
    public static Genus getGenus(String genusTaxonName) {
        //ProfileCounter.add("TaxonMgr.getGenus()" + AntwebUtil.getShortStackTrace());

      if (genusTaxonName == null) return null;
      if (s_genera == null) return null; // Could happen due to server initialization.
      return (Genus) s_genera.get(genusTaxonName);
    }

    public static Genus getGenusFromName(String genusName) {
      //ProfileCounter.add("TaxonMgr.getGenusFromName()" + AntwebUtil.getShortStackTrace());

      if (genusName == null) return null;

      Taxon genus = null;
      //A.log("getGenus() s_genera:" + s_genera.size());

      boolean genusFound = false;
      s_ambiguousGenusReported = false;
      for (Taxon g : s_genera.values()) {
        //A.log("getGenus() genusTaxonName:" + genusName + " genus:" + genus.getName());
        if (genusName.equals(g.getName())) {
            if (Status.SYNONYM.equals(g.getStatus())) {
                //A.log("Not using synonym " + g.getName());
                continue;
            }
            genus = g;
            if (genusFound == true && !s_ambiguousGenusReported) {
                s_ambiguousGenusReported = true;
                s_log.warn("getGenusFromName(). AMBIGUOUS! Found a second genus with genusName:" + genusName + ". generaSize:" + s_genera.size());
                //AntwebUtil.logShortStackTrace();
                /* Probably.
                	at org.calacademy.antweb.util.TaxonMgr.getGenusFromName(TaxonMgr.java:151)
	                at org.calacademy.antweb.upload.SpecimenUploadParse.parseLine(SpecimenUploadParse.java:294)
	                at org.calacademy.antweb.upload.SpecimenUpload.importSpecimens(SpecimenUpload.java:164)
	                at org.calacademy.antweb.upload.SpecimenUploader.uploadSpecimenFile(SpecimenUploader.java:98)
             	    at org.calacademy.antweb.upload.SpecimenUploader.uploadSpecimenFile(SpecimenUploader.java:48)
                 */
            }
            genusFound = true;
            // Keep looping. Small performance cost but if we find another, ambiguity bad.
        }
      }
      if (genusFound) {
          return (Genus) genus;
      }
      return null;
    }

    public static int s_getTaxonCount = 0;
    public static Taxon getTaxon(String taxonName) {

        //ProfileCounter.add("TaxonMgr.getTaxon()" + AntwebUtil.getShortStackTrace());

        if (taxonName == null) return null;
        if (s_taxa == null) {
            s_log.debug("getTaxon() returning null because s_taxa is null");
            // Could happen due to serverinitialization.
            return null;
        }

        Taxon taxon = s_taxa.get(taxonName);
        if (taxon == null) {
            //A.log("getTaxon() taxon not found:" + taxonName);
            return null;
        }
        //A.log("getTaxon() returning taxon:" + taxon);

        ++s_getTaxonCount;
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

    // Seems this has been changed. Only now used by TypeStatusMgr and TestAction.
    // !!! Only used by Specimen Upload. (First call will cause a 40 second delay). Nope, used to be slow. Now fast.
    public static Species getSpecies(Connection connection, String taxonName) {
        ProfileCounter.add("TaxonMgr.getSpecies()");

        if (taxonName == null) return null;

      if (s_species == null) {
        s_species = new HashMap<>();
        TaxonDb taxonDb = new TaxonDb(connection);      
        ArrayList<Taxon> species = taxonDb.getTaxa("taxarank in ('" + Rank.SPECIES + "', '" + Rank.SUBSPECIES + "')");
        s_log.debug("getSpecies() speciesCount:" + species.size());
        for (Taxon taxon : species) {
          s_species.put(taxon.getTaxonName(), taxon);
        } 
      }
      return (Species) s_species.get(taxonName);
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


    // This is a rough but effective solution. Set true during worldants upload (in UploadAction) and false at the end.
    private static boolean s_isInWorldants = false;
    public static boolean isInWorldants() { return s_isInWorldants; }
    public static void setIsInWorldants(boolean isInWorldants) {
        s_isInWorldants = isInWorldants;
    }

    public static boolean isUseRefreshing() {
        return false;
    }

}


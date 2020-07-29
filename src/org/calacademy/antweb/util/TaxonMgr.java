    package org.calacademy.antweb.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import org.apache.struts.action.*;

import javax.servlet.http.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.Formatter;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class TaxonMgr extends Manager {

    private static final Log s_log = LogFactory.getLog(TaxonMgr.class);

    private static ArrayList<Taxon> s_subfamilies = null;
    
    private static HashMap<String, Taxon> s_genera = null;
    
    private static HashMap<String, Taxon> s_species = null;

    // Shallow copies
    private static HashMap<String, Taxon> s_taxa = null;

    private static HashMap<String, ArrayList<String>> s_subgenusHashMap = new HashMap<String, ArrayList<String>>();

    //private static List<String> taxaNamesList = null;
    private static List<String> prettyTaxaNamesList = null;

    public static void populate(Connection connection, boolean forceReload, boolean initialRun) {
        if (!forceReload && (s_subfamilies != null)) return;

        TaxonDb taxonDb = new TaxonDb(connection);
        s_subfamilies = taxonDb.getTaxa(Rank.SUBFAMILY);
        //A.log("populate() subfamilies:" + s_subfamilies);

        ArrayList<Taxon> genera = taxonDb.getTaxa(Rank.GENUS);
        s_genera = new HashMap<String, Taxon>();

        //A.log("populate() genera.size:" + genera.size());
        for (Taxon taxon : genera) {
            s_genera.put(taxon.getTaxonName(), taxon);
        }

        // For Taxon Name Search Autocomplete
        prettyTaxaNamesList = new ArrayList<String>();
        prettyTaxaNamesList.addAll(CommonNames.getNames());

        List<String> taxaNamesList = taxonDb.getTaxonNames();
        for (String taxonName : taxaNamesList) {
            prettyTaxaNamesList.add(Taxon.getPrettyTaxonName(taxonName));
        }

//        TaxonDb taxonDb = new TaxonDb(connection);
        s_taxa = new HashMap<String, Taxon>();
        ArrayList<Taxon> taxa = taxonDb.getShallowTaxa();
        for (Taxon taxon : taxa) {
            s_taxa.put(taxon.getTaxonName(), taxon);
        }

        if (!initialRun) {
            try {
                postInitialize(connection);
            } catch (SQLException e) {
                s_log.warn("populate() e:" + e);
            }
        }


        s_subgenusHashMap = taxonDb.getSubgenusHashMap();
    }

    //Called through UtilAction to, in a separate thread, populate the curators with adm1.
    // Time consuming. About 8 seconds.
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
      List<String> prettyTaxaNamesSubset = new ArrayList<String>();
      int i = 0;

      for (String taxonName : prettyTaxaNamesList) {
        boolean containsAll = true;
        for (int j=0 ; j < texts.length ; ++j) {
          //log("getPrettyTaxaNames() text:" + text + " j:" + texts[j] + " taxonName:" + taxonName);
          if (!taxonName.toLowerCase().contains(texts[j])) containsAll = false;
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
      ArrayList<Genus> genera = new ArrayList<Genus>();
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

    // !!! Only used by Specimen Upload. (First call will cause a 40 second delay).
    public static Species getSpecies(Connection connection, String taxonName) {
      if (taxonName == null) return null;

      if (s_species == null) {
        s_species = new HashMap<String, Taxon>();
        TaxonDb taxonDb = new TaxonDb(connection);      
        ArrayList<Taxon> species = taxonDb.getTaxa("taxarank in ('" + Rank.SPECIES + "', '" + Rank.SUBSPECIES + "')");
        A.log("getSpecies() speciesCount:" + species.size());
        for (Taxon taxon : species) {
          s_species.put(taxon.getTaxonName(), taxon);
        } 
      }
      return (Species) s_species.get(taxonName);
    }

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


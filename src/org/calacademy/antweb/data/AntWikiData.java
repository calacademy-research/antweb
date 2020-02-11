package org.calacademy.antweb.data;

import java.io.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


import org.apache.struts.action.*;
import org.apache.regexp.*;


public final class AntWikiData {

    private static Log s_log = LogFactory.getLog(AntWikiData.class);

    private String shortTaxonName = null;
    private String subfamily = null;
	private String genus = null;;
	private String species = null;;
	private String subspecies = null;
    private String country = null;
      
    public AntWikiData(String theLine) {
      String[] components = null;
  	  try {   
         RE tab = new RE("\t");
	     components = tab.split(theLine);
		  int i = 0;
		  subfamily = components[i].toLowerCase();
		  // shortTaxonName = components[0].toLowerCase();
		  genus = components[i+1].toLowerCase();
		  species = components[i+2];
		  subspecies = null;
		  if (components.length > i+3) subspecies = components[i+3];          
          if (components.length > i+4) country =  components[i+4];
        
		  shortTaxonName = genus + " " + species;
		  if (subspecies != null) shortTaxonName += " " + subspecies;

      } catch (RESyntaxException e) {
          s_log.warn("AntWikiData() e:" + e);
 	  } catch (ArrayIndexOutOfBoundsException e) {
		 s_log.warn("AntWikiData() size:" + components.length + " subfamily:" + subfamily + " genus:" + genus + " species:" + species + " subspecies:" + subspecies + " e:" + e);
	  }	  

    } 
    
    public String getCountry() { 
      return country;	  
    }
    public String getShortTaxonName() {
      return shortTaxonName;
    }
    public String getTaxonName() {
      //Taxon genus = TaxonMgr.getGenusFromName(getGenus());
      //if (genus != null) return genus.getTaxonName();
      //return null;
      String taxonName = subfamily + genus + " " + species;
      taxonName = taxonName.toLowerCase();
      if (subspecies != null) taxonName += " " + subspecies;
      return taxonName;
    }
    public String getSubfamily() {
      return subfamily;
    }
    public String getGenus() {
      return genus;
    }
    public String getSpecies() {
      return species;
    }
    public String getSubspecies() {
      return subspecies;
    }
    
}
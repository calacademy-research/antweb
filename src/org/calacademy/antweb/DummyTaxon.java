package org.calacademy.antweb;

import java.util.*;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
/**
 * A stand-in Taxon made from a taxon name only (makeDummyTaxon splits the name into subfamily, genus, species and
 * rank), for code that handles a name without a full taxon page. Used by the Bioregion and Introduced Map Managers
 * (one row per genus or species with its bioregion map), by the species list import (SpeciesListUpload looks up a
 * current valid name with DummyTaxonDb.getDummyTaxon), as the empty taxon of a field guide without one, and by the
 * page-text orphan methods of OrphansDb (with suggested valid names), which nothing calls since the Orphan Manager
 * was removed. getFullName leaves out the subfamily part of the name.
 */
public class DummyTaxon extends Taxon {
    /** This class can be used to populate and manipulate description_edits or other objects that 
     * are keyed on taxonName
     */

    private static final Log s_log = LogFactory.getLog(DummyTaxon.class);

    private String bioregionMap;


    //private String name = null;

    public static DummyTaxon makeDummyTaxon(String taxonName) {
      DummyTaxon dummy = new DummyTaxon();
      dummy.setTaxonName(taxonName);
      
      dummy.setFamily("formicidae");
      dummy.setSubfamily(Taxon.getSubfamilyFromName(taxonName));
      dummy.setGenus(Taxon.getGenusFromName(taxonName));
      dummy.setSpecies(Taxon.getSpeciesFromName(taxonName));
      dummy.setSubspecies(Taxon.getSubspeciesFromName(taxonName));
      
      dummy.setRank(Taxon.getRankFromName(taxonName));
      return dummy;
    }

    public String getTaxonName() {
        //A.log("getTaxonName() taxonName:" + name);
        return name;
    }
    public void setTaxonName(String taxonName) {
        name = taxonName;
    }
        
    public boolean isDummy() {
      return true;
    }

    public String getFullName() {
      // Used here: http://localhost/antweb/orphanDescEdits.do
      
      if (getTaxonName() == null) {
        return null;
      }

      for (Taxon aSubfamily : TaxonMgr.getSubfamilies()) {
        String subfamily = aSubfamily.getSubfamily();
        String subst = null;
        try {
          subst = getTaxonName().substring(0, subfamily.length());
        } catch (StringIndexOutOfBoundsException e) { 
          // safely ignore
        }
        if (subfamily.equals(subst)) {
          //A.log("getFullName() subfamily:" + subfamily + " subst:" + subst);
          return getTaxonName().substring(subfamily.length());
        }

      }
      //s_log.warn("getFullName() return:" + getName());
      return name;
    }
    
    public void setBioregionMap(String bioregionMap) {
      this.bioregionMap = bioregionMap;
    }
    public String getBioregionMap() {
      return this.bioregionMap;
    }
    
    // This is to provide suggestions to the curator during the Orphan Manager Desc Edit process.
    ArrayList<DummyTaxon> possibleValidNames;
    public ArrayList<DummyTaxon> getPossibleValidNames() {
      return possibleValidNames;
    }
    public void setPossibleValidNames(ArrayList<DummyTaxon> possibleValidNames) {
      this.possibleValidNames = possibleValidNames;
    }
    
    public String toString() {
      return "dummyTaxonName:" + getTaxonName() + super.toString() + " parentTaxonName:" + getParentTaxonName() + " parent:" + getParent();
    }
    
}

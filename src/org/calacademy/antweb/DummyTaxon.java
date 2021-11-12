package org.calacademy.antweb;

import java.util.*;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public class DummyTaxon extends Taxon {
    /** This class can be used to populate and manipulate description_edits or other objects that 
     * are keyed on taxonName
     */

    private static Log s_log = LogFactory.getLog(DummyTaxon.class);

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
    ArrayList<DummyTaxon> possibleValidNames = null;
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

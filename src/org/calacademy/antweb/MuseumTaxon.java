package org.calacademy.antweb;

import org.calacademy.antweb.geolocale.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/**
 * A taxon's row in a museum's species list (table museum_taxon, key code and taxon_name): its counts of
 * subfamilies, genera, species, specimens and images. Made by Museum.getTaxonSet when a taxon page or list is seen
 * in a museum (museumCode=); OverviewTaxon.init reads the row. The rows are written by nightly set 3
 * (MuseumDb.populate) and set 4 (image counts). Docs: docs/data-model.md (museum_taxon), docs/flows/taxon-pages.md.
 */
public class MuseumTaxon extends OverviewTaxon {

    private static final Log s_log = LogFactory.getLog(MuseumTaxon.class);

    private String museumCode;    

    public MuseumTaxon() {
    }

    public MuseumTaxon(Museum museum, String taxonName, String rank) {
      this.taxonName = taxonName;
      this.museumCode = museum.getCode();
      this.rank = rank;
    }
    
    // init(Connection connection) is found in OverviewTaxon.java.
    
    public String getTable() {
      return "museum_taxon";
    }
    
    public String getKeyClause() {
      return " code = '" + museumCode + "'";
    }
    
    public String toString() {
      return "MuseumTaxon museumCode:" + getMuseumCode() 
        + " <br>taxonName:" + getTaxonName() + " rank:" + rank
        + " <br>created:" + getCreated()
        + " <br>source:" + getSource()        
        + " <br>subfamilyCount:" + getSubfamilyCount() 
        + " <br>genusCount:" + getGenusCount()
        + " <br>speciesCount:" + getSpeciesCount() 
        + " <br>specimenCount:" + getSpecimenCount()
        + " <br>imageCount:" + getImageCount()
        ;
    }

    public String getMuseumCode() {
      return museumCode;
    }
    public void setMuseumCode(String museumCode) {
      this.museumCode = museumCode;
    }
    
}

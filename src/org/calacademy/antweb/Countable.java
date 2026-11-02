package org.calacademy.antweb;

/**
 * The count queries of a place for the nightly count crawls: specimens of each name, children and grandchildren of a
 * rank, the update of a count column, and the image count per name. Implemented by Project, Museum,
 * geolocale/Bioregion and geolocale/Geolocale; run by home/CountDb (childrenCountCrawl, imageCountCrawl) and
 * GeolocaleTaxonCountDb. Docs: docs/numbers.md (rows 20 to 37), docs/flows/nightly.md.
 */
public interface Countable {

    String getCountSpecimensQuery();

    //Just for Geolocale
    //public String getCountSpecimensLocalQuery();

    String getCountChildrenQuery(String rank);
    
    String getCountGrandChildrenQuery(String rank, String column);

    String getUpdateCountSQL(String parentTaxonName, String columnName, int count);
    //public String getPrepUpdateCountSQL(String parentTaxonName, String columnName, int count);
    
    String getTaxonImageCountQuery();

    String getUpdateImageCountSQL(String taxonName, int sum); //, String rank);
    
}
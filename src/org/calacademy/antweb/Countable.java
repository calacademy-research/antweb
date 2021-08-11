package org.calacademy.antweb;

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
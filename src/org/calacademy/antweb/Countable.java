package org.calacademy.antweb;

public interface Countable {

    public String getCountSpecimensQuery();

    //Just for Geolocale
    //public String getCountSpecimensLocalQuery();

    public String getCountChildrenQuery(String rank);
    
    public String getCountGrandChildrenQuery(String rank, String column);

    public String getUpdateCountSQL(String parentTaxonName, String columnName, int count);
    //public String getPrepUpdateCountSQL(String parentTaxonName, String columnName, int count);
    
    public String getTaxonImageCountQuery();

    public String getUpdateImageCountSQL(String taxonName, int sum); //, String rank);
    
}
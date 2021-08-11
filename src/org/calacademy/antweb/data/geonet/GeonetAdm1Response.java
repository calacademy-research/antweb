package org.calacademy.antweb.data.geonet;


import java.util.*;


/*
http://geonames.nga.mil/arcgis/rest/services/Research/GeoNames/MapServer/2/query?where=COUNTRY_CD+is+not+NULL&text=&objectIds=&time=&geometry=&geometryType=esriGeometryEnvelope&inSR=&spatialRel=esriSpatialRelIntersects&relationParam=&outFields=COUNTRY_CD%2C+ADM1%2C+ADM1_NAME&returnGeometry=false&returnTrueCurves=false&maxAllowableOffset=&geometryPrecision=&outSR=&returnIdsOnly=false&returnCountOnly=false&orderByFields=&groupByFieldsForStatistics=&outStatistics=&returnZ=false&returnM=false&gdbVersion=&returnDistinctValues=false&resultOffset=&resultRecordCount=
 
{
 "displayFieldName": "ADM1_NAME",
 "fieldAliases": {
  "COUNTRY_CD": "COUNTRY_CD",
  "ADM1": "ADM1",
  "ADM1_NAME": "ADM1_NAME"
 },
 "fields": [
  {
   "name": "COUNTRY_CD",
   "type": "esriFieldTypeString",
   "alias": "COUNTRY_CD",
   "length": 2
  },
  {
   "name": "ADM1",
   "type": "esriFieldTypeString",
   "alias": "ADM1",
   "length": 2
  },
  {
   "name": "ADM1_NAME",
   "type": "esriFieldTypeString",
   "alias": "ADM1_NAME",
   "length": 200
  }
 ],
 "features": [
  {
   "attributes": {
    "COUNTRY_CD": "AF",
    "ADM1": "01",
    "ADM1_NAME": "Badakhshān"
   }
  }
  , ...
 ]  
}  
  
*/



// These classes facilitate Gson.
class GeonetAdm1Response {
    String displayFieldName;

    List<Field> fields;
    List<Feature> features;
    
    public String toString() { return "GeonetResponse l:" + ((features != null) ? features.size() : 0); }
}

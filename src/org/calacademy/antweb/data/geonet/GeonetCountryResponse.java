package org.calacademy.antweb.data.geonet;

import java.util.*;

import org.calacademy.antweb.data.*;


/*
http://geonames.nga.mil/arcgis/rest/services/Research/GeoNames/MapServer/1/query?where=COUNTRY_CD+is+not+NULL&text=&objectIds=&time=&geometry=&geometryType=esriGeometryEnvelope&inSR=&spatialRel=esriSpatialRelIntersects&relationParam=&outFields=COUNTRY_CD%2C+COUNTRY_NM&returnGeometry=true&returnTrueCurves=false&maxAllowableOffset=&geometryPrecision=&outSR=&returnIdsOnly=false&returnCountOnly=false&orderByFields=&groupByFieldsForStatistics=&outStatistics=&returnZ=false&returnM=false&gdbVersion=&returnDistinctValues=false&resultOffset=&resultRecordCount=

{
 "displayFieldName": "COUNTRY_CD",
 "fieldAliases": {
  "COUNTRY_CD": "COUNTRY_CD",
  "COUNTRY_NM": "COUNTRY_NM"
 },
 "fields": [
  {
   "name": "COUNTRY_CD",
   "type": "esriFieldTypeString",
   "alias": "COUNTRY_CD",
   "length": 2
  },
  {
   "name": "COUNTRY_NM",
   "type": "esriFieldTypeString",
   "alias": "COUNTRY_NM",
   "length": 50
  }
 ],
 "features": [
  {
   "attributes": {
    "COUNTRY_CD": "AF",
    "COUNTRY_NM": "Afghanistan"
   }
  }
  , ...
 ]
}  
  
*/



// These classes facilitate Gson.
class GeonetCountryResponse {
    String displayFieldName;
    List<Field> fields;
    List<Feature> features;
    
    public String toString() { return "GeonetResponse l:" + ((features != null) ? features.size() : 0); }
}
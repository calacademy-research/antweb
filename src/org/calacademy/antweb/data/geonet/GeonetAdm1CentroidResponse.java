package org.calacademy.antweb.data.geonet;


import java.util.*;


/*
? http://geonames.nga.mil/arcgis/rest/services/Research/GeoNames/MapServer/2/query?where=COUNTRY_CD+is+not+NULL&text=&objectIds=&time=&geometry=&geometryType=esriGeometryEnvelope&inSR=&spatialRel=esriSpatialRelIntersects&relationParam=&outFields=COUNTRY_CD%2C+ADM1%2C+ADM1_NAME&returnGeometry=false&returnTrueCurves=false&maxAllowableOffset=&geometryPrecision=&outSR=&returnIdsOnly=false&returnCountOnly=false&orderByFields=&groupByFieldsForStatistics=&outStatistics=&returnZ=false&returnM=false&gdbVersion=&returnDistinctValues=false&resultOffset=&resultRecordCount=
 

FEATURE_COUNTRY_CODE: BA
FEATURE_COUNTRY_NAME: Bahrain
NAME: Al Muḩāfaz̧ah ash Shamālīyah
PRIMARY_ADMIN_DIVISION: Ash Shamālīyah
Point:
X: 50.4875 
Y: 26.175   
  
  
{
 "displayFieldName": "NAME",
 "fieldAliases": {
  "FEATURE_COUNTRY_CODE": "FEATURE_COUNTRY_CODE",
  "FEATURE_COUNTRY_NAME": "FEATURE_COUNTRY_NAME",
  "NAME": "NAME",
  "PRIMARY_ADMIN_DIVISION": "PRIMARY_ADMIN_DIVISION"
 },
 "geometryType": "esriGeometryPoint",
 "spatialReference": {
  "wkid": 4326,
  "latestWkid": 4326
 },
 "fields": [
  {
   "name": "FEATURE_COUNTRY_CODE",
   "type": "esriFieldTypeString",
   "alias": "FEATURE_COUNTRY_CODE",
   "length": 255
  },
  {
   "name": "FEATURE_COUNTRY_NAME",
   "type": "esriFieldTypeString",
   "alias": "FEATURE_COUNTRY_NAME",
   "length": 2000
  },
  {
   "name": "NAME",
   "type": "esriFieldTypeString",
   "alias": "NAME",
   "length": 500
  },
  {
   "name": "PRIMARY_ADMIN_DIVISION",
   "type": "esriFieldTypeString",
   "alias": "PRIMARY_ADMIN_DIVISION",
   "length": 200
  }
 ],
 "features": [
  {
   "attributes": {
    "FEATURE_COUNTRY_CODE": "BA",
    "FEATURE_COUNTRY_NAME": "Bahrain",
    "NAME": "Al Muḩāfaz\u0327ah al Janūbīyah",
    "PRIMARY_ADMIN_DIVISION": "Al Janūbīyah"
   },
   "geometry": {
    "x": 50.55,
    "y": 26.0
   }
  },  
  
*/



// These classes facilitate Gson.
class GeonetAdm1CentroidResponse {
    String displayFieldName;

    List<Field> fields;
    List<CentroidFeature> features;
    
    public String toString() { return "GeonetResponse l:" + (features != null ? features.size() : 0); }
}

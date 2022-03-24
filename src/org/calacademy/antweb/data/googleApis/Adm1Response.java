package org.calacademy.antweb.data.googleApis;

import java.util.*;
//import com.google.maps.model.AddressComponents;


// These classes facilitate Gson.
class Adm1Response {

    public List<Result> results;
    
    public String toString() { return "Adm1Response l:" + (results != null ? results.size() : 0); }
}

class Result {
  public AddressComponents[] address_components;
  
  public Geometry geometry;
  
  public String toString() { return "Result"; }
}

class AddressComponents {
  public String long_name;
  public String short_name;
  public AddressComponentType[] types;
}

// We included the googleapis AddressComponentType;

class Geometry {
  public Bounds bounds;
  public Location location;
  
  public String toString() { return "Geometry bounds:" + bounds + " location:" + location; }
}

class Bounds {  
  public LatLng northeast;
  public LatLng southwest;
  
  public String toString() { return "Geometry northeast:" + northeast + " southwest:" + southwest; }
}

class Location {  
  public String lat;
  public String lng;
  
  public String toString() { return "location lat:" + lat + " lng:" + lng; }
}

class LatLng {
  public String lat;
  public String lng;
  
  public String toString() { return "latLng lat:" + lat + " lng:" + lng; }
}


/*
{
   "results" : [
      {
         "address_components" : [
            {
               "long_name" : "Yolo",
               "short_name" : "Yolo",
               "types" : [ "locality", "political" ]
            },
            {
               "long_name" : "Yolo County",
               "short_name" : "Yolo County",
               "types" : [ "administrative_area_level_2", "political" ]
            },
            {
               "long_name" : "California",
               "short_name" : "CA",
               "types" : [ "administrative_area_level_1", "political" ]
            },
            {
               "long_name" : "United States",
               "short_name" : "US",
               "types" : [ "country", "political" ]
            }
         ],
         "formatted_address" : "Yolo, CA, USA",
         "geometry" : {
            "bounds" : {
               "northeast" : {
                  "lat" : 38.750153,
                  "lng" : -121.7968741
               },
               "southwest" : {
                  "lat" : 38.7269609,
                  "lng" : -121.8211591
               }
            },
            "location" : {
               "lat" : 38.732968,
               "lng" : -121.8072828
            },
            "location_type" : "APPROXIMATE",
            "viewport" : {
               "northeast" : {
                  "lat" : 38.750153,
                  "lng" : -121.7968741
               },
               "southwest" : {
                  "lat" : 38.7269609,
                  "lng" : -121.8211591
               }
            }
         },
         "place_id" : "ChIJyxxcCGDchIAR4-skTfm7Na0",
         "types" : [ "locality", "political" ]
      }
   ],
   "status" : "OK"
}
*/
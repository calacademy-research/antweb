package org.calacademy.antweb.data.geonet;

class CentroidFeature {
  CentroidAttributes attributes;
  Geometry geometry;

  public String toString() { 
    return attributes.toString() + " " + geometry.toString();
  }
}

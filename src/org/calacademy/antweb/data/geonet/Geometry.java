package org.calacademy.antweb.data.geonet;

class Geometry {
  String x;
  String y;

  public String getX() { return x; }

  public String getY() { return y; }

  public String toString() {
    String str = "";
    if (getX() != null && getY() != null) str += getX() + ", " + getY();
    return str;
  }
}

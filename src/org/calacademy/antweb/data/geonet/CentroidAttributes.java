package org.calacademy.antweb.data.geonet;

/*
    "FEATURE_COUNTRY_CODE": "BA",
    "FEATURE_COUNTRY_NAME": "Bahrain",
    "NAME": "Al Muḩāfaz\u0327ah al Janūbīyah",
    "PRIMARY_ADMIN_DIVISION": "Al Janūbīyah"
*/

class CentroidAttributes {
  String FEATURE_COUNTRY_CODE;
  String FEATURE_COUNTRY_NAME;
  String NAME;
  String PRIMARY_ADMIN_DIVISION;

  public String getCountryCode() { return FEATURE_COUNTRY_CODE; }

  public String getCountryName() { return FEATURE_COUNTRY_NAME; }

  public String getName() { return NAME; }

  public String getPrimaryAdminDivision() { return PRIMARY_ADMIN_DIVISION; }

  public String toString() {
    String str = "countryCode:" + getCountryCode();
    if (getCountryName() != null) str += " countryName:" + getCountryName();
    if (getName() != null) str += " adm1Name:" + getName();
    if (getPrimaryAdminDivision() != null) str += " getPrimaryAdminDivision():" + getPrimaryAdminDivision();
    return str;
  }

}
package org.calacademy.antweb.data.geonet;

class Attributes {
  String COUNTRY_CD;
  String COUNTRY_NM;
  String ADM1;
  String ADM1_NAME;
  
  public String getCountryCode() { return COUNTRY_CD; }

  public String getCountryName() { return COUNTRY_NM; }

  public String getAdm1Code() { return ADM1; }
  
  public String getAdm1Name() { return ADM1_NAME; }
  
  public String toString() {
    String str = "countryCode:" + getCountryCode();
    if (getCountryName() != null) str += " countryName:" + getCountryName();
    if (getAdm1Code() != null) str += " adm1Code:" + getAdm1Code();
    if (getAdm1Name() != null) str += " adm1Name:" + getAdm1Name();
    return str;
  }

}
package org.calacademy.antweb.util;

public class AntwebDebug {

  // Master switch this must be true for this utility to work. Turn off by setting to false.
  public static boolean isDebug = true;

  public static String code = "usnm609585";  // for specimen, of course.

  public static String taxonName = "dolichoderinaektunaxia jucunda";


  public static boolean isDebugCode(String debugCode) {
    if (!isDebug) return false;

    if (code != null && code.equals(debugCode)) return true;
    return false;
  }

  public static boolean isDebugTaxon(String debugTaxonName) {
     if (!isDebug) return false;

    if (taxonName != null && taxonName.equals(debugTaxonName)) return true;
    return false;
  }
}
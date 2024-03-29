package org.calacademy.antweb.util;

/*
AntwebDebug is designed to be system-wide utility to turn on/off logging. This allows logging code to be inserted for
debugging that will not need to be removed/modified before deployment. It can be called like this:

AntwebDebug.isDebugTaxon(taxonName)

Could be extended to fetch code, taxonName or other object names from a property file.
Could be extended to allow for a set of codes, taxonNames, etc...
*/

public class AntwebDebug {

  // Master switch this must be true for this utility to work. Turn off by setting to false.
  public static final boolean isDebug = true;

  public static final String code = "usnm609585";  // for specimen, of course.

  public static final String taxonName = "dolichoderinaektunaxia jucunda";


  public static boolean isDebugCode(String debugCode) {
    if (!isDebug) return false;

      return code != null && code.equals(debugCode);
  }

  public static boolean isDebugTaxon(String debugTaxonName) {
     if (!isDebug) return false;

      return taxonName != null && taxonName.equals(debugTaxonName);
  }
}
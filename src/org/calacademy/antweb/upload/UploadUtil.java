package org.calacademy.antweb.upload;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.A;
import org.calacademy.antweb.util.AntwebUtil;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;

public class UploadUtil {

    private static final Log s_log = LogFactory.getLog(UploadUtil.class);

    // Characters a specimen code may contain (after lower-casing and space removal), e.g.
    // casent0005428, antweb1032442, blf1862(05)-16, casent0123456-d01, mcz:ent:00012345.
    private static final java.util.regex.Pattern SPECIMEN_CODE = java.util.regex.Pattern.compile("[a-z0-9._:/()#+-]+");

    /** True if the specimen code uses only the allowed characters (letters, digits and . _ : / ( ) # + -). */
    public static boolean isValidSpecimenCode(String code) {
        return code != null && SPECIMEN_CODE.matcher(code).matches();
    }

    /** The one form of a species epithet for a specimen identified only to genus (Brian Fisher, 2026-10-28). */
    public static final String INDET_EPITHET = "(indet)";

    /**
     * True when a species epithet says "not identified to species" (Brian Fisher, 2026-10-28): "indet", "undet" or
     * "(indet)", in any case, with or without a trailing period ("Indet", "indet.", "UNDET."). Blank and "sp." are
     * handled by the specimen upload itself (SpecimenUploadParse.parseLine).
     */
    public static boolean isIndetEpithet(String species) {
        if (species == null) return false;
        String s = species.trim().toLowerCase(java.util.Locale.ROOT);
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1).trim();
        return "indet".equals(s) || "undet".equals(s) || INDET_EPITHET.equals(s);
    }

    /**
     * There is one form for a specimen identified only to genus: "Pheidole (indet)", in parentheses, so it sorts first
     * and is never read as a species epithet "indet" (Brian Fisher, 2026-10-28). Any isIndetEpithet value becomes
     * "(indet)"; anything else is returned unchanged.
     */
    public static String normalizeIndetEpithet(String species) {
        return isIndetEpithet(species) ? INDET_EPITHET : species;
    }

    static String getFirstLine(String fileName, String encoding) {
        String theLine = "";

        try (BufferedReader in =
                    new BufferedReader(new InputStreamReader(new FileInputStream(fileName), encoding))) {

            // parse the header (an empty file has none)
            theLine = in.readLine();
            theLine = theLine == null ? "" : theLine.toLowerCase();
        } catch (IOException e) {
            s_log.error("getFirstLine(" + fileName + ", " + encoding + ") e: " + e);
            //AntwebUtil.errorStackTrace(e);
        }
        return theLine;
    }

    static String convertDate(String startDate) {

        String newDate = "";
        try {
            SimpleDateFormat oldFormat = new SimpleDateFormat("dd MMM yyyy", java.util.Locale.ENGLISH);   // English month names, whatever the server's locale
            SimpleDateFormat newFormat = new SimpleDateFormat("yyyy-MM-dd");
            Date date = oldFormat.parse(startDate);
            newDate = newFormat.format(date);
        } catch (ParseException pe) {
            s_log.error("convertDate() startDate:" + startDate + " e:" + pe);
        }
        return newDate;
    }


    public static String makeName(Hashtable item) {

        StringBuilder sb = new StringBuilder(24);

        if (validNameKey("taxon_name", item)) {
            if ("formicidae".equals(item.get("taxon_name")))
                return "formicidae";
        }

        if (validNameKey("subfamily", item)) {
            if (item.get("subfamily").equals("formicidae")) {
                sb.append("formicidae");
            } else {
                sb.append((String) item.get("subfamily"));
            }
        } else return null;

        if (validNameKey("genus", item)) {
            sb.append((String) item.get("genus"));
        }
        //        if (validNameKey("subgenus", item)) {
        //            sb.append(" (" + (String) item.get("subgenus") + ")");
        //        } else if (validNameKey("speciesgroup", item)) {
        //            sb.append(" (" + (String) item.get("speciesgroup") + ")");
        //        }
        if (validNameKey("species", item)) {
            sb.append(" " + item.get("species"));
        }

        if (validNameKey("subspecies", item)) {
            sb.append(" " + item.get("subspecies"));
        }

        //A.log("makeName() name:"+ sb.toString());  

        return sb.toString();
    }

    static boolean validNameKey(String key, Hashtable item) {
        boolean valid = false;
        if (item.containsKey(key)) {
            String keyVal = (String) item.get(key);
            if (!keyVal.equals("null") && !keyVal.equals("")) {
                valid = true;
            }
        }
        return valid;
    }

/*
    public static String generateKey(String text) {
        String asciiSum = AntwebUtil.getAsciiSum(text);
        return asciiSum;
    }
*/


    // To be deprecated. Generate a temporary code if we don't like the existing one.
// Use genKey for locality and collection.
    public static String cleanCode(String code) {
      /* This method is useful for constructing codes of suitable quality for Antweb.
         Used for locality and collection when the entered value is insufficient */
    
      String cleanCode = code;
      if (cleanCode != null) {
        cleanCode = quotesToDash(cleanCode);
        if (cleanCode.contains("%")) cleanCode = Formatter.replace(cleanCode, "%", "");
        if (cleanCode.contains("#")) {
           cleanCode = Formatter.replace(cleanCode, "#", "");
           //A.log("cleanCode() code:" + code + " cleanCode:" + cleanCode);
        }

        if (cleanCode.contains("'")) cleanCode = Formatter.replace(cleanCode, "'", "");
        if (cleanCode.contains("'")) cleanCode = Formatter.replace(cleanCode, "'", ""); // same?
        if (cleanCode.contains(" ")) cleanCode = Formatter.replace(cleanCode, " ", "");
        //if (!Formatter.hasApha(code)) { }
      }

      //if (!cleanCode.equals(code)) A.log("cleanCode() code:" + code + " cleanCode:" + cleanCode);
      return cleanCode;
    }

    /**
     * A double quote in a locality or collection code becomes "-" (two in a row also become one "-"): Brian Fisher,
     * 2026-10-23. Spreadsheet exports double a quote ("" for "), so the same place arrived as two codes (KM55""805 and
     * KM55"805); both are now KM55-805. The upload report lists every code changed this way (MessageMgr.codeQuoteReplaced).
     */
    public static String quotesToDash(String code) {
        if (code == null || !code.contains("\"")) return code;
        return code.replace("\"\"", "-").replace("\"", "-");
    }

    public static String createCode() {
      return createCode(null);
    }
    public static String createCode(String code) {
        String cleanCode = null;
        String prefix = "tc";
        if (cleanCode == null) {
            cleanCode = prefix + AntwebUtil.getRandomNumber();
        } else {
            String tempCode = prefix + code;
            if (tempCode.length() == 4) cleanCode = prefix + "0" + cleanCode;
            if (tempCode.length() == 3) cleanCode = prefix + "00" + cleanCode;
        }
        return cleanCode;
    }

    public static String cleanHtml(String str) {
      int x = 0;
      //if (AntwebProps.isDevMode()) x = 1/0;
      if (str == null) return null;
      String newStr = null;
      try {
		newStr = str.substring(0, str.indexOf("<i>")) + "et al." + str.substring(str.indexOf("</i>") + 4);
		//A.log("cleanHtml() str:" + str + " newStr:" + newStr);
      } catch (StringIndexOutOfBoundsException e) {
        return str;
      }
      return newStr;
    }
   
}



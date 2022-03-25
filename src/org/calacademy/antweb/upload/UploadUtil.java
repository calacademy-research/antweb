package org.calacademy.antweb.upload;

import java.io.*;
import java.util.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;

public class UploadUtil {

    private static Log s_log = LogFactory.getLog(UploadUtil.class); 
            
    static String getFirstLine(String fileName, String encoding) {
        String theLine = "";
        
            try {
                BufferedReader in =    
                    new BufferedReader(new InputStreamReader(new FileInputStream(fileName), encoding));

                // parse the header
                theLine = in.readLine();
                theLine = theLine.toLowerCase();
            } catch (IOException e) {
                s_log.error("getFirstLine(" + fileName + ", " + encoding + ") e: " + e);
                //AntwebUtil.errorStackTrace(e);
            }
        return theLine;
    }    
    
    static String convertDate(String startDate) {
        
        String newDate = "";
        try {
            SimpleDateFormat oldFormat = new SimpleDateFormat("dd MMM yyyy");
            SimpleDateFormat newFormat = new SimpleDateFormat("yyyy-MM-dd");
            Date date = oldFormat.parse(startDate);
            newDate = newFormat.format(date);
        } catch(ParseException pe) {
            s_log.error("convertDate() startDate:" + startDate + " e:" + pe);
        }
        return newDate;
    }
    
    
    public static String makeName(Hashtable item) {

        StringBuffer sb = new StringBuffer();

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
   

    public static String cleanCode(String code) {
      /* This method is useful for constructing codes of suitable quality for Antweb.
         Used for locality and collection when the entered value is insufficient */
    
      String cleanCode = code;
      if (cleanCode != null) {
        if (cleanCode.contains("%")) cleanCode = Formatter.replace(cleanCode, "%", "");
        if (cleanCode.contains("#")) {
           cleanCode = Formatter.replace(cleanCode, "#", "");
           //A.log("cleanCode() code:" + code + " cleanCode:" + cleanCode);
        }
        if (cleanCode.contains("\'")) cleanCode = Formatter.replace(cleanCode, "\'", "");
        if (cleanCode.contains(" ")) cleanCode = Formatter.replace(cleanCode, " ", "");
        //if (!Formatter.hasApha(code)) { }
      }
      if (cleanCode == null || cleanCode.length() < 5) {
        // Not a good code. Make a new and better one.
        String prefix = "tc";
	
        if (cleanCode == null) {
          cleanCode = prefix + AntwebUtil.getRandomNumber();        
        } else {
          String tempCode = prefix + code;
          if (tempCode.length() == 4) cleanCode = prefix + "0" + cleanCode;
          if (tempCode.length() == 3) cleanCode = prefix + "00" + cleanCode;
        } 
      }

      //if (!cleanCode.equals(code)) A.log("cleanCode() code:" + code + " cleanCode:" + cleanCode);
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



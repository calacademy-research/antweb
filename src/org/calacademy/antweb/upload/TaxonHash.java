package org.calacademy.antweb.upload;

import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public final class TaxonHash extends Hashtable<String, Object> {

    private static Log s_log = LogFactory.getLog(TaxonHash.class);

    public static String INFO = "info";
    public static String WARN = "warn";

    public TaxonHash() {
    }
 
    public String logLevel = null;
    
    public void setLogLevel(String logLevel) {
      this.logLevel = logLevel;
    }
    
    public String getLogLevel() {
      return logLevel;
    }      

    public void log(String location) {
      if (INFO.equals(logLevel)) {
        s_log.info(location + " " + toString());
      }
      if (WARN.equals(logLevel)) {
        s_log.warn(location + " " + toString());
      } 
    }
    
    public void log(String level, String location) {
      setLogLevel(level);
      log(location);
    }

    public String toString() {
      String taxonName = (String) get("taxon_name");
      String status = (String) get("status");
      String currentValidRank = (String) get("current_valid_rank");
      
      return "taxonHash:" + super.toString() + " taxonName:" + taxonName  
        + " currentValidRank:" + currentValidRank + " status:" + status;            

    
    }

}

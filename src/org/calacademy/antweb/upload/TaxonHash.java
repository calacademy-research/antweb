package org.calacademy.antweb.upload;

import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public final class TaxonHash extends Hashtable<String, Object> {

    private static final Log s_log = LogFactory.getLog(TaxonHash.class);

    public static final String INFO = "info";
    public static final String WARN = "warn";

    public TaxonHash() {
    }
 
    public String logLevel;
    
    public void setLogLevel(String logLevel) {
      this.logLevel = logLevel;
    }
    
    public String getLogLevel() {
      return logLevel;
    }      

    public void log(String location) {
      if (INFO.equals(logLevel)) {
        s_log.info(location + " " + this);
      }
      if (WARN.equals(logLevel)) {
        s_log.warn(location + " " + this);
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

package org.calacademy.antweb.upload;

import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

class TaxonQueryHashMap extends HashMap {
  
  private static final Log s_log = LogFactory.getLog(TaxonQueryHashMap.class);
      
  private String source = null;
  
  TaxonQueryHashMap() {
    super();
  }
  TaxonQueryHashMap(String source) {
    super();
    setSource(source);
  }

  public void setSource(String source) {
    this.source = source;
  }
  
  public void put(String key, String value) {
    if ("worldants".equals(source) || "skip".equals(source)) return;

    if (size() > 3000) {
      //A.ilog("TaxonQueryHashMap.put() very large Map size. size:" + size());
      return;
    }
        
    super.put(key, value);
  }
  
  public boolean containsKey(String taxonName) {
    if ("worldants".equals(source) || "skip".equals(source)) return false;
    
    return super.containsKey(taxonName);  
  }    
}

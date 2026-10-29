package org.calacademy.antweb.upload;

import java.util.*;

/**
 * Counts, per title, the description records an AntCat (WorldAnts) or species-list load saves
 * (AntwebUpload.saveDescription); only its size is used, in a progress log line of AntwebUpload.saveTaxon.
 * Internal to the upload package: no page and no table of its own.
 */
class DescCounter extends HashMap {


/** 
  * This class counts Descriptions  
  */
  
     void register(String title) {   // was userId
         Object o = get(title);
         if (o == null) {
             put(title, 1);
         } else {
             Integer i = (Integer) o;
             put(title, i + 1);
         }
     }
         
     int reportTitle(String title) {
         Object o = get(title);
         if (o == null) {
             return 0;
         } else {
             Integer i= (Integer) o;
             return i;
         }
     }

     String report() {
        String report = "";
        Iterator<String> iter = keySet().iterator(); 
        int i = 0;
        while (iter.hasNext()) {
          String title = iter.next();
          Integer count = (Integer) get(title);
          if (i > 0) report += ", ";
          report += title + ":" + count.toString();
          ++i;          
        }
        return report;
     }
}
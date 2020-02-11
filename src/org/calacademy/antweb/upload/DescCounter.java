package org.calacademy.antweb.upload;

import java.util.*;

class DescCounter extends HashMap {


/** 
  * This class counts Descriptions  
  */
  
     void register(String title) {   // was userId
         Object o = get(title);
         if (o == null) {
             put(title, new Integer(1));
         } else {
             Integer i = (Integer) o;
             put(title, new Integer(i.intValue() + 1));
         }
     }
         
     int reportTitle(String title) {
         Object o = get(title);
         if (o == null) {
             return 0;
         } else {
             Integer i= (Integer) o;
             return i.intValue();
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
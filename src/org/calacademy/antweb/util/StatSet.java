package org.calacademy.antweb.util;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class StatSet {

    private static final Log s_log = LogFactory.getLog(StatSet.class);

    public StatSet(String name) {
      this.name = name;
    }
    
    private final String name;

    private String title1;
    private int value1 = 0;
    private String title2;
    private int value2 = 0;
    private String title3;
    private int value3 = 0;
    private String title4;
    private int value4 = 0;

    public void set1(String title, int value) {
      title1 = title;
      value1 = value;    
    }
    public void set2(String title, int value) {
      title2 = title;
      value2 = value;    
    }
    public void set3(String title, int value) {
      title3 = title;
      value3 = value;    
    }
    public void set4(String title, int value) {
      title4 = title;
      value4 = value;    
    }
    
    public String getHeader() {
      int col = 0;
      String retVal = "<table border=1><tr><th></th>"; // Start with empty column header (name).
      if (title1 != null) {
        retVal += "<th>" + title1 + "</th>";
        col = 1;
      }
      if (title2 != null) {
        retVal += "<th>" + title2 + "</th>";
        col = 2;
      }
      if (title3 != null) {
        retVal += "<th>" + title3 + "</th>";
        col = 3;
      }
      if (title4 != null) {
        retVal += "<th>" + title4 + "</th>";
        col = 4;
      }
      retVal += "</tr>";
      return retVal;      
    }
    
    public String toString() {
      String retVal = "";
      retVal += "<tr align=right><td>" + name + "</td><td>" + value1 + "</td><td>" + value2 + "</td><td>" + value3 + "</td><td>" + value4 + "</td></tr>"; 
      return retVal;
    }

    public String getFooter() {
      return "</table>";
    }
        
    public String log() {
        return "";  
    }

}
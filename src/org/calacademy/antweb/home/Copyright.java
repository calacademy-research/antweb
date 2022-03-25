package org.calacademy.antweb.home;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Copyright {

    private static final Log s_log = LogFactory.getLog(Copyright.class);

    private int id;
    private String copyright;
    private int year = 0;
      

    public Copyright() {
    }

    public Copyright(int id, String copyright, int year) {
      this.id = id;
      this.copyright = copyright;
      this.year = year;
    }
    
    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }

    public String getCopyright() {
        return copyright;
    }
    public void setCopyright(String copyright) {
        this.copyright = copyright;
    }

    public int getYear() {
        return year;
    }
    public void setYear(int year) {
        this.year = year;
    }
    
    public String toString() {
      return "copyright:" + getCopyright() + " id:" + getId() + " year:" + getYear(); 
    }    
    
}

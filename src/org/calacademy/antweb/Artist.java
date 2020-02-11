package org.calacademy.antweb;

import java.sql.*;
import java.util.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import org.calacademy.antweb.upload.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.geolocale.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
  
import java.util.Comparator;
    
public class Artist {

    private static Log s_log = LogFactory.getLog(Artist.class);
    
    private int id;
    private String name;
    private Date created;
    private boolean isActive;
    private int imageCount;
    private int specimenCount;
    private int curatorId;
    private int groupId;

    public Artist() {
    }
        
    public Artist(int id, String name) {
      setId(id);
      setName(name);
    }
    
    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    
    public int getCuratorId() {
        return curatorId;
    }
    public void setCuratorId(int curatorId) {
        this.curatorId = curatorId;
    }
    public Curator getCurator() {
      return LoginMgr.getCurator(getCuratorId());
    }
    
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String toString() {
      return name;
    }

    public Date getCreated() {
        return this.created;
    }
    public void setCreated(Date created) {
        this.created = created;
    }
    
    public boolean getIsActive() {
      return isActive;
    }
      
    public void setIsActive(boolean isActive) {
      this.isActive = isActive;
    }         
 
    public int getImageCount() {
      return imageCount;
    }
    public void setImageCount(int imageCount) {
      this.imageCount = imageCount;
    }
    public int getSpecimenCount() {
      return specimenCount;
    }
    public void setSpecimenCount(int specimenCount) {
      this.specimenCount = specimenCount;
    }
 
    public String getDisplayCounts() {
      String displayCounts = "" + Formatter.commaFormat(getImageCount());
      if (getSpecimenCount() > 0) {
        displayCounts += " (from " + Formatter.commaFormat(getSpecimenCount()) + " specimen)";
      }
      return displayCounts;
    }

    public String getLink() {
      String link = "";
      link += "<a href='" + AntwebProps.getDomainApp() + "/artist.do?id=" + getId() + "'>" + getName() + "</a>";
      return link;
    }     
    
    public static Comparator<Artist> ArtistNameComparator = new Comparator<Artist>() {

        public int compare(Artist a1, Artist a2) {
           String name1 = a1.getName().toUpperCase();
           String name2 = a2.getName().toUpperCase();

           //ascending order
           return name1.compareTo(name2);

           //descending order
           //return name2.compareTo(name1);
        }
    };

    /*Comparator for sorting the list by roll no*/
    public static Comparator<Artist> ArtistImageCountComparator = new Comparator<Artist>() {

        public int compare(Artist a1, Artist a2) {

           int count1 = a1.getImageCount();
           int count2 = a2.getImageCount();

           /*For ascending order*/
           //return count1 - count2;

           /*For descending order*/
           return   count2 - count1;
       }
    };    

    public static Comparator<Artist> ArtistSpecimenCountComparator = new Comparator<Artist>() {

        public int compare(Artist a1, Artist a2) {

           int count1 = a1.getSpecimenCount();
           int count2 = a2.getSpecimenCount();

           /*For ascending order*/
           //return count1 - count2;

           /*For descending order*/
           return   count2 - count1;
       }
    };     
}

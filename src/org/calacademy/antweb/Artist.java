package org.calacademy.antweb;

import java.util.Date;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
  
import java.util.Comparator;
import org.apache.commons.text.StringEscapeUtils;
    
public class Artist {

    private static final Log s_log = LogFactory.getLog(Artist.class);
    
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
      String safeName = StringEscapeUtils.escapeHtml4(getName());
      link += "<a href='" + AntwebProps.getDomainApp() + "/artist.do?id=" + getId() + "'>" + safeName + "</a>";
      return link;
    }     
    
    public static Comparator<Artist> ArtistNameComparator = (a1, a2) -> {
        String name1 = a1.getName().toUpperCase();
        String name2 = a2.getName().toUpperCase();

        //ascending order
        return name1.compareTo(name2);

        //descending order
        //return name2.compareTo(name1);
    };

    /*Comparator for sorting the list by roll no*/
    public static Comparator<Artist> ArtistImageCountComparator = (a1, a2) -> {

        int count1 = a1.getImageCount();
        int count2 = a2.getImageCount();

        /*For ascending order*/
        //return count1 - count2;

        /*For descending order*/
        return count2 - count1;
    };

    public static Comparator<Artist> ArtistSpecimenCountComparator = (a1, a2) -> {

        int count1 = a1.getSpecimenCount();
        int count2 = a2.getSpecimenCount();

        /*For ascending order*/
        //return count1 - count2;

        /*For descending order*/
        return count2 - count1;
    };
}

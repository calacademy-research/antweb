package org.calacademy.antweb;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Date;

import java.util.Comparator;

import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.upload.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public class Curator extends Login {

    private static Log s_log = LogFactory.getLog(Curator.class);

    private int specimenUploadCount = 0;
    private int imageUploadCount = 0;
      // The number of discrete image uploads.
    private int imagesUploadedCount = 0;
      // The total number of images uploaded.    
      
    private int descEditCount = 0;
    private Upload lastUpload = null;
    
    public String getLink() {
      return  "<a href='" + AntwebProps.getDomainApp() + "/curator.do?id=" + getId() + "'>" + getDisplayName() + "</a>";
    }
    
    public int getSpecimenUploadCount() {
      return specimenUploadCount;    
    }
    public void setSpecimenUploadCount(int count) {
      specimenUploadCount = count;
    }   

    public int getImageUploadCount() {
      return imageUploadCount;    
    }
    public void setImageUploadCount(int count) {
      imageUploadCount = count;
    }   
    public int getImagesUploadedCount() {
      return imagesUploadedCount;    
    }
    public void setImagesUploadedCount(int count) {
      imagesUploadedCount = count;
    }   
    
    public int getDescEditCount() {
      return descEditCount;    
    }
    public void setDescEditCount(int count) {
      descEditCount = count;
    }   

    public Upload getLastUpload() {
      return lastUpload;
    }
    public void setLastUpload(Upload upload) {
      lastUpload = upload;
    }  
    

    public static Comparator<Curator> CuratorNameComparator = new Comparator<Curator>() {

        public int compare(Curator a1, Curator a2) {
           String name1 = a1.getDisplayName().toUpperCase();
           String name2 = a2.getDisplayName().toUpperCase();

           //ascending order
           return name1.compareTo(name2);

           //descending order
           //return name2.compareTo(name1);
        }
    };
    
    public static Comparator<Curator> CuratorGroupNameComparator = new Comparator<Curator>() {

        public int compare(Curator a1, Curator a2) {
           String name1 = a1.getGroup().getName().toUpperCase();
           String name2 = a2.getGroup().getName().toUpperCase();

           //ascending order
           return name1.compareTo(name2);

           //descending order
           //return name2.compareTo(name1);
        }
    };
    
    public static Comparator<Curator> CuratorDescEditComparator = new Comparator<Curator>() {

        public int compare(Curator a1, Curator a2) {

           int count1 = a1.getDescEditCount();
           int count2 = a2.getDescEditCount();

           /*For ascending order*/
           //return count1 - count2;

           /*For descending order*/
           return   count2 - count1;
       }
    };    

    public static Comparator<Curator> CuratorSpecimenUploadComparator = new Comparator<Curator>() {

        public int compare(Curator a1, Curator a2) {

           int count1 = a1.getSpecimenUploadCount();
           int count2 = a2.getSpecimenUploadCount();

           /*For ascending order*/
           //return count1 - count2;

           /*For descending order*/
           return   count2 - count1;
       }
    };      
    
}

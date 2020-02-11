package org.calacademy.antweb.imageUploader;

import java.util.*;
import java.io.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

import java.io.*;
import org.apache.commons.fileupload.*;
import org.apache.commons.io.output.*;

import org.im4java.core.*;
import org.im4java.process.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class ImageUpload {

    private static Log s_log = LogFactory.getLog(ImageUpload.class);

    public static String LICENSE = "Attribution-ShareAlike (BY-SA) Creative Commons License and GNU Free Documentation License (GFDL)";

    private int id = 0;
    private int curatorId = 0;
    private int groupId = 0;
    private Date created = null;
    private int artistId = 0;
    private Copyright copyright = null;
    private String license = null;

    private int imageCount = 0;
    private boolean complete = false;
    
    private ArrayList<ImageUploaded> images = null; 


    public ImageUpload() { 
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
    
    public int getGroupId() {
      return groupId;
    }
    public void setGroupId(int groupId) {
      this.groupId = groupId;  
    }
    
    public Date getCreated() {
      return created;
    }  
    public void setCreated(Date created) {
      this.created = created;
    } 

    public int getArtistId() {
      return artistId;
    }
    public void setArtistId(int artistId) {
      this.artistId = artistId;
    }   

    public Copyright getCopyright() {
      return copyright;
    }
    public void setCopyright(Copyright copyright) {
      this.copyright = copyright;  
    }

    public String getLicense() {
      return license;
    }
    public void setLicense(String license) {
      this.license = license;
    }  

    public ArrayList<ImageUploaded> getImages() {
      return images;
    }
    public void setImages(ArrayList<ImageUploaded> images) {
      this.images = images;
    }

    public void setImageCount(int imageCount) {
      this.imageCount = imageCount;   
    }
    public int getImageCount() {
      return imageCount;
    }

    public boolean getIsComplete() {
      return complete;
    }
    public void setIsComplete(boolean complete) {
      this.complete = complete;
    }
    
    public String toString() {
      return "id:" + getId() + " created:" + getCreated() + " group:" + getGroupId() + " curator:" + getCuratorId() + " artist:" + getArtistId();
    } 
}



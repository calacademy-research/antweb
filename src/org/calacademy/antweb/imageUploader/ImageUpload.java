package org.calacademy.antweb.imageUploader;

import java.util.*;

import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ImageUpload {

    private static final Log s_log = LogFactory.getLog(ImageUpload.class);

    public static final String LICENSE = "Attribution-ShareAlike (BY-SA) Creative Commons License and GNU Free Documentation License (GFDL)";

    private int id = 0;
    private int curatorId = 0;
    private int groupId = 0;
    private Date created;
    private int artistId = 0;
    private Copyright copyright;
    private String license;

    private int imageCount = 0;
    private boolean complete = false;
    
    private ArrayList<ImageUploaded> images;


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



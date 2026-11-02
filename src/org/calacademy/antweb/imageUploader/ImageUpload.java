package org.calacademy.antweb.imageUploader;

import java.util.*;

import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * One image upload (a row of table image_upload): curator, group, artist, copyright, license, image count and
 * complete, with its saved files (ImageUploaded, table image_uploaded) and the files not saved (table
 * image_upload_refused). Written and read by ImageUploadDb for the Image Uploader (imageUploadBatch.do), the upload
 * report (imageUploadReport.do) and the list of image uploads (listImageUploads.do). Docs:
 * docs/flows/image-upload.md, docs/data-model.md (image_upload).
 */
public class ImageUpload {

    private static final Log s_log = LogFactory.getLog(ImageUpload.class);

    /** Recorded in image_upload.license for each new upload (CC BY 4.0 since 2026-10-29; earlier rows keep their text). */
    public static final String LICENSE = org.calacademy.antweb.util.License.LINE;

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
    private List<ImageUploadRefusedDb.Refused> refused = new ArrayList<>();   // files not saved (image_upload_refused)


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

    /** Files of this upload that were not saved, and why (filled by ImageUploadReport). */
    public List<ImageUploadRefusedDb.Refused> getRefused() {
      return refused;
    }
    public void setRefused(List<ImageUploadRefusedDb.Refused> refused) {
      this.refused = refused == null ? new ArrayList<>() : refused;
    }
}

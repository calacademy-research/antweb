package org.calacademy.antweb.upload;

import java.util.Date;
import java.text.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Upload {

    private static Log s_log = LogFactory.getLog(Upload.class);

    private int id;
    private int uploadId;
    private int loginId;
    private String groupName;
    private int groupId;
    private String logFileName;
    private Date created;
    
    private int specimens;
    private int collections;
    private int localities;
    private int subfamilies;
    private int genera;
    private int species;
    private int ungeoreferenced;
    private int flagged;
    
    public Upload() {
    }

    public int getId() {
      return this.id;
    }
    public void setId(int id) {
      this.id = id;
    }

    public int getUploadId() {
      return this.uploadId;
    }
    public void setUploadId(int uploadId) {
      this.uploadId = uploadId;
    }

    public int getLoginId() {
      return this.loginId;
    }
    public void setLoginId(int loginId) {
      this.loginId = loginId;
    }

    public String getGroupName() {
      return this.groupName;
    }
    public void setGroupName(String groupName) {
      this.groupName = groupName;
    }
    
    public int getGroupId() {
      return this.groupId;
    }
    public void setGroupId(int groupId) {
      this.groupId = groupId;
    }

    public String getUploadDir() {
        String uploadDir = null;
        // Nov 11, 2019 we reimplemented the directory names.
        SimpleDateFormat sdfo = new SimpleDateFormat("yyyyMMdd"); 
        Date d1 = null;
        try {
          d1 = sdfo.parse("20191111"); // Coming from the log file format, for convenience.
        } catch (ParseException e) {
          s_log.warn("ParseException e:" + e);
        }
        if (getCreated() != null && d1 != null && (getCreated().after(d1) || getCreated().equals(d1))) {
          if (getLogFileName().contains("pecimen")) uploadDir = "specimen";
          if (getLogFileName().contains("orldants")) uploadDir = "worldants";
        } else {
          uploadDir = "upload";
        }        
        //A.log("getUploadDir() uploadDir:" + uploadDir + " d1:" + d1 + " created:" + getCreated() + " compare:" + getCreated().after(d1));
        return uploadDir;
    }

    public String getLogFileName() {
      return this.logFileName;
    }
    public void setLogFileName(String logFileName) {
      this.logFileName = logFileName;
    }

    public Date getCreated() {
        return this.created;
    }
    public void setCreated(Date created) {
        this.created = created;
    }
    
    public String getCountsStr() {
      return "Specimens:" + getSpecimens()
        + " Collections:" + getCollections()
        + " Localities:" + getLocalities()
        + " Subfamilies:" + getSubfamilies()
        + " Genera:" + getGenera()
        + " Species:" + getSpecies()
        + " Ungeoreferenced:" + getUngeoreferenced()
        + " Flagged:" + getFlagged();
    }
    
    public int getSpecimens() {
      return specimens;
    }
    public void setSpecimens(int specimens) {
      this.specimens = specimens;
    }
    public int getCollections() {
      return collections;
    }
    public void setCollections(int collections) {
      this.collections = collections;
    }
    public int getLocalities() {
      return localities;
    }
    public void setLocalities(int localities) {
      this.localities = localities;
    }
    public int getSubfamilies() {
      return subfamilies;
    }
    public void setSubfamilies(int subfamilies) {
      this.subfamilies = subfamilies;
    }
    public int getGenera() {
      return genera;
    }
    public void setGenera(int genera) {
      this.genera = genera;
    }
    public int getSpecies() {
      return species;
    }
    public void setSpecies(int species) {
      this.species = species;
    }
    public int getUngeoreferenced() {
      return ungeoreferenced;
    }
    public void setUngeoreferenced(int ungeoreferenced) {
      this.ungeoreferenced = ungeoreferenced;
    }
    public int getFlagged() {
      return flagged;
    }    
    public void setFlagged(int flagged) {
      this.flagged = flagged;
    }

}

package org.calacademy.antweb;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Date;
import java.io.InputStream;


import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public class TeamMember {

    private static Log s_log = LogFactory.getLog(TeamMember.class);
        
    private int id;
    private String name;
    private String roleOrg;
    private String email;
    private String imgLoc;
    private String imgFileName;
    private int imgWidth;
    private int imgHeight;
    private String imgFileType;
    private String imgFileSize;
    private Blob imgFileBin;    
    private InputStream fileInputStream;
    
    private String text;
    private int section;
    private int rank;
    private boolean isPublished;
    private Date created;

    public TeamMember() {
        super();
    }
    
    public void init() {
        setName("");
        setRoleOrg("");
        setEmail("");
        setText("");
    }

    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getRoleOrg() {
        return roleOrg;
    }
    public void setRoleOrg(String roleOrg) {
        this.roleOrg = roleOrg;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getImgLoc() {
        return imgLoc;
    }
    public void setImgLoc(String name) {
        this.imgLoc = imgLoc;
    }
    
    public String getImgFileName() {
        return imgFileName;
    }
    public void setImgFileName(String name) {
        this.imgFileName = imgFileName;
    }    

    public int getImgWidth() {
      return imgWidth;
    }
    public void setImgWidth(int width) {
      this.imgWidth = width;
    }
    public int getImgHeight() {
      return imgHeight;
    }
    public void setImgHeight(int height) {
      this.imgHeight = height;
    }

    public String getImgFileType() {
        return imgFileType;
    }    
    public void setImgFileType(String imgFileType) {
        this.imgFileType = imgFileType;
    }

    public String getImgFileSize() {
        return imgFileSize;
    }
    public void setImgFileSize(String size) {
        this.imgFileSize = imgFileSize;
    }    
    
    public Blob getImgFileBin() {
        return imgFileBin;
    }
    public void setImgFileBin(Blob imgFileBin) {
        this.imgFileBin = imgFileBin;
    }
    
    public InputStream getImgFileInputStream() {
      return fileInputStream;
    }
    
    public void setImgFileInputStream(InputStream fileInputStream) {
      this.fileInputStream = fileInputStream;
    }
    
    public String getText() {
        return text;
    }
    public void setText(String text) {
        this.text = text;
    }
    
    public int getSection() {
        return section;
    }
    public void setSection(int section) {
        this.section = section;
    }
    
    public int getRank() {
        return rank;
    }
    public void setRank(int rank) {
        this.rank = rank;
    }
    
    public Boolean isPublished() {
        return this.isPublished;
    }
    public void setIsPublished(boolean isPublished) {
        this.isPublished = isPublished;
    }
    
    
    public Date getCreated() {
        return this.created;
    }
    public void setCreated(Date created) {
        this.created = created;
    }
    

    public String toString() {
      return "TeamMember id:" + id + " name:" + name + " email:" + email 
        + " isPublished:" + isPublished;
    }

         
     public String toDisplayString() {
         // Display name how the user likes.  If they enter a username, display it, otherwise email.
         if ((this.name != null) && (!this.name.equals(""))) {
           return this.name;
         } else {
           return this.email;
         }
     }

}

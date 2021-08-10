package org.calacademy.antweb.upload;

import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;    
    
public class UploadLine {

    private static Log s_log = LogFactory.getLog(UploadLine.class);

    private int id = 0;
    private String fileName = null;
	private int lineNum = 0;
	private int displayLineNum = 0;
	private int groupId = 0;
	private String line = null;
	private Date created = null;
	
  
    public int getId() {
      return this.id;
    }
    public void setId(int id) {
      this.id = id;
    }
    
    public String getFileName() {
	  return fileName;
    }
	public void setFileName(String fileName) {
		this.fileName = fileName;
    }

	public void setLineNum(int thisLineNum) {
	  lineNum = thisLineNum;
	}
	public int getLineNum() {
	  return lineNum;
	}

	public void setDisplayLineNum(int thisDisplayLineNum) {
	  displayLineNum = thisDisplayLineNum;
	}
	public int getDisplayLineNum() {
	  return displayLineNum;
	}
	
	public void setGroupId(int thisGroupId) {
	  groupId = thisGroupId;
	}
	public int getGroupId() {
	  return groupId;
	}
	
    public String getLine() {
	  return line;
    }
	public void setLine(String line) {
		this.line = line;
    }

    public java.util.Date getCreated() {
        return this.created;
    }
    public void setCreated(java.util.Date created) {
        this.created = created;
    }
    
}
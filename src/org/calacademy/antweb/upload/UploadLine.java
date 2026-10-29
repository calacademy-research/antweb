package org.calacademy.antweb.upload;

import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;    
    
/**
 * One line of a specimen file kept in table upload_line (file, line number in the file and as shown in the
 * report, group, text): the lines LineNumMgr found broken by an extra carriage return. Read by
 * UploadDb.getUploadLine for logMgr.do?action=uploadLine, the report's link to the line.
 */
public class UploadLine {

    private static final Log s_log = LogFactory.getLog(UploadLine.class);

    private int id = 0;
    private String fileName;
	private int lineNum = 0;
	private int displayLineNum = 0;
	private int groupId = 0;
	private String line;
	private Date created;
	
  
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

    public Date getCreated() {
        return this.created;
    }
    public void setCreated(Date created) {
        this.created = created;
    }
    
}
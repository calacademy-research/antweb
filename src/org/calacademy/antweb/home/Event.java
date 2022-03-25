package org.calacademy.antweb.home;

import java.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class Event {

    private static final Log s_log = LogFactory.getLog(Event.class);

// Should match with web/taxonPage-body.jsp.  Poor design.  Should be properties of Description.java.

    public static final String TAXON_PAGE_IMAGES = "images";
      // will match with the editField from web/common/taxonImageEditField.jsp
    public static final String TAXON_PAGE_VIDEOS = "videos";
      // will match with the editField from web/common/taxonVideoEditField.jsp
    public static final String TAXON_PAGE_OVERVIEW = "overview";

    private int id;
    private String operation;
    private int curatorId;
    private String name;
    private Date created;

    private String curator;  // This will be group - curator
      
    public Event() {
    }
      
    public Event(String operation, int curatorId, String name, Date created) {
        setOperation(operation);
        setCuratorId(curatorId);
        setName(name);
        setCreated(created);
    }

    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }

    public String getOperation() {
        return operation;
    }
    public void setOperation(String operation) {
        this.operation = operation;
    }

    public Date getCreated() {
        return this.created;
    }
    public void setCreated(Date created) {
        this.created = created;
    }
      
    public int getCuratorId() {
        return curatorId;
    }
    public void setCuratorId(int curatorId) {
        this.curatorId = curatorId;
    }
      
    public String getName() {
        return this.name;
    }
    public void setName(String name) {
        this.name = name;
    }
    
    public String toString(String createdStr) {
      return "operation:" + getOperation() + " curatorId:" + getCuratorId() + " name:" + getName() + " createdStr:" + createdStr; 
    }    
    
    public String toQueryString(String createdStr) {
      return "'" + getOperation() + "', " + getCuratorId() + ", '" + getName() + "', '" + createdStr + "'"; 
    }
}

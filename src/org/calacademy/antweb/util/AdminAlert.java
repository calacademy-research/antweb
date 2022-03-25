package org.calacademy.antweb.util;

import org.calacademy.antweb.util.*;

import java.sql.*;
//import java.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class AdminAlert {

    private static final Log s_log = LogFactory.getLog(AdminAlert.class);

    public AdminAlert() {
    }
    
    private int id = 0;
    private String alert;
    private Timestamp created;
    private boolean isAcknowledged = false;    

    public int getId() {
      return id;
    }
    public void setId(int id) {
      this.id = id;
    }
    
    public void setAlert(String alert) {
      this.alert = alert;
    }
    public String getAlert() {
      return alert;
    }
    
    public Timestamp getCreated() {
        return created;
    }
    public void setCreated(Timestamp created) {
        this.created = created;
    }    

    // Also see GeolocaleMgr.isIsland().
    public boolean isAcknowledged() {
        return getIsAcknowledged();
    }
    public boolean getIsAcknowledged() {
        return isAcknowledged;
    }
    public void setIsAcknowledged(boolean isAcknowledged) {
        this.isAcknowledged = isAcknowledged;
    } 
    
    public String toString() {
      return getAlert();    
    }
}
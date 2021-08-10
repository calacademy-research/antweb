package org.calacademy.antweb.curate.speciesList;

import java.util.*;
import java.io.Serializable;
import java.sql.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
        
import org.calacademy.antweb.util.*;

public class GeolocaleTaxonLog extends GeolocaleTaxon {

    private static Log s_log = LogFactory.getLog(GeolocaleTaxonLog.class);

    private int logId = 0;
    private Timestamp created;
    private int curatorId;
    //private String projectName;
    private int geolocaleId;
    private boolean isCurrent;
    
    private ArrayList<GeolocaleTaxonLogDetail> details = new ArrayList<>();

    public GeolocaleTaxonLog() {
       if (getDetails() == null) setDetails(new ArrayList<>());
    }
    
    public String toString() {
      return "GeolocaleTaxonLog logId:" + logId + " geolocaleId:" + getGeolocaleId() + " curateId:" + curatorId 
        + " created:" + created + " detail count:" + getDetails().size();
    }

    public int getLogId() {
      return logId;
    }
    public void setLogId(int logId) {
      this.logId = logId;
    }
    
    public Timestamp getCreated() {
      return created;
    }
    public void setCreated(Timestamp created) {
      this.created = created;
    }
    
    public int getCuratorId() {
      return curatorId;
    }
    public void setCuratorId(int curatorId) {
      this.curatorId = curatorId;
    }

    public boolean getIsCurrent() {
      return this.isCurrent;
    }
    public void setIsCurrent(boolean isCurrent) {
      this.isCurrent = isCurrent;
    }
        
    public int getGeolocaleId() {
      return this.geolocaleId;
    } 
    public void setGeolocaleId(int geolocaleId) {
      this.geolocaleId = geolocaleId;      
    }    
    
    // Better names.  convenience methods
    public ArrayList<GeolocaleTaxonLogDetail> getDetails() {
      return this.details;
    }
    public void setDetails(ArrayList<GeolocaleTaxonLogDetail> details) {
      this.details = details;
    }
    
    public String getHeading() {
  
      if (getLogId() == 0) return "Master List";
  
      String heading = "";
  
      if (getIsCurrent()) {
        heading += "<a href=\"" + AntwebProps.getDomainApp() + "/speciesListTool.do\"><font color=green>Current</font></a>";
      } else {
        heading += "Log Id:" + getLogId();      
      }

      heading += "<br>" + getCreated();
      int curatorId = getCuratorId();
      Login login = LoginMgr.getLogin(curatorId);
      String curatorName = "";
      if (login != null) curatorName = "(" + login.getName() + ")";
      heading += "<br>curator:" + getCuratorId() + curatorName;

      
      return heading;
    }   


}

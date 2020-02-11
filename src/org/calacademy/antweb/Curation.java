package org.calacademy.antweb;

import java.util.*;
import java.io.Serializable;
import java.sql.*;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.geolocale.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
        
import org.calacademy.antweb.util.*;

public class Curation extends GeolocaleTaxon {

    private static Log s_log = LogFactory.getLog(Curation.class);

/*
    private int geolocaleId;
    private String taxonName;
    private String source;
    private Timestamp created;
*/
    private int curatorId;

    public Curation() {
    }
    
    public void setGeolocaleTaxon(GeolocaleTaxon geolocaleTaxon) {
      setSource(geolocaleTaxon.getSource());
      setGeolocaleId(geolocaleTaxon.getGeolocaleId());
      setTaxonName(geolocaleTaxon.getTaxonName());
    }
    
/*    
    public int getGeolocaleId() {
      return geolocaleId;
    }
    public void setGeolocaleId(int geolocaleId) {
      this.geolocaleId = geolocaleId;
    }

    public String getTaxonName() {
      return taxonName;
    }
    public void setTaxonName(String taxonName) {
      this.taxonName = taxonName;
    }    
    
    public String getSource() {
      return source;
    }
    public void setSource(String source) {
      this.source = source;
    }
    
	public Timestamp getCreated() {
		return created;
	}
	public void setCreated(Timestamp created) {
		this.created = created;
	}
   */ 
    public int getCuratorId() {
      return curatorId;
    }
    public void setCuratorId(int curatorId) {
      this.curatorId = curatorId;
    }    
}

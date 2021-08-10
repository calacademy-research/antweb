package org.calacademy.antweb.curate.speciesList;

import org.apache.struts.action.*;
import javax.servlet.http.*;

public class SpeciesListHistoryForm extends ActionForm {

    protected String action;

    String speciesListName = "comorosants";
    int projLogId = 0;
    int geoLogId = 0;    
    //int curatorId = 0;
    //Date created = null;    

    public String getAction() {
	    return action;
    }
    public void setAction(String action) {
    	this.action = action;
    }

	public String getSpeciesListName() {
		return speciesListName;
	}
	public void setSpeciesListName(String speciesListName) {
		this.speciesListName = speciesListName;
	}

    public int getProjLogId() {
      return projLogId;
    } 
    public void setProjLogId(int projLogId) {
      this.projLogId = projLogId;
    }
    public int getGeoLogId() {
      return geoLogId;
    } 
    public void setGeoLogId(int geoLogId) {
      this.geoLogId = geoLogId;
    }
    /*
    public int getCuratorId() {
      return curatorId;
    }
    public void setCuratorId(int curatorId) {
      this.curatorId = curatorId;
    }    

    public Date getCreated() {
      return this.created;
    }
    public void setCreated(Date created) {
      this.created = created;
    }
*/

    public String toString() {
      return "SpeciesListHistoryForm projLogId:" + projLogId + " geoLogId:" + geoLogId + " speciesListName:" + speciesListName;
    }

	public void reset(ActionMapping mapping, HttpServletRequest request) {
	    super.reset(mapping, request);
	    
		speciesListName = null;
		projLogId = 0;
		geoLogId = 0;
		//curatorId = 0;
		//created = null;
	}

	public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
		ActionErrors errors = new ActionErrors();
		//if ((taxa == null) || (Arrays.asList(taxa).size() < 1))
		//	errors.add("rank", new ActionError("error.rank.required"));

		return errors;
	}

}



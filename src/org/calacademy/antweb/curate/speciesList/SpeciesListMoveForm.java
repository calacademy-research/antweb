package org.calacademy.antweb.curate.speciesList;

import org.apache.struts.action.*;
import javax.servlet.http.*;
import org.apache.struts.upload.FormFile;

public class SpeciesListMoveForm extends ActionForm {

    protected String action;

    protected String moveSubfamily;
    protected String moveGenus;
    protected String moveSpecies;    

    protected String toSubfamily;
    protected String toGenus;
    protected String toSpecies;    

    public String getAction() {
	    return action;
    }
    public void setAction(String action) {
    	this.action = action;
    }

    public String getMoveSubfamily() {
	    return moveSubfamily;
    }
    public void setMoveSubfamily(String moveSubfamily) {
    	this.moveSubfamily = moveSubfamily;
    }  
    public String getMoveGenus() {
	    return moveGenus;
    }
    public void setMoveGenus(String moveGenus) {
    	this.moveGenus = moveGenus;
    }  
    public String getMoveSpecies() {
	    return moveSpecies;
    }
    public void setMoveSpecies(String moveSpecies) {
    	this.moveSpecies = moveSpecies;
    }  

    public String getToSubfamily() {
	    return toSubfamily;
    }
    public void setToSubfamily(String toSubfamily) {
    	this.toSubfamily = toSubfamily;
    }  
    public String getToGenus() {
	    return toGenus;
    }
    public void setToGenus(String toGenus) {
    	this.toGenus = toGenus;
    }  
    public String getToSpecies() {
	    return toSpecies;
    }
    public void setToSpecies(String toSpecies) {
    	this.toSpecies = toSpecies;
    }  


	public void reset(ActionMapping mapping, HttpServletRequest request) {
		this.action = null;
	}

	public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
		ActionErrors errors = new ActionErrors();
		//if ((taxa == null) || (Arrays.asList(taxa).size() < 1))
		//	errors.add("rank", new ActionError("error.rank.required"));

		return errors;
	}

    
}



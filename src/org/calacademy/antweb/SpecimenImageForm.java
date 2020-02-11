package org.calacademy.antweb;


import javax.servlet.http.HttpServletRequest;
import org.apache.struts.action.ActionError;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.util.*;

public final class SpecimenImageForm extends ActionForm {

    private String imageId = null; // This is a usable alternative.  Primary key.
    private String code = null;    // This is the what we should use.
    private String name = null;    // This is what we did use.  Backwards compatible
    private String shot = null;
    private int number = 0;
    private String artist = null;
    private String action = null;
    
    public String getImageId() {
	    return (this.imageId);
    }

    public void setImageId(String imageId) {
        this.imageId = imageId;
    }
    
    public String getCode() {
	    return (this.code);
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
    	return (this.name);
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShot() {
		return shot;
	}

	public void setShot(String shot) {
		this.shot = shot;
	}

	public int getNumber() {
		return number;
	}

	public void setNumber(int number) {
		this.number = number;
	}

    public String getArtist() {
      return this.artist;
    }
    public void setArtist(String artist) {
      this.artist = artist;
    }

    public String getAction() {
      return this.action;
    }
    public void setAction(String action) {
      this.action = action;
    }
        
	public void reset(ActionMapping mapping, HttpServletRequest request) {
	    this.imageId = null;
        this.code = null;
        this.name = null;
        this.shot = null;
        this.number = 0;
        this.artist = null;
        this.action = null;
    }

    public ActionErrors validate(ActionMapping mapping,
    				HttpServletRequest request) {

        ActionErrors errors = new ActionErrors();
        if ((name == null) || (name.length() < 1))
          if ((code == null) || (code.length() < 1))
            if ((imageId == null) || (imageId.length() < 1))
              errors.add("name", new ActionError("error.name.required"));
              
        HttpUtil.blockFishingAttack(request, errors);
                      
        return errors;
    }
}

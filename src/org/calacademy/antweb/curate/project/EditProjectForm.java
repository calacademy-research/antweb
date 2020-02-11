package org.calacademy.antweb.curate.project;


import java.sql.Date;

import javax.servlet.http.HttpServletRequest;
import org.apache.struts.action.ActionError;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.upload.FormFile;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class EditProjectForm extends ActionForm {

    private static Log s_log = LogFactory.getLog(EditProjectForm.class);

/* check name - also make sure to add where clause to update */
	
	protected String projectName;
    protected String scope;
    private boolean isLive; 
	protected String title;
	protected String author;
	protected Date lastChanged;
	protected String contents;
	protected String specimenImage1;
	protected String specimenImage2;
	protected String specimenImage3;
	protected String specimenImage1Link;
	protected String specimenImage2Link;
	protected String specimenImage3Link;
	protected String mapImage;
	protected String authorBio;
	protected String authorImage;
	//protected String root;
	protected String locality;
	protected String coords;
	protected String extent;
	protected FormFile theFile;
	
	protected String action;

	private int geolocaleId;
	
	private String displayKey;
    
    public String getProjectName() {
	return (this.projectName);
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

	public String getAuthor() {
		return author;
	}
	public void setAuthor(String author) {
		this.author = author;
	}

	public String getAuthorBio() {
		return authorBio;
	}
	public void setAuthorBio(String authorBio) {
		this.authorBio = authorBio;
	}

	public String getAuthorImage() {
		return authorImage;
	}
	public void setAuthorImage(String authorPhoto) {
		this.authorImage = authorPhoto;
	}

	public String getScope() {
		return scope;
	}
	public void setScope(String scope) {
		this.scope = scope;
	}

    public boolean getIsLive() {
        return isLive;
    }
    public void setIsLive(boolean isLive) {
        this.isLive = isLive;
    }

	public String getContents() {
		return contents;
	}
	public void setContents(String contents) {
		this.contents = contents;
	}

	public String getCoords() {
		return coords;
	}
	public void setCoords(String coords) {
		this.coords = coords;
	}

	public String getExtent() {
		return extent;
	}
	public void setExtent(String extent) {
		this.extent = extent;
	}

	public Date getLastChanged() {
		return lastChanged;
	}
	public void setLastChanged(Date lastChanged) {
		this.lastChanged = lastChanged;
	}

	public String getLocality() {
		return locality;
	}
	public void setLocality(String locality) {
		this.locality = locality;
	}

	public String getMapImage() {
		return mapImage;
	}
	public void setMapImage(String mapImage) {
		this.mapImage = mapImage;
	}
/*
	public String getRoot() {
		return root;
	}
	public void setRoot(String root) {
		this.root = root;
	}
*/
	public String getSpecimenImage1() {
		return specimenImage1;
	}
	public void setSpecimenImage1(String specimenImage1) {
		this.specimenImage1 = specimenImage1;
	}

	public String getSpecimenImage1Link() {
		return specimenImage1Link;
	}
	public void setSpecimenImage1Link(String specimenImage1Link) {
		this.specimenImage1Link = specimenImage1Link;
	}

	public String getSpecimenImage2() {
		return specimenImage2;
	}
	public void setSpecimenImage2(String specimenImage2) {
		this.specimenImage2 = specimenImage2;
	}

	public String getSpecimenImage2Link() {
		return specimenImage2Link;
	}
	public void setSpecimenImage2Link(String specimenImage2Link) {
		this.specimenImage2Link = specimenImage2Link;
	}

	public String getSpecimenImage3() {
		return specimenImage3;
	}
	public void setSpecimenImage3(String specimenImage3) {
		this.specimenImage3 = specimenImage3;
	}

	public String getSpecimenImage3Link() {
		return specimenImage3Link;
	}
	public void setSpecimenImage3Link(String specimenImage3Link) {
		this.specimenImage3Link = specimenImage3Link;
	}

	public String getTitle() {
	  //s_log.warn("getTitle() title:" + title);
		return title;
	}
	public void setTitle(String title) {
	  //s_log.warn("setTitle() title:" + title);
		this.title = title;
	}

	public FormFile getTheFile() {
		return theFile;
	}
	public void setTheFile(FormFile theFile) {
		this.theFile = theFile;
	}

    protected boolean speciesListMappable;
    
	public boolean getSpeciesListMappable() {
       //s_log.warn("getSpeciesListMappable() " + speciesListMappable);
	    return speciesListMappable;
		//return (new Boolean(speciesListMappable)).toString();
	}
	public void setSpeciesListMappable(boolean speciesListMappable) {
       //s_log.warn("setSpeciesListMappable() " + speciesListMappable);
		this.speciesListMappable = speciesListMappable; //("on".equals(speciesListMappable));
	}    
	
		
	public boolean isSpeciesListMappable() {
	    return speciesListMappable;
	}
	
	public int getGeolocaleId() {
	  return geolocaleId;
	}
	public void setGeolocaleId(int geolocaleId) {
	  this.geolocaleId = geolocaleId;
	}

	public String getAction() {
	  //s_log.warn("getTitle() title:" + title);
		return action;
	}
	public void setAction(String action) {
		this.action = action;
	}
	
    public String getDisplayKey() {
      return displayKey;
    }
    public void setDisplayKey(String displayKey) {
      this.displayKey = displayKey;
    }
	

    public void reset(ActionMapping mapping, HttpServletRequest request) {
        this.projectName = "";
        title = "";
/*        
        author = "";
        contents = "";
        specimenImage1 = "";
        specimenImage2 = "";
        specimenImage3 = "";
        specimenImage1Link = "";
        specimenImage2Link = "";
        specimenImage3Link = "";
        mapImage = "";
        authorBio = "";
        authorImage= "";
        //root = "";
        locality = "";
        coords = "";
        extent = "";
        scope = "";
        // isLive = "";
        geolocaleId = 0;
        //speciesListMappable = false;
*/
        action = "";
    }

    public Project getProject() {
        Project project = new Project();
        project.setName(getProjectName());
        project.setTitle(getTitle());
        
        project.setAuthor(getAuthor());
        project.setLastChanged(getLastChanged());
        project.setContents(getContents());
        project.setSpecimenImage1(getSpecimenImage1());
        project.setSpecimenImage2(getSpecimenImage2());
        project.setSpecimenImage3(getSpecimenImage3());
        project.setSpecimenImage1Link(getSpecimenImage1Link());
        project.setSpecimenImage2Link(getSpecimenImage2Link());
        project.setSpecimenImage3Link(getSpecimenImage3Link());
        project.setMapImage(getMapImage());
        project.setAuthorBio(getAuthorBio());
        project.setAuthorImage(getAuthorImage());
        //project.setRoot(getRoot());
        project.setLocality(getLocality());
        project.setCoords(getCoords());
        project.setExtent(getExtent());
        project.setScope(getScope());
        project.setIsLive(getIsLive());
        project.setSpeciesListMappable(getSpeciesListMappable());
        project.setDisplayKey(getDisplayKey());
        
        return project;
    }
    
    public Project freshProject() {
        Project project = new Project();
        project.setName(getProjectName());
        String title = Formatter.initCap(getProjectName());
        if (title != null) {
          try {
             title = title.substring(0, title.indexOf("ants"));
             project.setTitle(title);
          } catch (StringIndexOutOfBoundsException e) {
            title = "";
          }
        }   
        project.setSpeciesListMappable(true);
        project.setLastChanged(DBUtil.getCurrentSQLDate());
        project.setContents("");
        project.setSpecimenImage1("");
        project.setSpecimenImage2("");
        project.setSpecimenImage3("");
        project.setSpecimenImage1Link("");
        project.setSpecimenImage2Link("");
        project.setSpecimenImage3Link("");
        project.setMapImage("");
        project.setAuthor("");
        project.setAuthorBio("");
        project.setAuthorImage("");
        //project.setRoot(getRoot());
        project.setLocality("");
        project.setCoords("");
        project.setExtent("");
        project.setScope("");
        project.setIsLive(false);
        project.setSource("editProjectForm");
        project.setDisplayKey("");
        return project;
    }
        
}

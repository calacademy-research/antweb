package org.calacademy.antweb.curate.speciesList;

import org.calacademy.antweb.search.AdvancedSearchForm;
import org.apache.struts.action.*;
import javax.servlet.http.*;

/*
There are a lot of data items stored in the session. They are aggregated in SpeciesListToolProps.
These values are only, and exactly, all properties that are submitted by forms and requests.
Some of them are persisted to the session via SpeciesListToolProps.persist(SpeciesListToolForm);
*/

public class SpeciesListToolForm extends AdvancedSearchForm {

    protected String action;
    protected String editSpeciesList = "none";
    protected String browse;
    
    private boolean isFreshen = false;
    
	private String taxa[] = null;
	private String chosen1[] = null;    
	private String chosen2[] = null;    
	private String chosen3[] = null;  

	private String speciesListName = null;
	
	private String mapSpeciesList1Name = null;
	private String mapSpeciesList2Name = null;
	private String mapSpeciesList3Name = null;

    private String doSearch = null;        
    
	private String refSpeciesListType = null;    
	private String refSpeciesListName = null;    
    private String displaySubfamily = "none";
    
    private int projLogId = 0;
    private int geoLogId = 0; 
    
    public String getAction() {
	    return action;
    }
    public void setAction(String action) {
    	this.action = action;
    }

    public String getBrowse() {
	    return browse;
    }
    public void setBrowse(String browse) {
    	this.browse = browse;
    }  

	public String[] getTaxa() {
		return taxa;
	}
	public void setTaxa(String[] strings) {
		taxa = strings;
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
        
    public String getDisplaySubfamily() {
	    return displaySubfamily;
    }
    public void setDisplaySubfamily(String displaySubfamily) {
    	this.displaySubfamily = displaySubfamily;
    }

    public String getDoSearch() {
	    return doSearch;
    }
    public void setDoSearch(String doSearch) {
    	this.doSearch = doSearch;
    }

    public String getEditSpeciesList() {
	  return editSpeciesList;
    }
    public void setEditSpeciesList(String editSpeciesList) {
  	  this.editSpeciesList = editSpeciesList;
    }

    // Used just for the history form
	public String getSpeciesListName() {
		return (this.speciesListName);
	}
	public void setSpeciesListName(String name) {
	    if ("null".equals(name)) return;
		this.speciesListName = name;
	}
    
	public String getMapSpeciesList1Name() {
		return (this.mapSpeciesList1Name);
	}
	public void setMapSpeciesList1Name(String name) {
	    if ("null".equals(name)) return;
		this.mapSpeciesList1Name = name;
	}
	public String getMapSpeciesList2Name() {
		return (this.mapSpeciesList2Name);
	}
	public void setMapSpeciesList2Name(String name) {
	    if ("null".equals(name)) return;
		this.mapSpeciesList2Name = name;
	}
	public String getMapSpeciesList3Name() {
		return (this.mapSpeciesList3Name);
	}
	public void setMapSpeciesList3Name(String name) {
	    if ("null".equals(name)) return;
		this.mapSpeciesList3Name = name;
	}
	
	public String getRefSpeciesListType() {
		return (this.refSpeciesListType);
	}
	public void setRefSpeciesListType(String type) {
	    if ("null".equals(type)) return;
		this.refSpeciesListType = type;
	}
	public String getRefSpeciesListName() {
		return (this.refSpeciesListName);
	}
	public void setRefSpeciesListName(String name) {
	    if ("null".equals(name)) return;
		this.refSpeciesListName = name;
	}

	public String[] getChosen1() {
		return (this.chosen1);
	}
	public void setChosen1(String[] chosen1) {
		this.chosen1 = chosen1;
	}
	public String[] getChosen2() {
		return (this.chosen2);
	}
	public void setChosen2(String[] chosen2) {
		this.chosen2 = chosen2;
	}
	public String[] getChosen3() {
		return (this.chosen3);
	}
	public void setChosen3(String[] chosen3) {
		this.chosen3 = chosen3;
	}

    public boolean getIsFreshen() {
      return isFreshen;
    }
    public void setIsFreshen(boolean isFreshen) {
      this.isFreshen = isFreshen;
    }

    public void persist(SpeciesListToolForm toolForm) {
        super.persist(toolForm);
        //if (toolForm.getAction() != null) 
        setAction(null);
                
		//if (toolForm.getDoSearch() != null)
		  setDoSearch(toolForm.getDoSearch());
		
		if (toolForm.getDisplaySubfamily() != null)
		  setDisplaySubfamily(toolForm.getDisplaySubfamily());

		if (toolForm.getMapSpeciesList1Name() != null)
		  setMapSpeciesList1Name(toolForm.getMapSpeciesList1Name());
		if (toolForm.getMapSpeciesList2Name() != null)
		  setMapSpeciesList2Name(toolForm.getMapSpeciesList2Name());
		if (toolForm.getMapSpeciesList3Name() != null)
		  setMapSpeciesList3Name(toolForm.getMapSpeciesList3Name());
		if (toolForm.getRefSpeciesListType() != null)
		  setRefSpeciesListType(toolForm.getRefSpeciesListType());
		if (toolForm.getRefSpeciesListName() != null)
		  setRefSpeciesListName(toolForm.getRefSpeciesListName());
		  
		if (toolForm.getGeoLogId() != 0) 
		  setGeoLogId(toolForm.getGeoLogId());
		if (toolForm.getProjLogId() != 0) 
		  setProjLogId(toolForm.getProjLogId());
    }
    
    public void resetSearch() {
      super.resetSearch();
      setGeoLogId(0);
      setProjLogId(0);
      setDoSearch(null);
    }
    
    public String getLinkParams() {
      String linkParams = super.getLinkParams();
      linkParams = "";
      // if (getAction() != null) linkParams += "action=" + getAction() + "&";
      linkParams +=
          "doSearch=" + getDoSearch()
        + "&displaySubfamily=" + getDisplaySubfamily()

        + "&mapSpeciesList1Name=" + getMapSpeciesList1Name()
        + "&mapSpeciesList2Name=" + getMapSpeciesList2Name()
        + "&mapSpeciesList3Name=" + getMapSpeciesList3Name()
        + "&refSpeciesListType=" + getRefSpeciesListType()      
        + "&refSpeciesListName=" + getRefSpeciesListName()
        
        + "&geoLogId=" + getGeoLogId()
        + "&projLogId=" + getProjLogId()
        
        + "&" + linkParams;
        
        return linkParams;
    }

	public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
		ActionErrors errors = new ActionErrors();
		//if ((taxa == null) || (Arrays.asList(taxa).size() < 1))
		//	errors.add("rank", new ActionError("error.rank.required"));

		return errors;
	}

    public String toString() {
      String superVal = super.toString();
      return 
          " action:" + getAction()
        + " doSearch:" + getDoSearch() 
        + " displaySubfamily:" + getDisplaySubfamily()
        + " mapSpeciesList1Name:" + getMapSpeciesList1Name()
        + " mapSpeciesList2Name:" + getMapSpeciesList2Name()
        + " mapSpeciesList3Name:" + getMapSpeciesList3Name()
        + " refSpeciesListType:" + getRefSpeciesListType() 
        + " refSpeciesListName:" + getRefSpeciesListName()
        + " projLogId:" + getProjLogId() + " geoLogId:" + getGeoLogId()
        + " freshen:" + getIsFreshen()
        + " " + superVal;
    }
    
}





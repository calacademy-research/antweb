package org.calacademy.antweb.search;

import javax.servlet.http.HttpServletRequest;
import org.apache.struts.action.ActionError;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.util.*;

/**
 * Form bean for the search page.  This form has the following fields,
 * with default values in square brackets:
 * <ul>
 * <li><b>searchType</b> - the type of search (equals, contains)
 * <li><b>name</b> - the name of the thing to find
 * <li><b>types</b> - show only things that have types
 * <li><b>images</b> - show only things that have images
 * </ul>
*/

public class SearchForm extends ActionForm {
    private String searchType = "contains";
    private String name = "";
    private String taxonName = "";
    private String types = null;
    public String imagesOnly = null;
    private String project = null;
    private int geolocaleId = 0;

    public String getSearchType() {
        return (this.searchType);
    }
    public void setSearchType(String searchType) {
        this.searchType = searchType;
    }

    public String getName() {
        return (this.name);
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getTaxonName() {
        return (this.taxonName);
    }
    public void setTaxonName(String name) {
        this.taxonName = name;
    }

    public String getTypes() {
    return (this.types);
    }

    public void setTypes(String types) {
        this.types = types;
    }

    public String getImagesOnly() {
		return (this.imagesOnly);
    }
    public void setImagesOnly(String imagesOnly) {    
        this.imagesOnly = imagesOnly;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }
    
    public int getGeolocaleId() {
        return geolocaleId;
    }
    public void setGeolocaleId(int geolocaleId) {
        this.geolocaleId = geolocaleId;
    }

    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {

        ActionErrors errors = new ActionErrors();
        
        // Bag out...
        if (true) return errors;
        
        if ((name == null) || (name.length() < 1)) {
          if (((imagesOnly == null) || (imagesOnly.length() < 1))  && ((types == null) || (types.length() < 1))) {
            errors.add("name", new ActionError("error.name.required"));
          } 
        }

        if ((searchType == null) || (searchType.length() < 1))
            errors.add("searchType", new ActionError("error.searchType.required"));

        AntwebUtil.blockFishingAttack(request, errors);

        return errors;
    }

    public void reset(ActionMapping mapping, HttpServletRequest request) {
        this.searchType = "contains";
        this.name = "";
        this.taxonName = "";
        this.imagesOnly = null;
        this.types = null;
        this.project = null;
    }
}

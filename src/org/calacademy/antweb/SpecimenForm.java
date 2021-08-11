package org.calacademy.antweb;


import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionMapping;

/**
 * Form bean for the specimen page.  This form has the following fields,
 * with default values in square brackets:
 * <ul>
 * <li><b>name</b> - the name of the specimen to view a description of
 * </ul>
*/

public final class SpecimenForm extends DescriptionForm {


    private String code = null;
    private String name = null;
    private String project = null;

    public String getName() {
	return (this.name);
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
	return (this.code);
    }
    public void setCode(String code) {
        this.code = code;
    }
    
    public String getProject() {
        return (this.project);
    }
    public void setProject(String project) {
        this.project = project;
    }    

    public void reset(ActionMapping mapping, HttpServletRequest request) {
        super.reset(mapping, request);
        this.name = null;
        this.code = null;
        this.project = null;
    }

}

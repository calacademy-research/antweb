package org.calacademy.antweb.curate.project;


import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts.action.*;

import org.calacademy.antweb.util.*;

/**
 * Form bean curateProjectForm (one field, project, required). Not used: struts-config.xml declares the bean,
 * but no action names it.
 */
public class ProjectForm extends ActionForm {

/* check name - also make sure to add where clause to update */
	
	protected String project;
	
	
public String getProject() {
	return project;
}
public void setProject(String project) {
	this.project = project;
}
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        this.project = null;
    }

    public ActionErrors validate(ActionMapping mapping,
    				HttpServletRequest request) {

        ActionErrors errors = new ActionErrors();
        if (project == null || project.length() < 1)
            errors.add("name", new ActionMessage("error.name.required"));
            
        AntwebUtil.blockFishingAttack(request, errors);
                    
        return errors;
    }
}

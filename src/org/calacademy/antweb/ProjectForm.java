package org.calacademy.antweb;


import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts.action.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/**
 * Unused as of 2026-10-30: the form bean projectForm (a project name; extends DescriptionForm) is declared in
 * WEB-INF/struts-config.xml, but no action mapping uses it and no Java or JSP refers to this class. The project
 * pages use overviewForm (OverviewForm); the curator project forms are in curate/project.
 */
public final class ProjectForm extends DescriptionForm {

    private static final Log s_log = LogFactory.getLog(ProjectForm.class);

    private String name;

    public void reset(ActionMapping mapping, HttpServletRequest request) {
        this.name = null;
    }

    public String getName() {
        return this.name;
    }
    public void setName(String name) {
        this.name = name;
    }
            
}

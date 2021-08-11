package org.calacademy.antweb;


import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class ProjectForm extends DescriptionForm {

    private static Log s_log = LogFactory.getLog(ProjectForm.class);

    private String name = null;

    public void reset(ActionMapping mapping, HttpServletRequest request) {
        this.name = null;
    }

    public String getName() {
        return (this.name);
    }
    public void setName(String name) {
        this.name = name;
    }
            
}

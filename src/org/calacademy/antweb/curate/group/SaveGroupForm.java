package org.calacademy.antweb.curate.group;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

/**
 * Form bean saveGroupForm of saveGroup.do and viewGroup.do: the upload group's number (id), name, short name
 * (abbrev), admin curator (adminLoginId), newGroup ("true" on the new-group form) and step (delete). validate()
 * requires a name for a new group.
 */
public class SaveGroupForm extends ActionForm {

    private String id;
    private String name;
    private String adminLoginId;
    private String newGroup;
    private String abbrev;

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getAdminLoginId() {
        return adminLoginId;
    }
    public void setAdminLoginId(String adminLoginId) {
        this.adminLoginId = adminLoginId;
    }

    public String getNewGroup() {
        return newGroup;
    }
    public void setNewGroup(String newGroup) {
        this.newGroup = newGroup;
    }

    public String getAbbrev() {
        return abbrev;
    }
    public void setAbbrev(String abbrev) {
        this.abbrev = abbrev;
    }

    /* This get/set method supports specifying action during form submission */    
    private String step;
    public String getStep() {
        return step;
    }
    public void setStep(String step) {
        this.step = step;
    }
    

    public ActionErrors validate(ActionMapping mapping,
            HttpServletRequest request) {

        ActionErrors errors = null; 
        
        if (newGroup == null || newGroup.equals("true")) {
            errors = new ActionErrors();
            
            if (name == null || name.length() < 1)
                errors.add("error", new ActionMessage("error.group.namerequired"));
            
            //if ((name != null) && (name.indexOf(" ") != -1))
            //    errors.add("error", new ActionMessage("error.group.nonamespaces"));
        }
        return errors;
    }


}

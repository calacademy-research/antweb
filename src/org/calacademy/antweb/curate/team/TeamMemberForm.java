package org.calacademy.antweb.curate.team;

import javax.servlet.http.HttpServletRequest;
import org.apache.struts.action.ActionError;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.*;

public class TeamMemberForm extends ActionForm {

    private String id;
    private String name;
    private String roleOrg;
    private String text;
    private String email;


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
    public String getRoleOrg() {
        return roleOrg;
    }
    public void setRoleOrg(String roleOrg) {
        this.roleOrg = roleOrg;
    }

    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    public String getText() {
        return text;
    }
    public void setText(String text) {
        this.text = text;
    }


    public ActionErrors validate(ActionMapping mapping,
            HttpServletRequest request) {

        ActionErrors errors = null; 
        
       // if ((newGroup == null) || (newGroup.equals("true"))) {
       //     errors = new ActionErrors();
            
       //     if ((name == null) || (name.length() < 1))
       //         errors.add("error", new ActionError("error.group.namerequired"));
            
            //if ((name != null) && (name.indexOf(" ") != -1))
            //    errors.add("error", new ActionError("error.group.nonamespaces"));
       // }
        return errors;
    }


}

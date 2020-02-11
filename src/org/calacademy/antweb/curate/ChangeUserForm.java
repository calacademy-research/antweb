package org.calacademy.antweb.curate;

import org.apache.struts.action.ActionForm;


public class ChangeUserForm extends ActionForm {

    protected String group;

    public String getGroup() {
        return group;
    }
    public void setGroup(String group) {
        this.group = group;
    }
}



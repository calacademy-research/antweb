package org.calacademy.antweb.curate;

import org.apache.struts.action.ActionForm;


/**
 * Form bean changeUserForm (one field, group). Not used: struts-config.xml declares the bean, but no action
 * names it.
 */
public class ChangeUserForm extends ActionForm {

    protected String group;

    public String getGroup() {
        return group;
    }
    public void setGroup(String group) {
        this.group = group;
    }
}



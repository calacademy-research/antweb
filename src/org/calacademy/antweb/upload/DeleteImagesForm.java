package org.calacademy.antweb.upload;


import javax.servlet.http.HttpServletRequest;
//import org.apache.struts.action.ActionError;
import org.apache.struts.action.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class DeleteImagesForm extends ActionForm {

    private static Log s_log = LogFactory.getLog(DeleteImagesForm.class);

    private String daysAgo = null;
    private String group = null;
    private String[] chosen = null;

    public String[] getChosen() {
    return (this.chosen);
    }


    public void setChosen(String[] chosen) {
        this.chosen = chosen;
    }

  
    public String getDaysAgo() {
        return daysAgo;
    }


    public void setDaysAgo(String daysAgo) {
        this.daysAgo = daysAgo;
    }


    public String getGroup() {
        return group;
    }


    public void setGroup(String group) {
        this.group = group;
    }


    public ActionErrors validate(ActionMapping mapping,
                    HttpServletRequest request) {

        ActionErrors errors = new ActionErrors();
        //if ((name == null) || (name.length() < 1))
        //    errors.add("name", new ActionError("error.name.required"));
       // if ((rank == null) || (rank.length() < 1))
        //    errors.add("rank", new ActionError("error.rank.required"));

        return errors;
    }
}

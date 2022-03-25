package org.calacademy.antweb;


import javax.servlet.http.HttpServletRequest;
//import org.apache.struts.action.ActionError;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.util.*;

/**
 * Form bean for the broswer page.  This form has the following fields,
 * with default values in square brackets:
 * <ul>
 * <li><b>rank</b> - the rank of the taxon to browse
 * <li><b>name</b> - the name of the taxon to browse
 * </ul>
*/

public final class ChooseComparisonForm extends ActionForm {


    private String rank;
    private String name;
    private String[] chosen;

    public String[] getChosen() {
	return this.chosen;
    }


    public void setChosen(String[] chosen) {
        this.chosen = chosen;
    }

    public String getRank() {
	return this.rank;
    }


    public void setRank(String rank) {
        this.rank = rank;
    }


    public String getName() {
	return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void reset(ActionMapping mapping, HttpServletRequest request) {
        this.rank = null;
        this.name = null;
    }

    public ActionErrors validate(ActionMapping mapping,
    				HttpServletRequest request) {

        ActionErrors errors = new ActionErrors();
        //if ((name == null) || (name.length() < 1))
        //    errors.add("name", new ActionError("error.name.required"));
       // if ((rank == null) || (rank.length() < 1))
        //    errors.add("rank", new ActionError("error.rank.required"));
        
        AntwebUtil.blockFishingAttack(request, errors);
         
        return errors;
    }
}

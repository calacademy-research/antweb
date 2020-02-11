package org.calacademy.antweb;


import javax.servlet.http.HttpServletRequest;
import org.apache.struts.action.ActionError;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.util.*;

public final class TaxaPageForm extends ActionForm {

// This class is not used.  Parameters are used.

    private String rank = null;
    private String caste = null;

    public String getCaste() {
    	return (this.caste);
    }
    public void setCaste(String caste) {
        this.caste = caste;
    }
    
    public String getRank() {
    	return (this.rank);
    }
    public void setRank(String rank) {
        this.rank = rank;
    }


    public void reset(ActionMapping mapping, HttpServletRequest request) {
        this.rank = null;
        this.caste = null;
    }

    public ActionErrors validate(ActionMapping mapping,
    				HttpServletRequest request) {

        ActionErrors errors = new ActionErrors();
        if ((rank == null) || (rank.length() < 1))
            errors.add("rank", new ActionError("error.rank.required"));

        AntwebUtil.blockFishingAttack(request, errors);
        
        return errors;
    }
}

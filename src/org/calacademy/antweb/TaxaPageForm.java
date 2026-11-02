package org.calacademy.antweb;


import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.util.*;

/**
 * Form bean taxaPageForm of /taxonomicPage.do (TaxaPageAction): caste, which the action reads, and rank (the action
 * reads rank from the request parameter itself). validate() asks for a rank and blocks fishing requests. The older
 * comment in the class says it is not used, but the caste is read from it.
 */
public final class TaxaPageForm extends ActionForm {

// This class is not used.  Parameters are used.

    private String rank;
    private String caste;

    public String getCaste() {
    	return this.caste;
    }
    public void setCaste(String caste) {
        this.caste = caste;
    }
    
    public String getRank() {
    	return this.rank;
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
        if (rank == null || rank.length() < 1)
            errors.add("rank", new ActionMessage("error.rank.required"));

        AntwebUtil.blockFishingAttack(request, errors);
        
        return errors;
    }
}

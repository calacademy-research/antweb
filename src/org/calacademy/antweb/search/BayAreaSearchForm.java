package org.calacademy.antweb.search;

import org.calacademy.antweb.util.*;

import javax.servlet.http.HttpServletRequest;
import org.apache.struts.action.ActionError;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionMapping;


/**
 * Form bean for the bay area search page.  This form has the following fields,
 * with default values in square brackets:
 * <ul>
 * <li><b>adm2</b> -an array of chosen adm2s  (counties)
 * </ul>
*/

public final class BayAreaSearchForm extends SearchForm {

    private String[] adm2s = null;
    
    public ActionErrors validate(ActionMapping mapping,
    				HttpServletRequest request) {

        ActionErrors errors = new ActionErrors();
        
        if (adm2s == null) {
			errors.add("name", new ActionError("error.name.required"));
        }
        
        AntwebUtil.blockFishingAttack(request, errors);
        
        return errors;
    }

    public void reset(ActionMapping mapping, HttpServletRequest request) {
        this.adm2s = null;
    }

    public String[] getAdm2s() {
        return adm2s;
	}
	public void setAdm2s(String[] strings) {
        adm2s = strings;
	}
}

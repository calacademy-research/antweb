package org.calacademy.antweb.upload;


import javax.servlet.http.HttpServletRequest;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

/**
 * Form bean for getting a world authority file.  This form has the following fields,
 * with default values in square brackets:
 * <ul>
 * <li><b>type</b> - either extinct or extant
* </ul>
*/

public final class WorldAuthorityForm extends ActionForm {


    private String mode;
    private String fileName;
    

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getMode() {
	return this.mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

    public void reset(ActionMapping mapping, HttpServletRequest request) {
        this.mode = null;
        this.fileName = null;
    }

    public ActionErrors validate(ActionMapping mapping,
    				HttpServletRequest request) {

        ActionErrors errors = new ActionErrors();
        return errors;
    }
}

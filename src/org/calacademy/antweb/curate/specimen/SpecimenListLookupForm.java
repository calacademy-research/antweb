package org.calacademy.antweb.curate.specimen;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.upload.FormFile;

public class SpecimenListLookupForm extends ActionForm {
    private FormFile file;

    public FormFile getFile() {
        return file;
    }

    public void setFile(FormFile file) {
        this.file = file;
    }

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        file = null;
    }
}

package org.calacademy.antweb.curate.speciesList;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.upload.FormFile;

/**
 * Form bean validateSpeciesListForm of validateSpeciesList.do: the uploaded file, action (download or
 * downloadCorrected) and showUnmatched (also list the valid species the file lacks).
 */
public class ValidateSpeciesListForm extends ActionForm {
    private FormFile file;
    private String action;
    private boolean showUnmatched;

    public FormFile getFile() {
        return file;
    }

    public void setFile(FormFile file) {
        this.file = file;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public boolean isShowUnmatched() {
        return showUnmatched;
    }

    public void setShowUnmatched(boolean showUnmatched) {
        this.showUnmatched = showUnmatched;
    }

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        this.file = null;
        this.action = null;
        this.showUnmatched = false;
    }
}

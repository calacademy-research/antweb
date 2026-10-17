package org.calacademy.antweb;


import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts.action.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;

/**
 * Form bean for the browser page and related pages such as
 * navigateHierarchy, getComparison, oneView, chooseComparison,
 * and description.  This form has the following fields,
 * with default values in square brackets:
 * <ul>
 * <li><b>rank</b> - the rank of the taxon to browse
 * <li><b>name</b> - the name of the taxon to browse
 * <li><b>project</b> - the project of the taxon to browse
 *  <li><b>chosen</b> - the options to display of the taxon to browse
 * </ul>
*/

public class DescriptionForm extends ActionForm {

    private static final Log s_log = LogFactory.getLog(DescriptionForm.class);

    private boolean m_isSaveEditField = false;
    private String m_editField;
    private String m_contents;
    private String m_imageUrl = "";
    // Editor safety (DescEditSafety): fingerprint of the stored text when the editor opened,
    // "1" once the editor showed that text, "1" for the "Clear this field" button.
    private String m_loadedHash;
    private String m_editorReady;
    private String m_clearField;
    // "Restore this version" on the History page (fieldHistory.do): the history row's hist_id.
    private String m_restoreFrom;

    public void reset(ActionMapping mapping, HttpServletRequest request) {
        m_isSaveEditField = false;
        m_editField = null;
        m_contents = null;
        m_imageUrl = null;    
        m_loadedHash = null;
        m_editorReady = null;
        m_clearField = null;
        m_restoreFrom = null;
    }
    
    public ActionErrors validate(ActionMapping mapping,
                    HttpServletRequest request) {

        ActionErrors errors = new ActionErrors();
        //if ((name == null) || (name.length() < 1))
        //    errors.add("name", new ActionMessage("error.name.required"));
       // if ((rank == null) || (rank.length() < 1)) {
           //if ((AntwebUtil.getRequestInfo(request)).contains("taxonName=")) {
             // no problem.  New direct access method.  For example:
             //      http://localhost/antweb/description.do?taxonName=amblyoponinaeprionopelta%20descarpentriesi           
           //} //else {
             //s_log.warn("Bad rank:" + rank);
             //s_log.info("Bad rank:" + rank + " requestInfo:" + AntwebUtil.getRequestInfo(request));
           //}
           // errors.add("rank", new ActionMessage("error.rank.required"));
       // }
/*
        if ((name.length() > 50)) {
            errors.add("name", new ActionMessage("error.longfield"));
        }
        if ((rank.length() > 50)) {
            errors.add("rank", new ActionMessage("error.longfield"));
        }
        if ((project.length() > 50)) {
            errors.add("project", new ActionMessage("error.longfield"));
        }
*/
        AntwebUtil.blockFishingAttack(request, errors);

        return errors;
    }
        

    public String getContents() {
      return m_contents;
    }
    public void setContents(String contents) {
      m_contents = contents;
    }

    public boolean getIsSaveEditField() {
      return m_isSaveEditField;
    }
    public void setIsSaveEditField(boolean isSaveEditField) {
      m_isSaveEditField = isSaveEditField;
    }
    
    public String getEditField() {
      return m_editField;
    }    
    public void setEditField(String editField) {
      m_editField = editField;    
    }
    
    public String getImageUrl() {
      return m_imageUrl;
    }
    public void setImageUrl(String imageUrl) {
      m_imageUrl = imageUrl;
    }    

    public String getLoadedHash() {
      return m_loadedHash;
    }
    public void setLoadedHash(String loadedHash) {
      m_loadedHash = loadedHash;
    }

    public String getEditorReady() {
      return m_editorReady;
    }
    public void setEditorReady(String editorReady) {
      m_editorReady = editorReady;
    }

    public String getClearField() {
      return m_clearField;
    }
    public void setClearField(String clearField) {
      m_clearField = clearField;
    }

    public String getRestoreFrom() {
      return m_restoreFrom;
    }
    public void setRestoreFrom(String restoreFrom) {
      m_restoreFrom = restoreFrom;
    }
}

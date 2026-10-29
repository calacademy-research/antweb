package org.calacademy.antweb.upload;

//import org.apache.struts.action.ActionForm;

import org.calacademy.antweb.upload.UploadForm;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/**
 * Form bean of /descEditImageUpload.do (struts-config name descEditImageUploadForm): the file (theFile2,
 * from UploadForm), folder and output file name, and the page it came from (target, rank, name, editField).
 * Filled by the page-text editor's upload field (common/descEdit/taxonImageEditField.jsp, imageDescEditField.jsp)
 * and read by DescEditImageUploadAction and the confirm pages (common/descEdit/*ImageConfirmForm-body.jsp).
 */
public class DescEditImageUploadForm extends UploadForm {

    private static final Log s_log = LogFactory.getLog(DescEditImageUploadForm.class);

//  protected FormFile theFile2;
//  protected String homePageDirectory = "";
 
    private String rank;
    private String name;
    private String editField;
    private String target;
    private String contents;
    
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
    
    public String getEditField() {
    return this.editField;
    }

    public void setEditField(String editField) {
        this.editField = editField;
    }
        
    public String getTarget() {
      return target;
    }
    
    public void setTarget(String target) {
      this.target = target;
    }
    
    public String getContents() {
      return contents;
    }
    
    public void setContents(String contents) {
      this.contents = contents;
    }
}



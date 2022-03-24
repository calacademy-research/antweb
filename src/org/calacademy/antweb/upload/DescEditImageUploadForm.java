package org.calacademy.antweb.upload;

//import org.apache.struts.action.ActionForm;

import org.calacademy.antweb.upload.UploadForm;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class DescEditImageUploadForm extends UploadForm {

    private static Log s_log = LogFactory.getLog(DescEditImageUploadForm.class);

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



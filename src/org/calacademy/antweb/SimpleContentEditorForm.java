package org.calacademy.antweb;
 
import org.apache.struts.action.ActionForm;

/*
 * Created on Jul 26, 2006
 */

/**
 * Form bean for a simple editor which reads a file into a
 * textarea and then saves the edited text area back to the file
 * <ul>
 * <li><b>fileName</b> - the file to read
 * <li><b>contents</b> - the contents to edit
 * </ul>
*/

public final class SimpleContentEditorForm extends ActionForm {

    private String fileName;
    private String contents;

    public String getContents() {
        return contents;
    }
    public void setContents(String contents) {
        this.contents = contents;
    }

    public String getFileName() {
        return fileName;
    }
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
}

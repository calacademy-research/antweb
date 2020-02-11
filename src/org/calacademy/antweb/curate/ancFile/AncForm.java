package org.calacademy.antweb.curate.ancFile;



import java.sql.Date;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.*;

/**
 * Form bean for the specimen page.  This form has the following fields,
 * with default values in square brackets:
 * <ul>
 * <li><b>name</b> - the name of the specimen to view a description of
 * </ul>
*/

public final class AncForm extends ActionForm {

/* check name - also make sure to add where clause to update */
	
	protected int id;
	protected String title;
	protected Date lastChanged;
	protected String contents;
	protected String fileName;
	protected String directory;
	protected int project;
	
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public Date getLastChanged() {
		return lastChanged;
	}
	public void setLastChanged(Date lastChanged) {
		this.lastChanged = lastChanged;
	}
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
	public String getDirectory() {
		return directory;
	}
	public void setDirectory(String directory) {
		this.directory = directory;
	}
	
	public int getProject() {
		return project;
	}
	public void setProject(int project) {
		this.project = project;
	}
	
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        this.id = -1;
        this.title = null;
        this.contents=null;
        this.fileName=null;
        this.directory=null;
        this.lastChanged=null;
        this.project=-1;
    }

    
}

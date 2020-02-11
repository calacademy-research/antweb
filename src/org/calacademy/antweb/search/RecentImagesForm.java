package org.calacademy.antweb.search;

import org.calacademy.antweb.*;

import javax.servlet.http.HttpServletRequest;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;


/**
 * Form bean for the search page.  This form has the following fields,
 * with default values in square brackets:
 * <ul>
 * <li><b>searchType</b> - the type of search (equals, contains)
 * <li><b>name</b> - the name of the thing to find
 * <li><b>images</b> - show only things that have images
 * </ul>
*/

public final class RecentImagesForm extends ActionForm {

	protected String numToShow="";
	protected String daysAgo = "";
	protected String group = "";
	protected String fromDate = "";
	protected String toDate = "";
	

	
	/**
	 * @return Returns the daysAgo.
	 */
	public String getDaysAgo() {
		return daysAgo;
	}
	/**
	 * @param daysAgo The daysAgo to set.
	 */
	public void setDaysAgo(String daysAgo) {
		this.daysAgo = daysAgo;
	}

	public String getNumToShow() {
		return numToShow;
	}
	public void setNumToShow(String numToShow) {
		this.numToShow = numToShow;
	}

	public String getGroup() {
		return group;
	}
	public void setGroup(String group) {
		this.group = group;
	}
	public String getFromDate() {
		return fromDate;
	}
	public void setFromDate(String fromDate) {
		this.fromDate = fromDate;
	}
	public String getToDate() {
		return toDate;
	}
	public void setToDate(String toDate) {
		this.toDate = toDate;
	}

	
}

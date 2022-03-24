package org.calacademy.antweb.search;

import javax.servlet.http.HttpServletRequest;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.util.*;

/**
 * Form bean for pulling a list of taxa into a collection of taxa objects:
 * <ul>
 * <li><b>taxa[]</b> - a list of taxa to pull
 * <li><b>project</b> - the project of the taxon to browse
 *  <li><b>chosen</b> - the options to display of the taxon to browse
 * </ul>
*/

public class TaxaFromSearchForm extends SearchForm {

	private String taxa[];
	private String project;
	private String caste;
	private String chosen[];
	private String resultRank;
	private String output;


	public String getProject() {
		return (this.project);
	}
	public void setProject(String project) {
		this.project = project;
	}

	public String getCaste() {
		return (this.caste);
	}
	public void setCaste(String caste) {
		this.caste = caste;
	}
	
	public String[] getChosen() {
		return (this.chosen);
	}

	public void setChosen(String[] chosen) {
		this.chosen = chosen;
	}

	public void reset(ActionMapping mapping, HttpServletRequest request) {
		this.taxa = null;
		this.project = null;
		this.chosen = null;
	}

	public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
		ActionErrors errors = new ActionErrors();
		//if ((taxa == null) || (Arrays.asList(taxa).size() < 1))
		//	errors.add("rank", new ActionError("error.rank.required"));

        AntwebUtil.blockFishingAttack(request, errors);

		return errors;
	}

	public String[] getTaxa() {
		return taxa;
	}
	public void setTaxa(String[] strings) {
		taxa = strings;
	}
	

	public String getResultRank() {
		return resultRank;
	}
	public void setResultRank(String resultRank) {
         //A.log("setResultRank() resultRank:" + resultRank);
         //AntwebUtil.logStackTrace();
		this.resultRank = resultRank;
	}

	public String getOutput() {
		return output;
	}
	public void setOutput(String output) {
         //A.log("setResultRank() resultRank:" + resultRank);
         //AntwebUtil.logStackTrace();
		this.output = output;
	}

}

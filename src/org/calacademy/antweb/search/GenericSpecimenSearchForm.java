package org.calacademy.antweb.search;

import org.calacademy.antweb.util.*;

import javax.servlet.http.HttpServletRequest;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;


/**
 * Form bean for the generic specimen search page.  This form has the following fields,
 * with default values in square brackets:
 * <ul>
 * <li>subfamily
 * <li>genus
 * <li>species
 * <li>field
 * <li>value
 * <li>project
 * </ul>
*/

public final class GenericSpecimenSearchForm extends ActionForm {

	private String subfamily = null;
	private String genus = null;
	private String species = null;
	private String field = null;
	private String value = null;
    private String project = null;
    
	/**
	 * @return Returns the project.
	 */
	public String getProject() {
		return project;
	}
	/**
	 * @param project The project to set.
	 */
	public void setProject(String project) {
		this.project = project;
	}
	/**
	 * @return Returns the field.
	 */
	public String getField() {
		return field;
	}
	/**
	 * @param field The field to set.
	 */
	public void setField(String field) {
		this.field = field;
	}
	/**
	 * @return Returns the genus.
	 */
	public String getGenus() {
		return genus;
	}
	/**
	 * @param genus The genus to set.
	 */
	public void setGenus(String genus) {
		this.genus = genus;
	}
	/**
	 * @return Returns the species.
	 */
	public String getSpecies() {
		return species;
	}
	/**
	 * @param species The species to set.
	 */
	public void setSpecies(String species) {
		this.species = species;
	}
	/**
	 * @return Returns the subfamily.
	 */
	public String getSubfamily() {
		return subfamily;
	}
	/**
	 * @param subfamily The subfamily to set.
	 */
	public void setSubfamily(String subfamily) {
		this.subfamily = subfamily;
	}
	/**
	 * @return Returns the value.
	 */
	public String getValue() {
		return value;
	}
	/**
	 * @param value The value to set.
	 */
	public void setValue(String value) {
		this.value = value;
	}
	
	 public void reset(ActionMapping mapping, HttpServletRequest request) {
        this.field = null;
        this.value = null;
        this.subfamily = null;
        this.genus = null;
        this.species = null;        
    }

    public ActionErrors validate(ActionMapping mapping,
    				HttpServletRequest request) {

        ActionErrors errors = new ActionErrors();

        AntwebUtil.blockFishingAttack(request, errors);

        return errors;
    }
}

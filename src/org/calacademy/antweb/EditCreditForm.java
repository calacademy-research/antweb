package org.calacademy.antweb;

import org.apache.struts.action.ActionForm;

public class EditCreditForm extends ActionForm {

	protected String copyright = "";
	protected String artist = "";
	protected String license = "";
	protected String changeType = "";
	protected String changeField = "";
	protected String newValue = "";
	protected String selectedValue = "";

	/**
	 * @return
	 */
	public String getLicense() {
		return license;
	}

	/**
	 * @param string
	 */
	public void setLicense(String string) {
		license = string;
	}

	/**
	 * @return
	 */
	public String getArtist() {
		return artist;
	}

	/**
	 * @param string
	 */
	public void setArtist(String string) {
		artist = string;
	}

	/**
	 * @return
	 */
	public String getCopyright() {
		return copyright;
	}

	/**
	 * @param string
	 */
	public void setCopyright(String string) {
		copyright = string;
	}

	/**
	 * @return
	 */
	public String getChangeField() {
		return changeField;
	}

	/**
	 * @return
	 */
	public String getChangeType() {
		return changeType;
	}

	/**
	 * @param string
	 */
	public void setChangeField(String string) {
		changeField = string;
	}

	/**
	 * @param string
	 */
	public void setChangeType(String string) {
		changeType = string;
	}

	/**
	 * @return
	 */
	public String getNewValue() {
		return newValue;
	}

	/**
	 * @param string
	 */
	public void setNewValue(String string) {
		newValue = string;
	}

	/**
	 * @return
	 */
	public String getSelectedValue() {
		return selectedValue;
	}

	/**
	 * @param string
	 */
	public void setSelectedValue(String string) {
		selectedValue = string;
	}

}

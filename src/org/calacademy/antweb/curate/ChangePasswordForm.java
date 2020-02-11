package org.calacademy.antweb.curate;

import org.apache.struts.action.ActionForm;


public class ChangePasswordForm extends ActionForm {

	private String oldPassword;
	private String newPassword1;
	private String newPassword2;


  
	/**
	 * @return Returns the newPassword1.
	 */
	public String getNewPassword1() {
		return newPassword1;
	}
	/**
	 * @param newPassword1 The newPassword1 to set.
	 */
	public void setNewPassword1(String newPassword1) {
		this.newPassword1 = newPassword1;
	}
	/**
	 * @return Returns the newPassword2.
	 */
	public String getNewPassword2() {
		return newPassword2;
	}
	/**
	 * @param newPassword2 The newPassword2 to set.
	 */
	public void setNewPassword2(String newPassword2) {
		this.newPassword2 = newPassword2;
	}
	/**
	 * @return Returns the oldPassword.
	 */
	public String getOldPassword() {
		return oldPassword;
	}
	/**
	 * @param oldPassword The oldPassword to set.
	 */
	public void setOldPassword(String oldPassword) {
		this.oldPassword = oldPassword;
	}
}



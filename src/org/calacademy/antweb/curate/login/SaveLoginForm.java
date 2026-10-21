package org.calacademy.antweb.curate.login;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class SaveLoginForm extends ActionForm {

    private static final Log s_log = LogFactory.getLog(SaveLoginForm.class);

    private String id;
    private String name;
    private String email;
    private String firstName;
    private String lastName;
    private String password;
    private String retypePassword;
    private boolean isAdmin;

    private boolean isUploadSpecimens;
    private boolean isUploadImages;    
    private String[] groups;

    private String[] projects;
    private String[] countries;

    // The login form since 2026-10-20 (LoginEditPage, editForm=2): the assigned projects and
    // countries to remove (stored keys) and the ones to add (as typed or picked from the list).
    private String editForm;
    private String[] removeProjects;
    private String[] addProjects;
    private String[] removeCountries;
    private String[] addCountries;
    public String getEditForm() { return editForm; }
    public void setEditForm(String editForm) { this.editForm = editForm; }
    public String[] getRemoveProjects() { return removeProjects; }
    public void setRemoveProjects(String[] v) { this.removeProjects = v; }
    public String[] getAddProjects() { return addProjects; }
    public void setAddProjects(String[] v) { this.addProjects = v; }
    public String[] getRemoveCountries() { return removeCountries; }
    public void setRemoveCountries(String[] v) { this.removeCountries = v; }
    public String[] getAddCountries() { return addCountries; }
    public void setAddCountries(String[] v) { this.addCountries = v; }
    
    public String getId() {
      //A.log("getId():" + id);
        return id;
    }
    public void setId(String id) {
      //A.log("setId(" + id + ")");
        this.id = id;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getFirstName() {
        return firstName;
    }
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    
    public String getLastName() {
        return lastName;
    }
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
    
    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }    
    
    public String getRetypePassword() {
        return retypePassword;
    }
    public void setRetypePassword(String retypePassword) {
        this.retypePassword = retypePassword;
    }    

    // Needed to change the password of your own login.
    private String currentPassword;
    public String getCurrentPassword() {
        return currentPassword;
    }
    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }
    
    public boolean isAdmin() {
        return this.isAdmin;
    }
    public void setIsAdmin(boolean isAdmin) {
        this.isAdmin = isAdmin;
    }

    public Boolean isUploadSpecimens() {
      s_log.debug("SaveLoginForm() isUploadSpecimens:" + this.isUploadSpecimens);
        return this.isUploadSpecimens;
    }
    public void setIsUploadSpecimens(boolean isUploadSpecimens) {
      s_log.debug("SaveLoginForm() setIsUploadSpecimens:" + isUploadSpecimens);
      this.isUploadSpecimens = isUploadSpecimens;
    }
    
    public Boolean isUploadImages() {
        return this.isUploadImages;
    }
    public void setIsUploadImages(boolean isUploadImages) {
      this.isUploadImages = isUploadImages;
    }
    
    public String[] getGroups() {
        return groups;
    }
    public void setGroups(String[] groups) {
        this.groups = groups;
    }
    

    public String[] getProjects() {
        return projects;
    }
    public void setProjects(String[] projects) {
        this.projects = projects;
    }
    
    public String[] getCountries() {
        return countries;
    }
    public void setCountries(String[] countries) {
        this.countries = countries;
    }    
    
    /* This get/set method supports specifying action during form submission */    
    private String step;
    public String getStep() {
        return step;
    }
    public void setStep(String step) {
        this.step = step;
    }
     
    private String delete;
    public String getDelete() {
        return delete;
    }
    public void setDelete(String delete) {
        this.delete = delete;
    }
            
    /* This get/set method supports specifying action during form submission */    
    private String changePassword;
    public String getChangePassword() {
        return changePassword;
    }
    public void setChangePassword(String changePassword) {
        this.changePassword = changePassword;
    }

    private String uploadAs;
    public String getUploadAs() {
        return uploadAs;
    }
    public void setUploadAs(String uploadAs) {
        this.uploadAs = uploadAs;
    }


    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {

        ActionErrors errors = null;

        if (!"delete".equals(getStep())) {

            errors = new ActionErrors();

            // The administrator's form (editForm=2) checks a changed email in LoginEdit, so an old
            // stored address that this check would refuse does not block every other change.
            if (!"2".equals(editForm) && email != null && !"".equals(email) && !AntwebUtil.validEmail(email)) {
                errors.add("error", new ActionMessage("error.login.invalidEmail"));
            }

            String pw = password == null ? "" : password;
            String retype = retypePassword == null ? "" : retypePassword;
            if (!pw.equals(retype)) {
                errors.add("error", new ActionMessage("error.login.retypePasswordMismatch"));        
            }

            Login accessLogin = LoginMgr.getAccessLogin(request);
            
            // only on the case of invite should these be invoked.
            if ("invite".equals(getStep())) {            
              //s_log.warn("is invite form validation");
              
              if (getName() != null && accessLogin != null &&
                      getName().equals(accessLogin.getName())) {
                  // This condition is to catch and block the error of form autofill 
                errors.add("error", new ActionMessage("error.login.invalidUsername"));
              } else {
                s_log.debug("validate() name:" + getName() + " accessName:" + (accessLogin == null ? null : accessLogin.getName()));
              }
              if (
                  getPassword() != null && !getPassword().equals("") ||
                          getRetypePassword() != null && !getRetypePassword().equals("")
                  ) {
                errors.add("error", new ActionMessage("error.login.notEmptyPasswords"));              
              }
            }
            // (Until 2026-10-20 an administrator's save was refused here with "Empty Password"
            // when the password boxes were blank, which they always are since passwords are
            // hashed and no longer shown: a blank password means "keep it".)
        }           
        return errors;
    }

}

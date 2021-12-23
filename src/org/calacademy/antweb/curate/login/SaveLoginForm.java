package org.calacademy.antweb.curate.login;

import javax.servlet.http.HttpServletRequest;
import org.apache.struts.action.ActionError;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class SaveLoginForm extends ActionForm {

    private static Log s_log = LogFactory.getLog(SaveLoginForm.class);

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

    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {

        ActionErrors errors = null;

        if (!"delete".equals(getStep())) {

            errors = new ActionErrors();

            if ((email != null) && (!"".equals(email)) && (!AntwebUtil.validEmail(email))) {
                errors.add("error", new ActionError("error.login.invalidEmail"));
            }

            if ((retypePassword == null) || (!password.equals(retypePassword))) {
                errors.add("error", new ActionError("error.login.retypePasswordMismatch"));        
            }

            Login accessLogin = LoginMgr.getAccessLogin(request);
            
            // only on the case of invite should these be invoked.
            if ("invite".equals(getStep())) {            
              //s_log.warn("is invite form validation");
              
              if ((getName() != null) && 
                (getName().equals(accessLogin.getName()))) {
                  // This condition is to catch and block the error of form autofill 
                errors.add("error", new ActionError("error.login.invalidUsername"));
              } else {
                s_log.warn("validate() name:" + getName() + " accessName:" + accessLogin.getName());
              }
              if (
                  ((getPassword() != null) && (!getPassword().equals(""))) ||
                  ((getRetypePassword() != null) && (!getRetypePassword().equals("")))
                  ) {
                errors.add("error", new ActionError("error.login.notEmptyPasswords"));              
              }
            } else {
              //s_log.warn("is NOT invite form validation");
              if ((accessLogin != null) && (accessLogin.isAdmin())) {
                // And admin can modify a user without entering username/pwd.  They are not updated.
                if ((getPassword() != null) && (getPassword().equals(""))) {
                  errors.add("error", new ActionError("error.login.emptyPassword"));              
                }
              }
            }
        }           
        return errors;
    }

}

package org.calacademy.antweb;

import javax.servlet.http.HttpServletRequest;
import org.apache.struts.action.ActionError;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class EditLoginForm extends ActionForm {

    private static Log s_log = LogFactory.getLog(EditLoginForm.class);

    private String id;
    private String name;
    private String email;
    private String firstName;
    private String lastName;
    private String password;
    private String retypePassword;
    
    public String isSubmit;
    public String getIsSubmit() {
        return isSubmit;
    }
    public void setIsSubmit(String isSubmit) {
        this.isSubmit = isSubmit;
    }

    public String getId() {
     //if (AntwebProps.isDevMode()) {
     // s_log.warn("getId():" + id);
     // AntwebUtil.logShortStackTrace(6);
     //}
        return id;
    }
    public void setId(String id) {
    s_log.debug("setId(" + id + ")");
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
    
    
    /* This get/set method supports specifying action during form submission */    
    private String step;
    public String getStep() {
        return step;
    }
    public void setStep(String step) {
        this.step = step;
    }
    
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        isSubmit = null;
    }
    
    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {

        ActionErrors errors = null; 

        s_log.warn("validate() step:" + getStep());

            errors = new ActionErrors();

            if (email == null || !AntwebUtil.validEmail(email)) {
                errors.add("error", new ActionError("error.login.invalidEmail"));
            }

            if (retypePassword == null || !password.equals(retypePassword)) {
                errors.add("error", new ActionError("error.login.retypePasswordMismatch"));        
            }

            Login accessLogin = LoginMgr.getAccessLogin(request);  
            request.getSession().setAttribute("thisLogin", accessLogin);                  
            //String accessUserName = null;
            //if (accessLogin != null) accessUserName = accessLogin.getName();
            
            if (
               getPassword() != null && !getPassword().equals("") ||
                       getRetypePassword() != null && !getRetypePassword().equals("")
               ) {
                errors.add("error", new ActionError("error.login.notEmptyPasswords"));              
            }

                 
        return errors;
    }


}

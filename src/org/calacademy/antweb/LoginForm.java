package org.calacademy.antweb;


import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.*;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class LoginForm extends ActionForm {


    private static Log s_log = LogFactory.getLog(LoginForm.class);
    
	private String userName;
	private String password;

    // Mark added
    private String target;
    private String value;

	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}

	public String getUserName() {
//A.log("LoginForm s:" + userName);	
		return userName;
	}
	public void setUserName(String userName) {
//A.log("LoginForm s:" + userName);	
		this.userName = userName;
	}
	
	public String getTarget() {
		return target;
	}
	public void setTarget(String target) {
		this.target = target;
	}	

	public String getValue() {
		return value;
	}
	public void setValue(String value) {
		this.value = value;
	}	

    public ActionErrors validate(ActionMapping mapping,
                    HttpServletRequest request) {
        ActionErrors errors = new ActionErrors();

        AntwebUtil.blockFishingAttack(request, errors);

        return errors;
    }	
	
}



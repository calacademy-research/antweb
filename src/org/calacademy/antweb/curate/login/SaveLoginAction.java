package org.calacademy.antweb.curate.login;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

public final class SaveLoginAction extends Action {

    private static Log s_log = LogFactory.getLog(SaveLoginAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm f,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        ActionForward a = Check.init("project", request, mapping); if (a != null) return a;
        
        HttpSession session = request.getSession();
        boolean isNew = false;
        SaveLoginForm form = (SaveLoginForm) f;

        Login accessLogin = LoginMgr.getAccessLogin(request);
        Group accessGroup = GroupMgr.getAccessGroup(request);
        
        if (accessGroup == null) {
          return (mapping.findForward("login"));
        }

		A.log("execute() 0 id:" + form.getId() + " step:" + form.getStep() + " delete:" + form.getDelete());
        
        int id = (Integer.valueOf(form.getId()).intValue());
        Login login = null;

        java.sql.Connection connection = null;
        // try to get a database connection
        try {
            javax.sql.DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "SaveLoginAction.execute()");

            if ("delete".equals(form.getDelete())) {
                s_log.warn("execute() delete");      
                (new LoginDb(connection)).deleteById(id);
                request.setAttribute("message", "Login deleted id:" + id);
                return (mapping.findForward("message"));
            }

            login = (new LoginDb(connection)).getLogin(id);

            if (login == null) {
                isNew = true;
                login = new Login();
                login.setId(id);
            }
            login.setName(form.getName());
            login.setEmail(form.getEmail());
            login.setPassword(form.getPassword()); 
            login.setFirstName(form.getFirstName());
            login.setLastName(form.getLastName());
            login.setIsAdmin(form.isAdmin());
            login.setIsUploadSpecimens(form.isUploadSpecimens());
            login.setIsUploadImages(form.isUploadImages());

            // Handle Projects Access
            String[] projects = form.getProjects();
            if (projects == null) projects = new String[0];
            ArrayList<String> projectsList = new ArrayList(Arrays.asList(projects));            
            ArrayList<SpeciesListable> projectObjects = new ArrayList<SpeciesListable>();
            for (String projectName : projectsList) {
              Project project = ProjectMgr.getProject(projectName);
              projectObjects.add(project); 
            }
            login.setProjects(projectObjects);

            // Handle Country Access
            String[] countries = form.getCountries();
            if (countries == null) countries = new String[0];
            ArrayList<String> countriesList = new ArrayList(Arrays.asList(countries));
            ArrayList<SpeciesListable> countryObjects = new ArrayList<SpeciesListable>();
            for (String countryName : countriesList) {
              Geolocale country = GeolocaleMgr.getCountry(countryName);
              countryObjects.add(country);
            }
            login.setCountries(countryObjects);

            
            String[] groups = form.getGroups();
            String groupName = null;
            if (groups == null) {
                groups = new String[0];
                s_log.info("execute() groups is null");
            } else {
              groupName = groups[0];
              s_log.info("execute() groupName:" + groupName);   
            }
            if (groupName != null) {
              //s_log.warn("execute() login group:" + groupName);
              Group group = GroupMgr.getGroup(groupName);
              if (group != null) {
                login.setGroupId(group.getId());
              }
            }

A.log("execute() groupName:" + groupName + " loginGroupId:" + login.getGroupId() + " groups:" + groups);   

            //login.setGroups(new ArrayList(Arrays.asList(groups)));
            if (isNew) {
                s_log.info("execute() new Login via invite creation.");

                (new LoginDb(connection)).saveLogin(login);
            } else {

// was: Login accessLogin = accessGroup.getLogin();
//   boolean isOwnLogin = (accessGroup != null) && (accessGroup.getLogin().getName().equals(form.getName()));
//   The above does not work, because Brian may not have set the users name for it to be equal to the form name.

              boolean isOwnLogin = (accessLogin != null) && (!accessLogin.isAdmin());
A.log("execute() 1 accessLogin:" + accessLogin + " isOwnLogin:" + isOwnLogin + " formName:" + form.getName());
              if ((accessLogin == null)   // as is the case for invites
                || (isOwnLogin)) {        // as is for users logged in through the invite page
                ActionErrors errors = passPwdCheck(form);
                if (!errors.isEmpty()) {
                    saveErrors(request, errors);
                    return (mapping.findForward("failure"));    
                }  
                (new LoginDb(connection)).userUpdateLogin(login);
              } else {
                // it is an administrator saving a users data.  Do not modify passwords but allow the save.

                if ("Change Password".equals(form.getChangePassword())) {
                  s_log.warn("execute() changePassword:" + form.getStep()); 

                  ActionErrors errors = passPwdCheck(form);
                  if (!errors.isEmpty()) {
                    saveErrors(request, errors);
                    return (mapping.findForward("failure"));    
                  }    
		
                  String newPassword = (String) form.getPassword();		
                  (new LoginDb(connection)).changePassword(login, newPassword); 
                  return (mapping.findForward("success"));
          
                } else {
                  s_log.info("execute() 4 step is:" + form.getStep() + " changePassword:" + form.getChangePassword());
                  (new LoginDb(connection)).adminUpdateLogin(login);                
                }
              }
            }

            // create directory for curator login if it does not exist
            //String accessName = login.getName(); 
            boolean createdDir = (new Utility()).createDirectory("web/curator/" + login.getId());
            A.log("execute createdDir:" + createdDir);
            // Test for createdDir?
            
            LoginMgr.populate(connection, true);             
            
            if ("invite".equals(form.getStep())) {
                s_log.warn("execute() step:" + form.getStep()); 
                request.setAttribute("inviteLogin", login);                
                return (mapping.findForward("invite"));
            }

        } catch (AntwebException e) {
            s_log.error("execute() e:" + e + "isNew:" + isNew);
            request.setAttribute("message", e.getMessage());
            return (mapping.findForward("message"));
        } catch (SQLException e) {
            s_log.error("execute() e:" + e + "isNew:" + isNew);
            org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
            request.setAttribute("message", e.getMessage());
            return (mapping.findForward("message"));
        // So that exception is sent to log.  Still will appear on web page as stacktrace.
        } catch (Exception e) {
            s_log.error(e);
            throw e;                    
        } finally {
            DBUtil.close(connection, this, "SaveLoginAction.execute()");
        }

		return (mapping.findForward("success"));
	}


    private ActionErrors passPwdCheck(SaveLoginForm form) {
        ActionErrors errors = new ActionErrors();
        ActionMessage msg = null;
                      
        String newPassword = (String) form.getPassword();
        String newPassword2 = (String) form.getRetypePassword();
		
        if ((newPassword == null) || (newPassword.length() == 0)) {
            errors.add("error", new ActionError("error.login.needPassword"));
        } else if (!newPassword.equals(newPassword2)) {
            errors.add("error", new ActionError("error.login.passwordsMustMatch"));
        }

        A.log("passPwdCheck newPassword:" + newPassword + " errors:" + errors);
        return errors;
    }
    private ActionMessages passPwdCheck2(SaveLoginForm form) {
        ActionMessages messages = new ActionMessages();
        ActionMessage msg = null;
                      
        String newPassword = (String) form.getPassword();
        String newPassword2 = (String) form.getRetypePassword();
		
        if ((newPassword == null) || (newPassword.length() == 0)) {
            msg = new ActionMessage("error.login.needPassword");
            messages.add("message",msg);
            A.log("passPwdCheck needPassword");
        } else if (!newPassword.equals(newPassword2)) {
            msg = new ActionMessage("error.login.passwordsMustMatch");
            messages.add("message", msg);
            A.log("passPwdCheck passwords must match");
        }

        A.log("passPwdCheck newPassword:" + newPassword + " messages:" + messages);
        return messages;
    }


}

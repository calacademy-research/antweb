package org.calacademy.antweb.curate.login;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.struts.action.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

public final class SaveLoginAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(SaveLoginAction.class);

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
          return mapping.findForward("login");
        }

		s_log.debug("execute() 0 id:" + form.getId() + " step:" + form.getStep() + " delete:" + form.getDelete());
        
        int id;
        try {
            id = Integer.parseInt(form.getId());
        } catch (NumberFormatException e) {
            return mapping.findForward("permissionDenied");
        }

        // Admins may create, edit and delete any login. Everyone else may only edit their own
        // (role fields are protected again in LoginDb.updateLogin) and may not delete.
        boolean isAdminUser = LoginMgr.isAdmin(accessLogin);
        if (!isAdminUser) {
            boolean isOwn = accessLogin != null && accessLogin.getId() > 0 && accessLogin.getId() == id;
            if (!isOwn || "delete".equals(form.getDelete())) {
                s_log.warn("execute() denied: login:" + (accessLogin == null ? null : accessLogin.getId()) + " tried to save id:" + id);
                return mapping.findForward("permissionDenied");
            }
        }
        Login login = null;

        Connection connection = null;
        // try to get a database connection
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "SaveLoginAction.execute()");

            LoginDb loginDb = new LoginDb(connection);

            if ("delete".equals(form.getDelete())) {
                s_log.warn("execute() delete");
                loginDb.deleteById(id);
                request.setAttribute("message", "Login deleted id:" + id);
                return mapping.findForward("message");
            }

            login = loginDb.getLogin(id);

            if (login == null) {
                isNew = true;
                login = new Login();
                login.setId(id);
            }
            login.setName(form.getName());
            login.setEmail(form.getEmail());
            // A blank password means: keep the current one (the form no longer shows it).
            // An invitation (new login) has no password yet: "" until the user accepts.
            String newPassword = form.getPassword();
            if (newPassword != null && !newPassword.isEmpty()) login.setPassword(newPassword);
            login.setFirstName(form.getFirstName());
            login.setLastName(form.getLastName());
            login.setIsAdmin(form.isAdmin());
            login.setIsUploadSpecimens(form.isUploadSpecimens());
            login.setIsUploadImages(form.isUploadImages());
            login.setUploadAs(form.getUploadAs());

            // Handle Projects Access
            String[] projects = form.getProjects();
            if (projects == null) projects = new String[0];
            ArrayList<String> projectsList = new ArrayList<>(Arrays.asList(projects));
            ArrayList<SpeciesListable> projectObjects = new ArrayList<>();
            for (String projectName : projectsList) {
              Project project = ProjectMgr.getProject(projectName);
              projectObjects.add(project); 
            }
            login.setProjects(projectObjects);

            // Handle Country Access
            String[] countries = form.getCountries();
            if (countries == null) countries = new String[0];
            ArrayList<String> countriesList = new ArrayList<>(Arrays.asList(countries));
            ArrayList<SpeciesListable> countryObjects = new ArrayList<>();
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

s_log.debug("execute() groupName:" + groupName + " loginGroupId:" + login.getGroupId() + " groups:" + groups);

            //login.setGroups(new ArrayList(Arrays.asList(groups)));
            if (isNew) {
                s_log.info("execute() new Login via invite creation.");

                loginDb.saveLogin(login);
            } else {

// was: Login accessLogin = accessGroup.getLogin();
//   boolean isOwnLogin = (accessGroup != null) && (accessGroup.getLogin().getName().equals(form.getName()));
//   The above does not work, because Brian may not have set the users name for it to be equal to the form name.

              boolean isOwnLogin = accessLogin != null && !accessLogin.isAdmin();
s_log.debug("execute() 1 accessLogin:" + accessLogin + " isOwnLogin:" + isOwnLogin + " formName:" + form.getName());
              if (accessLogin == null   // as is the case for invites
                || isOwnLogin) {        // as is for users logged in through the invite page
                // Accepting an invite requires a password; editing your own login may leave it blank (unchanged).
                ActionErrors errors = accessLogin == null ? passPwdCheck(form) : passPwdMatch(form);
                if (!errors.isEmpty()) {
                    saveErrors(request, errors);
                    return mapping.findForward("failure");
                }  
                loginDb.updateLogin(login, accessLogin);
              } else {
                // it is an administrator saving a users data.  Do not modify passwords but allow the save.

                if ("Change Password".equals(form.getChangePassword())) {
                  s_log.warn("execute() changePassword:" + form.getStep()); 

                  ActionErrors errors = passPwdCheck(form);
                  if (!errors.isEmpty()) {
                    saveErrors(request, errors);
                    return mapping.findForward("failure");
                  }    
		
                  loginDb.changePassword(login, form.getPassword());
                  return mapping.findForward("success");
          
                } else {
                  s_log.info("execute() 4 step is:" + form.getStep() + " changePassword:" + form.getChangePassword());
                  loginDb.updateLogin(login, accessLogin);
                }
              }
            }

            // create directory for curator login if it does not exist
            //String accessName = login.getName(); 
            boolean createdDir = new Utility().createDirectory("web/curator/" + login.getId());
            s_log.debug("execute createdDir:" + createdDir);
            // Test for createdDir?
            
            LoginMgr.populate(connection, true, false);             
            
            if ("invite".equals(form.getStep())) {
                s_log.warn("execute() step:" + form.getStep()); 
                request.setAttribute("inviteLogin", login);                
                return mapping.findForward("invite");
            }

        } catch (AntwebException e) {
            s_log.error("execute() e:" + e + "isNew:" + isNew);
            request.setAttribute("message", Html.esc(e.getMessage()));
            return mapping.findForward("message");
        } catch (SQLException e) {
            s_log.error("execute() e:" + e + "isNew:" + isNew);
            AntwebUtil.logStackTrace(e);
            request.setAttribute("message", Html.esc(e.getMessage()));
            return mapping.findForward("message");
        // So that exception is sent to log.  Still will appear on web page as stacktrace.
        } catch (Exception e) {
            s_log.error(e);
            throw e;                    
        } finally {
            DBUtil.close(connection, this, "SaveLoginAction.execute()");
        }

		return mapping.findForward("success");
	}


    // Password may be blank (keep the current one); if given, both fields must match.
    private ActionErrors passPwdMatch(SaveLoginForm form) {
        ActionErrors errors = new ActionErrors();
        String newPassword = form.getPassword() == null ? "" : form.getPassword();
        String newPassword2 = form.getRetypePassword() == null ? "" : form.getRetypePassword();
        if (!newPassword.equals(newPassword2)) {
            errors.add("error", new ActionMessage("error.login.passwordsMustMatch"));
        }
        return errors;
    }

    private ActionErrors passPwdCheck(SaveLoginForm form) {
        ActionErrors errors = new ActionErrors();
        ActionMessage msg = null;
                      
        String newPassword = form.getPassword();
        String newPassword2 = form.getRetypePassword();
		
        if (newPassword == null || newPassword.length() == 0) {
            errors.add("error", new ActionMessage("error.login.needPassword"));
        } else if (!newPassword.equals(newPassword2)) {
            errors.add("error", new ActionMessage("error.login.passwordsMustMatch"));
        }

        s_log.debug("passPwdCheck errors:" + errors);
        return errors;
    }
    private ActionMessages passPwdCheck2(SaveLoginForm form) {
        ActionMessages messages = new ActionMessages();
        ActionMessage msg = null;
                      
        String newPassword = form.getPassword();
        String newPassword2 = form.getRetypePassword();
		
        if (newPassword == null || newPassword.length() == 0) {
            msg = new ActionMessage("error.login.needPassword");
            messages.add("message",msg);
            s_log.debug("passPwdCheck needPassword");
        } else if (!newPassword.equals(newPassword2)) {
            msg = new ActionMessage("error.login.passwordsMustMatch");
            messages.add("message", msg);
            s_log.debug("passPwdCheck passwords must match");
        }

        s_log.debug("passPwdCheck messages:" + messages);
        return messages;
    }


}

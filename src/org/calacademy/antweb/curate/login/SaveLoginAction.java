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
        
        // Not logged in (GET or POST): the log-in page. ("login" is not a forward of this action:
        // it answered an empty page. Until 2026-10-20 visitors reached the log-in page only because
        // SaveLoginForm.validate() refused the empty form first.)
        ActionForward notLoggedIn = LoginMgr.mustLogIn(request, mapping); if (notLoggedIn != null) return notLoggedIn;
        if (accessGroup == null) {
          return mapping.findForward("notLoggedIn");
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
        // A login is changed only by the form: a POST, whose form token SessionRequestFilter checks.
        // A link or an image on another page (a GET) cannot change or delete a login.
        if (!HttpUtil.isPost(request)) {
            request.setAttribute("message", "Please use the form to change a login.");
            return mapping.findForward("message");
        }
        Login login = null;

        Connection connection = null;
        // try to get a database connection
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "SaveLoginAction.execute()");

            LoginDb loginDb = new LoginDb(connection);

            if ("delete".equals(form.getDelete())) {
                if (accessLogin != null && accessLogin.getId() == id) {
                    request.setAttribute("message", "You cannot delete your own login.");
                    return mapping.findForward("message");
                }
                s_log.warn("execute() delete");
                loginDb.deleteById(id);
                adminLog("login " + id + " deleted by login " + accessLogin.getId());
                LoginMgr.populate(connection, true, false);
                request.setAttribute("message", "Login " + id + " was deleted. <a href=\"" + AntwebProps.getDomainApp() + "/viewLogins.do\">Login Manager</a>");
                return mapping.findForward("message");
            }

            login = loginDb.getLogin(id);
            String storedPassword = login == null ? null : login.getPassword();

            // An administrator changes an existing login (also their own): LoginEdit, in one step.
            if (isAdminUser && login != null) {
                boolean self = accessLogin.getId() == id;
                if ("Change Password".equals(form.getChangePassword()) && !self) {
                    String problem = PasswordPolicy.check(form.getPassword(), form.getRetypePassword(), login.getName(), login.getEmail());
                    if (problem != null) return refuse(request, mapping, connection, id, false, List.of(problem));
                    loginDb.changePassword(login, form.getPassword());
                    endSessions(connection, request, login, false, accessLogin);
                    List<String> changes = List.of("Password: set by you. They are logged out everywhere and log in with the new password.");
                    adminLog("login " + id + " changed by login " + accessLogin.getId() + ": password set");
                    request.setAttribute(LoginEditPage.CHANGES_ATTR, changes);
                    request.setAttribute("savedLoginId", id);
                    return mapping.findForward("success");
                }
                return saveByAdmin(mapping, request, form, connection, loginDb, login, accessLogin, self);
            }

            // Only safe characters in names, and a valid email (checked when changed).
            String nameProblem = LoginNames.problem(form.getName(), form.getFirstName(), form.getLastName(), form.getEmail(), login);
            if (nameProblem != null) {
                request.setAttribute("message", Html.esc(nameProblem));
                return mapping.findForward("message");
            }

            if (login == null) {
                isNew = true;
                // The number is chosen now, not taken from the form: above every login number ever
                // used (LoginDb.nextLoginId), so a deleted login's number and curator folder are never reused.
                int freshId = LoginDb.nextLoginId(connection, LoginDb.curatorDir());
                if (freshId != id) s_log.info("execute() new login: form number " + id + ", saved as " + freshId);
                id = freshId;
                if (new java.io.File(LoginDb.curatorDir(), String.valueOf(id)).exists()) {
                    // Never write a new login into an existing folder (its files belong to someone else).
                    s_log.error("execute() new login " + id + ": curator folder exists already, not saved");
                    return refuse(request, mapping, connection, id, true, List.of("The curator folder for login number " + id
                        + " exists already (web/curator/" + id + "). Please try again; if it happens again, tell the site administrator."));
                }
                login = new Login();
                login.setId(id);
                login.setGroupId(LoginEdit.NO_GROUP);
                String n = form.getName() == null ? "" : form.getName().trim();
                String m = form.getEmail() == null ? "" : form.getEmail().trim();
                if (n.isEmpty() && !m.isEmpty() && m.length() <= LoginEdit.MAX_USERNAME) form.setName(m);
                if (form.getName() == null || form.getName().trim().isEmpty()) {
                    return refuse(request, mapping, connection, id, true, List.of("Enter a username (or an email address of at most " + LoginEdit.MAX_USERNAME + " characters)."));
                }
                // No password: the new login gets a one-time link to choose one.
                if ((form.getPassword() == null || form.getPassword().isEmpty()) && !"invite".equals(form.getStep())) form.setStep("invite");
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
            // An invitation never grants administrator rights; an administrator can add them
            // later by editing the login.
            if ("invite".equals(form.getStep())) login.setIsAdmin(false);
            login.setIsUploadSpecimens(form.isUploadSpecimens());
            login.setIsUploadImages(form.isUploadImages());
            login.setUploadAs(form.getUploadAs());

            // Projects and countries: only a new login (made by an administrator) gets them here;
            // LoginDb.updateLogin() does not change them when people save their own login.
            ArrayList<SpeciesListable> projectObjects = new ArrayList<>();
            ArrayList<SpeciesListable> countryObjects = new ArrayList<>();
            if (isNew && isAdminUser) {
                List<String> problems = new ArrayList<>();
                TreeSet<String> projects = LoginEdit.newList(new TreeSet<>(), null, form.getAddProjects(), LoginEditOptions.projectOptions(), "live project", problems);
                TreeSet<String> countries = LoginEdit.newList(new TreeSet<>(), null, form.getAddCountries(), LoginEditOptions.countryOptions(), "country", problems);
                if (!problems.isEmpty()) return refuse(request, mapping, connection, id, true, problems);
                for (String projectName : projects) projectObjects.add(ProjectMgr.getProject(projectName));
                for (String countryName : countries) countryObjects.add(GeolocaleMgr.getCountry(countryName));
            }
            login.setProjects(projectObjects);
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
            if ("none".equals(groupName)) {
              login.setGroupId(LoginEdit.NO_GROUP);
            } else if (groupName != null) {
              //s_log.warn("execute() login group:" + groupName);
              // The Login form sends the group's number (since 2026-10-20); a name still works.
              Group group = groupName.matches("\\d{1,9}") ? GroupMgr.getGroup(Integer.parseInt(groupName)) : GroupMgr.getGroup(groupName);
              if (group != null) {
                login.setGroupId(group.getId());
              }
            }

s_log.debug("execute() groupName:" + groupName + " loginGroupId:" + login.getGroupId() + " groups:" + groups);

            // Will a new password be stored? (LoginDb.updateLogin() stores one only for your own
            // login; administrators set other logins' passwords with the Change Password button.)
            boolean isSelf = accessLogin != null && accessLogin.getId() == id;
            boolean storesPassword = newPassword != null && !newPassword.isEmpty()
                && (isNew || accessLogin == null || isSelf || "Change Password".equals(form.getChangePassword()));
            if (storesPassword) {
                String problem = checkNewPassword(request, form, storedPassword, isSelf);
                if (problem != null) {
                    ActionErrors errors = new ActionErrors();
                    errors.add("error", new ActionMessage(problem, false));
                    saveErrors(request, errors);
                    return mapping.findForward("failure");
                }
            }

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
                  endSessions(connection, request, login, isSelf, accessLogin);
                  return mapping.findForward("success");
          
                } else {
                  s_log.info("execute() 4 step is:" + form.getStep() + " changePassword:" + form.getChangePassword());
                  loginDb.updateLogin(login, accessLogin);
                }
              }
            }

            if (storesPassword && !isNew) endSessions(connection, request, login, isSelf, accessLogin);

            // create directory for curator login if it does not exist
            //String accessName = login.getName(); 
            boolean createdDir = new Utility().createDirectory("web/curator/" + login.getId());
            s_log.debug("execute createdDir:" + createdDir);
            // Test for createdDir?
            
            LoginMgr.populate(connection, true, false);             
            
            if ("invite".equals(form.getStep())) {
                s_log.warn("execute() step:" + form.getStep()); 
                request.setAttribute("inviteLogin", login);
                // One-time link (14 days) to set a password; only for a login that has none yet.
                Login saved = loginDb.getLogin(login.getId());
                if (isAdminUser && saved != null && (saved.getPassword() == null || saved.getPassword().isEmpty())) {
                    request.setAttribute("inviteLink", PasswordResetMgr.newInviteLink(connection, login.getId(), accessLogin.getId()));
                }
                return mapping.findForward("invite");
            }
            if (isNew) request.setAttribute(LoginEditPage.CHANGES_ATTR, List.of("Login created: " + login.getName()));
            request.setAttribute("savedLoginId", login.getId());

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


    /**
     * An administrator saves the login form for an existing login (LoginEdit: all changes in one
     * transaction, nothing changed when a check fails). Their own new password, if typed, is
     * stored after the checks of checkNewPassword(). The next page lists what changed.
     */
    private ActionForward saveByAdmin(ActionMapping mapping, HttpServletRequest request, SaveLoginForm form, Connection connection,
                                      LoginDb loginDb, Login login, Login accessLogin, boolean isSelf) throws SQLException {
        int id = login.getId();
        String newPassword = form.getPassword();
        // Other logins' passwords are set only with "Set this password" (a separate form).
        boolean storesPassword = isSelf && newPassword != null && !newPassword.isEmpty();
        if (storesPassword) {
            String problem = checkNewPassword(request, form, login.getPassword(), true);
            if (problem != null) return refuse(request, mapping, connection, id, false, List.of(problem));
        }

        LoginEdit.Change change = new LoginEdit.Change();
        change.id = id;
        change.name = form.getName();
        change.email = form.getEmail();
        change.firstName = form.getFirstName();
        change.lastName = form.getLastName();
        change.isAdmin = form.isAdmin();
        change.uploadSpecimens = form.isUploadSpecimens();
        change.uploadImages = form.isUploadImages();
        change.group = form.getGroups() == null || form.getGroups().length == 0 ? null : form.getGroups()[0];
        change.uploadAs = form.getUploadAs();
        change.removeProjects = form.getRemoveProjects();
        change.addProjects = form.getAddProjects();
        change.removeCountries = form.getRemoveCountries();
        change.addCountries = form.getAddCountries();
        // An older copy of the form (without editForm=2) sent the whole lists instead: keep the
        // assignments as they are rather than guess.
        if (!"2".equals(form.getEditForm())) {
            change.removeProjects = null; change.addProjects = null; change.removeCountries = null; change.addCountries = null;
        }

        LoginEdit.Result result = LoginEdit.apply(connection, change, accessLogin.getId(), true,
            LoginEditOptions.projectOptions(), LoginEditOptions.countryOptions(), LoginEditOptions.projectLabels());
        if (!result.problems.isEmpty()) return refuse(request, mapping, connection, id, false, result.problems);

        List<String> changes = new ArrayList<>(result.changes);
        if (storesPassword) {
            loginDb.changePassword(login, newPassword);
            endSessions(connection, request, login, true, accessLogin);
            changes.add("Password: changed");
        }
        if (!changes.isEmpty()) {
            adminLog("login " + id + " changed by login " + accessLogin.getId() + ": " + LoginEdit.join(changes));
            LoginMgr.populate(connection, true, false);
        }
        if (result.after != null && result.after.uploadSpecimens && result.after.groupId == LoginEdit.NO_GROUP) {
            changes.add("Note: this login may upload specimen data but has no upload group, so its uploads are refused until it has one.");
        }

        request.setAttribute(LoginEditPage.CHANGES_ATTR, changes);
        request.setAttribute("savedLoginId", id);
        if ("invite".equals(form.getStep()) && !isSelf) {
            Login saved = loginDb.getLogin(id);
            if (saved != null && (saved.getPassword() == null || saved.getPassword().isEmpty())) {
                request.setAttribute("inviteLogin", saved);
                request.setAttribute("inviteLink", PasswordResetMgr.newInviteLink(connection, id, accessLogin.getId()));
                return mapping.findForward("invite");
            }
        }
        return mapping.findForward("success");
    }

    /** Back to the form with the problems; nothing was changed. */
    private ActionForward refuse(HttpServletRequest request, ActionMapping mapping, Connection connection, int id, boolean isNew,
                                 List<String> problems) throws SQLException {
        ActionErrors errors = new ActionErrors();
        for (String p : problems) errors.add("error", new ActionMessage(p, false));
        errors.add("error", new ActionMessage("Nothing was changed.", false));
        saveErrors(request, errors);
        // The form shows this login again (also if another login was opened in another tab meanwhile).
        Login shown = isNew ? null : new LoginDb(connection).getLogin(id);
        if (shown != null) request.getSession().setAttribute("thisLogin", shown);
        LoginEditOptions.prepare(connection, request, id, isNew);
        return mapping.findForward("failure");
    }

    private static void adminLog(String text) {
        LogMgr.appendLog("admin.log", DateUtil.getFormatDateTimeStr() + " " + text);
    }

    // A new password: only from a POST, with your current password when it is your own login,
    // and following PasswordPolicy. Returns null if fine, else the reason.
    private String checkNewPassword(HttpServletRequest request, SaveLoginForm form, String storedPassword, boolean isSelf) {
        if (!HttpUtil.isPost(request)) return "Please use the form to change a password.";
        if (isSelf && !PasswordHash.matches(storedPassword, form.getCurrentPassword())) {
            return "To change your password, please enter your current password.";
        }
        return PasswordPolicy.check(form.getPassword(), form.getRetypePassword(), form.getName(), form.getEmail());
    }

    // After a password change: cancel reset links, clear must_reset and end the login's sessions
    // (except this one, when it is your own login).
    private void endSessions(Connection connection, HttpServletRequest request, Login login, boolean isSelf, Login accessLogin) throws SQLException {
        int version = new PasswordResetDb(connection).afterPasswordChange(login.getId());
        PasswordResetMgr.noteVersion(login.getId(), version);
        if (isSelf) request.getSession().setAttribute(PasswordResetMgr.SESSION_VERSION_ATTR, version);
        PasswordResetMgr.logEvent("password changed for login:" + login.getId() + " by login:" + (accessLogin == null ? "invitee" : accessLogin.getId()));
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

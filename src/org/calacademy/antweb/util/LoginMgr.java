package org.calacademy.antweb.util;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.home.*;


import jakarta.servlet.http.*;
import org.apache.struts.action.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.Nullable;

/**
 * In-memory logins and curators (table login, through LoginDb) and the session login: isAdmin, isCurator,
 * isLoggedIn, getAccessLogin (logs out a session ended by a password reset or an admin, login.session_version)
 * and logIn (new session id, CSRF token, 4-hour idle time). Loaded by AntwebMgr; postInitialize loads the
 * curators (getAllCurators) with their upload numbers. At the first load it also starts a background thread
 * (startPasswordConversion) that creates missing tables and columns (password reset, remember_token,
 * image_upload_refused, login.inactive, place_image_count, group_count), marks background jobs of the last run
 * interrupted and hashes plain-text passwords. Docs: docs/flows/email-and-password-reset.md. Tests:
 * LoginSessionFixesTest, PasswordResetTest.
 */
public class LoginMgr extends Manager {

    private static final Log s_log = LogFactory.getLog(LoginMgr.class);

    private static ArrayList<Login> s_logins;
    private static ArrayList<Curator> s_curators;
    /** When s_curators and their numbers were read (postInitialize; the count check compares them, 2026-10-22). */
    private static volatile java.sql.Timestamp s_curatorsRead;

    public static java.sql.Timestamp getCuratorsRead() {
        return s_curatorsRead;
    }

    public static void populate(Connection connection, boolean forceReload, boolean initialRun) {
      if (!forceReload && s_logins != null) return;
      
      startPasswordConversion();

      LoginDb loginDb = new LoginDb(connection);
      try {
        s_logins = loginDb.getAllLogins();
        //loginDb.getImageUploadCounter();  // initialize the LoginMgr counts.
      } catch (SQLException e) {
        s_log.warn("populate() e:" + e);
      }

      // Subsequent runs should execute this. But not the first, because of postInitialize().
        if (!initialRun) {
            try {
                postInitialize(connection);
            } catch (SQLException e) {
                s_log.warn("populate() e:" + e);
            }
        }
    }

    private static boolean s_passwordConversionStarted = false;

    // Once per application start, in the background: create the password reset
    // table and login columns if missing (PasswordResetDb), then replace any
    // plain-text passwords left in the login table with hashes (see PasswordHash).
    private static synchronized void startPasswordConversion() {
      if (s_passwordConversionStarted) return;
      s_passwordConversionStarted = true;
      Thread t = new Thread(() -> {
        try (Connection conn = DBUtilSimple.getDataSource().getConnection()) {
          try {
            new PasswordResetDb(conn).ensureSchema();
          } catch (SQLException e) {
            s_log.error("startPasswordConversion() password reset schema not created e:" + e);
          }
          try {
            new RememberTokenDb(conn).ensureSchema();
          } catch (SQLException e) {
            s_log.error("startPasswordConversion() remember_token schema not created e:" + e);
          }
          try {
            new ImageUploadRefusedDb(conn).ensureSchema();   // image upload "Not saved" list
          } catch (SQLException e) {
            s_log.error("startPasswordConversion() image_upload_refused schema not created e:" + e);
          }
          try {
            new InactiveAccountDb(conn).ensureSchema();   // login.inactive, login.inactive_since
          } catch (SQLException e) {
            s_log.error("startPasswordConversion() login.inactive columns not created e:" + e);
          }
          try {
            new org.calacademy.antweb.home.PlaceImageCountDb(conn).ensureSchema();   // place_image_count (Images on place pages)
          } catch (SQLException e) {
            s_log.error("startPasswordConversion() place_image_count not created e:" + e);
          }
          try {
            // Stored licence texts to CC BY 4.0 (decision of 2026-10-29), backup first; no-op once done.
            org.calacademy.antweb.home.LicenseUpdateDb.run(conn);
          } catch (SQLException e) {
            s_log.error("startPasswordConversion() licence update not done e:" + e);
          }
          try {
            // group_count, at start-up: CREATE TABLE commits the open transaction, which in nightly set 3 was the set's own.
            org.calacademy.antweb.home.GroupCountDb.ensureTable(conn);
          } catch (SQLException e) {
            s_log.error("startPasswordConversion() group_count not created e:" + e);
          }
          // background_job (status pages of uploads and admin jobs); jobs of the last run become "interrupted".
          BackgroundJobDb.startup(conn);
          int count = new LoginDb(conn).hashLegacyPasswords();
          if (count > 0) populate(conn, true, true);  // drop the cached plain-text values
        } catch (Exception e) {
          s_log.error("startPasswordConversion() e:" + e);
        }
      }, "password-conversion");
      t.setDaemon(true);
      t.start();
    }

    //Called through UtilAction to, in a separate thread, populate the curators with adm1.
    public static void postInitialize(Connection connection) throws SQLException {
        LoginDb loginDb = new LoginDb(connection);

        java.sql.Timestamp read = new java.sql.Timestamp(System.currentTimeMillis());
        s_curators = loginDb.getAllCurators();
        s_curatorsRead = read;

        // Why can't we loop through s_curators?
        ArrayList<Curator> tempList = new ArrayList<>(s_curators);

        for (Curator curator : tempList) {
            loginDb.postInstantiate(curator);
        }
    }

    public static void Xreload(Login login, Connection connection) {
        LoginDb loginDb = new LoginDb(connection);
        try {
          Login newLogin = loginDb.getLogin(login.getId());
          int index = s_logins.indexOf(login);

          A.log("reload() attempt login:" + newLogin.getId() + " at index:" + index);

          if (index != -1) {
            s_logins.set(index, login);
            A.log("reload login:" + newLogin.getId() + " at index:" + index);
          }

          Curator newCurator = loginDb.getCurator(login.getId());
          A.log("reload() attempt curator:" + newCurator.getId());

          if (newCurator != null) {
            index = s_curators.indexOf(login);
              A.log("reload() attempt curator:" + newCurator.getId() + " at index:" + index);

            if (index != -1) {
                s_curators.set(index, newCurator);
                A.log("reload() curator:" + newCurator.getId() + " at index:" + index);
            }
          }
        } catch (SQLException e) {
          s_log.error("reload() e:" + e);
        }
    }

    public static Login getAdminLogin() {
      return getLogin(1);
    }


    public static Login getAnonLogin() {
        Login login = new Login();
        login.setName("Anonymous");
        login.setCreated(new Date());
        login.setGroupId(-1);   // Hardcoded anonymous group.    
        login.setIsAdmin(false);
        return login;            
    }

    public static Login getLogin(HttpServletRequest request) {
        return getAccessLogin(request);
    }
    public static Login getAccessLogin(HttpServletRequest request) {
		// getSession(false): reading who is logged in never creates a session. After a session is ended below, a later
		// call on the same page (the header, after part of the page was sent) made a new one and failed with "Cannot
		// create a session after the response has been committed": the page was cut off (staging e2e 2026-10-27).
		HttpSession session = request.getSession(false);
		if (session == null) return null;
        Login login = (Login) session.getAttribute("accessLogin");
        // Sessions ended by a password reset or by an administrator (login.session_version).
        if (login != null && login.getId() > 0 && !PasswordResetMgr.isSessionCurrent(session, login.getId())) {
            s_log.info("getAccessLogin() session ended for login:" + login.getId());
            // Log the session out, but do not invalidate it: this runs anywhere in a request, also in the header after
            // part of the page was sent, and the page's later JSPs need a valid session. Invalidating it there cut
            // the page off ("Cannot create a session after the response has been committed"; staging e2e
            // 2026-10-27, a test logging in to the same account while another thread loaded the home page).
            session.removeAttribute("accessLogin");
            session.removeAttribute(Csrf.SESSION_ATTR);
            return null;
        }
        return login;
    }

    /**
     * Log the login in: new session id (prevents session fixation), then the login in the session.
     * Used by the login form (LoginAction) and by "Keep me logged in" (RememberMeMgr).
     */
    public static final int LOGGED_IN_IDLE_SECONDS = 4 * 60 * 60;

    public static void logIn(HttpServletRequest request, Login login) {
        request.getSession();
        request.changeSessionId();
        // New CSRF token for the logged-in session (a token seen before login is not reused).
        request.getSession().removeAttribute(Csrf.SESSION_ATTR);
        // Logged-in sessions last 4 hours idle (anonymous ones keep Tomcat's 30 minutes), so a
        // curator editing a long page doesn't lose the session (and its form token) mid-edit.
        request.getSession().setMaxInactiveInterval(LOGGED_IN_IDLE_SECONDS);
        setAccessLogin(request, login);
    }

    /** Log the login in on this session (call after request.changeSessionId()). */
    public static void setAccessLogin(HttpServletRequest request, Login login) {
        HttpSession session = request.getSession();
        session.setAttribute("accessLogin", login);
        if (login != null) PasswordResetMgr.stampSession(session, login.getId());
    }

    public static void removeAccessLogin(HttpServletRequest request) {
		request.getSession().setAttribute("accessLogin", null); 
    }

    // See the Check.java for how to invoke...
    public static ActionForward mustLogIn(HttpServletRequest request, ActionMapping mapping) {
		if (LoginMgr.getAccessLogin(request) == null) {
		    s_log.debug("mustLogin() login:" + LoginMgr.getAccessLogin(request));
			return mapping.findForward("notLoggedIn");
		}
        return null;
    }
    public static ActionForward mustBeAdmin(HttpServletRequest request, ActionMapping mapping) {
		if (!LoginMgr.isAdmin(request)) {
			return mapping.findForward("notLoggedIn");
		}
        return null;
    }
    public static ActionForward mustBeCurator(HttpServletRequest request, ActionMapping mapping) {
        if (!LoginMgr.isCurator(request)) {
            return mapping.findForward("notLoggedIn");
        }
        return null;
    }

    public static boolean isAdmin(HttpServletRequest request) {
        Login accessLogin = getAccessLogin(request);
	    return isAdmin(accessLogin);
	}
	
	public static boolean isAdmin(Login accessLogin) {
        return accessLogin != null && accessLogin.isAdmin();
    }

    public static boolean isAnonLogin(Login login) {
        if ("Anonymous".equals(login.getName())) return true;
        return false;
    }

    public static boolean isLoggedIn(HttpServletRequest request) {
        Login login = getAccessLogin(request);
        boolean isLoggedIn = (login != null);
        //A.log("isLoggedIn() isLoggedIn:" + isLoggedIn);
        return isLoggedIn;
    }

    public static boolean isCurator(HttpServletRequest request) {
        Login accessLogin = getAccessLogin(request);
	    return isCurator(accessLogin);
	}
	public static boolean isCurator(Login accessLogin) {
        return accessLogin != null && accessLogin.isCurator();
    }

    public static boolean isDeveloper(HttpServletRequest request) {
        Login accessLogin = getAccessLogin(request);
	    return isDeveloper(accessLogin);
	}
	/** Developer tools and debug output are for administrators (the admin role, not login ids). */
	public static boolean isDeveloper(Login accessLogin) {
        return accessLogin != null && accessLogin.isAdmin();
    }
    
    public static boolean isMingnaUtep(Login accessLogin) {
        return accessLogin != null && accessLogin.getId() == 9716;
    }

    public static boolean isJack(HttpServletRequest request) {
        Login accessLogin = getAccessLogin(request);
	    return isJack(accessLogin);
	}
	public static boolean isJack(Login accessLogin) {
        return accessLogin != null && accessLogin.getId() == 2;
    }

    public static boolean isInitialized() {
      return s_logins != null && s_curators != null;
    }

    public static ArrayList<Login> getLogins() {
      return s_logins;
    }

    public static Login getLogin(int id) {
      if (getLogins() == null) return null; // Will happen during initialization.
      for (Login login : getLogins()) {
        if (id == login.getId()) return login;
      }     
      return null;
    }
    public static Login getLogin(String name) {
      for (Login login : getLogins()) {
        if (name.equals(login.getName())) return login;
      }     
      return null;
    }

    public static ArrayList<Curator> getCurators() {
      return s_curators;
    }

    public static @Nullable Curator getCurator(Login login) {
        if (login == null) return null;
        return getCurator(login.getId());
    }

    public static @Nullable Curator getCurator(String idStr) {
        if (idStr == null) return null;
        int id = Integer.parseInt(idStr);
        return getCurator(id);
    }
    public static @Nullable Curator getCurator(int id) {
        if (getCurators() == null) return null;
        for (Curator curator : getCurators()) {
            //A.log("getCurator() id:" + id + " curatorID:" + curator.getId());
            if (id == curator.getId()) return curator;
        }
        return null;
    }

// ------------------------------------------------

    private static final HashMap<String, Counts> imageUploadCounts = new HashMap<>();

    static class Counts {
      int imageUploads = 0;
      int imagesUploaded = 0;

      public Counts(int i1, int i2) {
        imageUploads = i1;
        imagesUploaded = i2;
      }

      public String toString() {
        return "counts - imageUploads:" + imageUploads + " imagesUploaded:" + imagesUploaded;
      }
    }

    public static void addImageUpload(String groups, int count) {
      // groups could be a comma separated list. For now, ignore.
      
      int groupId = 0;
      if (!groups.contains(",")) {
        groupId = Integer.parseInt(groups);
      }
      if (groupId == 0) return;
      Counts counts = imageUploadCounts.get("" + groupId);    
      if (counts == null) {
        counts = new Counts(1, count);
      } else {
        counts = new Counts(counts.imageUploads + 1, counts.imagesUploaded + count);
      }
      //A.log("addImageUpload() group:" + groups + " count:" + count + " counts:" + counts);
      imageUploadCounts.put("" + groupId, counts);
    }

    public static int getImageUploadCount(Curator curator) {
      int groupId = curator.getGroupId();
      Counts counts = imageUploadCounts.get("" + groupId);
      if (counts == null) return 0;

      s_log.debug("getImageUploadCount() curator:" + curator + " " + counts);

      return counts.imageUploads;
    }

    public static int getImagesUploadedCount(Curator curator) {
      int groupId = curator.getGroupId();
      Counts counts = imageUploadCounts.get("" + groupId);
      if (counts == null) return 0;
      return counts.imagesUploaded;
    }

}


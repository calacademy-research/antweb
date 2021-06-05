package org.calacademy.antweb.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.home.*;


import javax.servlet.http.*;
import org.apache.struts.action.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class LoginMgr extends Manager {

    private static final Log s_log = LogFactory.getLog(LoginMgr.class);

    private static ArrayList<Login> s_logins = null;
    private static ArrayList<Curator> s_curators = null;

    public static void populate(Connection connection, boolean forceReload, boolean initialRun) {
      if (!forceReload && (s_logins != null)) return;      
      
      LoginDb loginDb = (new LoginDb(connection));
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

    //Called through UtilAction to, in a separate thread, populate the curators with adm1.
    public static void postInitialize(Connection connection) throws SQLException {
        LoginDb loginDb = new LoginDb(connection);
        s_curators = loginDb.getAllCurators();

        ArrayList<Curator> tempList = new ArrayList<Curator>();
        for (Curator curator : s_curators) {
            tempList.add(curator);
        }

        for (Curator curator : tempList) {
            loginDb.postInstantiate(curator);
        }
    }

    public static Login getAdminLogin() {
      return getLogin(1);
    }
    
    public static Login getAnonLogin() {
        Login login = new Login();
        login.setName("Anonymous");
        login.setCreated(new java.util.Date());
        login.setGroupId(-1);   // Hardcoded anonymous group.    
        login.setIsAdmin(false);
        return login;            
    }
    
    public static Login getAccessLogin(HttpServletRequest request) {
		return (Login) request.getSession().getAttribute("accessLogin"); 
    }

    public static void removeAccessLogin(HttpServletRequest request) {
		request.getSession().setAttribute("accessLogin", null); 
    }

    // See the Check.java for how to invoke...
    public static ActionForward mustLogIn(HttpServletRequest request, ActionMapping mapping) {
		if (LoginMgr.getAccessLogin(request) == null) {
		    A.log("mustLogin() login:" + LoginMgr.getAccessLogin(request));
			return (mapping.findForward("notLoggedIn"));
		}
        return null;
    }
    public static ActionForward mustBeAdmin(HttpServletRequest request, ActionMapping mapping) {
		if (!LoginMgr.isAdmin(request)) {
			return (mapping.findForward("notLoggedIn"));
		}
        return null;
    }

    public static boolean isAdmin(HttpServletRequest request) {
        Login accessLogin = getAccessLogin(request);
	    return isAdmin(accessLogin);
	}
	
	public static boolean isAdmin(Login accessLogin) {
	  if ((accessLogin != null) && (accessLogin.isAdmin())) return true;
	  return false;
	}

    public static boolean isLoggedIn(HttpServletRequest request) {
        Login accessLogin = getAccessLogin(request);
        return (accessLogin != null);
    }
    
    public static boolean isCurator(HttpServletRequest request) {
        Login accessLogin = getAccessLogin(request);
	    return isCurator(accessLogin);
	}
	public static boolean isCurator(Login accessLogin) {
	  if ((accessLogin != null) && (accessLogin.isCurator())) return true;
	  return false;
	}

    public static boolean isDeveloper(HttpServletRequest request) {
        Login accessLogin = getAccessLogin(request);
	    return isDeveloper(accessLogin);
	}
	public static boolean isDeveloper(Login accessLogin) {
	  if ((accessLogin != null) 
	    // A specific list of Developer admins.
	    && (
	     (accessLogin.getId() == Login.MARK)
         || (accessLogin.getId() == Login.TEST_LOGIN)
	    )) return true;
	  return false;
	}    
    

    public static boolean isMark(HttpServletRequest request) {
	    return LoginMgr.isMark(getAccessLogin(request));
	}
	public static boolean isMark(Login accessLogin) {
        if ((accessLogin != null) && (accessLogin.getId() == 22)) return true;
        return false;
    }

    public static boolean isPeter(HttpServletRequest request) {
        return LoginMgr.isPeter(getAccessLogin(request));
    }
    public static boolean isPeter(Login accessLogin) {
        if ((accessLogin != null) && (accessLogin.getId() == 36)) return true;
        return false;
    }

    public static boolean isMichele(HttpServletRequest request) {
        Login accessLogin = getAccessLogin(request);
	    return isMichele(accessLogin);
	}
	public static boolean isMichele(Login accessLogin) {
	  if ((accessLogin != null) && (accessLogin.getId() == 23)) return true;
	  return false;
	}	
    public static boolean isJack(HttpServletRequest request) {
        Login accessLogin = getAccessLogin(request);
	    return isJack(accessLogin);
	}
	public static boolean isJack(Login accessLogin) {
	  if ((accessLogin != null) && (accessLogin.getId() == 2)) return true;
	  return false;
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

    public static Curator getCurator(int id) {
        if (getCurators() == null) return null;
        for (Curator curator : getCurators()) {
            if (id == curator.getId()) return curator;
        }
        return null;
    }

// ------------------------------------------------

    private static HashMap<String, Counts> imageUploadCounts = new HashMap<String, Counts>();

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
        groupId = Integer.valueOf(groups).intValue();
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

      A.log("getImageUploadCount() curator:" + curator + " " + counts);

      return counts.imageUploads;
    }

    public static int getImagesUploadedCount(Curator curator) {
      int groupId = curator.getGroupId();
      Counts counts = imageUploadCounts.get("" + groupId);
      if (counts == null) return 0;
      return counts.imagesUploaded;
    }

}


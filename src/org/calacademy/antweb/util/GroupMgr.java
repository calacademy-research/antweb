package org.calacademy.antweb.util;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.home.*;


import javax.servlet.http.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class GroupMgr {

    private static final Log s_log = LogFactory.getLog(GroupMgr.class);

    private static ArrayList<Group> s_groups;
    private static ArrayList<Group> s_uploadGroups;
        
    public static void populate(Connection connection) {
      populate(connection, false);
    }
    
    public static void populate(Connection connection, boolean forceReload) {
        s_log.debug("populate()");

      if (!forceReload && (s_groups != null)) return;

      GroupDb groupDb = (new GroupDb(connection));
      try {
        //A.log("populate()");
        s_groups = groupDb.getAllGroups();

          //A.log("populate() getAllGroups");
        s_uploadGroups = groupDb.getAllGroupsWithSpecimenData();

        //A.log("=populate() getAllGroupsWithSpecimenData");
      } catch (SQLException e) {
        s_log.warn("populate() e:" + e);
      }

      //A.log("GroupMgr.populate() groups:" + s_groups);
    }

    private static Group getAnonGroup() {
        Group group = new Group();
        group.setName("");
        group.setId(-1);             
        return group;
    }


    public static Group getAccessGroup(HttpServletRequest request) {
		Login accessLogin = (Login) request.getSession().getAttribute("accessLogin"); 
        if (accessLogin != null) return accessLogin.getGroup();
        return null;
    }

    public static boolean isCAS(HttpServletRequest request) {
        Group accessGroup = getAccessGroup(request);
	    return isCAS(accessGroup);
	}
	public static boolean isCAS(Group accessGroup) {
        return accessGroup != null && accessGroup.getId() == 1;
    }

    public static boolean isDavis(HttpServletRequest request) {
        Group accessGroup = getAccessGroup(request);
	    return isDavis(accessGroup);
	}
	public static boolean isDavis(Group accessGroup) {
        return accessGroup != null && accessGroup.getId() == 16;
    }
		
		

    public static ArrayList<Group> getGroups() {
      return s_groups;
    }

    public static ArrayList<Group> getUploadGroups() {
      if (s_uploadGroups == null) {
        //s_log.warn("getUploadGroups() not done initializing()");
        return null;
      }
      return s_uploadGroups;
    }

    public static Group getAdminGroup() {
      return getGroup(1);
    }    
    
    public static Group getGroup(int id) {
      if (getGroups() == null) return null; // will happen during startup.
      if (id == -1) return getAnonGroup();
      for (Group group : getGroups()) {
        if (id == group.getId()) return group;
      }     
      return null;
    }
    public static Group getGroup(String name) {
      for (Group group : getGroups()) {
        if (name.equals(group.getName())) return group;
      }     
      return null;
    }

}

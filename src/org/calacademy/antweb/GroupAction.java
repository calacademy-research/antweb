package org.calacademy.antweb;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;
import java.util.*;

import org.calacademy.antweb.upload.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

//import org.apache.commons.collections.*;
//import com.google.common.collect.*;

public final class GroupAction extends Action {

    private static final Log s_log = LogFactory.getLog(GroupAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        ActionForward invalid = HttpUtil.invalidRequest(request, mapping); if (invalid != null) return invalid;        

		// Extract attributes we will need
		HttpSession session = request.getSession();
			
        DynaActionForm df = (DynaActionForm) form;
        String name = (String) df.get("name"); // Name could be id or name. We try id first.
        int groupId = 0;
        Integer id = (Integer) df.get("id");
        if (id != null) groupId = id;
        
        //A.log("GroupAction.execute() name:" + name + " groupId:" + groupId);

        if (!Utility.isBlank(name) || groupId != 0) {
          Group group = getGroup(name, groupId, request);

          if (group == null) {
			  String message = "  Group not found:" + name + ".";
			  request.setAttribute("message", message);
			  return mapping.findForward("message");
          }

          Connection connection = null;
          String dbMethodName = DBUtil.getDbMethodName("GroupAction.execute()");
          try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);
            
            LoginDb loginDb = new LoginDb(connection);
			group.setCurators(loginDb.getCurators(group.getId()));
            //A.log("execute() setCurators() groupId:" + group.getId());
          } catch (SQLException e) {
            AntwebUtil.log("GroupAction.execute() e:" + e);
          } finally {
            DBUtil.close(connection, this, dbMethodName);
          }
          
//          request.setAttribute("curators" curators);
		  request.setAttribute("group", group);
		  return mapping.findForward("group");
        } else {
          String orderBy = (String) df.get("orderBy");
          if (orderBy != null && orderBy.toLowerCase().contains("select")) {
              s_log.warn("execute() rejected orderBy:" + orderBy);
              request.setAttribute("message", "invalid request");
              return mapping.findForward("message");
          }
          ArrayList<Group> groups = getUploadGroups(request, orderBy);
          if (groups == null) {
			  String message = " Problem fetching groups with orderBy:" + orderBy;
              s_log.warn("execute() " + message);
			  request.setAttribute("message", message);
			  return mapping.findForward("message");
          }
		  request.setAttribute("groups", groups);
		  //A.log("GroupAction.execute() orderby:" + orderBy + " groups:" + groups);
		  return mapping.findForward("groups");
        }
		
		//s_log.warn("execute() set request attribute locality:" + locality);
	}

    private Group getGroup(String name, int groupId, HttpServletRequest request) {
		Group group = null;
		Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("GroupAction.getGroup()");
		try {
			DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);
            GroupDb groupDb = new GroupDb(connection);
            group = groupDb.getGroup(groupId);    
            //A.log("execute() code:" + code + " group:" + group);
            if (group == null) {
              group = groupDb.getGroup(name);
              //A.log("getGroup() groupId:" + groupId + " name:" + name + " group:" + group);
            } // else A.log("NOT");
	
			if (group == null) {        
              return null;
			}

            UploadDb uploadDb = new UploadDb(connection);
            Upload upload = uploadDb.getLastUploadByGroup(groupId);
            group.setLastUpload(upload);
            //A.log("GroupAction.getGroup() groupId:" + groupId + " upload:" + upload);
            
            group.setFirstUploadDate(uploadDb.getFirstUploadDate(groupId));
            group.setLastUploadDate(uploadDb.getLastUploadDate(groupId));
            group.setUploadCount(uploadDb.getUploadCount(groupId));
            group.setCuratorList(groupDb.getCuratorList(groupId));
            
            Map map = new ObjectMapDb(connection).getGroupMap(groupId);
            request.setAttribute("map", map);
                       
		} catch (SQLException e) {
			s_log.error("getGroup() e:" + e);
		} finally {	
			DBUtil.close(connection, this, dbMethodName);
		}
		return group;
	}	
 
    private ArrayList<Group> getUploadGroups(HttpServletRequest request, String orderBy) {       
        ArrayList<Group> groups = null;
		Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("GroupAction.getUploadGroups()");
		try {
			DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);
            GroupDb groupDb = new GroupDb(connection);
            groups = groupDb.getUploadGroups(orderBy);
            
            UploadDb uploadDb = new UploadDb(connection);
            for (Group group : groups) {
                int groupId = group.getId();
				Upload lastUpload = uploadDb.getLastUploadByGroup(groupId);
				group.setLastUpload(lastUpload);
				//A.log("GroupAction.getGroup() groupId:" + groupId + " lastUpload:" + lastUpload.getCreated	()); // + " upload:" + upload.getId());
			
				group.setFirstUploadDate(uploadDb.getFirstUploadDate(groupId));
				group.setLastUploadDate(uploadDb.getLastUploadDate(groupId));
				group.setUploadCount(uploadDb.getUploadCount(groupId));

				group.setCuratorList(groupDb.getCuratorList(groupId));
            }
		} catch (SQLException e) {
			s_log.error("getUploadGroups() e:" + e);
		} finally {	
			DBUtil.close(connection, this, dbMethodName);
		}
		return groups;
	}	
	
}

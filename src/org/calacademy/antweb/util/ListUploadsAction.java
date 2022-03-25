package org.calacademy.antweb.util;

import java.io.IOException;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.upload.*;
import org.calacademy.antweb.imageUploader.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class ListUploadsAction extends Action {

    private static final Log s_log = LogFactory.getLog(ListUploadsAction.class);

    public ActionForward execute(
        ActionMapping mapping,
        ActionForm form,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException, ServletException {
        
        ActionForward a = Check.initLogin(request, mapping); if (a != null) return a;
        
        Locale locale = getLocale(request);
        HttpSession session = request.getSession();
                                        
        String target = HttpUtil.getTarget(request);        
        if (target.contains("listImageUploads")) {
          ActionForward b = Check.init(Check.UPLOAD, request, mapping); if (b != null) return b;
/*
          if (!ArtistMgr.isInitialized()) {                
            request.setAttribute("message", "Server is initializing...");
            return (mapping.findForward("message")); 
          }
*/        
          listImageUploads(request, form);          
      //  } else if (target.contains("listWorldantsUploads")) {
      //    listWorldantsUploads(request, form);
        } else {
        
          listSpecimenUploads(request, form);
        }
        
        return mapping.findForward("success");
    }

    private void listImageUploads(HttpServletRequest request, ActionForm form) {
    
        DynaActionForm df = (DynaActionForm) form;
        Integer groupId = (Integer) df.get("groupId");
        int groupIdInt = 0;
        if (groupId != null) {
            groupIdInt = groupId;
        }
        String groupName = null;

        Integer curatorId = (Integer) df.get("curatorId");        
        int curatorIdInt = 0;
        if (curatorId != null) {
            curatorIdInt = curatorId;
        }

        ArrayList<ImageUpload> imageUploads = null;
        Connection connection = null;
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "listImageUploads()");          

            String criteria = "";
            if (groupIdInt != 0) {
              criteria += " where group_id = " + groupIdInt;
            }
            if (curatorIdInt != 0) {
              criteria += " where curator_id = " + curatorIdInt;
            }
            imageUploads = new ImageUploadDb(connection).getImageUploads(criteria);

        } catch (SQLException e) {
            s_log.error("listImageUploads() e:" + e);
        } finally {
            DBUtil.close(connection, this, "listImageUploads()");
        }            
                    
        String message = null;

        if (groupIdInt != 0) {
          Group group = GroupMgr.getGroup(groupId);
          String title = "" + groupId;
          if (group != null) title = group.getAbbrev();
          title = "<a href='" + AntwebProps.getDomainApp() + "/group.do?id=" + groupId + "'>" + title + "</a>";
          message = "Image Uploads Reports for Group: " + title;
        } else if (curatorIdInt != 0) {
          Curator curator = LoginMgr.getCurator(curatorId);
          String title = "" + curatorId;
          if (curator != null) title = curator.getDisplayName();
          title = "<a href='" + AntwebProps.getDomainApp() + "/curator.do?id=" + curatorId + "'>" + title + "</a>";
          message = "Image Upload Reports for Login: " + title;
        } else {
          message = "Image Upload Reports for All Groups";        
        }

        request.setAttribute("message", message);
        request.setAttribute("imageUploads", imageUploads);
    }      
    
    private void listSpecimenUploads(HttpServletRequest request, ActionForm form) {
    
        DynaActionForm df = (DynaActionForm) form;
        Integer groupId = (Integer) df.get("groupId");        
        int groupIdInt = 0;
        if (groupId != null) {
            groupIdInt = groupId;
        }
        String groupName = null;

        Integer loginId = (Integer) df.get("loginId");        
        int loginIdInt = 0;
        if (loginId != null) {
            loginIdInt = loginId;
        }
    
        ArrayList<Upload> uploads = new ArrayList<>();
        Connection connection = null; 
        Statement stmt = null;
        ResultSet rset = null;
        String query = null;
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "listSpecimenUploads()");          

            //s_log.warn("execute groupId:" + groupId);

            stmt = DBUtil.getStatement(connection, "listSpecimenUploads()");
            
            // number of specimens imaged
            query = "select log_file_name, created, group_name, login_id, group_id from upload ";
            if (groupIdInt != 0) {
              query += " where group_id = " + groupIdInt;
            }
            if (loginIdInt != 0) {
              query += " where login_id = " + loginIdInt;
            }
            query += " order by created desc";
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                Upload upload = new Upload();
                upload.setLogFileName(rset.getString("log_file_name"));
                upload.setCreated(rset.getDate("created"));
                groupName = rset.getString("group_name");
                upload.setGroupName(groupName);
                upload.setGroupId(rset.getInt("group_id"));
                upload.setLoginId(rset.getInt("login_id"));
                uploads.add(upload);
            }

        } catch (SQLException e) {
            s_log.error("listSpecimenUploads() e:" + e + " theQuery:" + query);
        } finally {
            DBUtil.close(connection, stmt, rset, this, "listSpecimenUploads()");
        }            
                    
        String message = null;

        if (groupIdInt != 0) {
          Group group = GroupMgr.getGroup(groupId);
          String title = "" + groupId;
          if (group != null) title = group.getAbbrev();
          title = "<a href='" + AntwebProps.getDomainApp() + "/group.do?id=" + groupId + "'>" + title + "</a>";
          message = "Specimen Upload Reports for Group: " + title;
        } else if (loginIdInt != 0) {
          Login login = LoginMgr.getLogin(loginId);
          String title = "" + loginId;
          if (login != null) title = login.getDisplayName();
          title = "<a href='" + AntwebProps.getDomainApp() + "/curator.do?id=" + loginId + "'>" + title + "</a>";
          message = "Specimen Upload Reports for Login: " + title;
        } else {
          message = "Specimen Upload Reports for All Groups";        
        }

        request.setAttribute("message", message);
        request.setAttribute("uploads", uploads);
    }    

}

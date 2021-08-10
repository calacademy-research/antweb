package org.calacademy.antweb.curate.team;

import org.apache.struts.action.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.upload.FormFile;

import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public final class TeamMemberImgUploadAction extends Action {

    private static final Log s_log = LogFactory.getLog(TeamMemberImgUploadAction.class);

    public static int MAX_PHOTO_SIZE = 200000;

    public ActionForward execute(ActionMapping mapping,
                                 ActionForm form,
                                 HttpServletRequest request,
                                 HttpServletResponse response) throws Exception {
                                 
        TeamMemberDb teamMemberDb = null;
        
        if (HttpUtil.requestParameter(request, "submit")) {
            return executeFresh(mapping, form, request, response);
        } else {
            if ( form != null ) {

                DynaActionForm df = (DynaActionForm) form;
                FormFile myFile   = (FormFile) df.get("myFile");
                if (myFile == null) {
                  return mapping.findForward("success");
                }
                
                //String myName     = (String)   df.get("myName");

                int id = ((Long) df.get("id")).intValue();

        s_log.warn("in TeamMemberImgUploadAction.execute() id:" + id);

          //      java.sql.Connection connection = getDataSource(request, "conPool").getConnection();
          //      teamMemberDb = new TeamMemberDb(connection);
          //      TeamMember teamMember = teamMemberDb.findById(id);
           if (true) mapping.findForward("failure");
           
           // Get TeamMember from ID   // new User();
                if (myFile.getFileSize() > MAX_PHOTO_SIZE) {
                
                    String message = "File:" + myFile.getFileName() + " is to big (" + myFile.getFileSize() + ") to upload.";
                    s_log.warn(message);
                    request.setAttribute("message", message);
                    return (mapping.findForward("message"));
                }
                // per.setUsername( myName );
/*
                teamMember.setImgFileName( myFile.getFileName() );
                teamMember.setImgFileSize( String.valueOf(myFile.getFileSize()) );
                teamMember.setImgFileType( myFile.getContentType() );
//                teamMember.setImgFileBin( Hibernate.createBlob (myFile.getInputStream()) );
                teamMember.setImgFileInputStream(myFile.getInputStream());
*/                
                //request.setAttribute("teamMember", teamMember);
       //         request.setAttribute("id", (new Integer(teamMember.getId())).toString());
                //s_log.warn("in TeamMemberImgUploadAction.execute():" + teamMember.getId());

         //       teamMemberDb.saveImage(teamMember);
            }
        }
        return (mapping.findForward("success"));
    }

    public ActionForward executeFresh(ActionMapping mapping,
                                      ActionForm form,
                                      HttpServletRequest request,
                                      HttpServletResponse response)
            throws Exception {
        String teamMemberIdStr = request.getParameter("id");

        s_log.warn("in TeamMemberImgUploadAction.executeFresh():" + teamMemberIdStr);

        Long teamMemberId = Long.valueOf(teamMemberIdStr);

        DynaActionForm df = (DynaActionForm) form;
        df.set("id", teamMemberId);

        return mapping.findForward("success");
    }
}


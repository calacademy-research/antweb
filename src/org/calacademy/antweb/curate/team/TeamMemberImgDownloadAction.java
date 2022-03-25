package org.calacademy.antweb.curate.team;

import org.apache.struts.action.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletOutputStream;
import java.io.InputStream;
import java.sql.Connection;

import org.calacademy.antweb.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public final class TeamMemberImgDownloadAction extends Action {

    private static final Log s_log = LogFactory.getLog(TeamMemberImgDownloadAction.class);
    
    public ActionForward execute(ActionMapping mapping, 
                                 ActionForm form, 
                                 HttpServletRequest request, 
                                 HttpServletResponse response) throws Exception {

        String id = request.getParameter("id");
        int idInt = Integer.parseInt(id);

       s_log.warn("in TeamMemberImgDonwload id is " + id);


		Connection connection = null;
        TeamMember teamMember = null;
    //    try {
      //      connection = getDataSource(request, "conPool").getConnection();

        //    teamMember = (new TeamMemberDb(connection)).findById(idInt);
  /*      } catch (SQLException e) {
            s_log.error("execute() e:" + e);
        } catch (NumberFormatException e) {
          s_log.error("id:" + id + " e:" + e);
          return null;
        } finally {
            try {
                connection.close();
            } catch (SQLException e) {
                s_log.error("execute() finally e:" + e);
            }
        }
*/
        if (teamMember == null) {
          s_log.error("execute() no teamMember:" + idInt);
          return null;
        }
        String fileSize = teamMember.getImgFileSize();
       // s_log.warn("in TeamMemberImgDonwload filesize:" + fileSize);        
        if (fileSize != null) {       
          response.setContentLength  ( Integer.parseInt(fileSize) );
        }
        response.setContentType    ( teamMember.getImgFileType());
        response.setHeader         ("Content-disposition", "attachment; filename=" + teamMember.getImgFileName());
        response.setHeader         ("Cache-Control", "max-age=600");

        ServletOutputStream outStream = response.getOutputStream();


        if (teamMember.getImgFileBin() != null) {
          InputStream in = teamMember.getImgFileBin().getBinaryStream();
          //s_log.warn("in TeamMemberImgDownload inputStream:" + in);        

          byte[] buffer = new byte[32768];
          int n = 0;
          while ( ( n = in.read(buffer)) != -1) {
            outStream.write(buffer, 0, n);
          }
          in.close();
          outStream.flush();
        } else {
          s_log.warn("teamMemberImgDownload is null for teamMemberId:" + teamMember.getId());
        }

        return null;
     }

}

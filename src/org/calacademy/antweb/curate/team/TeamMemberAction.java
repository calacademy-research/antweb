package org.calacademy.antweb.curate.team;

import javax.servlet.http.*;
import org.apache.struts.action.*;

import java.sql.*;
import java.util.ArrayList;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
import org.calacademy.antweb.*;
import org.calacademy.antweb.home.*;

public class TeamMemberAction extends Action {

    private static final Log s_log = LogFactory.getLog(TeamMemberAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm f,
		HttpServletRequest request, HttpServletResponse response) {

        TeamMemberDb teamMemberDb = null;
		Connection connection = null;
		ArrayList<TeamMember> curatorList = new ArrayList<>();
        try {
          connection = getDataSource(request, "conPool").getConnection();
          teamMemberDb = new TeamMemberDb(connection);

          TeamMemberForm form = (TeamMemberForm) f;

          if (form.getId() != null) {
            // A submit!
            s_log.warn("execute() id:" + form.getId());
            TeamMember teamMember = new TeamMember();
            teamMember.setId(Integer.parseInt(form.getId()));
            teamMember.setRoleOrg(form.getRoleOrg());
            teamMember.setName(form.getName());
            teamMember.setEmail(form.getEmail());
            teamMember.setText(form.getText());
            //teamMember.setImgFileType(form.getFileType());
/*
            teamMember.setImgLoc(form.getImgLoc());
            teamMember.setImgWidth(form.getImgWidth());
            teamMember.setImgHeight(form.getImgHeight());
            teamMember.setFileName(form.getFileName());
            teamMember.setFileType(form.getFileType());
            teamMember.setFileSize(form.getFileSize());
            teamMember.setFileBin(form.getFileBin());
            teamMember.setSection(form.getSection());
            teamMember.setRank(form.getRank());
            teamMember.setIsPublished(form.getIsPublished());
            */
            teamMemberDb.save(teamMember);
          }
  
          // generate the page for display        
          curatorList = teamMemberDb.getCurators();

        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
        } finally {
            try {
                connection.close();
            } catch (SQLException e) {
                s_log.error("execute() finally e:" + e);
            }
        }
        
        if (curatorList != null) {
          request.getSession().setAttribute("curatorList", curatorList);      
          return mapping.findForward("success");
        } else {
          return mapping.findForward("error");
        }		
		
	}
}

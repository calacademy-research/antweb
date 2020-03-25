package org.calacademy.antweb;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.AntwebUtil;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;

public final class BigPictureAction extends Action {

    private static Log s_log = LogFactory.getLog(BigPictureAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm theForm,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        if (ProjectMgr.hasMoved(request, response)) return null;

        ActionForward d = Check.valid(request, mapping); if (d != null) return d;      

        Login accessLogin = LoginMgr.getAccessLogin(request);
        Group accessGroup = GroupMgr.getAccessGroup(request);
                            
        SpecimenImage theImage = null;
        Specimen theSpecimen = new Specimen();
        
        SpecimenImageForm form = (SpecimenImageForm) theForm;
        String code = "", shot = ""; 
        int number = 0;

        HttpSession session = request.getSession();
        
        String query = null;
        java.sql.Connection connection = null;
        ResultSet rset = null;
        Statement stmt = null;
        try {
            javax.sql.DataSource dataSource = getDataSource(request, "conPool");
            
            if (HttpUtil.tooBusyForBots(dataSource, request)) { HttpUtil.sendMessage(request, mapping, "Too busy for bots."); }
            
            connection = DBUtil.getConnection(dataSource, "BigPictureAction.execute()");
            
            A.log("execute() imageId:" + form.getImageId());
            if (form.getImageId() != null) {
              // Poor design. Antipattern.
              ImageDb.getFormProps(form, connection);
            }

            //s_log.info("execute() code:" + form.getCode());

            code = form.getCode();
            if (code == null) code = form.getName();
            code = code.toLowerCase();
            shot = form.getShot();   
            if (shot == null || "null".equals(shot)) {
              request.setAttribute("message", "null is an invalid shot type.");
              return (mapping.findForward("message"));            
            }
            number = form.getNumber();
            if ("".equals(shot) || shot == null || "".equals(code) || code == null) {
              String message = "Must specimen code (name) and shot";
              request.setAttribute("message", message);
              return (mapping.findForward("message"));                       
            }
                        
            request.setAttribute("code", code);
            request.setAttribute("shot", shot);

            if (number == 0) number = 1;
            boolean success = false;
                                
            if (code.contains("shot=")) {
              String message = "Incorrect specimen identifier:" + code;
              s_log.error("execute() " + message);
              request.setAttribute("message", message);
              return (mapping.findForward("message"));            
            }

            ImageDb imageDb = new ImageDb(connection);
            theImage = imageDb.getSpecimenImage(code, shot, number);

            //A.log("execute() theImage:" + theImage);

            if (LoginMgr.isCurator(request)) {
              if (theImage == null || theImage.getGroup() == null) {
                s_log.warn("execute() no image:" + theImage + " and/or group for image code:" + code + " shot:" + shot + " number:" + number);
              } else {
                //s_log.warn("execute() theImage:" + theImage +  " group:" + theImage.getGroup() + " accessGroupId:" + accessGroup.getId() + " action:" + form.getAction());
                if (theImage.getGroup().getId() == accessGroup.getId() || LoginMgr.isAdmin(request)) {
                  if ("delete".equals(form.getAction())) {
                    imageDb = new ImageDb(connection);
                    boolean isDeleted = imageDb.deleteImage(code, shot, number);
                    String message = "image NOT deleted"; //. Report error to " + AntwebUtil.getAdminEmail();
                    if (isDeleted) message = "image deleted";
                    request.setAttribute("message", message);
                    return (mapping.findForward("message")); 
                  }
                }
              }
            }

            theSpecimen = new Specimen(code, ProjectMgr.getProject(Project.ALLANTWEBANTS), connection, true);
        } catch (SQLException e) {
            s_log.error("execute() e:" + e + " query:" + query);
        } finally {  
          DBUtil.close(connection, stmt, rset, this, "BigPictureAction.execute()");
        }
        
        // If we could ascertain success here (if the file exists) we could avoid using error.jsp
        if (theImage == null) {
          //s_log.warn("Image not found for image:" + code + " shot:" + shot + " number:" + number);
          String message = "BigPicture not found.";
          request.setAttribute("message", message);
          return (mapping.findForward("message"));
        }

        //theImage.setPaths();

/*
        SpecimenImage spec = new SpecimenImage();
        spec.setShot(shot);
        //spec.setShot(request.getParameter("shot"));
        String shotString = spec.getShotText(); 
*/
        String ogTitle = "Closeup " + theImage.getShotText() + " view of Specimen " + code + " from AntWeb.";
		String ogImage = theImage.getMedres();
		String ogDesc = "1 of " + theSpecimen.getImageCount() + " images of " + Taxon.getPrettyTaxonName(theSpecimen.getTaxonName()) + " from AntWeb.";
		//A.log("execute() theImage:" + theImage + " ogImage:" + ogImage);
		request.setAttribute("ogImage", ogImage);
		request.setAttribute("ogTitle",ogTitle);
		request.setAttribute("ogDesc", ogDesc);
		//OpenGraphMgr.setOGImage(ogImage);
		//OpenGraphMgr.setOGTitle("Closeup " + theImage.getShotText() + " view of Specimen " + code + " from AntWeb.");
		//OpenGraphMgr.setOGDesc("1 of " + theSpecimen.getImageCount() + " images of " + Taxon.getPrettyTaxonName(theSpecimen.getTaxonName()) + " from AntWeb.");

        //if (AntwebProps.isDeveloper(request)) s_log.warn("execute() ogTitle:" + ogTitle + " ogImage:" + ogImage + " ogDesc:" + ogDesc);

        session.setAttribute("theImage", theImage);
        session.setAttribute("theImageTaxon", theSpecimen);
        session.setAttribute("specimen", theSpecimen);
        session.setAttribute("taxon", theSpecimen);
        
        // Set a transactional control token to prevent double posting
        saveToken(request);

        return (mapping.findForward("success"));
    }
}

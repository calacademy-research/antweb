package org.calacademy.antweb;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;

public final class BigPictureAction extends Action {

    private static final Log s_log = LogFactory.getLog(BigPictureAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm theForm,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        if (ProjectMgr.hasMoved(request, response)) return null;

        ActionForward d = Check.valid(request, mapping); if (d != null) return d;
        
        Login accessLogin = LoginMgr.getAccessLogin(request);
        Group accessGroup = GroupMgr.getAccessGroup(request);

        HttpSession session = request.getSession();

        if (HttpUtil.tooBusyForBots(request)) { HttpUtil.sendMessage(request, mapping, "Too busy for bots."); }

        String query = null;
        Connection connection = null;
        ResultSet rset = null;
        Statement stmt = null;
        String dbMethodName = DBUtil.getDbMethodName("BigPictureAction.execute()");
        //A.log("execute() dbMethodName:" + dbMethodName);

        String adminMessage = null;

        SpecimenImage theImage = null;
        Specimen theSpecimen = new Specimen();

        SpecimenImageForm form = (SpecimenImageForm) theForm;
        String imageId = form.getImageId();
        String code = null;
        String shot = null;
        int number = 0;

        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName, HttpUtil.getTarget(request));

            SessionRequestFilter.processRequest(request, connection);

            ImageDb imageDb = new ImageDb(connection);

            String notFoundMsg = "Image not found.";

            if (!AntwebUtil.isEmpty(imageId)) {
                //s_log.info("execute() imageId:" + form.getImageId());
                if (imageId != null) {
                    // Poor design. Antipattern.

                    theImage = imageDb.getSpecimenImage(imageId);

                    if (theImage == null) notFoundMsg = "Image not found for imageId:" + imageId;
                    //ImageDb.getFormProps(form, connection);
                }
            } else {

                code = form.getCode();
                if (code == null) code = form.getName();
                if (code != null) code = code.toLowerCase();

                shot = form.getShot();
                number = form.getNumber();

                if (AntwebUtil.isEmpty(code) || AntwebUtil.isEmpty(shot)) {
                    request.setAttribute("message", "Must specify code and shot, or imageId. code:" + code + " shot:" + shot + " imageId:" + imageId);
                    return mapping.findForward("message");
                }
                if (number == 0) number = 1;

                theImage = imageDb.getSpecimenImage(code, shot, number);
                if (theImage == null) notFoundMsg = "Image not found for code:" + code + " shot:" + shot + " number:" + number;
            }

            //s_log.info("execute() code:" + form.getCode());

            if (theImage == null) {
                String message = notFoundMsg;
                s_log.warn("execute() " + message);
                request.setAttribute("message", message);
                return mapping.findForward("message");
            }

            request.setAttribute("code", theImage.getCode());
            request.setAttribute("shot", theImage.getShot());

            boolean success = false;
                                
            if (code != null && code.contains("shot=")) {
              String message = "Incorrect specimen identifier:" + code;
              s_log.error("execute() " + message);
              request.setAttribute("message", message);
              return mapping.findForward("message");
            }

            //A.log("execute() theImage:" + theImage);

            if (LoginMgr.isCurator(request)) {
              if (theImage == null) {
                s_log.warn("execute() no image:" + theImage + " for imageId:" + imageId + " code:" + code + " shot:" + shot + " number:" + number);
                //adminMessage = "<%= if (accessLogin.isAdmin()) { out.println(\"<a href='" + AntwebProps.getDomainApp() + "'>Remove</a> \"); } %>";
              } else {
                if (theImage.getGroup() != null) {
                  //s_log.warn("execute() theImage:" + theImage +  " group:" + theImage.getGroup() + " accessGroupId:" + accessGroup.getId() + " action:" + form.getAction());
                  if (theImage.getGroup().getId() == accessGroup.getId() || LoginMgr.isAdmin(request)) {
                      if ("delete".equals(form.getAction())) {
                          imageDb = new ImageDb(connection);
                          boolean isDeleted = imageDb.deleteImage(code, shot, number);
                          String message = "image NOT deleted"; //. Report error to " + AntwebUtil.getAdminEmail();
                          if (isDeleted) message = "image deleted";
                          request.setAttribute("message", message);
                          return mapping.findForward("message");
                      }
                  }
                } else {
                   s_log.warn("execute() theImage:" + theImage + " as no group, accessGroupId:" + accessGroup.getId() + " action:" + form.getAction());
                }
              }
            }

            theSpecimen = new Specimen(code, ProjectMgr.getProject(Project.ALLANTWEBANTS), connection, true);
        } catch (SQLException e) {
            s_log.error("execute() e:" + e + " query:" + query);
        } finally {  
          DBUtil.close(connection, stmt, rset, this, dbMethodName);
        }

        // If we could ascertain success here (if the file exists) we could avoid using error.jsp
        if (theImage == null) {
          //s_log.warn("Image not found for image:" + code + " shot:" + shot + " number:" + number);
          String message = "BigPicture not found.";
          if (adminMessage != null) message += adminMessage;
          request.setAttribute("message", message);
          return mapping.findForward("message");
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

        return mapping.findForward("success");
    }
}

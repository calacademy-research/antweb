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

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;


public final class PictureLikeAction extends Action {

    private static final Log s_log = LogFactory.getLog(PictureLikeAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        HttpSession session = request.getSession();

        ActionForward c = Check.login(request, mapping); if (c != null) return c;        
        Login accessLogin = LoginMgr.getAccessLogin(request);        

        if (accessLogin == null) {
            String message = "You must be logged in to Like a picture.";
            request.setAttribute("message", message);
            return mapping.findForward("message");
        }
        
        String code = ((SpecimenImageForm) form).getCode();
        if (code == null) code = ((SpecimenImageForm) form).getName();
        code = code.toLowerCase();
        if (code.contains("shot=")) {
            String message = "Incorrect specimen identifier:" + code;
            s_log.error("execute() " + message);
            request.setAttribute("message", message);
            return mapping.findForward("message");
        }

        String shot = ((SpecimenImageForm) form).getShot();
        int number = ((SpecimenImageForm) form).getNumber();
        if (number == 0) number = 1; // if it wasn't entered, default to 1.

        request.setAttribute("code", code);
        request.setAttribute("shot", shot);

        SpecimenImage theImage = null;
/*
        SpecimenImage theImage = new SpecimenImage();        
        theImage.setCode(code);
        theImage.setShot(shot);
        theImage.setNumber(number);
*/
         
        
        boolean success = false;        
        Specimen theSpecimen = new Specimen();
        
        String query = null;
        Connection connection = null;
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "PictureLikeAction.execute()");

            ImageDb imageDb = new ImageDb(connection);
            theImage = imageDb.getSpecimenImage(code, shot, number);

            query = "select id, has_tiff from image where image_of_id = '" + code 
                  + "' and shot_type = '" + shot + "' and shot_number = " + number;

            s_log.info("execute() tiffQuery:" + query);            

            Statement stmt = connection.createStatement();
            stmt.executeQuery(query);
            ResultSet rset = stmt.getResultSet();
            int id = 0;
            int hasTiff = 0;
            while (rset.next()) {
               id = rset.getInt("id");
               hasTiff = rset.getInt("has_tiff");
            }
            theImage.setHasTiff(hasTiff == 1);
            stmt.close();
            theSpecimen = new Specimen(code, connection, true);   // was "worldants"

            likeSpecimenImage(id, accessLogin, dataSource);
        } catch (SQLException e) {
            s_log.error("execute() e:" + e + " query:" + query);
        } finally {
            DBUtil.close(connection, this, "PictureLikeAction.execute()");
        }
        //theImage.setPaths();
        
        // If we could ascertain success here (if the file exists) we could avoid using error.jsp
        if (theImage == null) {
         s_log.warn("Image not found for image:" + code + "shot:" + shot + " number:" + number);
          String message = "BigPicture not found.";
          request.setAttribute("message", message);
          return mapping.findForward("message");
        }
        
        boolean requestScope = "request".equals(mapping.getScope());
        if (requestScope) {
            request.setAttribute("theImage", theImage);
            request.setAttribute("theImageTaxon", theSpecimen);
        } else {     // standard use is session based.
            session.setAttribute("theImage", theImage);
            session.setAttribute("theImageTaxon", theSpecimen);
        }
        
        // Set a transactional control token to prevent double posting
        saveToken(request);
        return mapping.findForward("bigPicture");
    }

    private void likeSpecimenImage(int imageId, Login accessLogin, DataSource dataSource) {

        Group accessGroup = accessLogin.getGroup();
        String query = null;
        Statement stmt = null;
        Connection connection = null;
        try {
            connection = DBUtil.getConnection(dataSource, "PictureLikeAction.likeSpecimenImage()");

            int groupId = 0;
            int loginId = 0;
            if (accessGroup != null) {
              groupId = accessGroup.getId();
              if (accessLogin != null) loginId = accessLogin.getId();
            }
            
            query = "insert into image_like (image_id, group_id, login_id) " 
                + " values (" + imageId + "," + groupId + "," + loginId + ")";

            s_log.info("likeSpecimenImage() tiffQuery:" + query);            

            stmt = connection.createStatement();
            stmt.executeUpdate(query);
        } catch (SQLException e) {
            s_log.error("likeSpecimenImage() e:" + e + " query:" + query);
        } finally {
            DBUtil.close(connection, stmt, null, this, "PictureLikeAction.likeSpecimenImage()");
        }    
    }
}

package org.calacademy.antweb;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.*;
import java.sql.*;
import java.util.*;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class DefaultSpecimenAction extends Action {

    private static String s_taxonName = null;

    private static Log s_log = LogFactory.getLog(DefaultSpecimenAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

      ActionForward c = Check.login(request, mapping); if (c != null) return c;   

      Login accessLogin = LoginMgr.getAccessLogin(request);
      Group accessGroup = accessLogin.getGroup();

      HttpSession session = request.getSession();

      DynaActionForm df = (DynaActionForm) form;
      String taxonName = (String) df.get("taxonName");

      String specimenCode = (String) df.get("specimenCode");
      String command = (String) df.get("command");
      String caste = (String) df.get("caste");
      //String value = (String) df.get("value");
      
      boolean isPost = HttpUtil.isPost(request);
      
      //String target = HttpUtil.getTarget(request); 
      String target = request.getHeader("referer");

      A.log("execute() taxonName:" + taxonName + " command:" + command + " isPost:" + isPost + " caste:" + caste + " code:" + specimenCode + " target:" + target);

      if ("cancel".equals(command)) {
	    session.setAttribute("defaultSpecimenTaxon", null);
            
        HttpUtil.sendRedirect(target, request, response);
        return null;
      }

      java.sql.Connection connection = null;
      try {
        javax.sql.DataSource dataSource = getDataSource(request, "conPool");
        connection = DBUtil.getConnection(dataSource, "updateDefaultSpecimen()");
		ImagePickDb imagePickDb = new ImagePickDb(connection);

        if ("delete".equals(command)) {
   	      session.setAttribute("defaultSpecimenTaxon", null);
          
		  imagePickDb.setDefaultSpecimen(caste, taxonName, null, accessLogin);

          String message = DateUtil.getFormatDateTimeStr() + " default of taxon:" + taxonName + " deleted by user:" + accessLogin.getName();
          LogMgr.appendLog("defaultSpecimen.log", message);
  
          HttpUtil.sendRedirect(target, request, response);
          return null;
        }

        if ("unpost".equals(command)) {

          imagePickDb.unsetDefaultSpecimen(caste, taxonName, accessLogin);
          
          String message = DateUtil.getFormatDateTimeStr() + " specimenCode:" + specimenCode + " unset as the default " + caste + " of taxon:" + taxonName + " by user:" + accessLogin.getName();
          LogMgr.appendLog("defaultSpecimen.log", message);

		  // Have to use session because of sendRedirect.  Will be deleted after display in specimen-body.jsp
          message = "<h3><img src='" + AntwebProps.getDomainApp() + "/image/antIcon1.jpg' width=30><font color=green>&nbsp;Specimen</font>:<b>" 
            + specimenCode + "</b> is unset as the default " + caste + " image of <font color=green>taxon</font>:<b>" 
            + "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + Taxon.getPrettyTaxonName(taxonName) + "</a></b></h3>";
		  session.setAttribute("message", message);

          HttpUtil.sendRedirect(target, request, response);
          return null;
        }


        if (isPost || "post".equals(command)) {

          imagePickDb.setDefaultSpecimen(caste, taxonName, specimenCode, accessLogin);
          
          String message = DateUtil.getFormatDateTimeStr() + " specimenCode:" + specimenCode + " set as the default " + caste + " of taxon:" + taxonName + " by user:" + accessLogin.getName();
          LogMgr.appendLog("defaultSpecimen.log", message);

		  // Have to use session because of sendRedirect.  Will be deleted after display in specimen-body.jsp
          message = "<h3><img src='" + AntwebProps.getDomainApp() + "/image/antIcon1.jpg' width=30><font color=green>&nbsp;Specimen</font>:<b>"  
           + "<a href='" + AntwebProps.getDomainApp() + "/specimen.do?code=" + specimenCode + "'>" + specimenCode + "</a>"
           + "</b> is set as the default " + caste + " image for <font color=green>taxon</font>:<b>" 
           + "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + Taxon.getPrettyTaxonName(taxonName) + "</a></b></h3>";
		  session.setAttribute("message", message);

          HttpUtil.sendRedirect(target, request, response);
          
          return null;
        }
        
        if (taxonName != null) session.setAttribute("defaultSpecimenTaxon", taxonName);
        //A("execute() taxonName:" + taxonName + " target:" + target);

        HttpUtil.sendRedirect(target, request, response);
        return null;

	  } catch (SQLException e) {
		s_log.error("execute() e:" + e);
	  } finally { 		
		DBUtil.close(connection, this, "updateDefaultSpecimen()");
  	  }
		        
	  return null;
    }
    
/*
    public static boolean isIn(Specimen specChild, ArrayList<FavoriteImage> favoriteImageList) {
      for (FavoriteImage favoriteImage : favoriteImageList) {
        if (favoriteImage.getSpecimenCode().equals(specChild.getCode())) return true;
      }
      return false;
    }
*/
}

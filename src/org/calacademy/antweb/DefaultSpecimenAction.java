package org.calacademy.antweb;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/**
 * Sets or removes the default (representative) specimen of a taxon for one caste.
 * Changes run only on a POST (the form token is checked by SessionRequestFilter); the Pick and
 * Unpick links on the images page are small POST forms (Csrf.postButton). After a change the
 * curator is sent to the taxon's images page, never to a page named by the request.
 */
public final class DefaultSpecimenAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(DefaultSpecimenAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

      ActionForward c = Check.curator(request, mapping); if (c != null) return c;   

      Login accessLogin = LoginMgr.getAccessLogin(request);

      HttpSession session = request.getSession();

      DynaActionForm df = (DynaActionForm) form;
      String taxonName = (String) df.get("taxonName");

      String specimenCode = (String) df.get("specimenCode");
      String command = (String) df.get("command");
      String caste = (String) df.get("caste");

      boolean isPost = HttpUtil.isPost(request);

      s_log.debug("execute() taxonName:" + taxonName + " command:" + command + " isPost:" + isPost + " caste:" + caste + " code:" + specimenCode);

      if ("cancel".equals(command)) {
	    session.setAttribute("defaultSpecimenTaxon", null);
        HttpUtil.sendRedirect(returnTarget(taxonName), request, response);
        return null;
      }

      boolean isChange = "delete".equals(command) || "unpost".equals(command) || "post".equals(command);
      if (!isChange) {
        // Old "choose image" flow: only remembers the taxon in the session.
        if (taxonName != null) session.setAttribute("defaultSpecimenTaxon", taxonName);
        HttpUtil.sendRedirect(returnTarget(taxonName), request, response);
        return null;
      }

      // Changes: POST only (the form token is checked on POST).
      if (!isPost) {
        request.setAttribute("message", "Nothing was changed. Use the Pick or Unpick button on the images page.");
        return mapping.findForward("message");
      }
      if (!(Caste.WORKER.equals(caste) || Caste.QUEEN.equals(caste) || Caste.MALE.equals(caste))) {
        request.setAttribute("message", "Nothing was changed: unknown caste.");
        return mapping.findForward("message");
      }
      if (taxonName == null || taxonName.isEmpty() || taxonName.length() > 128) {
        request.setAttribute("message", "Nothing was changed: no taxon.");
        return mapping.findForward("message");
      }

      Connection connection = null;
      String dbMethodName = DBUtil.getDbMethodName("DefaultSpecimenAction.execute()");
      try {
        DataSource dataSource = getDataSource(request, "conPool");
        connection = DBUtil.getConnection(dataSource, dbMethodName);
		ImagePickDb imagePickDb = new ImagePickDb(connection);

        if ("post".equals(command)) {
          if (!imagePickDb.isSpecimenOfTaxon(specimenCode, taxonName)) {
            request.setAttribute("message", "Nothing was changed: specimen " + Html.esc(specimenCode) + " is not a specimen of " + Html.esc(taxonName) + ".");
            return mapping.findForward("message");
          }
        } else if (!imagePickDb.isTaxon(taxonName)) {
          request.setAttribute("message", "Nothing was changed: unknown taxon " + Html.esc(taxonName) + ".");
          return mapping.findForward("message");
        }

        if ("delete".equals(command)) {
   	      session.setAttribute("defaultSpecimenTaxon", null);
          
		  imagePickDb.setDefaultSpecimen(caste, taxonName, null, accessLogin);

          String message = DateUtil.getFormatDateTimeStr() + " default of taxon:" + taxonName + " deleted by user:" + accessLogin.getName();
          LogMgr.appendLog("defaultSpecimen.log", message);
  
          HttpUtil.sendRedirect(returnTarget(taxonName), request, response);
          return null;
        }

        if ("unpost".equals(command)) {

          imagePickDb.unsetDefaultSpecimen(caste, taxonName, accessLogin);
          
          String message = DateUtil.getFormatDateTimeStr() + " specimenCode:" + specimenCode + " unset as the default " + caste + " of taxon:" + taxonName + " by user:" + accessLogin.getName();
          LogMgr.appendLog("defaultSpecimen.log", message);

		  // Have to use session because of sendRedirect.  Will be deleted after display in imagePage-body.jsp
          message = "<h3><img src='" + AntwebProps.getDomainApp() + "/image/antIcon1.jpg' width=30><font color=green>&nbsp;Specimen</font>:<b>" 
            + Html.esc(specimenCode) + "</b> is unset as the default " + Html.esc(caste) + " image of <font color=green>taxon</font>:<b>" 
            + "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + Html.escAttr(Html.url(taxonName)) + "'>" + Html.esc(Taxon.getPrettyTaxonName(taxonName)) + "</a></b></h3>";
		  session.setAttribute("message", message);

          HttpUtil.sendRedirect(returnTarget(taxonName), request, response);
          return null;
        }

        // post
        imagePickDb.setDefaultSpecimen(caste, taxonName, specimenCode, accessLogin);
          
        String message = DateUtil.getFormatDateTimeStr() + " specimenCode:" + specimenCode + " set as the default " + caste + " of taxon:" + taxonName + " by user:" + accessLogin.getName();
        LogMgr.appendLog("defaultSpecimen.log", message);

		// Have to use session because of sendRedirect.  Will be deleted after display in imagePage-body.jsp
        message = "<h3><img src='" + AntwebProps.getDomainApp() + "/image/antIcon1.jpg' width=30><font color=green>&nbsp;Specimen</font>:<b>"  
         + "<a href='" + AntwebProps.getDomainApp() + "/specimen.do?code=" + Html.escAttr(Html.url(specimenCode)) + "'>" + Html.esc(specimenCode) + "</a>"
         + "</b> is set as the default " + Html.esc(caste) + " image for <font color=green>taxon</font>:<b>" 
         + "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + Html.escAttr(Html.url(taxonName)) + "'>" + Html.esc(Taxon.getPrettyTaxonName(taxonName)) + "</a></b></h3>";
		session.setAttribute("message", message);

        HttpUtil.sendRedirect(returnTarget(taxonName), request, response);
        return null;

	  } catch (SQLException e) {
		s_log.error("execute() e:" + e);
        request.setAttribute("message", "Nothing was changed: database error.");
        return mapping.findForward("message");
	  } finally { 		
		DBUtil.close(connection, this, dbMethodName);
  	  }
    }

    /** Where to go afterwards: the taxon's images page on this site (never a page named by the request). */
    static String returnTarget(String taxonName) {
      String domainApp = AntwebProps.getDomainApp();
      if (taxonName == null || taxonName.isEmpty()) return domainApp + "/";
      return domainApp + "/images.do?taxonName=" + Html.url(taxonName);
    }
}

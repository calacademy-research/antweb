package org.calacademy.antweb.upload;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

//import com.sun.org.apache.xerces.internal.impl.xpath.regex.*;

import org.calacademy.antweb.*;

/** 
  * This is called only from worldAuthoritySave.do, invoked from web/world_authority_admin_results-body.jsp\  
  */
  
public final class WorldAuthorityAction extends Action {
	
	public ActionForward execute(
		ActionMapping mapping,
		ActionForm form,
		HttpServletRequest request,
		HttpServletResponse response)
		throws IOException, ServletException {

		// Extract attributes we will need
		HttpSession session = request.getSession();
		Utility util = new Utility();
		String docPath = Utility.getDocRoot() + "/worldAuthorityFiles";
		
		String success = "success";
		String mode = ((WorldAuthorityForm) form).getMode();
		String fileName = ((WorldAuthorityForm) form).getFileName();
		fileName = docPath + "/" + fileName;
		if (mode.equals("rollback")) {
			boolean rolledBack = Utility.rollbackFile(fileName);
			if (!rolledBack) {
				success = "failure";
			}
		} else if (mode.equals("save")) {
			
			String extinct = (String) session.getAttribute("extinct");
			String extant = (String) session.getAttribute("extant");
			
			if (extinct != null && extinct.length() > 0) {
				Utility.backupFile(docPath + "/extinct.xls");
				util.saveStringToFile(extinct, docPath + "/extinct.xls");
			}
			
			if (extant != null && extant.length() > 0) {
				Utility.backupFile(docPath + "/extant.xls");
				util.saveStringToFile(extant, docPath + "/extant.xls");
			}
		} else {
			success = "failure";
		}
		return mapping.findForward(success);
			
	}

/*  Mark.  Jan 10, 2011.  Unused, so commented out.

	private boolean rollback(String fileName) {
		boolean success = true;
		
			String backup = fileName = ".bak";
			
		return success;
	}
*/	
}


package org.calacademy.antweb.util;

import java.io.IOException;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import org.apache.struts.action.*;

import org.calacademy.antweb.util.AntwebUtil;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class ShowLineAction extends Action {

    private static Log s_log = LogFactory.getLog(ShowLineAction.class);

    public ActionForward execute(
        ActionMapping mapping,
        ActionForm form,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException, ServletException {

        // Extract attributes we will need
        Locale locale = getLocale(request);
        HttpSession session = request.getSession();

        DynaActionForm df = (DynaActionForm) form;
        
        String fileName = "/usr/local/tomcat/webapps/antweb/web/workingdir/worldants_speciesList.txt";
        String line = "1532";

        fileName  = (String) df.get("file");  
        line = (String) df.get("line");

        String command = "awk '{if ((NR == " + line + ")) print $0}' " + fileName; 
        String message = new AntwebSystem().launchProcess(command, true);

        String shortMessage = "No results";
        if (message != null && message.length() < 100) shortMessage = "-" + message + "-";
        if (message != null && message.length() >= 100) shortMessage = message.substring(0, 100);

        if (AntwebProps.isDevMode()) AntwebUtil.log("command:" + command + " results:" + shortMessage);

        request.setAttribute("message", message);
        return mapping.findForward("success");
      }
}

package org.calacademy.antweb.util;

import java.io.IOException;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import org.apache.struts.action.*;
import java.sql.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.AntwebUtil;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class ToUploadAction extends Action {

    private static Log s_log = LogFactory.getLog(ToUploadAction.class);

    public ActionForward execute(
        ActionMapping mapping,
        ActionForm form,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException, ServletException {

        // Extract attributes we will need
        Locale locale = getLocale(request);
        HttpSession session = request.getSession();

        String toUploadDir = AntwebProps.getProp("site.toUpload");

        DynaActionForm df = (DynaActionForm) form;
        String action = (String) df.get("action");                

        if (action.equals("clear")) {

          //String message = "To Upload Dir List";
          String now = DateUtil.getEolFormatDateStr();
          String backupDir = toUploadDir + "backup/" + now + "/";
          (new Utility()).makeDirTree(backupDir);

          String message = (new AntwebSystem()).launchProcess("ls " + toUploadDir, true);
          //s_log.warn("execute:" + message);
 
          String[] lines = message.split("<br>");
          for (String line : lines) {
            //s_log.warn("execute() line:" + line);
            if ( 
                (line == null) ||
                ("".equals(line)) ||
                ("backup".equals(line))
              ) continue;

            String command = "mv " + toUploadDir + line + " " + backupDir;
            String retVal = (new AntwebSystem()).launchProcess(command, true);
            //s_log.warn("command:" + command + " retVal:" + retVal);
          }
        }
        if (action.equals("errorLogTail")) {
            String errorLog = AntwebProps.getProp("site.errorLog");   
            String grepOption = " --lines ";
            if (AntwebProps.isProp("isMac")) grepOption = " -n ";     
            String command = "tail " + grepOption + " 2000 " + errorLog;
            String message = (new AntwebSystem()).launchProcess(command, true);
            request.setAttribute("message", message);
            return (mapping.findForward("success"));
        }

        //s_log.warn("toUploadDir:" + toUploadDir);
        String message = (new AntwebSystem()).launchProcess("ls -al " + toUploadDir, true);
        request.setAttribute("message", message);
        return (mapping.findForward("success"));
      }
}

package org.calacademy.antweb;

import java.io.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class DisplayAction extends Action {

/* Currently this is a proof of concept class.  By calling a URL such as:
      http://localhost/antweb/display.do?name=tetramorium&rank=genus&project=allantwebants

   display.do will either return the cached corresponding description.do page or it will
   make a fetch from description.do and cache the results.  This has the potential to 
   greatly increase the performance of our system.
        Work will remain to integrate this with description.do (also browse.do?) and to 
   make sure that only unlogged in pages are written, and returned.
        Work will be required in order to not use a different url for the caching version.
   Integrate functionality with description.do?
*/

    private static Log s_log = LogFactory.getLog(DisplayAction.class);

    
    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

if (true) return null;  // functionality disabled.

        String pathInfo = request.getPathInfo();    
        String query = request.getQueryString();

        String url = AntwebProps.getDomainApp() + "/description.do" + (query == null ? "" : "?" + query);

        s_log.warn("execute() url:" + url + " pathInfo:" + pathInfo + " query:" + query);

        String dir = "/web/data/taxon/";
        new Utility().createDirectory(dir);
        String fileName = query;
        String fullPath = AntwebProps.getDocRoot() + dir + fileName;
        String output = null;

        if (FileUtil.fileExistsAndCurrent(fullPath)) {
            output = AntwebUtil.readFile(fullPath);
        } else {
            output = HttpUtil.getUrl(url);    
            AntwebUtil.writeFile(fullPath, output);
        }
        
        response.setContentType("text/html");
        Writer writer = response.getWriter();
        writer.write(output);
        return null;
    }

}

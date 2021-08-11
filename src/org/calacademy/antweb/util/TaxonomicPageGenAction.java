package org.calacademy.antweb.util;

import java.io.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.Utility;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class TaxonomicPageGenAction extends Action {

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

    private static Log s_log = LogFactory.getLog(TaxonomicPageGenAction.class);
    
// Called like this: http://localhost/antweb/taxonomicPageGen.do?rank=genus&project=newzealandants&images=true    
    
    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        String pathInfo = request.getPathInfo();    
        String query = request.getQueryString();
        String project = request.getParameter("project");
        project = project.substring(0, project.indexOf("ants"));

        String url = AntwebProps.getDomainApp() + "/taxonomicPage.do" + (query == null ? "" : "?" + query) + "&simple=true";

        s_log.warn("execute() url:" + url + " pathInfo:" + pathInfo + " query:" + query);

        String dir = "/" + project + "/";
        (new Utility()).createDirectory(dir);
        String fileName = "taxonomicPageGen.jsp";
        String fullPath = AntwebProps.getDocRoot() + dir + fileName;
        String output = null;

        /* bayarea messes with formatting, austrailian has no ants, atol and others have too many ants. 
           See TaxonomicPageGenAction.java 
        */
        
        if ( 
        ("bayarea".equals(project))   
/*
     || ("austrailian".equals(project))  
     || ("atol".equals(project)) 
     || ("southeastasia".equals(project))
     || ("allantweb".equals(project))
     || ("fossil".equals(project))
     || ("world".equals(project))
     || ("neotropical".equals(project))
     || ("african".equals(project))
*/
        ) {
            output = "";        
        } else {     
            output = HttpUtil.getUrl(url);    
            if (output.length() > 1000) s_log.warn("execute() !!!!! fullPath:" + fullPath);
        }
        
        AntwebUtil.writeFile(fullPath, output);        
        response.setContentType("text/html");
        Writer writer = response.getWriter();
        writer.write(output);
        return null;
    }

}

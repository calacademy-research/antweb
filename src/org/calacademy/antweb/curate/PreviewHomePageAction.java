package org.calacademy.antweb.curate;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;

import java.util.ResourceBundle;
import java.util.Locale;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
import org.calacademy.antweb.util.AntwebProps;    
import org.calacademy.antweb.*;

public final class PreviewHomePageAction extends Action {

    private static Log s_log = LogFactory.getLog(PreviewHomePageAction.class);

    public ActionForward execute( ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
        
        // Extract attributes we will need
        Locale locale = getLocale(request);
        HttpSession session = request.getSession();
        
        HomePageForm theForm = (HomePageForm) form;
        generateHomePage(theForm);

        return (mapping.findForward("success"));
    }
    
    public void generateHomePage(HomePageForm form) {        
        // get the doc root
        Utility util = new Utility();
        String docBase = util.getDocRoot();
        
        // open the new file
        try {
            String outFile = docBase + "homePagePreview-body.jsp";
            BufferedWriter out = new BufferedWriter(new FileWriter(outFile));
            
            // grab the template file
            String templateFile = AntwebProps.getDocRoot() + AntwebProps.getProp("site.antwebhomepagetemplate");

            BufferedReader in = new BufferedReader(new FileReader(templateFile));
            String str, prestring, slot;
            Object variable;
            int start;
            int end;
            while ((str = in.readLine()) != null) {
                //do the substitutions one at a time
                if ((str.contains("[%")) && (str.contains("%]"))) {
                    while (str.length() > 0) {
                        start = str.indexOf("[%");
                        end = str.indexOf("%]");
                        if (start == -1) {
                            out.write(str);
                            str = "";
                        } else {
                            prestring = str.substring(0,start);
                            out.write(prestring);
                            slot = str.substring(start+3, end-1);
                            variable = getSlotValue(slot, form);
                            if (variable != null) {
                                out.write(variable.toString());
                            }
                            str = str.substring(end+2);
                        }
                        
                    }
                    out.write("\n");
                } else {
                    out.write(str);
                    out.write("\n");
                }
            }
            out.close();
            in.close();
        } catch (IOException e) {
            s_log.error("generateHomePage() e:" + e);
            org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
        }
    }
    
    private Object getSlotValue(String slot, HomePageForm form) {

        String method;
        Object result = null;
        Formatter format = new org.calacademy.antweb.Formatter();
        Field field;
        java.lang.Class thisClass;
        Object[] paramsObj = {};
        try {
            java.lang.Class params[] = {};
            Method thisMethod;            
            method = "get" + format.capitalizeFirstLetter(slot);
            thisClass = java.lang.Class.forName("org.calacademy.antweb.curate.HomePageForm");
            thisMethod = thisClass.getDeclaredMethod(method, params);

            result = thisMethod.invoke(form, paramsObj);
            
        } catch (SecurityException e) {
            org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
        } catch (IllegalArgumentException e) {
            org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
        } catch (ClassNotFoundException e) {
            org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
        } catch (NoSuchMethodException e) {
            org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
        } catch (IllegalAccessException e) {
            org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
        } catch (InvocationTargetException e) {
            org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
        }
        return result;
    }
    
}

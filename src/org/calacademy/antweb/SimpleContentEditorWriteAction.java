package org.calacademy.antweb;

import java.io.File;

import java.io.FileWriter;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
import org.calacademy.antweb.util.*;

public final class SimpleContentEditorWriteAction extends Action {

    private static Log s_log = LogFactory.getLog(SimpleContentEditorWriteAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        ActionForward c = Check.login(request, mapping); if (c != null) return c;
        Login accessLogin = LoginMgr.getAccessLogin(request);

        String docRoot = Utility.getDocRoot();
        
        String fileName = ((SimpleContentEditorForm) form).getFileName();
        String contents = ((SimpleContentEditorForm) form).getContents();

        // only this file can be editted right now
        if (!fileName.equals("staff_gen_inc.jsp")) {
            s_log.error("execute() filename is not staff_gen_inc.jsp");
            return mapping.findForward("failure");
        }

        if (AntwebProps.isDevMode()) {
            try {
                String loginDir = docRoot + "web/curator/" + accessLogin.getId() + "/";
                File outputFile = new File(loginDir + fileName);
                FileWriter outFile = new FileWriter(outputFile);
               s_log.info("execute() Writing to file:" + outputFile);
                outFile.write(contents);
                outFile.close();
            } catch (IOException e) {
                s_log.error("Unable to read from file " + docRoot + fileName + ": " + e);
                AntwebUtil.logStackTrace(e);
            }
        } else {       
            try {
                File outputFile = new File(docRoot + fileName);
                FileWriter outFile = new FileWriter(outputFile);
               s_log.info("execute() Writing to file:" + outputFile);
                outFile.write(contents);
                outFile.close();
            } catch (IOException e) {
                s_log.error("Unable to read from file " + docRoot + fileName + ": " + e);
                AntwebUtil.logStackTrace(e);
            }
        }        
        ((SimpleContentEditorForm) form).setContents(contents);

        return mapping.findForward("success");
    }
}

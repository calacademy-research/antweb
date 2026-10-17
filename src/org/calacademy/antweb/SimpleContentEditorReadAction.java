package org.calacademy.antweb;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.util.AntwebUtil;

public final class SimpleContentEditorReadAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(SimpleContentEditorReadAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        // Administrators only, and only a plain file name in the docroot (no paths).
        ActionForward c = org.calacademy.antweb.util.Check.admin(request, mapping); if (c != null) return c;

        String docRoot = Utility.getDocRoot();
        
        String fileName = ((SimpleContentEditorForm) form).getFileName();
        if (fileName == null || !fileName.matches("[A-Za-z0-9_.-]{1,100}") || fileName.contains("..")) {
            return org.calacademy.antweb.util.HttpUtil.sendMessage(request, mapping, "Invalid file name.");
        }
        String contents = "";

        try {
            FileInputStream fis = new FileInputStream (docRoot + fileName);
            BufferedReader br   = new BufferedReader(new InputStreamReader(fis));
            String thisLine;
            while ((thisLine = br.readLine()) != null) {
                contents += thisLine + "\n";
            }
            // Close our input stream
            fis.close();
            br.close();
        } catch (IOException e) {
            s_log.error("Unable to read from file:" + docRoot + fileName + " e:" + e);
            AntwebUtil.logStackTrace(e);
        }
        
        ((SimpleContentEditorForm) form).setContents(contents);

        return mapping.findForward("success");
    }
}

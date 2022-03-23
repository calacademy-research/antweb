package org.calacademy.antweb;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.util.AntwebUtil;

public final class SimpleContentEditorReadAction extends Action {

    private static Log s_log = LogFactory.getLog(SimpleContentEditorReadAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        String docRoot = (new Utility()).getDocRoot();
        
        String fileName = ((SimpleContentEditorForm) form).getFileName();
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

        return (mapping.findForward("success"));
    }
}

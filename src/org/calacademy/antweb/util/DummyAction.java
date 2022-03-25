package org.calacademy.antweb.util;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.*;


import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public final class DummyAction extends Action {

    private static final Log s_log = LogFactory.getLog(DummyAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        HttpUtil.setUtf8(request, response);

        String target = HttpUtil.getTarget(request);        
        if (target.contains("userAgents.do")) {
   		  return mapping.findForward("userAgents");
	    }
	    
	    return mapping.findForward("success");
	}

}

package org.calacademy.antweb.curate;

import java.io.IOException;
import java.sql.Statement;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.upload.*;

import java.sql.*;

public final class CurateActionB extends Action {

    private static Log s_log = LogFactory.getLog(CurateActionB.class);
    
	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

		AntwebUtil.log("CurateActionB.execute()");

        ActionForward c = Check.login(request, mapping); if (c != null) return c;
        //Login accessLogin = LoginMgr.getAccessLogin(request);

		HttpSession session = request.getSession();

		AntwebUtil.log("CurateActionB.execute() END");
        return mapping.findForward("curateB");
	}
}

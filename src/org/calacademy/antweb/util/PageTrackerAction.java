package org.calacademy.antweb.util;

import java.io.IOException;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import org.apache.struts.action.*;
import java.sql.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.upload.*;
import org.calacademy.antweb.imageUploader.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public final class PageTrackerAction extends Action {

    private static Log s_log = LogFactory.getLog(PageTrackerAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
                                 HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

        ActionForward c = Check.admin(request, mapping); if (c != null) return c;

        return (mapping.findForward("success"));
    }
}
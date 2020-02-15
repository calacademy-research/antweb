package org.calacademy.antweb;

import java.io.IOException;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

public final class SpecimenAction extends DescriptionAction {

    private static Log s_log = LogFactory.getLog(SpecimenAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        if (ProjectMgr.hasMoved(request, response)) return null;

        ActionForward a = Check.init(Check.LOGIN, request, mapping); if (a != null) return a;
        ActionForward d = Check.valid(request, mapping); if (d != null) return d;
        
        java.util.Date startTime = new java.util.Date();   

        Locale locale = getLocale(request);
        HttpSession session = request.getSession();
        
        Login accessLogin = LoginMgr.getAccessLogin(request);

        SpecimenForm specimenForm = (SpecimenForm) form;

        String code = specimenForm.getCode();
        String name = specimenForm.getName();

        if (code == null) code = name;
        if (code == null) {
			request.setAttribute("message", "Specimen code not found");
			return (mapping.findForward("message"));        
        }
        code = code.toLowerCase();
        Specimen specimen = null;

        if (code != null) {
            java.sql.Connection connection = null;
            try {
                javax.sql.DataSource dataSource = getDataSource(request, "conPool");
                
                if (HttpUtil.tooBusyForBots(dataSource, request)) { HttpUtil.sendMessage(request, mapping, "Too busy for bots."); }
                
                connection = DBUtil.getConnection(dataSource, "SpecimenAction.execute()", HttpUtil.getTarget(request));
                boolean specimenExists = false;
                specimen = new Specimen();
                specimen.setCode(code);
                specimen.setBrowserParams(request.getQueryString());                                            

                Overview overview = OverviewMgr.getAndSetOverview(request);
                if (overview == null) return OverviewMgr.returnMessage(request, mapping);

                specimen.setConnection(connection);
                specimenExists = specimen.isSpecimen(overview); // Uses overview?
                //A.log("execute() code:" + code + " isSpecimen:" + specimenExists);

                if (specimenExists) {
                    specimen.init();
                    specimen.fullInit();
                    //A.log("execute() uploadedBy:" + specimen.getAccessGroup());
                    boolean success = saveDescriptionEdit(specimenForm, specimen, accessLogin, request, connection);   
                    if (!success) return (mapping.findForward("message"));    
                    
                    if (accessLogin != null) getDescEditHistory(specimen, connection, request);
                    
                    ArrayList specimenList = new ArrayList();
                    specimenList.add(specimen.getCode());
                    
                    specimen.setMap(new Map(specimenList, connection));                    
                    //A.log("execute() map:" + specimen.getMap() + " specimenList:" + specimenList + " function:" + specimen.getMap().getGoogleMapFunction());     
                    
                    TaxonPropDb taxonPropDb = new TaxonPropDb(connection);
                    specimen.setDefaultFor(taxonPropDb.getDefaultFor(specimen.getCode()));
                    
                } else {
                    String message = "Specimen:" + code + " is not in the AntWeb database";

                    if ((overview != null) && (!overview.toString().equals("allantwebants")) && (!overview.toString().equals("")) ) {
                      message += " for overview:" + overview + ".  <br><br>Go to <a href=" + AntwebProps.getDomainApp() + "/specimen.do?code=" + code + "&project=allantwebants>All Antweb</a>.";
                    } else {
                      message += ".";
                    }

                    if (org.calacademy.antweb.upload.UploadAction.isInUploadProcess()) {
                        // An upload is currently in process.  Request that this process be re-attempted shortly.
                        message += "  A curator is currently in the process of an Upload.  Please try again shortly.";
                    } else {
                        //s_log.warn(message + "  No upload in process.");
                        LogMgr.appendLog("notFound.txt", "SpecimenAction.execute() " + message);
                    }
                    
                    request.setAttribute("message", message);
                    return (mapping.findForward("message"));
                }
            } catch (Exception e) {
                s_log.error("execute() e:" + e);
                if (!e.toString().contains("Connections could not be acquired from the underlying database")) {
                  AntwebUtil.logStackTrace(e);
                }
                return (mapping.findForward("failure"));
            } finally {
                try {
                    QueryProfiler.profile("specimenAction", startTime);	            
                } catch (Exception e) {
                    s_log.error("execute() profiler e:" + e);
                }
                DBUtil.close(connection, this, "SpecimenAction.execute()");
            }


			//A.log("execute() specimen:" + specimen + " taxonImages:" + specimen.getImages() );

            String ogTitle = "AntWeb specimen with unique identifier " + specimen.getCode().toUpperCase() + " identified as " + Taxon.getPrettyTaxonName(specimen.getTaxonName());
            String ogImage = null;
            String ogDesc = null;
            
            //A.log("execute() ogTitle:" + ogTitle);
            //request.setAttribute("ogTitle", specimen.getTitleString());
            request.setAttribute("ogTitle", ogTitle);
			if (specimen.getImages() != null) {
              //A.log("execute() images:" + specimen.getImages());			
			  SpecimenImage headShot = (SpecimenImage) specimen.getImages().get("p1");
			  if (headShot != null) {
				ogImage = headShot.getHighres();
				A.log("execute() ogImage:" + ogImage);
				request.setAttribute("ogImage", ogImage);
			  } else {
			    A.log("execute() No Open Graph Image set.");
		      }
			}
		    //if (AntwebProps.isDeveloper(request)) s_log.warn("execute() ogTitle:" + ogTitle + " ogImage:" + ogImage + " ogDesc:" + ogDesc);
		 
		 
            if ("request".equals(mapping.getScope())) {
                request.setAttribute("specimen", specimen);
            } else {
                Specimen oldSpec = (Specimen) session.getAttribute("specimen");
                if (oldSpec != null) {
                    try {
                        oldSpec.finalize();
                    } catch (Throwable e) {
                        s_log.error("error finalizing specimen " + e);
                    }
                }
                session.setAttribute("specimen", specimen);
            }
                  
            
            // Mark added.  Sept 13, 2010.  To correct breadcrumb trail.
            session.setAttribute("taxon", specimen);
        }

        // Set a transactional control token to prevent double posting
        //saveToken(request);

        // Forward control to the edit user registration page
        return (mapping.findForward("success"));
    }
}

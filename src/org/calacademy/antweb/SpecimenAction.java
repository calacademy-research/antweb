package org.calacademy.antweb;

import java.io.IOException;
import java.sql.Connection;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.upload.UploadAction;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

public final class SpecimenAction extends DescriptionAction {

    private static final Log s_log = LogFactory.getLog(SpecimenAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        if (ProjectMgr.hasMoved(request, response)) return null;

        ActionForward a = Check.init(Check.LOGIN, request, mapping); if (a != null) return a;
        ActionForward d = Check.valid(request, mapping); if (d != null) return d;

        Login accessLogin = LoginMgr.getAccessLogin(request);
        if (LoginMgr.isAdmin(accessLogin)) {
          ActionForward c = Check.init(Check.UPLOAD, request, mapping); if (c != null) return c;
        }

        Date startTime = new Date();
        Locale locale = getLocale(request);
        HttpSession session = request.getSession();

        SpecimenForm specimenForm = (SpecimenForm) form;

        String code = specimenForm.getCode();
        String name = specimenForm.getName();

        if (code == null) code = name;
        if (code == null) {
			request.setAttribute("message", "Specimen code not found");
			return mapping.findForward("message");
        }
        code = code.toLowerCase();
        Specimen specimen = null;

        if (code != null) {

            if (HttpUtil.tooBusyForBots(request)) { HttpUtil.sendMessage(request, mapping, "Too busy for bots."); }

            Connection connection = null;
            String dbMethodName = DBUtil.getDbMethodName("SpecimenAction.execute()");
            try {
                DataSource dataSource = getDataSource(request, "conPool");
                connection = DBUtil.getConnection(dataSource, dbMethodName, HttpUtil.getTarget(request));

                boolean specimenExists = false;
                specimen = new Specimen();
                specimen.setCode(code);
                specimen.setBrowserParams(request.getQueryString());                                            

                Overview overview = null;
                try {
                    overview = OverviewMgr.getAndSetOverview(request);
                } catch (AntwebException e) {
                    return OverviewMgr.returnMessage(request, mapping, e);
                }

                specimenExists = specimen.isSpecimen(connection, overview); // Uses overview?
                //A.log("execute() code:" + code + " isSpecimen:" + specimenExists);

                if (specimenExists) {
                    specimen.init(connection);
                    specimen.fullInit(connection);
                    //A.log("execute() uploadedBy:" + specimen.getAccessGroup());
                    boolean success = saveDescriptionEdit(specimenForm, specimen, accessLogin, request, connection);   
                    if (!success) return mapping.findForward("message");
                    
                    if (accessLogin != null) getDescEditHistory(specimen, connection, request);
                    
                    ArrayList<String> specimenList = new ArrayList<>();
                    specimenList.add(specimen.getCode());
                    
                    specimen.setMap(new Map(specimenList, connection));                    
                    //A.log("execute() map:" + specimen.getMap() + " specimenList:" + specimenList + " function:" + specimen.getMap().getGoogleMapFunction());     
                    
                    TaxonPropDb taxonPropDb = new TaxonPropDb(connection);
                    specimen.setDefaultFor(taxonPropDb.getDefaultFor(specimen.getCode()));
                    
                } else {
                    String message = "Specimen:" + code + " is not in the AntWeb database";

                    if (overview != null && !overview.toString().equals("allantwebants") && !overview.toString().equals("")) {
                      message += " for overview:" + overview + ".  <br><br>Go to <a href=" + AntwebProps.getDomainApp() + "/specimen.do?code=" + code + "&project=allantwebants>All Antweb</a>.";
                    } else {
                      message += ".";
                    }

                    if (UploadAction.isInUploadProcess()) {
                        // An upload is currently in process.  Request that this process be re-attempted shortly.
                        message += "  A curator is currently in the process of an Upload.  Please try again shortly.";
                    } else {
                        //s_log.warn(message + "  No upload in process.");
                        LogMgr.appendLog("notFound.txt", "SpecimenAction.execute() " + message);
                    }
                    
                    request.setAttribute("message", message);
                    return mapping.findForward("message");
                }
            //} catch (AntwebException e) {
            //    s_log.error("execute() e:" + e + " " + HttpUtil.getRequestInfo(request));
            //    return (mapping.findForward("failure"));
            } catch (Exception e) {
                if (!e.toString().contains("Connections could not be acquired from the underlying database")) {
                  AntwebUtil.logStackTrace(e);
                } else {
                    s_log.error("execute() e:" + e);
                }
                return mapping.findForward("failure");
            } finally {
                try {
                    QueryProfiler.profile("specimenAction", startTime);	            
                } catch (Exception e) {
                    s_log.error("execute() profiler e:" + e);
                }
                DBUtil.close(connection, this, dbMethodName);
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
			  SpecimenImage headShot = specimen.getImages().get("p1");
			  if (headShot != null) {
				ogImage = headShot.getHighres();
				s_log.debug("execute() ogImage:" + ogImage);
				request.setAttribute("ogImage", ogImage);
			  } else {
			    s_log.debug("execute() No Open Graph Image set.");
		      }
			}
		    //if (AntwebProps.isDeveloper(request)) s_log.warn("execute() ogTitle:" + ogTitle + " ogImage:" + ogImage + " ogDesc:" + ogDesc);

            request.setAttribute("specimen", specimen);
            //session.setAttribute("specimen", specimen);

            // Mark added.  Sept 13, 2010.  To correct breadcrumb trail.
            // Feb2020
            //session.setAttribute("taxon", specimen);
        }

        // Set a transactional control token to prevent double posting
        //saveToken(request);

        // Forward control to the edit user registration page
        return mapping.findForward("success");
    }
}

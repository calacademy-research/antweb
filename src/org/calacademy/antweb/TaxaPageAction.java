package org.calacademy.antweb;

import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;

import java.sql.*;
import java.io.*;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;

public final class TaxaPageAction extends Action {

    private static final Log s_log = LogFactory.getLog(TaxaPageAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        HttpUtil.setUtf8(request, response);       

        Login accessLogin = LoginMgr.getAccessLogin(request);

        Date startTime = new Date(); // for AntwebUtil.finish(request, startTime);

        ActionForward a = Check.init(Check.TAXON, request, mapping); if (a != null) return a;
        ActionForward d = Check.valid(request, mapping); if (d != null) return d;

        Locale locale = getLocale(request);
        HttpSession session = request.getSession();

        String rank = request.getParameter("rank");

        if (ProjectMgr.hasMoved(request, response)) return null;

        boolean withImages = "true".equals(request.getParameter("images"));     // Mark.  Temp.  True creates performance problems;

        if (AntwebProps.isDevMode() && withImages) {  // If the server is struggling, this would be good functionality to restrict.
            ActionForward c = Check.loginValid(request, mapping); if (c != null) return c;
        }

        boolean withTaxa = true;

        boolean withSpecimen = false;
        if ("true".equals(request.getParameter("specimen")) || "true".equals((String) session.getAttribute("specimenTaxa"))) {
          withSpecimen = true;
          session.setAttribute("specimenTaxa", "true");
        }   
        if ("false".equals(request.getParameter("specimen"))) {
          withSpecimen = false;
          session.removeAttribute("specimenTaxa");
        }   

        String simpleStr = request.getParameter("simple");
        boolean simple = "true".equals(simpleStr);

        String isImaged = request.getParameter("isImaged");
        //A.log("execute() isImaged:" + isImaged);
        
        TaxaPageForm taxaPageForm = (TaxaPageForm) form;
		String caste = Caste.getCaste(taxaPageForm.getCaste(), request);
  	    //A.log("execute() rank:" + rank + " caste:" + caste + " formCaste:" + taxaPageForm.getCaste());

        String[] testStrings = {rank, simpleStr, isImaged, caste};
        if (HttpUtil.hasIllegalChars(testStrings, request)) {
          request.setAttribute("message", "Illegal characters.");
          return mapping.findForward("message");
        }

        Overview overview = null;
        try {
            overview = OverviewMgr.getAndSetOverview(request);
        } catch (AntwebException e) {
            return OverviewMgr.returnMessage(request, mapping, e);
        }

        if (AntwebMgr.isServerInitializing(overview)) {
          request.setAttribute("message", "One moment please, MuseumMgr is initializing.");
          return mapping.findForward("message");
        }

        TaxaPage taxaPage = new TaxaPage();
        taxaPage.setRequest(request);
        taxaPage.setBrowserParams(rank, overview);

        if (rank != null) {
          Connection connection = null;
          String connName = "TaxaPageAction.execute()" + AntwebUtil.getRandomNumber();

          if (HttpUtil.tooBusyForBots(request)) { HttpUtil.sendMessage(request, mapping, "Too busy for bots."); }

          //int uniqueNumber = AntwebUtil.getRandomNumber();
          try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, connName, HttpUtil.getTarget(request));

            //s_log.info("execute() uniqueNumber:" + uniqueNumber + " request:" + HttpUtil.getTarget(request));
            /*
              Some TaxaPageAction requests are not getting closed. Here we are logging a number
              when opened. Next we can see which request is not being closed by looking for the 
              same number below.
            */

            //if (overview instanceof Adm1) A.log("execute() overview:" + overview + " parent:" + overview.getParentName());
			if (overview == null) {
			  request.setAttribute("message", "overview not found");
			  return mapping.findForward("message");
			}        

            if (!Rank.isValid(rank)) {
                String message = "invalid rank:" + rank;
				s_log.info("execute() " + message); 
				request.setAttribute("message", message);
				return mapping.findForward("message");
            }

 			if ("species".equals(rank)
			  && (
			     Project.ALLANTWEBANTS.equals(overview)
			  || Project.WORLDANTS.equals(overview)   
			  )
			  && !"false".equals(isImaged)
			  && withImages
			  && overview == null
			  ) {
				String message = "Sorry, due to resultset size, this is an unreasonable request.  Rank:" + rank + " overview:" + overview;
				s_log.info("Execute() " + message); 
				request.setAttribute("message", message);
				return mapping.findForward("message");
			}

              String data = null;

              // Caching Logic Part I
              boolean isGetCache = "true".equals(request.getParameter("getCache"));  //fieldGuideForm.getGetCache()));  // this forces the fetching from cache if available.
              boolean isGenCache = "true".equals(request.getParameter("genCache"));   //fieldGuideForm.getGenCache());
              if (withImages && !isGenCache) {

                  boolean fetchFromCache = false && AntwebCacheMgr.isFetchFromCache(accessLogin, isGetCache);


                  // Return a cache paged if not logged in, and cached.
                  //A.log("execute() getCache:" + isGetCache);

                  if (fetchFromCache) {
                      // was: data = AntwebCacheMgr.fetchFromCache("taxaPage", project, rank);
                      data = AntwebCacheMgr.fetchFromCache("taxaPage", overview.getName(), rank);
                      if (data != null) {
                          //if (AntwebProps.isDevOrStageMode())
                          s_log.info("execute() Fetched cached page.  Rank:" + rank + " overview:" + overview + " cacheType:taxaPage");
                          PrintWriter out = response.getWriter();
                          out.println(data);
                          return null;
                      } else {

                          if (isGetCache) {
                              String message = "not fetched from cache.  Rank:" + rank + " overview:" + overview + " cacheType:taxaPage";
                              //if (AntwebProps.isDevOrStageMode())
                              s_log.info("Execute() " + message);
                              request.setAttribute("message", message);
                              return mapping.findForward("message");
                          }
                      }
                  }
              }

            String statusSetStr = StatusSet.getStatusSet(request, overview);
			String statusSetSize = StatusSet.getStatusSetSize(request);
            taxaPage.setStatusSetStr(statusSetStr);
            taxaPage.setStatusSetSize(statusSetSize);
            
            if (Rank.SUBSPECIES.equals(rank)) {
                String message = "Subspecies not supported for taxonomic page. Use species.";
                s_log.error("execute() " + message + " " + HttpUtil.getRequestInfo(request));
                request.setAttribute("message", message);
                return mapping.findForward("message");
            }


// no double
            //taxaPage.setOverview(overview);
            //A.log("execute() overview:" + overview);
            String successMsg = taxaPage.fetchChildren(connection, overview, rank, withImages, withTaxa, withSpecimen, true, caste, new StatusSet(statusSetStr)); //, orderBy);
            // String successMsg = "SUCCESS!";
            if (successMsg != null) {
                request.setAttribute("message", successMsg);
                return mapping.findForward("message");
            }

//         if (HttpUtil.getTarget(request).contains("ionName=Oceania") && (AntwebProps.isDevMode() || LoginMgr.isMark(request))) { s_log.warn("MarkNote() break:" + HttpUtil.getTarget(request)); return (mapping.findForward("error"));}
// Doubled

            /* 
              With withImages set, a request like this will take 2/5 minutes...  Really?
              http://localhost/antweb/taxonomicPage.do?rank=genus&project=allantwebants                
            */

/*
            // Caching Logic Part II
            if (false && withImages && !isGenCache && !isGetCache) {
              //int busy = DBStatus.getNumBusyConnections(dataSource);
                int busy = DBStatus.getNumBusyConnections(connection);
                AntwebCacheMgr.finish(request, connection, busy, startTime, "taxaPage", overview, rank);
            }
*/

            //if (overview instanceof Adm1) A.log("execute() overview:" + overview + " parent:" + overview.getParentName());
          } catch (SQLException e) {
            GregorianCalendar now = new GregorianCalendar();
            s_log.error("execute() at time " + now.get(Calendar.HOUR) 
              + ":" + now.get(Calendar.MINUTE) + ":" + now.get(Calendar.SECOND) + " e:" + e);
          } finally {
            DBUtil.close(connection, this, connName);
            //s_log.info("execute() closing uniqueNumber:" + uniqueNumber);
          }

          request.setAttribute("taxaPage", taxaPage);
        }

        // Set a transactional control token to prevent double posting
        saveToken(request);
        
        // A.log("execute() children:" + taxaPage.getChildren() + " simple:" + simple);                      

        if (taxaPage.getChildren() == null) {
            return mapping.findForward("failure");
        }

        return mapping.findForward("success");
    }
}

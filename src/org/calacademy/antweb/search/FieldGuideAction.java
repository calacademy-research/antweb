package org.calacademy.antweb.search;

import java.io.*;

import java.net.URL;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import java.sql.*;
import java.util.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.search.*;
import org.calacademy.antweb.geolocale.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class FieldGuideAction extends Action {

    private static Log s_log = LogFactory.getLog(FieldGuideAction.class);    
    
    private static int s_simultaneousExecutes = 0;
    private final static int MAX_SIMULTANEOUS_EXECUTES = 2;
    
	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        ActionForward a = Check.loginValid(request, mapping); if (a != null) return a; 
        ActionForward d = Check.valid(request, mapping); if (d != null) return d;

        Login accessLogin = LoginMgr.getAccessLogin(request);

		java.util.Date startTime = new java.util.Date(); // for AntwebUtil.finish(request, startTime);

        HttpSession session = request.getSession();
        //if (session.getAttribute("activeSession") == null) return mapping.findForward("sessionExpired");
        
    	FieldGuide fieldGuide = new FieldGuide();
		FieldGuideForm fieldGuideForm = ((FieldGuideForm) form);

		String rank = fieldGuideForm.getRank();
		String subfamily = fieldGuideForm.getSubfamily();
		String genus = fieldGuideForm.getGenus();
		String species = fieldGuideForm.getSpecies();
		String subspecies = fieldGuideForm.getSubspecies();

        Overview overview = OverviewMgr.getAndSetOverview(request);
        if (overview == null) return OverviewMgr.returnMessage(request, mapping);

        LocalityOverview localityOverview = null;        
        if (overview instanceof LocalityOverview) {
            localityOverview = (LocalityOverview) overview;
        }
                    
		if ((rank != null) && (rank.length() == 0 )) {
			rank = null;
		}
	
		if (rank == null) {
		  if ((subspecies != null) && (!subspecies.equals(""))) {
		    rank = "subspecies";
		  } else if ((species != null) && (!species.equals(""))) {
		    rank = "species";
          } else if  ((genus != null) && (!genus.equals(""))) {
            rank = "genus";
          } else rank = "subfamily";
		}
	
		String caste = fieldGuideForm.getCaste();
		if (AntwebProps.isDevMode() && Caste.DEFAULT.equals(caste)) {
		  caste = Caste.QUEEN;
        }
        A.log("FieldGuideAction.execute() caste:" + caste);
        
        if (s_simultaneousExecutes > MAX_SIMULTANEOUS_EXECUTES) {
            LogMgr.appendLog("throttle.txt", DateUtil.getFormatDateTimeStr(new java.util.Date()) + " " + AntwebUtil.getRequestInfo(request));
            String message = "Only " + s_simultaneousExecutes + " simultaneous field guide creation requests allowed at a time... try again shortly or log in for unrestricted access.";
            request.setAttribute("message", message);
            return (mapping.findForward("message")); 
        }
        ++s_simultaneousExecutes;

		String results = null;
		ArrayList<Taxon> theTaxa = null;
		java.sql.Connection connection = null;
		try {
			javax.sql.DataSource dataSource = getDataSource(request, "conPool");
			
            if (DBUtil.isServerBusy(dataSource, request)) {
              return mapping.findForward("message");            
            }

   		    connection = DBUtil.getConnection(dataSource, "FieldGuideAction.execute()");

   		    // Caching Logic Part I
            boolean isGetCache = ("true".equals(fieldGuideForm.getGetCache()));  // this forces the fetching from cache if available.
            String data = null;               
            boolean isGenCache = "true".equals(fieldGuideForm.getGenCache());

            if (!isGenCache) {
              // Return a cache paged if not logged in, and cached.              
              boolean fetchFromCache = AntwebCacheMgr.isFetchFromCache(accessLogin, isGetCache);              
              A.log("execute() fetchFromCache:" + fetchFromCache);
              if (fetchFromCache) {
                data = AntwebCacheMgr.fetchFromCache("fieldGuide", subfamily, genus, rank, overview);
                if (data != null) {
                  //if (AntwebProps.isDevOrStageMode())
                    s_log.info("execute() Fetched cached page.  subfamily:" + subfamily + " genus:" + genus + " rank:" + rank + " overview:" + overview.getName() + " cacheType:fieldGuide");              
                  PrintWriter out = response.getWriter();
                  out.println(data);
                  return null;
                } else {
                  if (isGetCache) {
                    String message = "not fetched from cache.  Subfamily:" + subfamily + " genus:" + genus + " rank:" + rank + " overview:" + overview.getName() + " cacheType:fieldGuide";
                    //if (AntwebProps.isDevOrStageMode()) 
                      s_log.info("Execute() " + message); 
                    request.setAttribute("message", message);
                    return (mapping.findForward("message"));
                  }
                }
              }
            }

			fieldGuide.setOverview(overview);
			fieldGuide.setRank(rank);

            if (localityOverview != null) {
                if (Utility.isBlank(localityOverview.getExtent())) s_log.warn("execute() overview:" + overview + " has empty blank extent");
                fieldGuide.setExtent(localityOverview.getExtent());
            }
            
            A.log("execute() overview:" + overview + " subfamily:" + subfamily);

			if ((subfamily != null) || (genus != null) || (species != null)) {
    		    // Taxon Field Guides
				Taxon taxon = Taxon.getTaxonOfRank(subfamily, genus, species, subspecies);
                if (taxon == null) {
                  String message = "taxon not found for subfamily:" + subfamily + " genus:" + genus + " species:" + species + " subfamily:" + subfamily;
                  s_log.error("execute() " + message); 
				  request.setAttribute("message", message);
				  return (mapping.findForward("message")); 				
				}
				taxon.setConnection(connection);
				taxon.setTaxonomicInfo();			
				if (taxon.getRank().equals(Rank.SPECIES) || taxon.getRank().equals(Rank.SUBSPECIES)) {
					taxon.setChildrenLocalized(overview);
				} else {
					taxon.setChildren(overview);
				}
				theTaxa = taxon.getChildren();
                A.log("execute() rank:" +  rank + " order:" + taxon.getOrderName() + " family:" + taxon.getFamily() + " subfamily:" + subfamily + " genus:" + genus + " species:" + species + " taxa.size:" + theTaxa.size());


				fieldGuide.setTitle(subfamily, genus, species, overview.getName());
				fieldGuide.setTaxa(theTaxa);
				
				fieldGuide.setMembers(connection, overview);
			} else { 
			    // Regional Field Guides
                A.log("execute() rank:" + rank + " overview:" + overview + " caste:" + caste);			
         		
         		TaxaPage taxaPage = new TaxaPage();

				boolean withImages = true;     // Mark.  Temp.  True creates performance problems;
				if (Project.ALLANTWEBANTS.equals(overview)) withImages = false;
                StatusSet statusSet = new StatusSet(StatusSet.ALL_DETERMINED);
				taxaPage.fetchChildren(connection, overview, rank, false, true, false, false, caste, statusSet);
                // With withImages set, a request like this will take 2/5 minutes...  Really, still?
                //   http://localhost/antweb/taxonomicPage.do?rank=genus&project=allantwebants
				fieldGuide.setTitle("of " + Rank.getPluralRank(rank) + " in " + overview.getTitle());				
				fieldGuide.setTaxa(taxaPage.getChildren());
				fieldGuide.setMembers(connection, overview);                
                A.log("after");			
			}

   		    // Caching Logic Part II
            if (!isGenCache && !isGetCache) {
                int busy = DBUtil.getNumBusyConnections(dataSource);  
                // A.log("execute() rank:" + rank + " subfamily:" + subfamily + " genus:" + genus);                                            
                AntwebCacheMgr.finish(request, connection, busy, startTime, "fieldGuide", subfamily, genus, rank, overview);
            }
			
            request.setAttribute("fieldGuide", fieldGuide);

		} catch (SQLException e) {
			s_log.error("execute() 1 e:" + e + " subfamily:" + subfamily + " genus:" + genus + " species:" + species + " subspecies:" + subspecies + " overview:" + overview.getName() + " rank:" + rank);

		} catch (ClassCastException e) {
            String message = "Specimen level field guides not supported.";
            request.setAttribute("message", message);
            if (AntwebProps.isDevMode()) AntwebUtil.logStackTrace(e);
            //s_log.error("execute() 1 e:" + e + " requestInfo:" + AntwebUtil.getRequestInfo(request));	
            return mapping.findForward("message");  		
        } catch (NumberFormatException e) {
            if (AntwebProps.isDevMode()) AntwebUtil.logStackTrace(e);
            String message = e.toString();
            s_log.error("execute() 2 e:" + e + " requestInfo:" + AntwebUtil.getRequestInfo(request));	
            request.setAttribute("message", message);
            return mapping.findForward("message");
        } catch (ArrayIndexOutOfBoundsException e) {
            s_log.warn("FieldGuideForm:" + fieldGuideForm.toString());
            AntwebUtil.logStackTrace(e);           
        } catch (Exception e) {
            s_log.error("execute() 3 e:" + e);
            AntwebUtil.logStackTrace(e);
		} finally {
		    --s_simultaneousExecutes;
            QueryProfiler.profile("fieldGuide", startTime);   
            DBUtil.close(connection, this, "FieldGuideAction.execute()");     		
		}

		// Set a transactional control token to prevent double posting
	    saveToken(request);
		
        AntwebUtil.finish(request, startTime);
          
        try {
          request.setAttribute("fieldGuideTaxaHashCode", fieldGuide.getTaxa().hashCode());  
        } catch (NullPointerException e) {
          s_log.error("execute() NPE on fieldGuildeTaxaHashCode.");
        }
        /* This is added to help us trap the "Incorrect fieldGuide taxa" condition.  After
           server restart the field guide returned is correct, but it's taxa has a 1 as
           it's hashcode and the set is empty */
		//s_log.warn("fieldGuide taxaSize:" + fieldGuide.getTaxa().size() + " request:" + request + " fieldGuide:" + fieldGuide + " theTaxa:" + fieldGuide.getTaxa() + " hashCode:" + fieldGuide.getTaxa().hashCode());
        
		return (mapping.findForward("fieldGuideByTaxon"));
	}
	
}

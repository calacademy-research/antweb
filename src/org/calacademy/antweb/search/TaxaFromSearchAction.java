package org.calacademy.antweb.search;

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

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

public final class TaxaFromSearchAction extends Action {

    private static Log s_log = LogFactory.getLog(TaxaFromSearchAction.class);

    public ActionForward execute( ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        // Extract attributes we will need
        Locale locale = getLocale(request);
        HttpSession session = request.getSession();

        TaxaFromSearchForm taxaForm = (TaxaFromSearchForm) form;
        List taxa = null;
        if (taxaForm.getTaxa() != null) {
            taxa = Arrays.asList(taxaForm.getTaxa());
        }
        String[] chosen = taxaForm.getChosen();
        ArrayList theTaxaList = new ArrayList();
       
        Overview overview = OverviewMgr.getAndSetOverview(request);
        if (overview == null) return OverviewMgr.returnMessage(request, mapping);
        
        if (taxa != null) {

            Taxon taxon = null;
            java.sql.Connection connection = null;
            
            try {
                javax.sql.DataSource dataSource = getDataSource(request, "conPool");
                connection = DBUtil.getConnection(dataSource, "TaxaFromSearchAction.execute()");

				String theTaxonName;
				Species theTaxon = null;
				String genus;
				String species;
				Iterator iter = taxa.iterator();
				int firstSpace = 0;
				while (iter.hasNext()) {
					theTaxon = new Species();
					theTaxonName = (String) iter.next();

					firstSpace = theTaxonName.indexOf(' ');

					genus = theTaxonName.substring(0, firstSpace);
					species = theTaxonName.substring(firstSpace + 1);
					theTaxon.setSpecies(species);  // was setName()
					theTaxon.setRank("species");
					theTaxon.setGenus(genus);
					theTaxon.setConnection(connection);
					theTaxon.setTaxonomicInfo(); //project);
					theTaxon.setChildren(overview);
					if (chosen != null) {
						theTaxon.filterChildren(chosen);
					}
					String params = "rank=species&name=" + theTaxon.getName();
					params += "&genus=" + theTaxon.getGenus();
		
					//if (project != null) {
					//    theParams += "&project=" + project;
					//}
					params += overview.getParams();
					A.log("execute() params:" + params);
					
					theTaxon.setBrowserParams(params);
					//theTaxon.setDescription(project);  // was Dec 29, 2010 Mark
					theTaxon.setImages(overview);
					theTaxon.setConnection(null);  // Mark added July 4, 2013
					theTaxaList.add(theTaxon);
				}

            } catch (Exception sqle) {
                s_log.error("execute() e:" + sqle);
                return (mapping.findForward("failure"));
            } finally {
                DBUtil.close(connection, this, "execute()");
            }

            session.setAttribute("taxaToCompare", theTaxaList); 
            A.log("execute() scope:" + mapping.getScope());

/* // Was Feb2020
            if ("request".equals(mapping.getScope())) {
                request.setAttribute("taxaToCompare", theTaxaList);
            } else {
                ArrayList oldList = (ArrayList) session.getAttribute("taxaToCompare");
                if (oldList != null) {
                    Iterator finalIter = oldList.iterator();
                    Taxon oldTaxon = null;
                    while (finalIter.hasNext()) {
                        oldTaxon = (Taxon) finalIter.next();

                        try {
                            oldTaxon.callFinalize();
                        } catch (Throwable e) {
                            s_log.error("error finalizing " + oldTaxon.getName() + " " + e);
                        }
                    }
                }
                session.setAttribute("taxaToCompare", theTaxaList);
            }
*/

        }

        // Set a transactional control token to prevent double posting
        saveToken(request);
        
        if (request.getParameter("shot") != null) {
            return (mapping.findForward("oneView"));
        } else {
            return (mapping.findForward("success"));
        }
    }
}

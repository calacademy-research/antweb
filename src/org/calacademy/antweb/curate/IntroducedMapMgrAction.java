package org.calacademy.antweb.curate;

import java.util.*;
import java.io.*;
import java.sql.*;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/**
 * /introducedMapMgr.do: the Introduced Map Manager (curate/introducedMapMgr.jsp), admins only (Check.admin): for
 * each species of project introducedants (table proj_taxon) the bioregions where it is native (table taxon_prop,
 * prop introducedMap). A POST saves one species (TaxonPropDb.updateIntroducedMap). Opening the page also writes
 * an empty map for a listed species that has none; after any write TaxonPropMgr is reloaded. Docs:
 * docs/guides/manager-tools.md (Introduced Map Manager). Tests: tests/e2e/test_manager_tools.py,
 * test_hardening_2026_10_18.py.
 */
public final class IntroducedMapMgrAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(IntroducedMapMgrAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        HttpSession session = request.getSession();

        // Administrators only, like the other managers in the admin list (the page also fills
        // in empty maps when it loads).
        ActionForward c = Check.admin(request, mapping); if (c != null) return c;

        // Adding the empty rows changes data (a species with a row, even an empty one, counts as
        // introduced): only by POST from the page's button, never on simply opening the page.
        boolean createEmptyMaps = "createEmptyMaps".equals(request.getParameter("action"));
        if (createEmptyMaps) {
            ActionForward confirm = PostOnly.confirm(request, mapping,
                "Add an empty bioregion row for each listed species that has none.");
            if (confirm != null) return confirm;
        }
        int missingMapCount = 0;

		BioregionMapMgrForm bioregionMapMgrForm = (BioregionMapMgrForm) form;
        String orderBy = bioregionMapMgrForm.getOrderBy();

        orderBy = " order by genus, species, subspecies";
        s_log.debug("execute() orderBy:" + orderBy + " param:" + request.getParameter("orderBy"));

        ArrayList<Taxon> speciesList = new ArrayList<>();

        boolean reloadTaxonPropMgr = false;

        Connection connection = null;
        Statement stmt = null;
        ResultSet rset = null;
        try {         
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "IntroducedMapMgrAction.execute()");

			// (The old "refresh" of this table is disabled; see below.)
			if (HttpUtil.isPost(request) && !bioregionMapMgrForm.isRefresh() && !createEmptyMaps) {
			  s_log.debug("execute POST values:" + bioregionMapMgrForm);
			  TaxonPropDb taxonPropDb = new TaxonPropDb(connection);
			  String taxonName = bioregionMapMgrForm.getTaxonName();
			  if (!taxonPropDb.isTaxon(taxonName)) {
                request.setAttribute("message", "Nothing was changed: unknown species " + Html.esc(taxonName) + ".");
                return mapping.findForward("message");
			  }
              taxonPropDb.updateIntroducedMap(taxonName, bioregionMapMgrForm.getValues());
              reloadTaxonPropMgr = true;
			}

            /* // Won't be going backwards.
			A.log("execute() refresh:" + bioregionMapMgrForm.isRefresh());
            if (bioregionMapMgrForm.isRefresh() == true) {
			  TaxonPropDb taxonPropDb = new TaxonPropDb(connection);
              taxonPropDb.refreshIntroducedMap();
              reloadTaxonPropMgr = true;
            }
            */
            
            TaxonPropDb taxonPropDb = new TaxonPropDb(connection);
            ArrayList<String> introducedList = taxonPropDb.getIntroducedList(orderBy);            
            for (String taxonName : introducedList) {

              boolean introducedMapFound = false;

              DummyTaxon species = new DummyTaxon();
              species.setTaxonName(taxonName);

              DBUtil.close(stmt, rset, "IntroducedMapMgrAction.execute() row");
              PreparedStatement ps = connection.prepareStatement("select value from taxon_prop where prop = 'introducedMap' and taxon_name = ?");
              ps.setString(1, taxonName);
              stmt = ps;
              rset = ps.executeQuery();

              int i = 0;
              while (rset.next()) {
                ++i;
                introducedMapFound = true;
                String introducedMap = rset.getString("value");                
                species.setIntroducedMap(introducedMap);
              }
              if (i == 0) {
                // No row yet. Create the empty map only when asked (POST); otherwise count it for the page.
                if (createEmptyMaps) {
                  taxonPropDb.updateIntroducedMap(taxonName, "");
                  reloadTaxonPropMgr = true;
                } else {
                  ++missingMapCount;
                }
              }
              
              speciesList.add(species); 

			  //A.log("execute() taxonName:" + taxonName);
            }

            if (reloadTaxonPropMgr) {
  	  	      TaxonPropMgr.populate(connection, true);
            }
                        
        } catch (SQLException e) {
          s_log.error("execute() e:" + e);
        } finally {
            DBUtil.close(connection, stmt, rset, this, "IntroducedMapMgrAction.execute()");        
        }    

        //request.setAttribute("genusBioregionMap", genusBioregionMap);
        request.setAttribute("speciesList", speciesList);
        request.setAttribute("missingMapCount", missingMapCount);
        request.setAttribute("form", bioregionMapMgrForm);
        
        return mapping.findForward("success");
    }
}

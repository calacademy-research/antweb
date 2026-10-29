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
 * /bioregionMapMgr.do: the Bioregion Map Manager (curate/bioregionMapMgr.jsp), the bioregions where each valid
 * extant genus is native (table taxon_prop, prop bioregionMap). Curators look (Check.curator); a POST by an
 * admin saves one genus (TaxonPropDb.updateBioregionMap) and gives the genus in memory (TaxonMgr) the new map.
 * Specimen uploads flag specimens collected outside it. Docs: docs/guides/manager-tools.md (Bioregion Map
 * Manager). Tests: tests/e2e/test_manager_tools.py.
 */
public final class BioregionMapMgrAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(BioregionMapMgrAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        HttpSession session = request.getSession();
        HttpUtil.setUtf8(request, response);

        ActionForward c = Check.curator(request, mapping); if (c != null) return c;

/*
          String message = "Adm1Mgr Error. Country:" + countryName + "   Adm1:" + adm1;
          request.setAttribute("message", message);
          return (mapping.findForward("message"));
*/

		BioregionMapMgrForm bioregionMapMgrForm = (BioregionMapMgrForm) form;
        String orderBy = bioregionMapMgrForm.getOrderBy();
        //String orderBy = request.getParameter("orderBy");
        if ("genus".equals(orderBy)) orderBy = " order by t.genus";
          else orderBy = " order by t.subfamily, t.genus";
        s_log.debug("execute() orderBy:" + orderBy + " param:" + request.getParameter("orderBy"));

        //TreeMap<Taxon, String> genusBioregionMap = new TreeMap<String, String>();
        ArrayList<Taxon> genusList = new ArrayList<>();

        Connection connection = null;
        Statement stmt = null;
        ResultSet rset = null;
        try {         
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "BioregionMgrAction.execute()");

			// The "Refresh" that rebuilt the whole table from specimen data (isRefresh=true) was removed
			// on 2026-10-21: it counted introduced populations as native and overwrote hand corrections.
			// The isRefresh field (shared with the Introduced Map Manager's form) is ignored here.
            if (HttpUtil.isPost(request)) {
			  // A genus's native bioregions decide the "introduced" flags across the site: administrators
			  // only, like the Introduced Map Manager (curators can look at the table).
			  if (!LoginMgr.isAdmin(request)) {
                request.setAttribute("message", "Only an administrator can change the native bioregions. Nothing was changed.");
                return mapping.findForward("message");
			  }
			  s_log.debug("execute POST values:" + bioregionMapMgrForm);
			  TaxonPropDb taxonPropDb = new TaxonPropDb(connection);
			  String taxonName = bioregionMapMgrForm.getTaxonName();
			  if (!taxonPropDb.isTaxon(taxonName)) {
                request.setAttribute("message", "Nothing was changed: unknown genus " + Html.esc(taxonName) + ".");
                return mapping.findForward("message");
			  }
              taxonPropDb.updateBioregionMap(taxonName, bioregionMapMgrForm.getValues());
              // Uploads check specimens against the genus in memory: give it the new map now
              // (otherwise only the next reload of the taxa, after an upload or the nightly run, has it).
              Taxon inMemory = TaxonMgr.getGenus(taxonName);
              if (inMemory != null) inMemory.setBioregionMap(bioregionMapMgrForm.getValues());
			}

            // All valid extant genera with their bioregion map (one query; no value is put into SQL).
            String query1 = "select t.taxon_name, tp.value from taxon t"
                + " left join taxon_prop tp on tp.taxon_name = t.taxon_name and tp.prop = 'bioregionMap'"
                + " where t.taxarank = 'genus' and t.status = 'valid' and t.fossil = false " + orderBy;
            stmt = connection.createStatement();
            rset = stmt.executeQuery(query1);
            while (rset.next()) {
              DummyTaxon genus = new DummyTaxon();
              genus.setTaxonName(rset.getString("taxon_name"));
              String bioregionMap = rset.getString("value");
              if (bioregionMap != null) genus.setBioregionMap(bioregionMap);
              genusList.add(genus);
            }
                        
        } catch (SQLException e) {
          s_log.error("execute() e:" + e);
        } finally {
            DBUtil.close(connection, stmt, rset, this, "BioregionMgrAction.execute()");        
        }    

        //request.setAttribute("genusBioregionMap", genusBioregionMap);
        request.setAttribute("genusList", genusList);
        request.setAttribute("form", bioregionMapMgrForm);
        
        return mapping.findForward("success");
    }
}

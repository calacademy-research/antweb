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
        if ("genus".equals(orderBy)) orderBy = " order by genus";
          else orderBy = " order by subfamily, genus";
        s_log.debug("execute() orderBy:" + orderBy + " param:" + request.getParameter("orderBy"));

        //TreeMap<Taxon, String> genusBioregionMap = new TreeMap<String, String>();
        ArrayList<Taxon> genusList = new ArrayList<>();

        Connection connection = null;
        Statement stmt = null;
        ResultSet rset = null;
        try {         
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "BioregionMgrAction.execute()");

			s_log.debug("execute() refresh:" + bioregionMapMgrForm.isRefresh());
            if (bioregionMapMgrForm.isRefresh()) {
              // Rebuilds the whole table: admins only, and only from the POST button (form token).
              if (!LoginMgr.isAdmin(request)) {
                request.setAttribute("message", "Only an administrator can refresh the bioregion maps.");
                return mapping.findForward("message");
              }
              if (!HttpUtil.isPost(request)) {
                request.setAttribute("message", "Nothing was changed. Use the Refresh button on the Bioregion Map Manager page.");
                return mapping.findForward("message");
              }
			  TaxonPropDb taxonPropDb = new TaxonPropDb(connection);
              taxonPropDb.refreshBioregionMap();
            } else if (HttpUtil.isPost(request)) {
			  s_log.debug("execute POST values:" + bioregionMapMgrForm);
			  TaxonPropDb taxonPropDb = new TaxonPropDb(connection);
			  String taxonName = bioregionMapMgrForm.getTaxonName();
			  if (!taxonPropDb.isTaxon(taxonName)) {
                request.setAttribute("message", "Nothing was changed: unknown genus " + Html.esc(taxonName) + ".");
                return mapping.findForward("message");
			  }
              taxonPropDb.updateBioregionMap(taxonName, bioregionMapMgrForm.getValues());
			}

            String query1 = "select taxon_name from taxon where taxarank = 'genus' and status = 'valid' and fossil = false " + orderBy;
            stmt = connection.createStatement();
            rset = stmt.executeQuery(query1);

            Statement stmt2 = null;
            ResultSet rset2 = null;
            
            while (rset.next()) {

              boolean bioregionMapFound = false;

              String taxonName = rset.getString("taxon_name");

              DummyTaxon genus = new DummyTaxon();
              genus.setTaxonName(taxonName);

  			  String query2 = "select value from taxon_prop where prop = 'bioregionMap' and taxon_name = '" + taxonName + "'";       
              stmt2 = connection.createStatement();
              rset2 = stmt2.executeQuery(query2);
              
              while (rset2.next()) {
                bioregionMapFound = true;
                String bioregionMap = rset2.getString("value");
                
                genus.setBioregionMap(bioregionMap);
                //genusBioregionMap.put(taxonName, bioregionMap);
              }

              genusList.add(genus); 
                          
              stmt2.close();
              rset2.close();
			
			  //A.log("execute() taxonName:" + taxonName);
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

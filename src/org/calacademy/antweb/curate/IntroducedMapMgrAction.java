package org.calacademy.antweb.curate;

import java.util.*;
import java.io.*;
import java.sql.*;
import javax.sql.DataSource;

import javax.servlet.ServletException;
import javax.servlet.http.*;

import org.apache.struts.action.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class IntroducedMapMgrAction extends Action {

    private static Log s_log = LogFactory.getLog(IntroducedMapMgrAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        HttpSession session = request.getSession();

        ActionForward c = Check.login(request, mapping); if (c != null) return c;  

		BioregionMapMgrForm bioregionMapMgrForm = (BioregionMapMgrForm) form;
        String orderBy = bioregionMapMgrForm.getOrderBy();

        orderBy = " order by genus, species, subspecies";
        A.log("execute() orderBy:" + orderBy + " param:" + request.getParameter("orderBy"));

        ArrayList<Taxon> speciesList = new ArrayList<>();

        boolean reloadTaxonPropMgr = false;

        Connection connection = null;
        Statement stmt = null;
        ResultSet rset = null;
        try {         
            javax.sql.DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "IntroducedMapMgrAction.execute()");

			if (HttpUtil.isPost(request)) {
			  A.log("execute POST values:" + bioregionMapMgrForm);
			  TaxonPropDb taxonPropDb = new TaxonPropDb(connection);
              taxonPropDb.updateIntroducedMap(bioregionMapMgrForm.getTaxonName(), bioregionMapMgrForm.getValues());
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

  			  String query = "select value from taxon_prop where prop = 'introducedMap' and taxon_name = '" + taxonName + "'";       
              stmt = connection.createStatement();
              rset = stmt.executeQuery(query);

              int i = 0;
              while (rset.next()) {
                ++i;
                introducedMapFound = true;
                String introducedMap = rset.getString("value");                
                species.setIntroducedMap(introducedMap);
              }
              if (i == 0) {
                // No fields are flagged. So no map exists? Create the empty Map.
                //A.log("Introduced Map Mgr. Create empty Map for taxonName:" + taxonName);
                taxonPropDb.updateIntroducedMap(taxonName, "");
                reloadTaxonPropMgr = true;
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
        request.setAttribute("form", bioregionMapMgrForm);
        
        return (mapping.findForward("success"));
    }
}

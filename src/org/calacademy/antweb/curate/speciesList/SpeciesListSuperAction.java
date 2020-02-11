package org.calacademy.antweb.curate.speciesList;

import java.util.*;
import javax.servlet.http.*;
import org.apache.struts.action.*;
import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.*;
import org.calacademy.antweb.search.AdvancedSearchAction;
import org.calacademy.antweb.util.*;

public class SpeciesListSuperAction extends AdvancedSearchAction {

  private static Log s_log = LogFactory.getLog(SpeciesListSuperAction.class);
  /*  
    protected void putLookupDataInRequest(HttpServletRequest request, Connection connection) throws SQLException {
   
      String query = "";
      ResultSet rset = null;
      Statement stmt = DBUtil.getStatement(connection, "SpeciesListSuperAction.putLookupDataInRequest()");
      try {
                
        ArrayList<String> subfamilies = new ArrayList<String>();
        ArrayList<String> genera = new ArrayList<String>();
        ArrayList<String> speciesList = new ArrayList<String>();
        
        query = "select distinct genus from taxon where family=\"formicidae\";";

        stmt = connection.createStatement();
        rset = stmt.executeQuery(query);

        rset = stmt.executeQuery(query);
        while (rset.next()) {
            String genus = rset.getString(1);
            genera.add(genus);
        }
        request.setAttribute("genera", genera);

        DBUtil.close(stmt, rset, this, "");

        query = "select distinct species from taxon where family=\"formicidae\";";
        stmt = connection.createStatement();
        rset = stmt.executeQuery(query);
        while (rset.next()) {
            String species = rset.getString(1);
            speciesList.add(species);
        }
        request.setAttribute("specieList", speciesList);
        
      } finally {
        DBUtil.close(stmt, "SpeciesListSuperAction.putLookupDataInRequest()");
      }     
    }
  
*/

}

/*

1) Simply yes?
Rename form

  old: subfamily genus species
  new: subfamily genus [species]
  [Rename]

2) Added taxon must be a morpho if being created afresh?
   If pre-existing, may be anything.
   
3) Work for Luke?  Drop down box actions

*/   

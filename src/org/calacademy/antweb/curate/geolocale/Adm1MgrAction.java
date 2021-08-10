package org.calacademy.antweb.curate.geolocale;

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

public final class Adm1MgrAction extends Action {

    private static Log s_log = LogFactory.getLog(Adm1MgrAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
 
        HttpSession session = request.getSession();
        HttpUtil.setUtf8(request, response);      

        ActionForward c = Check.login(request, mapping); if (c != null) return c;

        DynaActionForm df = (DynaActionForm) form;
        String adm1 = (String) df.get("adm1Name");
        String countryName = (String) df.get("countryName");
                        
        Integer groupIdInt = (Integer) df.get("groupId");
        int groupId = 0;
        if (groupIdInt != null) {
          groupId = groupIdInt.intValue();
        }
        A.log("adm1:" + adm1 + " country:" + countryName);
        Geolocale country = GeolocaleMgr.getValidCountry(countryName);

        if (countryName == null || adm1 == null || country == null) {
          String message = "Adm1Mgr Error. Country:" + countryName + "   Adm1:" + adm1;
          request.setAttribute("message", message);
          return (mapping.findForward("message"));
        }

        ArrayList<Geolocale> list = new ArrayList<>();
        String codeStr = "";

        String escapeCountryName = AntFormatter.escapeQuotes(countryName);
        String escapeAdm1 = AntFormatter.escapeQuotes(adm1);
        String query1 = "select id, name, parent, valid_name, is_valid, source from geolocale where parent = '" + escapeCountryName + "' order by is_valid desc, name"; 
        String groupClause = "";
        if (groupId > 0) groupClause = " and access_group = " + groupId;
        String query2 = "select code from specimen where adm1 = '" + escapeAdm1 + "' and country = '" + escapeCountryName + "' " + groupClause + " limit 20";
        
        A.log("Adm1MgrAction execute() query1:" + query1);

        Connection connection = null;
        Statement stmt = null;
        ResultSet rset = null;
        try {         
            javax.sql.DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "SpeciesListToolAction.execute()");

            stmt = connection.createStatement();
            rset = stmt.executeQuery(query1);
            while (rset.next()) {
              Geolocale geolocale = new Geolocale();
              geolocale.setId(rset.getInt("id"));
              geolocale.setName(rset.getString("name"));
              geolocale.setParent(rset.getString("parent"));
              geolocale.setValidName(rset.getString("valid_name"));
              geolocale.setIsValid(rset.getBoolean("is_valid"));
              geolocale.setSource(rset.getString("source"));
              //A.log("Adm1MgrAction name:" + geolocale.getName() + " country:" + geolocale.getParent());
              list.add(geolocale);
            }

            int i = 0;
            stmt = connection.createStatement();
            rset = stmt.executeQuery(query2);
            while (rset.next()) {
              ++i;
              String code = rset.getString("code");
              //A.log("Adm1MgrAction code:" + code);
              String codeTag = " <a href='" + AntwebProps.getDomainApp() + "/specimen.do?code=" + code + "'>" + code + "</a>";
              if (i > 1) codeTag = ", " + codeTag;
              codeStr += codeTag;
              if (i >= 20) {
                codeStr += "...";
                break;
              }
            }

        } catch (SQLException e) {
          s_log.error("execute() adm1:" + adm1 + " country:" + countryName + " e:" + e);
        } finally {
            DBUtil.close(connection, stmt, rset, this, "Adm1MgrAction.execute()");        
        }    

//        String countryStr = "";
//        if (!countryName.equals(country.getName())) countryStr = "Valid country name for country:" + countryName + " is:" + country.getName() + ".  <br><br>";

//        String message = countryStr + "Adm1:" + adm1 + " did not match any of the valid adm1 for country:" + country.getName() + ", listed below.";
        
        request.setAttribute("form", df);
        
        request.setAttribute("country", country.getName());
        request.setAttribute("adm1", adm1);
//        request.setAttribute("message", message);
        request.setAttribute("list", list);
        request.setAttribute("codes", codeStr);
        
        return (mapping.findForward("success"));

//        String link = AntwebProps.getDomainApp() + "/editGeolocale.do?adm1=" + adm1 + "&country=" + country;
//        return (mapping.findForward("adm1"));
    }
}

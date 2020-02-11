<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.geolocale.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>

<%@ page import="java.util.*" %>

<br>

<h1>Country Adm1 List</h1>

<br>Preferred adm1s in bold. Alternative Adm1s in gray followed by preferred name in parentheses.
<br>
<br><b>Downloadable tab-delimited mapping files:</b>
<br>&nbsp;&nbsp;&nbsp;Antweb adm1/country mapping: <a href="<%= AntwebProps.getDomainApp() %>/countryAdm1List.do?action=antwebAdm1s">antwebAdm1s.txt</a>
<br>&nbsp;&nbsp;&nbsp;Accepted adm1/country mapping: <a href="<%= AntwebProps.getDomainApp() %>/countryAdm1List.do?action=acceptedAdm1s">acceptedAdm1s.txt</a>
<br>

<div class=left>
<%
   
    ArrayList<Geolocale> geolocales = GeolocaleMgr.getGeolocales();    
    String message = null; // (String) request.getAttribute("message"); 
    if (geolocales == null) message = "Geolocales is null";

    int regionCount = 0;
    int subregionCount = 0;
    int countryCount = 0;
    int validAdm1Count = 0;
    int invalidAdm1Count = 0;    
        
    if (message != null) out.println("<h2>" + message + "</h2>");

      ArrayList<Region> regions = GeolocaleMgr.getDeepRegions();

      int geolocaleCount = 0;
      for (Region region : regions) { 
        ++regionCount;
%>
        <br><br><b>Region: <%= region %></b>
<%      for (Subregion subregion : region.getSubregions()) { 
          ++subregionCount;
%>
          <br><br>&nbsp;&nbsp;<b>Subregion: <%= subregion %></b>
<%        for (Country country : subregion.getAllCountries()) { 
            if (!country.isValid()) continue;
            ++countryCount;
%>
            <br><br>&nbsp;&nbsp;&nbsp;&nbsp;<b>Country: <%= country %></b>
<%          for (Adm1 adm1 : country.getAllAdm1s()) {
              String color = "black";
              String appendStr = "";
              if (!adm1.getIsValid()) {
                ++invalidAdm1Count;
                color = "grey";
                String validName = adm1.getValidName();
                if (validName == null) continue;
                appendStr = " <b>(" + validName + ")</b>";
              }
              String prefix = "<font color=" + color + ">";
              String postfix = "</font>";
              if (adm1.getIsValid()) {
                ++validAdm1Count;
                prefix += "<b>";
                postfix += "</b>";
              }
%>
              <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<%= prefix %><%= adm1 %><%= postfix %><%= appendStr %>
<%
            }
          }
        }
      }

%>
<br><br><br>
<hr>
<h2>Totals</h2>
Region Count:<%= regionCount %>
<br>Subregion Count: <%= subregionCount %>
<br>Country Count:<%= countryCount %>
<br>Valid Adm1 Count:<%= validAdm1Count %>
<br>Invalid Adm1 Count:<%= invalidAdm1Count %>



</div > 



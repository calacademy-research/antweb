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

<div class=left>
<br>

<h1>Antweb Bioregion Country/Adm1 Info</h1>
<br><br>
<a href="<%= AntwebProps.getDomainApp() %>/image/indomalayaAustralasia.png"><img src="<%= AntwebProps.getDomainApp() %>/image/indomalayaAustralasia.png" border="0" width="300" title="Indomalaya / Australasia"></a>
<a href="<%= AntwebProps.getDomainApp() %>/image/indomalayaPalearctic.jpg"><img src="<%= AntwebProps.getDomainApp() %>/image/indomalayaPalearctic.jpg" border="0" width="300" title="Indomalaya / Palearctic"></a>
<a href="<%= AntwebProps.getDomainApp() %>/image/mexico.png"><img src="<%= AntwebProps.getDomainApp() %>/image/mexico.png" border="0" width="300" title="Mexico"></a>

<br>Counties in Bold, adm1s in gray.
<br>


<%
    //String domainApp = AntwebProps.getDomainApp();
    ArrayList<HashMap<String, String>> bioregionCountryList = (ArrayList<HashMap<String, String>>) request.getAttribute("bioregionCountryList");

    String message = null; // (String) request.getAttribute("message");

    if (bioregionCountryList == null) message = "Bioregion Country List is null";

    if (message != null) out.println("<h2>" + message + "</h2>");

    String lastBioregion = null;
    String lastCountry = null;
    
    for (HashMap<String, String> bioregionCountry : bioregionCountryList) {
      boolean displayBioregion = false;
      String bioregion = bioregionCountry.get("bioregion");
      if (!bioregion.equals(lastBioregion)) displayBioregion = true;
      lastBioregion = bioregion;

      boolean displayCountry = false; 
      String country = bioregionCountry.get("country");
      if (!country.equals(lastCountry)) displayCountry = true;
      lastCountry = country;
      
      String adm1 = bioregionCountry.get("adm1");
      String countryBioregion = bioregionCountry.get("countryBioregion");
      String countryBioregionAbbrev = Bioregion.getAbbrev(countryBioregion);

      //A.log("bioregionCountryLIst-body.jsp bioregion:" + bioregion + " country:" + country + " countryBioregion:" + countryBioregion + " countryBioregionAbbrev:" + countryBioregionAbbrev);

      if (countryBioregionAbbrev != null) {
        countryBioregionAbbrev = "(Primary bioregion: " + countryBioregion + ")";
      } else {
        countryBioregionAbbrev = "";
      }
      if (displayBioregion) { %>
        <br><br><b>Bioregion: <%= bioregion %></b>
   <% }
      if (displayCountry) { %>
        <br>&nbsp;&nbsp;<b><%= country %> <%= countryBioregionAbbrev %></b>
   <% }

      if (!"".equals(adm1)) { %>
        <br>&nbsp;&nbsp;&nbsp;&nbsp;<%= adm1 %>
   <% } %>      
      
 <% } %>

</div > 



<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.geolocale.*" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>

<!-- contribution-body.jsp -->
<%
Curation curation = (Curation) request.getAttribute("curation");
%>

<div class=left>

<br>
<h2>Curation</h2>
<hr>
<% if (curation == null) { %>
     <%= "none" %>
<% } else { %>

<br><b>Taxon: <a href='<%= AntwebProps.getDomainApp() %>/description.do?taxonName=<%= curation.getTaxonName() %>'><%= Taxon.getDisplayName(curation.getTaxonName()) %></a></b> 

<% 
Geolocale geolocale = GeolocaleMgr.getGeolocale(curation.getGeolocaleId());
%>
<br><b>Geolocale:</b> <%= geolocale.getLink() %>
<br><b>Display Source:</b> <%= curation.getSourceDisplay() %>
<br><b>Created:</b> <%= curation.getCreated() %>
<% Curator curator = LoginMgr.getCurator(curation.getCuratorId()); 
String curatorStr = "n/a";
if (curator != null) {
  curatorStr = curator.getLink();
}
%>

<br><b>Curator:</b> <%= curatorStr %>

<% if (LoginMgr.isDeveloper(request)) { %>
<br><br><br>
<h2>Dev only:</h2>
<b>Source:</b> <%= curation.getSource() %>
<% } %>

<% } %>

</div>






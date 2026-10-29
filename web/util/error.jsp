<%-- Error page ("Server Error. Apologies.") of the JSPs in web/util/ that name errorPage="error.jsp" (a
     relative path): serverDebug.jsp, serverStatus.jsp, sessionProps.jsp, statsPage.jsp, statsPage-body.jsp,
     listSpecimenUploads.jsp. The other pages use the site's /error.jsp. --%>
<%@ page import="org.calacademy.antweb.Taxon" %>

<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.geolocale.*" %>

<%@ page import="java.util.*" %>

<h1>error.jsp</h1>

Server Error. Apologies.

<%
String t = null;
//out.println(t.toString());
%>
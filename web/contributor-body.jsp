<%@ page language="java" %>
<%@ page import = "java.util.ResourceBundle" %>
<%@ page import = "java.util.ArrayList" %>
<%@ page import = "org.calacademy.antweb.*" %>
<%@ page import = "java.util.Date" %>
<%@ page import = "org.calacademy.antweb.util.*" %>
<%@ page import = "org.calacademy.antweb.geolocale.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.upload.Upload" %>

<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<% 
  Curation curation = (Curation) request.getAttribute("curation");

%>

<h2>Curation</h2>

<div class=right>
<br><b><a href="<%= AntwebProps.getDomainApp() %>/groups.do">Contributor</a></b>
</div>



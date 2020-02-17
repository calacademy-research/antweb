<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<title>Distribution Status Display Page</title>

<h2><u>Antweb Distribution Status Display Page</u></h2>

<br><img src="<%= AntwebProps.getDomainApp() %>/image/1x1.gif" width="11" height="12" border="0"> <b>Native</b> - This ant taxon is native to this location. This is the default.
<p><img src="<%= AntwebProps.getDomainApp() %>/image/redI.png" width="11" height="12" border="0"> <b>Introduced</b> - This species of ant is not native to this bioregion.
<p><img src="<%= AntwebProps.getDomainApp() %>/image/greenE.png" width="11" height="12" border="0"> <b>Endemic</b> - This species is only found in this location. Designation coming soon.</b>



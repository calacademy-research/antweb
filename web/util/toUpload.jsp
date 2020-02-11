<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import="org.calacademy.antweb.util.*" %>

<%@ page import="java.util.*" %>


<%@include file="/common/antweb-defs.jsp" %>

<%
    String domainApp = AntwebProps.getDomainApp();
%>

<h1>ToUpload Directory Tool</h1>

<div class=left>
<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br><br>

<%
    String message = (String) request.getAttribute("message"); 
    if (message != null) out.println(message);
%>
</div > 

<br>
View the <a href = "<%= domainApp %>/showLog.do?action=apacheLog">Error Log</a>
<br>
<a href = "<%= domainApp %>/toUpload.do?action=clear">Clear</a> out the ToUpload directory.  (Images retained on server).

<br><br>In cases, these links may resolve problems.  Use with caution.  Contact Michele Esposito or Mark Johnson.
<br>&nbsp;&nbsp;&nbsp;<a href="<%= domainApp %>/util.do?action=moveImages&code=">Move</a> old Antweb Images (to <a href="<%= domainApp %>/images/bak/">bak</a> dir).
<br>&nbsp;&nbsp;&nbsp;<a href="<%= domainApp %>/util.do?action=changeOwnerAndPerms&code=">Change</a> owner of existing Antweb Images to apache.

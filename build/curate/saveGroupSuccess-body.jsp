<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="org.calacademy.antweb.Group" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<% String domainApp = (new Utility()).getDomainApp(); %>

<div class=left>
<h1>Success!</h1>
<hr></hr>
You have successfully modified group information.

<% if (LoginMgr.isAdmin(request)) { %>
<p><a href="<%= domainApp %>/viewGroups.do">Return to the Group Manager</a>.
<br>
<p>Go to <a href="<%= domainApp %>/viewLogins.do">Login Manager</a>
<% } else { %>
<p><a href="<%= domainApp %>/curate.do">Return to Curatorial Tools</a>.
<% } %>
 
</div>
<div class=right>
</div> 

<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<% String domainApp = (new Utility()).getDomainApp(); %>

<%                            
    Login accessLogin = LoginMgr.getAccessLogin(request);
%>

<div class=left>
<h1>Success!</h1>
<hr></hr>
You have successfully modified login information.

<% if (LoginMgr.isAdmin(request)) { %>
<p>&nbsp;&nbsp;&nbsp;<a href="<%= domainApp %>/viewLogins.do">Login Manager</a>
<br>&nbsp;&nbsp;&nbsp;<a href="<%= domainApp %>/viewGroups.do">Group Manager</a>
<% } else { %>
<p><a href="<%= domainApp %>/curate.do">Return to Curatorial Tools</a>.
<% } %>
 
</div>
<div class=right>
</div> 

<%-- Body of the login saved page: the changes made (request attribute of LoginEditPage.CHANGES_ATTR, rendered by
     LoginEditPage.renderChanges) and links back to the login (savedLoginId), the Login Manager and the upload
     groups for admins, or to the Curate page. Read-only. --%>
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

<%
    java.util.List<String> changes = (java.util.List<String>) request.getAttribute(org.calacademy.antweb.curate.login.LoginEditPage.CHANGES_ATTR);
    Object savedLoginId = request.getAttribute("savedLoginId");
%>
<div class=left>
<h1>Login saved</h1>
<hr></hr>
<% if (changes == null) { %>
Your changes are saved.
<% } else { %>
<%= org.calacademy.antweb.curate.login.LoginEditPage.renderChanges(changes) %>
<p>The person sees new rights the next time they log in.</p>
<% } %>

<% if (LoginMgr.isAdmin(request)) { %>
<% if (savedLoginId instanceof Integer) { %>
<p>&nbsp;&nbsp;&nbsp;<a href="<%= domainApp %>/viewLogin.do?id=<%= savedLoginId %>">Back to this login</a>
<br>&nbsp;&nbsp;&nbsp;<a href="<%= domainApp %>/viewLogins.do">Login Manager</a>
<% } else { %>
<p>&nbsp;&nbsp;&nbsp;<a href="<%= domainApp %>/viewLogins.do">Login Manager</a>
<% } %>
<br>&nbsp;&nbsp;&nbsp;<a href="<%= domainApp %>/viewGroups.do">Upload groups</a>
<% } else { %>
<p><a href="<%= domainApp %>/curate.do">Return to Curatorial Tools</a>.
<% } %>
 
</div>
<div class=right>
</div> 

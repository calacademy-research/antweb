<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="org.calacademy.antweb.Group" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.Login" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<% String domainApp = (new Utility()).getDomainApp(); %>

<%                            
        //Login accessLogin = LoginMgr.getAccessLogin(request);
        //Group accessGroup = GroupMgr.getAccessGroup(request);
        
        Login inviteLogin = (Login) request.getAttribute("inviteLogin");
        String inviteLink = domainApp + "/antwebInvite.do?email=" + inviteLogin.getEmail();
%>

<div class=left>
<h1>Success!</h1>
<hr></hr>
You have created the new login.  Email to <b><%= inviteLogin.getEmail() %></b> this link: 
<br><br>&nbsp;&nbsp;&nbsp;<a href="<%= inviteLink %>"><%= inviteLink %></a>

<% if (LoginMgr.isAdmin(request)) { %>
<p><a href="<%= domainApp %>/viewLogins.do">Return to Your Login Admin Screen</a>.
<% } else { %>
<p><a href="<%= domainApp %>/curate.do">Return to Curatorial Tools</a>.
<% } %>
 
</div>
<div class=right>
</div> 

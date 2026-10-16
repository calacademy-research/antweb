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
        String inviteLink = (String) request.getAttribute("inviteLink");
%>

<div class=left>
<h1>Success!</h1>
<hr></hr>
<% if (inviteLink != null) { %>
You have created the new login <b><%= Html.esc(inviteLogin.getName()) %></b>. Email to <b><%= Html.esc(inviteLogin.getEmail()) %></b> this link
and the username:
<br><br>&nbsp;&nbsp;&nbsp;<a href="<%= Html.escAttr(inviteLink) %>"><%= Html.esc(inviteLink) %></a>
<br><br>The link lets them set a password. It works once and for 14 days; press Invite again on the login to make a new one.
This link is shown only now: copy it before leaving the page.
<% } else { %>
This login already has a password, so no invitation link was made. Use Change Password, or ask them to use "Forgot your password?".
<% } %>

<% if (LoginMgr.isAdmin(request)) { %>
<p><a href="<%= domainApp %>/viewLogins.do">Return to Your Login Admin Screen</a>.
<% } else { %>
<p><a href="<%= domainApp %>/curate.do">Return to Curatorial Tools</a>.
<% } %>
 
</div>
<div class=right>
</div> 

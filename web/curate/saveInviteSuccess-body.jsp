<%-- Body of the invitation page: the login's name and email (request attribute inviteLogin) and the one-time
     set-password link (inviteLink; valid 14 days, shown only here), or a note that the login already has a
     password; then the list of changes (LoginEditPage.renderChanges). Read-only. --%>
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
The login <b><%= Html.esc(inviteLogin.getName()) %></b> is saved. Email to <b><%= Html.esc(inviteLogin.getEmail()) %></b> this link
and the username:
<br><br>&nbsp;&nbsp;&nbsp;<a href="<%= Html.escAttr(inviteLink) %>"><%= Html.esc(inviteLink) %></a>
<br><br>The link lets them set a password. It works once and for 14 days; open the login and press "Save and make a set-password link" to make a new one.
This link is shown only now: copy it before leaving the page.
<% } else { %>
This login already has a password, so no invitation link was made. Use Change Password, or ask them to use "Forgot your password?".
<% } %>

<%= org.calacademy.antweb.curate.login.LoginEditPage.renderChanges((java.util.List<String>) request.getAttribute(org.calacademy.antweb.curate.login.LoginEditPage.CHANGES_ATTR)) %>
<% if (LoginMgr.isAdmin(request)) { %>
<p><a href="<%= domainApp %>/viewLogins.do">Login Manager</a>
<% } else { %>
<p><a href="<%= domainApp %>/curate.do">Return to Curatorial Tools</a>.
<% } %>
 
</div>
<div class=right>
</div> 

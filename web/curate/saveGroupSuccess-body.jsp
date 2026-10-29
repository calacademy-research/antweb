<%-- Body of the upload group saved page: the request attribute savedGroupText (escaped) and, for admins, links to
     add an uploader to the group, reopen it (savedGroupId), the upload group list and the Login Manager.
     Read-only. --%>
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
<% String savedGroupText = (String) request.getAttribute("savedGroupText");
   Integer savedGroupId = (Integer) request.getAttribute("savedGroupId"); %>
<%= Html.esc(savedGroupText == null ? "The upload group was saved." : savedGroupText) %>

<% if (LoginMgr.isAdmin(request)) { %>
<% if (savedGroupId != null) { %>
<p><a href="<%= domainApp %>/addUploader.do?groupId=existing&amp;existingGroupId=<%= savedGroupId %>">Add an uploader to this upload group</a>
(finds or invites the person, gives upload rights, prepares the welcome email).
<p><a href="<%= domainApp %>/viewGroup.do?id=<%= savedGroupId %>">Open the upload group again</a>
<% } %>
<p><a href="<%= domainApp %>/viewGroups.do">Return to the list of upload groups</a>.
<br>
<p>Go to <a href="<%= domainApp %>/viewLogins.do">Login Manager</a>
<% } else { %>
<p><a href="<%= domainApp %>/curate.do">Return to Curatorial Tools</a>.
<% } %>
 
</div>
<div class=right>
</div> 

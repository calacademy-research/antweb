<%-- Body of the Museum Manager: the museums (request attribute museums, table museum) with code (link to the
     form, viewMuseum.do), name (link to the public museum page) and Active, and an "Add a museum" link. Read-only.
     Docs: docs/guides/manager-tools.md (Museum Manager). --%>
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="java.util.*" %>

<%@ page import="org.calacademy.antweb.Museum" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<% String domainApp = (new Utility()).getDomainApp(); %>

<%@include file="/curate/adminCheck.jsp" %>

<%
  ArrayList<Museum> museums = (ArrayList) request.getAttribute("museums");
  if (museums == null) museums = new ArrayList<>();
%>

<div class=admin_left>
<h1>Museum Manager</h1>

<div class="mgr_help" style="max-width:60em;margin:6px 0 12px 0">
The museum codes that specimen data refer to (the "owned by" column of an upload, for example CASC).
Each museum has a public page with its counts. Click a code to change the museum's name, title or
Active setting, or to delete it. Saving changes only the museum list; the counts on the museum pages
are recounted by the nightly run (or the Recalculate button on the museum page).
</div>

<p><a href="<%= domainApp %>/viewMuseum.do">Add a museum</a></p>

<table>
<tr><th>Code (edit)</th><th>Name (public page)</th><th>Active</th></tr>
<tr><th><hr></th><th><hr></th><th><hr></th></tr>
<%
   for (Museum museum : museums) {
     String code = museum.getCode() == null ? "" : museum.getCode();
     %>
<tr>
<td><a href="<%= domainApp %>/viewMuseum.do?code=<%= Html.escAttr(Html.url(code)) %>"><%= Html.esc(code) %></a></td>
<td><a href="<%= domainApp %>/museum.do?code=<%= Html.escAttr(Html.url(code)) %>"><%= Html.esc(museum.getName()) %></a></td>
<td><%= museum.getIsActive() ? "yes" : "no" %></td>
</tr>
 <% } %>
</table>
<br>

</div>

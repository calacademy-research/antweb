<%-- Body of one museum's form (request attribute museum): code (fixed once the museum exists), name, title and
     Active, saved by a POST with the form token to viewMuseum.do; Delete asks on a second page
     (ManageMuseumsAction.confirmDeleteHtml). Shows the action's message. Docs: docs/guides/manager-tools.md
     (Museum Manager). --%>
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="org.calacademy.antweb.Museum" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>

<% String domainApp = (new Utility()).getDomainApp(); %>

<%@include file="/curate/adminCheck.jsp" %>

<%
	    Museum thisMuseum = (Museum) request.getAttribute("museum");
	    if (thisMuseum == null) thisMuseum = new Museum();
	    String code = thisMuseum.getCode() == null ? "" : thisMuseum.getCode();
	    // An existing museum keeps its code: the code is the key that specimen data refer to.
	    boolean isExisting = !code.isEmpty();
%>

<div class=left>
<h1>&nbsp;<%= isExisting ? "Museum " + Html.esc(code) : "Add a museum" %></h1>
</div>


<div class=right>
<br>
<a href="<%= AntwebProps.getDomainApp() %>/manageMuseum.do">Museum Manager</a>
</div>

<div class=admin_left>

<div class="mgr_help" style="max-width:60em;margin:6px 0 12px 0">
<b>Code</b>: as used in the "owned by" column of specimen data (for example CASC); it cannot be changed
once the museum exists (add a new museum and delete the old one instead).
<b>Name</b>: shown on the museum page and in lists. <b>Title</b>: the name in the museum page's
"change view" menu. <b>Active</b>: the nightly run makes the museum's specimen map only for active museums.
<b>Save</b> writes these four values to the museum list at once; specimen data are not changed.
</div>

<% String message = (String) request.getAttribute("message");
   if (message != null && !message.isEmpty()) { %>
     <p><font color=red><%= message %></font></p>
<% } %>

    <form action="<%= domainApp %>/viewMuseum.do" method="POST"><%= Csrf.field(request) %>

    <p><b>Museum Code:</b>
<% if (isExisting) { %>
    <input type="text" class="input_200" value="<%= Html.escAttr(code) %>" readonly>
    <input type="hidden" name="code" value="<%= Html.escAttr(code) %>">
<% } else { %>
    <input type="text" class="input_200" name="code" value="">
<% } %>

    <p><b>Museum Name:</b>
    <input type="text" class="input_200" name="name" value="<%= Html.escAttr(thisMuseum.getName()) %>">

    <p><b>Museum Title:</b>
    <input type="text" class="input_200" name="title" value="<%= Html.escAttr(thisMuseum.getTitle()) %>">

    <p><b>Active:</b>
    <input type="checkbox" name="isActive" <%= thisMuseum.getIsActive() ? "checked" : "" %>>

    <div class="msg_actions" align="center">
<input type="submit" name="done" value="Save">
<a href="<%= domainApp %>/manageMuseum.do">Cancel</a>
<% if (isExisting) { %>
&nbsp;&nbsp;
<%-- Delete asks on a second page (ManageMuseumsAction.confirmDeleteHtml). --%>
<button type="submit" name="step" value="delete">Delete</button>
<% } %>
    </div>

</form>
</div>

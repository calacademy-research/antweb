<%-- Body of the upload groups list: what an upload group is, a link to Add an uploader, the group queries, a
     "New upload group" button (POST to newGroup.do) and every group (session attribute antwebGroups, table
     ant_group) with links to its form and public page. Read-only. Docs: docs/guides/adding-an-uploader.md. --%>
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="java.util.*" %>

<%@ page import="org.calacademy.antweb.Group" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<% String domainApp = (new Utility()).getDomainApp(); %>


<%@include file="/curate/adminCheck.jsp" %>


<!-- jsp:useBean id="antwebGroups" scope="session" class="java.util.ArrayList<Group>" / -->
<%
  ArrayList<Group> antwebGroups = (ArrayList) session.getAttribute("antwebGroups");
%>

<div class=admin_left>
<h1>Upload groups</h1>
    <hr></hr>

    <p>An <b>upload group</b> is the institution, lab or personal collection that owns specimen records on AntWeb.
    Each specimen record belongs to one upload group; the logins in the group upload its records, and
    <b>an upload replaces all specimens of the uploader's upload group</b>. (Older pages and the database call it a "group".)</p>

    <p><b>Someone wants to upload specimens?</b> Use <a href="<%= domainApp %>/addUploader.do">Add an uploader</a>:
    one form finds or invites the person, chooses or creates their upload group, gives the upload rights and prepares the welcome email.</p>

          <li><b>Relevant Queries</b>
            <%= Queries.getQueryList("Groups") %>

    <br><br>
    <form action="<%= domainApp %>/newGroup.do" method="POST"><%= org.calacademy.antweb.util.Csrf.field(request) %>
    <b>New upload group only (no login): <input type=submit class=submit value="Add &#187;"></b>
    </form>
    
    <br><br>
    <p><h2>Existing upload groups:</h2>

    <table>
    <tr><th>Number</th>
    <th>Name</th>
    <th>Short name</th>
    </tr>

    <% String hrLine = "<tr><td><hr></td><td><hr></td><td><hr></td></tr>"; %>
    <%= hrLine %>
    
    <% for (Group group : antwebGroups) { %>
    <tr>
    <td><%= group.getId() %>.</td>
    <td><a href="<%= domainApp %>/viewGroup.do?id=<%= group.getId() %>"><%= Html.esc(group.getName()) %></a>
    <a href='<%= AntwebProps.getDomainApp() %>/group.do?id=<%= group.getId() %>'><img src="<%= AntwebProps.getDomainApp() %>/image/view_icon.png" height="13" width="13" title="View"></a>
    </td>
    <td><%= Html.esc(group.getAbbrev() == null ? "" : group.getAbbrev()) %></td>
    </tr>

     <% } %>
    </table>

</div>
<div class=right>
<br>
<a href="<%= AntwebProps.getDomainApp() %>/viewLogins.do">Login Manager</a>
<br><a href="<%= AntwebProps.getDomainApp() %>/addUploader.do">Add an uploader</a>

<br><br>

<div class=green_module><span class=module_header>NOTE:</span></div>

</div > 

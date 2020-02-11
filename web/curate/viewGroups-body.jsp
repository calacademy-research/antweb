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
<h1>Group Manager</h1>
    <hr></hr>

          <li><b>Relevant Queries</b>
            <%= Queries.getQueryList("Groups") %>

    From here you can manage and edit existing groups and what they have access to.
    <br>Add new groups at bottom of page.
    <br>
    

    <br><br>
    <form action="<%= domainApp %>/newGroup.do" method="POST">
    <b>Add New Group: <input type=submit class=submit value="Add &#187;"><b>
    </form>
    
    <br><br>
    <p><h2>Existing Groups:</h2>

    <table>
    <tr><th>ID</th>
    <th>Name</th>
    </tr>

    <% String hrLine = "<tr><td><hr></td><td><hr></td></tr>"; %>
    <%= hrLine %>
    
    <% for (Group group : antwebGroups) { %>
    <tr>
    <td><%= group.getId() %>.</td>
    <td><a href="<%= domainApp %>/viewGroup.do?id=<%= group.getId() %>"><%= group.getName() %></a>
    <a href='<%= AntwebProps.getDomainApp() %>/group.do?id=<%= group.getId() %>'><img src="<%= AntwebProps.getDomainApp() %>/image/view_icon.png" height="13" width="13" title="View"></a>
    </td>
    </tr>

     <% } %>
    </table>

</div>
<div class=right>
<br>
<a href="<%= AntwebProps.getDomainApp() %>/viewLogins.do">Login Manager</a>

<br><br>

<div class=green_module><span class=module_header>NOTE:</span></div>

</div > 

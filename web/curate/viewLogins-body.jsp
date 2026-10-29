<%-- Body of the Login Manager: links to the login lists (list.do usrAdm, usrAdmLastLogin), the login queries,
     Add an uploader, "Add New Login" (POST to newLogin.do), and every login (request attribute antwebLogins) with
     its upload group. Read-only. Docs: docs/guides/curate-page.md (Manager tools). --%>
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="org.calacademy.antweb.Group" %>
<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>

<%@ page import="java.util.*" %>

<% String secureDomainApp = AntwebProps.getSecureDomainApp(); %>

<%@include file="/curate/adminCheck.jsp" %>

<jsp:useBean id="antwebGroups" scope="session" class="java.util.ArrayList" />

<%
 ArrayList<Login> logins =   (ArrayList<Login>) request.getAttribute("antwebLogins");
 if (logins == null) logins = new ArrayList<>();
%>

<div class="admin_left loginListPage">
    <h1>Login Manager</h1>
    <p><a href="<%= secureDomainApp %>/list.do?action=usrAdm">All logins with their permissions</a>
     | <a href="<%= secureDomainApp %>/list.do?action=usrAdmLastLogin">Logins by last login</a></p>
    <hr></hr>

          <li><b>Relevant Queries</b>
            <%= Queries.getQueryList("Login") %>

    <br><br>
    <form action="<%= secureDomainApp %>/newLogin.do" method="POST"><%= org.calacademy.antweb.util.Csrf.field(request) %>
    <b>Someone wants to upload specimens?</b> Use <a href="<%= secureDomainApp %>/addUploader.do">Add an uploader</a>
    (finds or invites the person, chooses or creates their upload group, gives the upload rights, prepares the welcome email).
    <br><br><b>Add New Login (any other account): <input type=submit class=submit value="Add &#187;"></b>
    <p><p><p><p>
    </form>
    <p><br>
    <p><h2>Existing Logins:</h2>

    <%-- Name gets about a third of the width (long email addresses break), the upload group the rest,
         so most group names fit on one line (a long one wraps rather than being cut). Phone width: the list takes the whole screen. --%>
    <style>
    table.loginList { width: 100%; table-layout: fixed; border-collapse: collapse; }
    table.loginList col.num { width: 3em; }
    table.loginList col.name { width: 35%; }
    table.loginList td { vertical-align: top; padding: 1px 6px 1px 0; }
    table.loginList td.name { overflow-wrap: anywhere; }
    @media (max-width: 760px) {
      .admin_left.loginListPage { float: none; width: auto; padding: 0 8px; }
    }
    </style>
    <table class="loginList">
    <colgroup><col class="num"><col class="name"><col class="group"></colgroup>
    <tr>
    <th>#</th>
    <th>Name</th>
    <th>Upload group</th>
    </tr>   
    <% String hrLine = "<tr><td><hr></td><td><hr></td><td><hr></td></tr>"; %>
    <%= hrLine %>
    <% 
      int count = 0;
      for (Login login: logins) {
        ++count;
    %>
        <tr>
        <td><%= count %>.</td> 
        <td class="name"><a href='<%= AntwebProps.getDomainApp() %>/viewLogin.do?id=<%= login.getId() %>'><%= Html.esc(login.getDisplayName()) %></a></td>
        <td class="group"><%= login.getGroupLink() %></td>
        </tr>
    <% } %>
    </table>

</div>

<div class=right>
    <br>
    <a href="<%= AntwebProps.getDomainApp() %>/viewGroups.do">Upload groups</a>
    <br><br>
    <div class=green_module>
       <span class=module_header>NOTE:</span>
    </div>

</div > 

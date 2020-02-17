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
 ArrayList<Login> logins =   (ArrayList<Login>) session.getAttribute("antwebLogins");
%>

<div class="admin_left">
    <h1>Login Manager</h1>
    <hr></hr>

          <li><b>Relevant Queries</b>
            <%= Queries.getQueryList("Login") %>

    <br><br>
    <form action="<%= secureDomainApp %>/newLogin.do" method="POST">
    <b>Add New Login: <input type=submit class=submit value="Add &#187;"></b>
    <p><p><p><p>
    </form>
    <p><br>
    <p><h2>Existing Logins:</h2>

    <table>

    <tr>
    <th>#</th>
    <th>Name</a></th>
    <th>Group</a></th>
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
        <td><a href='<%= AntwebProps.getDomainApp() %>/viewLogin.do?id=<%= login.getId() %>'><%= login.getDisplayName() %></a>        
        <td><%= login.getGroupLink() %></td>
        </tr>
    <% } %>
    </table>

</div>

<div class=right>
    <br>
    <a href="<%= AntwebProps.getDomainApp() %>/viewGroups.do">Group Manager</a>
    <br><br>
    <div class=green_module>
       <span class=module_header>NOTE:</span>
    </div>

</div > 

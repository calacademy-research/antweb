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
%>

<div class=admin_left>
<h1>Museum Manager</h1>

<hr>
<br><br>

<table>
<tr><th>Admin</th><th>Public</th></tr>
<tr><th><hr></th><th><hr></th></tr>
<%
   for (Museum museum : museums) {
     %>
<tr>
<td><a href="<%= domainApp %>/viewMuseum.do?code=<%= museum.getCode() %>"><%= museum.getCode() %></a></td>
<td><a href="<%= domainApp %>/museum.do?code=<%= museum.getCode() %>"><%= museum.getName() %></a></td>
</tr>
 <% } %>
</table>
<br>
<hr>

<br><br>

<a href="<%= AntwebProps.getDomainApp() %>/viewMuseum.do">Create new Museum</a>
<p>

</div>

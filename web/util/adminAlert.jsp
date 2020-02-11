
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="java.util.*" %>
<%@ page import="java.io.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.upload.*" %>
<%@ page import="org.calacademy.antweb.home.*" %>

<%
  Utility util = new Utility();
  String domainApp = util.getDomainApp();
%>

<%@include file="/curate/adminCheck.jsp" %>

<title>Admin Alert Mgr</title>

<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br>

<h1>Admin Alert Manager</h1>

<%
    List<AdminAlert> adminAlerts = AdminAlertMgr.getAdminAlerts();
    if (adminAlerts.size() > 0) { %>
    Alerts: <b><%= adminAlerts.size() %></b>
<%
        for (AdminAlert adminAlert : adminAlerts) {
          //A.log("adminAlert.jsp alert:" + adminAlert.getAlert() + " id:" + adminAlert.getId());        
          out.println("<br>&nbsp;&nbsp;&nbsp;" + adminAlert.getCreated() + " &nbsp;&nbsp;&nbsp;" + adminAlert.getAlert() + " &nbsp;&nbsp;&nbsp;<a href='" + AntwebProps.getDomainApp() + "/adminAlert.do?action=remove&id=" + adminAlert.getId() + "'><img src='" + AntwebProps.getDomainApp() + "/image/redX.png' width='10' /></a>"); 
        }
%>
        <br><br>
        To acknowledge an Admin Alert click: <img src='<%= domainApp %>/image/redX.png' width='10' />
 <br>
        To acknowledge all Admin Alerts click: <a href='<%= AntwebProps.getDomainApp() %>/adminAlert.do?action=removeAll'><img src='<%= domainApp %>/image/redX.png' width='15' /></a>

 <% } else { %>
        <b>No Admin Alerts.</b>
 <% } %>

<br><br>
<hr>

<small>(The link in the header is only visible to administrators)</small>

<br><br>
To refresh: <a href='<%= AntwebProps.getDomainApp() %>/adminAlert.do?action=refresh'>Here</a>

<br><br>
QueryAlerts: <%= AdminAlertMgr.getQueryAlerts() %>

<br><br>
Full list: <a href='<%= AntwebProps.getDomainApp() %>/query.do?action=curiousQuery&name=adminAlerts'>Admin Alerts</a>

<br><br>
Admin Alert <a href='<%= AntwebProps.getDomainApp() %>/web/log/adminAlerts.log'>Log File</a>
<br>&nbsp;&nbsp;<small>These are more developer oriented... from places in the code that do not have access to a database connection.</small>

<% if (LoginMgr.isDeveloper(request)) { %>
<br><br>Create test alert: <a href='<%= AntwebProps.getDomainApp() %>/utilData.do?action=adminAlertTest'>Here</a>
<% } %>